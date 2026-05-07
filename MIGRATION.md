# DigitalOcean → Hetzner Migration

Plan for migrating the deadly web stack off DigitalOcean (free credits expiring) to Hetzner Cloud.

## Goals

- Drop monthly hosting cost (~$4–5/mo on Hetzner vs. $24/mo on DO).
- Stand up a real `beta` environment that can be created and torn down on demand.
- **Zero user-visible errors** during prod cutover by reverse-proxying through DO Caddy until DNS settles.
- Both providers supported in CI during the cutover window so we can fall back.

## Target architecture

| Env  | Provider | Server type | Spec               | Cost (running) | Lifecycle              |
|------|----------|-------------|--------------------|----------------|------------------------|
| beta | Hetzner  | CPX11       | 2 vCPU / 2GB / 40GB| ~€4.35/mo      | On-demand (hourly)     |
| prod | Hetzner  | CPX11       | 2 vCPU / 2GB / 40GB| ~€4.35/mo      | Long-running           |

- Location: `ash` (Ashburn, VA).
- IPv4 + IPv6 enabled.
- No Primary IP — DNS is updated when the IP changes (gated by a workflow toggle).

## Code changes (status)

- [x] `infra/hetzner/` — Terraform: `beta` + `prod` servers, firewalls, B2-backed state.
- [x] `scripts/setup-infra-secrets.sh` — uploads `HCLOUD_TOKEN`, `GODADDY_KEY`, `GODADDY_SECRET`.
- [x] `.github/workflows/infra-manage.yml` — `hetzner` provider option (default), `alpha` removed.
- [x] `.github/workflows/web-deploy.yml` — `provider` input added (default `hetzner`), TF dir per provider.
- [ ] `.github/workflows/web-deploy.yml` — `update_dns` checkbox (default off; flip default later for beta).
- [ ] `caddy/Caddyfile.cutover` — DO-side variant that proxies API/WS to Hetzner over validated TLS.
- [ ] `scripts/pull-cert-from-do.sh` — pulls live LE cert + key from DO into `.secrets/` (Phase 0).
- [ ] `scripts/push-cert-to-hetzner.sh` — pushes cert from `.secrets/` into Hetzner Caddy storage (Phase 1).
- [ ] `scripts/migrate-cutover.sh` — orchestrates snapshot → ship → swap → verify, with `--rollback`.

## DNS

- Provider: GoDaddy. Records to manage: `thedeadly.app`, `share.thedeadly.app`.
- TTL is set to **600s** (GoDaddy's minimum). Keep at 600s permanently.
- Updates are automated via GoDaddy API in `web-deploy.yml`, gated by the `update_dns` checkbox. Default off for now. The PATCH only fires if the resolved IP differs from the current A record.
- After PATCH, poll `dig @1.1.1.1` for up to **~700s** (one full TTL plus buffer). Treat timeout as a soft warning, not a failure — the PATCH itself either succeeded or didn't; propagation just takes its course.

## Secrets (in GitHub already)

| Secret             | Source                                  | Purpose                     |
|--------------------|-----------------------------------------|-----------------------------|
| `HCLOUD_TOKEN`     | `.secrets/hetzner-key.txt`              | Hetzner Cloud API           |
| `GODADDY_KEY`      | `.secrets/godaddy-key.txt` (KEY:SECRET) | GoDaddy DNS API key         |
| `GODADDY_SECRET`   | `.secrets/godaddy-key.txt`              | GoDaddy DNS API secret      |
| `DO_API_TOKEN`     | `infra/digitalocean/terraform.tfvars`   | DO (kept until DO destroyed)|
| `SSH_PRIVATE_KEY`  | `ssh-key-2026-03-15.key`                | Deploy access               |
| `SSH_PUBLIC_KEY`   | `ssh-key-2026-03-15.key.pub`            | Provisioned via cloud-init  |

---

# Cutover plan

The strategy is to reverse-proxy from DO → Hetzner during the cutover, so there is **one canonical database** (Hetzner's) and **zero user-visible errors** while DNS propagates. Stragglers with cached DNS hit DO Caddy, which forwards to Hetzner.

There is a brief (~30 sec) write-freeze on DO during the DB snapshot. After that, DO never writes to its DB again — its API container stays stopped and Caddy proxies API/WS to Hetzner.

## Phase 0 — Build the artifacts (no Hetzner dependency)

Everything in this phase is pure prep — laptop-side scripting, GitHub workflow edits, and pulling the cert from DO. Hetzner is not involved yet.

1. **Add the `update_dns` input to `web-deploy.yml`** (default `false`). When checked, after deploy succeeds the workflow:
   - Fetches the env's IP from Terraform output.
   - Gets the current GoDaddy A record for `${SITE_ADDRESS}`.
   - PATCHes only if the IP differs.
   - Polls `dig @1.1.1.1 ${SITE_ADDRESS}` for up to ~700s (TTL + buffer). Timeout is a soft warning, not a failure.
2. **Pull the live LE cert from DO into `.secrets/`.** The cert is bound to the hostname, not the IP, so it's portable. We stage it locally so Phase 1 can install it on Hetzner without needing DO ↔ HZ direct SSH at provisioning time.

   ```bash
   # scripts/pull-cert-from-do.sh
   mkdir -p .secrets/le-cert
   ssh deploy@$DO_IP 'docker compose exec -T caddy tar -czf - -C /data/caddy/certificates/acme-v02.api.letsencrypt.org-directory thedeadly.app' \
     > .secrets/le-cert/thedeadly.app.tar.gz
   ```

   `.secrets/` is gitignored. **Re-run this script just before Phase 1.2** if more than a few weeks have passed — DO Caddy may have renewed and our copy could be stale (LE renews ~30 days before expiry). Quick check:
   ```bash
   tar -xzOf .secrets/le-cert/thedeadly.app.tar.gz thedeadly.app/thedeadly.app.crt | openssl x509 -noout -enddate
   ```

3. **Create `caddy/Caddyfile.cutover`** — DO-side variant of the live Caddyfile that proxies `/api/*` and `/ws/*` to `https://${HETZNER_IP}` with **validated** TLS, using `tls_server_name thedeadly.app` so Caddy validates the cert against the hostname instead of the IP. Static UI continues to serve from the bind-mounted `ui-out`. The Hetzner IP is interpolated by the cutover script before upload.

   Sketch:
   ```caddy
   reverse_proxy /api/* https://${HETZNER_IP} {
     transport http {
       tls
       tls_server_name thedeadly.app
     }
     header_up Host {host}
   }
   ```
   Full TLS validation end-to-end — both sides hold the same real LE cert.

4. **Write `scripts/migrate-cutover.sh`** (see "Cutover script" section below).
5. **Take a manual analytics.db backup to B2.** The deploy workflow only backs up users.db. One-shot:
   ```bash
   ssh deploy@$DO_IP 'sqlite3 /opt/deadly/api-data/analytics.db ".backup /tmp/analytics-pre-cutover.db"'
   scp deploy@$DO_IP:/tmp/analytics-pre-cutover.db /tmp/
   aws s3 cp /tmp/analytics-pre-cutover.db s3://deadly-backups/db/ --endpoint-url ...
   ```

## Phase 1 — Stand up Hetzner prod (no real data, no impact on users)

Goal: prove the Hetzner stack works end-to-end with the real cert, before touching production data. Soak as long as needed.

| # | What | How | Verify |
|---|------|-----|--------|
| 1.1 | (If needed) re-pull cert from DO | `scripts/pull-cert-from-do.sh` — only if `.secrets/le-cert` is older than a couple weeks. | `openssl x509 -enddate` on the cert shows >60 days remaining. |
| 1.2 | Provision Hetzner prod box | Run **Web - Infra** with `action=launch, provider=hetzner, environment=prod`. | Workflow prints IP. SSH works as `deploy@<hz-ip>`. |
| 1.3 | Push LE cert from `.secrets/` to Hetzner | `scripts/push-cert-to-hetzner.sh` — extracts the tarball into Hetzner's Caddy storage volume; ensures Caddy UID owns the files; key is `0600`. | `ssh deploy@$HZ_IP 'docker compose exec caddy ls /data/caddy/certificates/.../thedeadly.app/'` shows cert + key. |
| 1.4 | Deploy code with empty DBs | Run **Web - Deploy** with `environment=prod, provider=hetzner, ref=main, update_dns=false`. | `curl --resolve thedeadly.app:443:<hz-ip> https://thedeadly.app/api/health` returns 200 **and serves the real LE cert** (`curl -vI` confirms issuer is Let's Encrypt, not Caddy Internal). Schemas auto-created on first start. |
| 1.5 | Snapshot DO DBs (live) and ship to HZ for testing | `ssh deploy@$DO_IP 'sqlite3 /opt/deadly/api-data/users.db ".backup /tmp/users.db" && sqlite3 /opt/deadly/api-data/analytics.db ".backup /tmp/analytics.db"'`, then `scp` DO→local→HZ to `/opt/deadly/api-data/`, fix ownership, `docker compose restart api`. **No DO downtime — `.backup` is an online operation.** This rehearses Phase 2.2 + 2.3. | Row counts on HZ match DO snapshot. |
| 1.6 | Real-data smoke test via `/etc/hosts` override | On laptop, add `<HZ_IP>  thedeadly.app` and `<HZ_IP>  share.thedeadly.app` to `/etc/hosts`. Visit `https://thedeadly.app` in a browser. Browse the site, log in (full OAuth round-trip — Google redirects back to the hostname, which now points to HZ on your laptop), check favorites, exercise analytics. **Remove the hosts entries when done.** Chrome caches DNS — restart browser or flush via `chrome://net-internals/#dns` if needed. | Real LE cert in browser (no warning). UI loads, login works, your real data appears. |
| 1.7 | Confirm GoDaddy TTL is 600s | Already done. Verify with `dig +noall +answer thedeadly.app`. | TTL ≤ 600. |

After Phase 1, Hetzner prod is running with the real cert and a recent (but soon-to-be-stale) snapshot of prod data. Phase 2 takes a fresh snapshot before cutover; the Phase 1 data is throwaway.

## Phase 2 — Cutover (the actual moment, ~5 minutes wall-clock)

This is where `scripts/migrate-cutover.sh` runs. Each numbered step below corresponds to a step in the script.

| # | What | How | Verify |
|---|------|-----|--------|
| 2.1 | Stop DO API (and analytics flush) | `ssh deploy@$DO_IP 'cd /opt/deadly && docker compose stop api'` | `docker compose ps api` shows Exited. Caddy + UI still serving (test in browser). |
| 2.2 | Snapshot DO DBs | `ssh deploy@$DO_IP 'sqlite3 /opt/deadly/api-data/users.db ".backup /tmp/users.db" && sqlite3 /opt/deadly/api-data/analytics.db ".backup /tmp/analytics.db"'` | `ls -la /tmp/{users,analytics}.db` on DO shows recent timestamps. |
| 2.3 | Ship snapshots to local then to Hetzner | `scp` DO→local→Hetzner, place at `/opt/deadly/api-data/{users,analytics}.db`, ensure `deploy:deploy` ownership. | File sizes match across the three locations. |
| 2.4 | Restart Hetzner API to load DBs | `ssh deploy@$HZ_IP 'cd /opt/deadly && docker compose restart api'` | `curl --resolve thedeadly.app:443:$HZ_IP https://thedeadly.app/api/health` returns 200. Spot-check that beta_applicants count matches DO snapshot count. |
| 2.5 | Render `Caddyfile.cutover` with `$HZ_IP` and ship to DO | `envsubst` or sed; copy into the running Caddy container; reload. | `ssh deploy@$DO_IP 'docker compose exec caddy caddy validate --config /etc/caddy/Caddyfile'` passes. |
| 2.6 | Reload DO Caddy | `ssh deploy@$DO_IP 'docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile'` | No errors in Caddy logs. `curl https://thedeadly.app/api/health` (real DNS, hits DO IP, proxies to Hetzner) returns 200. |
| 2.7 | End-to-end verification before DNS flip | Browser test: open `https://thedeadly.app`, log in (OAuth roundtrip via DO→Hetzner), favorite a show, check it persists. | Favorite shows up in Hetzner's DB: `ssh deploy@$HZ_IP 'sqlite3 /opt/deadly/api-data/users.db "SELECT * FROM favorite_shows ORDER BY added_at DESC LIMIT 5"'`. |

**At this point all real traffic is being served by Hetzner via DO Caddy as a proxy. DNS still points to DO. No user has seen an error.**

## Phase 3 — DNS flip (when you're confident)

Soak as long as you want — minutes, hours, overnight. When ready:

| # | What | How | Verify |
|---|------|-----|--------|
| 3.1 | PATCH GoDaddy A records | Either via the deploy workflow (`update_dns=true`) or one-off `curl` from the cutover script. Both `thedeadly.app` and `share.thedeadly.app` → Hetzner IP. | `dig @1.1.1.1 thedeadly.app` returns Hetzner IP within a few seconds. |
| 3.2 | Watch traffic shift | Tail Caddy logs on both. DO Caddy shows requests tapering; Hetzner Caddy shows requests ramping. | After ~600s, DO traffic should be near zero. Both sides serve the same valid cert throughout — no trust warnings at any point. |

## Phase 4 — Decommission (T+24h)

| # | What | How |
|---|------|-----|
| 4.1 | Confirm DO is receiving zero API traffic | Tail Caddy access log on DO; should be silent. |
| 4.2 | Confirm Hetzner backups working | Check B2 bucket; deploy workflow's backup step runs on prod deploys. |
| 4.3 | Destroy DO prod | **Web - Infra** with `action=destroy, provider=digitalocean, environment=prod`. |
| 4.4 | Destroy DO beta (if it was launched) | Same workflow, `environment=beta`. |
| 4.5 | Clean up `infra/digitalocean/*.tf` | Remove dev/legacy `digitalocean_droplet.server` and `alpha` resources; keep DO module retired. |
| 4.6 | Optionally drop DO from CI | Remove `digitalocean` choice from workflow inputs if you don't expect to fall back. |
| 4.7 | Revoke DO API token | DO console → API → revoke. Optionally delete `DO_API_TOKEN` GitHub secret. |
| 4.8 | Delete staged cert from `.secrets/` | `rm -rf .secrets/le-cert/` | Directory gone. Hetzner is the sole holder of the prod private key. |

## Phase 5 — Force a cert renewal to verify the renewal path

The cert we copied from DO will renew itself naturally ~30 days before expiry. We don't want to find out 60 days from now that auto-renewal is silently broken. Force a renewal while we're still paying attention.

Caddy has no first-class `renew` command. The reliable approach is to delete the cert from storage and reload — Caddy notices it's missing and re-acquires via ACME. Caddy 2.5+ keeps the in-memory cert serving requests until the new one arrives, so there's no serving gap.

| # | What | How | Verify |
|---|------|-----|--------|
| 5.1 | Note the current cert's `notBefore` | `echo \| openssl s_client -connect thedeadly.app:443 -servername thedeadly.app 2>/dev/null \| openssl x509 -noout -dates` | Record the date. |
| 5.2 | Delete cert files + reload Caddy | `ssh deploy@$HZ_IP 'cd /opt/deadly && docker compose exec caddy rm -f /data/caddy/certificates/acme-v02.api.letsencrypt.org-directory/thedeadly.app/thedeadly.app.{crt,key,json} && docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile'` | `docker compose logs caddy --tail 50` shows `obtaining certificate` → `certificate obtained successfully` within ~15s. |
| 5.3 | Verify new cert is being served | Re-run the openssl check from 5.1. | `notBefore` is within the last few minutes. Browser load shows the green padlock; no users reported errors. |

**LE rate limit caveat:** Let's Encrypt allows 5 duplicate certificates per week per hostname. One forced renewal here is fine; don't repeat-test more than a couple times.

After this phase, we've verified the full renewal path works against this Hetzner box. Future automatic renewals at T-30 days should just work.

## Rollback

At any point during Phase 2, before Phase 3 (DNS flip):

- Re-deploy the original Caddyfile on DO and restart DO API:
  ```bash
  ssh deploy@$DO_IP 'cd /opt/deadly && \
    docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile.original && \
    docker compose start api'
  ```
- DO is back to serving as before. Hetzner is untouched (and still has the DB snapshot from Phase 2.3, but that's fine — no harm in leaving it).

After Phase 3 (DNS flipped), rollback means flipping DNS back to DO **and** copying any post-cutover Hetzner DB writes back to DO. This is messy and we should avoid needing it. Soak in Phase 2.7 long enough to be confident before flipping DNS.

The cutover script (`scripts/migrate-cutover.sh --rollback`) automates the pre-Phase-3 rollback.

## Open risks

- **Private key staged in `.secrets/`.** The LE private key briefly lives on the laptop in `.secrets/le-cert/` between Phase 0.2 (pull) and Phase 1.3 (push). `.secrets/` is gitignored. Delete the staged copy after Phase 4 decommission — no reason to keep prod private keys around.
- **Cert renewal post-cutover.** The copied cert is valid ~90 days from its original issuance. Caddy on Hetzner attempts renewal ~30 days before expiry; DNS resolves to Hetzner by then, HTTP-01 succeeds. If cutover happens close to expiry, force a renewal on DO before pulling the cert so we start with a fresh ~90 days.
- **Stale cert in `.secrets/`.** If Phase 1 slips weeks after Phase 0.2, DO Caddy may have renewed and our staged copy is no longer the live cert. Re-run `pull-cert-from-do.sh` right before Phase 1.3 (Phase 1.1 covers this).
- **OAuth provider redirect URIs**: the registered callbacks (`https://thedeadly.app/api/auth/callback/google`, etc.) don't change. They go through DO Caddy → Hetzner during cutover, then directly to Hetzner after DNS flip. No re-registration needed.
- **Service worker caching**: if the web app ships a service worker, some users may see stale assets. Worth a quick check (`ls ui-out/sw.js` or similar). If present, consider a cache-bust as part of the cutover.
- **GHA workflows running concurrent with cutover** could re-deploy and clobber the cutover Caddyfile. Pause merges to `main` during the cutover window, or rely on the fact that the cutover script is the last thing to touch DO.

## Cost summary

| Item                          | Cost                       |
|-------------------------------|----------------------------|
| prod (CPX11, 24/7)            | €4.35/mo (~$4.70)          |
| beta (CPX11, occasional)      | ~€0.01/hr while running    |
| GoDaddy DNS                   | $0 (existing)              |
| B2 (tfstate + backups)        | unchanged                  |

vs. current DO prod alone at ~$24/mo.

---

# Cutover script outline

`scripts/migrate-cutover.sh` — invoke with `--do-ip`, `--hz-ip`, optional `--rollback`. Pseudocode:

```
parse args
load HCLOUD_TOKEN, GODADDY_KEY, GODADDY_SECRET from .secrets/
preflight checks:
  - confirm DNS TTL is 600
  - confirm Hetzner /api/health responds (via --resolve)
  - confirm DO /api/health responds
  - prompt: "Continue with cutover? (y/n)"

phase 2.1: ssh DO -> docker compose stop api
phase 2.2: ssh DO -> sqlite3 .backup for both DBs
phase 2.3: scp DO:/tmp/*.db -> local -> Hetzner:/opt/deadly/api-data/
phase 2.4: ssh Hetzner -> docker compose restart api
           verify health via --resolve
phase 2.5: render Caddyfile.cutover with $HZ_IP, scp to DO
phase 2.6: ssh DO -> docker cp + caddy reload
           verify proxy works (curl real DNS -> DO -> Hetzner)
phase 2.7: prompt: "Soak window. Test in browser. Continue to DNS flip? (y/n/rollback)"

phase 3.1: PATCH GoDaddy A records (only if --flip-dns specified)
phase 3.2: poll dig @1.1.1.1 for up to ~700s; timeout = soft warn
phase 3.3: print summary, exit
```

Rollback path (`--rollback`):
```
ssh DO -> caddy reload original Caddyfile
ssh DO -> docker compose start api
print summary
```
