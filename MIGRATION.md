# DigitalOcean → Hetzner Migration

Plan for migrating the deadly web stack off DigitalOcean (free credits expiring) to Hetzner Cloud.

## Status (as of 2026-05-07)

**Phases 0–3 done.** Production traffic is being served by Hetzner. DNS is flipped. DO is idle.

- Phase 0 — artifacts built ✓
- Phase 1 — Hetzner prod stood up, soaked, real-data smoke tested ✓
- Phase 2 — cutover ran via `scripts/migrate-cutover.sh` at ~17:00 UTC ✓
- Phase 3 — apex A flipped to `178.156.208.143`, fully propagated to all major resolvers within ~15 min ✓
- **Phase 4 — Decommission** (T+24h, i.e. **2026-05-08 afternoon**) ← next
- **Phase 5 — Force cert renewal** (any time after Phase 4)
- **Phase 6 — Repo cleanup** (after Phase 5 verified) ← deletes this doc

**Live IPs:**
- Hetzner prod: `178.156.208.143` (this is what `thedeadly.app` resolves to now)
- DO prod (idle, awaiting destroy): `147.182.140.188`

**If you're picking this up cold:** start at the [Phase 4 checklist](#phase-4--decommission-t24h). Everything before it is reference for what already happened.

## Next steps (cold-session checklist)

For when you re-open this doc tomorrow with no working memory.

1. **Sanity check** — confirm HZ is still serving and DNS still resolves there:
   ```bash
   dig @1.1.1.1 +short thedeadly.app A   # expect 178.156.208.143
   curl -fsS https://thedeadly.app/api/health   # expect 200
   ```
2. **Phase 4.1** — tail DO Caddy access log; expect silence on `/api/*` and `/ws/*`. Command in §4.1.
3. **Phase 4.2** — confirm a post-cutover backup exists in B2. Command in §4.2.
4. **Phase 4.3** — run **Web - Infra** workflow with `action=destroy, provider=digitalocean, environment=prod`. This is the irreversible one — only after 4.1 and 4.2 are clean.
5. **Phase 4.4–4.8** — DO beta destroy, Terraform cleanup, drop DO from workflow inputs, revoke `DO_API_TOKEN`, wipe `.secrets/le-cert/` and `.secrets/cutover-dbs/`. None are urgent; do at your leisure.
6. **Phase 5 (any time, before cert hits 60 days remaining)** — force a cert renewal on HZ to prove the ACME path works. Steps in §5.
7. **Phase 6 (after Phase 5 succeeds)** — delete this doc and the one-shot migration scripts from the repo. Steps in §6.

If anything in step 1 looks wrong, **stop** and read the [Learnings](#learnings-2026-05-07-cutover-run) section before touching anything — that's where the actual gotchas from the live run are recorded.

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
- [x] `.github/workflows/web-deploy.yml` — `update_dns` checkbox (default off; flip default later for beta).
- [x] `caddy/Caddyfile.cutover` — DO-side variant that proxies API/WS to Hetzner over validated TLS.
- [x] `scripts/pull-cert-from-do.sh` — pulls live LE cert + key from DO into `.secrets/` (Phase 0).
- [x] `scripts/push-cert-to-hetzner.sh` — pushes cert from `.secrets/` into Hetzner Caddy volume (Phase 1).
- [x] `scripts/migrate-cutover.sh` — orchestrates snapshot → ship → swap → verify, with `--rollback`.

Local tooling required to run the cutover script: `jq`, `envsubst` (gettext), `dig` (bind-utils), `aws` (awscli2), plus SSH access to both boxes.

## DNS

- Provider: GoDaddy. Records to manage: `thedeadly.app`, `share.thedeadly.app`.
- TTL is set to **600s** (GoDaddy's minimum). Keep at 600s permanently. **Caveat verified during Phase 1.7 (2026-05-07):** the apex A record and `www` CNAME were actually at TTL 3600 in GoDaddy (only `share` was already at 600). Both were lowered to 600 via the GoDaddy API at Phase 1.7. Wait ~1 hour after this change before cutover so resolver caches fully refresh to the new TTL — otherwise post-flip stragglers can hold the IP for the old TTL window instead of 10 minutes.
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

**Status:** Steps 1–4 complete. Hetzner Terraform module + workflow wiring committed (`33143b8f`). `HCLOUD_TOKEN`, `GODADDY_KEY`, `GODADDY_SECRET` pushed manually via `gh secret set` (see Open risks for why we skipped `setup-infra-secrets.sh`). LE cert staged at `.secrets/le-cert/thedeadly.app.tar.gz`. Step 5 (analytics.db backup) deferred to immediately before Phase 2 cutover.

1. **Add the `update_dns` input to `web-deploy.yml`** (default `false`). When checked, after deploy succeeds the workflow:
   - Fetches the env's IP from Terraform output.
   - Gets the current GoDaddy A record for `${SITE_ADDRESS}`.
   - PATCHes only if the IP differs.
   - Polls `dig @1.1.1.1 ${SITE_ADDRESS}` for up to ~700s (TTL + buffer). Timeout is a soft warning, not a failure.
2. **Pull the live LE certs from DO into `.secrets/`.** The certs are bound to hostnames, not the IP, so they're portable. We stage all three (`thedeadly.app`, `share.thedeadly.app`, `www.thedeadly.app`) locally so Phase 1 can install them on Hetzner without needing DO ↔ HZ direct SSH at provisioning time. **All three are required:** the Caddyfile serves apex + share + a www→apex redirect; if any of the three is missing on HZ, users hitting that hostname after the DNS flip see a TLS error until ACME succeeds (up to ~20 min).

   ```bash
   DO_IP=<do-ip> scripts/pull-cert-from-do.sh
   ```

   Default hostname list covers all three. Streams `tar -czf -` per hostname from inside DO's Caddy container directly to `.secrets/le-cert/<host>.tar.gz`. Also extracts the `.crt` (no key) for inspection. `.secrets/` is gitignored.

   **Re-run before Phase 1.3** if more than a few weeks have passed — DO Caddy may have renewed and our copies could be stale (LE renews ~30 days before expiry). The push script also checks expiry and refuses to ship an already-expired cert.

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
| 1.2 | Provision Hetzner prod box | Run **Web - Infra** with `action=launch, provider=hetzner, environment=prod`. | ✔️ Done 2026-05-07 (run 25498015079). Prod IPv4: `178.156.208.143`, IPv6: `2a01:4ff:f0:5676::1`. Terraform state in B2 bucket `deadly-tfstate` (endpoint `s3.us-west-004.backblazeb2.com`); credentials in `.secrets/b2-api-keys.txt` and GitHub `B2_TFSTATE_KEY_ID` / `B2_TFSTATE_APP_KEY` secrets. |
| 1.3 | Push LE certs from `.secrets/` to Hetzner | `HZ_IP=<hz-ip> scripts/push-cert-to-hetzner.sh` — pushes all three hostnames' tarballs into the `deadly_caddy_data` docker volume via a throwaway `busybox` container. Safe to run with Caddy already running, but **if Caddy is already running you must `docker compose restart caddy` afterward** — `caddy reload` does not re-scan storage for new cert files. UIDs/perms are preserved from the DO tarball; this works because both sides use the same Caddy image. | ✔️ Done 2026-05-07. All three certs (apex 45d / share / www) extracted into `deadly_caddy_data`. After Caddy restart, `curl https://{thedeadly,share.thedeadly,www.thedeadly}.app/` all return real LE certs (issuers `E7`/`E8`); www redirects 301 to apex as expected. |
| 1.4 | Deploy code with empty DBs | Run **Web - Deploy** with `environment=prod, provider=hetzner, ref=migrate-to-hz, update_dns=false`. **Note:** while this branch is unmerged, both `--ref` (workflow source) and `ref` input (code to deploy) must point at `migrate-to-hz` — `infra/hetzner/` does not exist on `main` yet. Also, `Web - Build Images` only auto-fires on push to `main` filtered by `api/**`/`ui/**`/etc., so before the first migrate-to-hz deploy you must dispatch **Web - Build Images** with `ref=migrate-to-hz` to publish images at the branch SHA the deploy will look up. | ✔️ Done 2026-05-07 (build run 25500328395, deploy run 25500546525). `curl --resolve thedeadly.app:443:178.156.208.143 https://thedeadly.app/api/health` returns 200 with issuer `Let's Encrypt E7` (real cert, not Caddy Internal). Schemas auto-created on first start. |
| 1.5 | Snapshot DO DBs (live) and ship to HZ for testing | `ssh deploy@$DO_IP 'sqlite3 /opt/deadly/api-data/users.db ".backup /tmp/users.db" && sqlite3 /opt/deadly/api-data/analytics.db ".backup /tmp/analytics.db"'`, then `scp` DO→local→HZ. **WAL gotcha:** the DBs run in WAL mode, so `users.db-wal` and `users.db-shm` exist alongside `users.db` whenever the API has been started. If you drop a fresh `users.db` on top of a stale WAL, sqlite re-applies the old WAL frames and your snapshot looks empty. The correct sequence on HZ: `docker compose stop api` → `rm -f /opt/deadly/api-data/{users,analytics}.db{,-wal,-shm}` → place the new `users.db` and `analytics.db` (no WAL/SHM) → `chown deploy:deploy` → `docker compose start api`. This rehearses Phase 2.2 + 2.3. | ✔️ Done 2026-05-07. After WAL cleanup, HZ row counts match DO exactly: users.db 104 beta_applicants / 87 auth_users / 8 favorite_shows; analytics.db 415 daily rollups / 9712 events (vs. 9736 live on DO — 24-event drift is expected since DO keeps serving until Phase 2.1). |
| 1.6 | Real-data smoke test via `/etc/hosts` override | On laptop, add `<HZ_IP>  thedeadly.app` and `<HZ_IP>  share.thedeadly.app` (and optionally `www.thedeadly.app`) to `/etc/hosts`. Visit `https://thedeadly.app` in a browser. Browse the site, log in (full OAuth round-trip — Google redirects back to the hostname, which now points to HZ on your laptop), check favorites, exercise analytics. **Remove the hosts entries when done.** Chrome caches DNS — restart browser or flush via `chrome://net-internals/#dns` if needed. | Real LE cert in browser (no warning). UI loads, login works, your real data appears. CLI pre-checks via curl: apex `/api/health` and `/` return 200 with valid LE cert; `share.thedeadly.app/` returns 200 with valid LE cert; `www.thedeadly.app/` returns 301 → apex. ✔️ Browser smoke 2026-05-07: OAuth login round-trip succeeded, favorite added in UI, row landed in HZ `favorite_shows` (count 8 → 9, new row at top with current timestamp). |
| 1.7 | Confirm GoDaddy TTL is 600s | Verify with `dig @1.1.1.1 +noall +answer thedeadly.app` (use `@1.1.1.1` to bypass any local `/etc/hosts` override). If apex A or `www` CNAME come back as 3600, lower them via the GoDaddy API: `curl -X PUT -H "Authorization: sso-key $KEY:$SECRET" -d '[{"data":"<existing>","ttl":600}]' https://api.godaddy.com/v1/domains/thedeadly.app/records/<TYPE>/<name>` (name=`@` for apex). | ✔️ 2026-05-07: pre-fix `thedeadly.app` A and `www` CNAME were both 3600; PUT'd both to 600. GoDaddy now returns TTL 600 for all three; resolver caches refresh within the old 3600s window. |

After Phase 1, Hetzner prod is running with the real cert and a recent (but soon-to-be-stale) snapshot of prod data. Phase 2 takes a fresh snapshot before cutover; the Phase 1 data is throwaway.

## Phase 2 — Cutover (the actual moment, ~5 minutes wall-clock)

**Status: ✓ Done 2026-05-07 (~17:00 UTC).** `scripts/migrate-cutover.sh --do-ip 147.182.140.188 --hz-ip 178.156.208.143` (no `--flip-dns`). All baseline/snapshot/HZ row-count fingerprints matched exactly. ~30–60s window of `/api/*` 502s between 2.1 (api stop) and 2.6 (Caddy reload). After 2.6, real third-party traffic flowed cleanly: an Android client (v2.24.0) did `app_open` → `cold_start` → `playback_start` for `gd91-06-14`, all written to HZ `analytics.db` while DNS still pointed at DO. Soaked ~30 min before flipping DNS. **Two script bugs fixed during the run; see [Learnings](#learnings-2026-05-07-cutover-run).**

This is where `scripts/migrate-cutover.sh` runs. Each numbered step below corresponds to a step in the script.

| # | What | How | Verify |
|---|------|-----|--------|
| 2.1 | Stop DO API (and analytics flush) | `ssh deploy@$DO_IP 'cd /opt/deadly && docker compose stop api'` | `docker compose ps api` shows Exited. Caddy + UI still serving (test in browser). |
| 2.2 | Snapshot DO DBs | `ssh deploy@$DO_IP 'sqlite3 /opt/deadly/api-data/users.db ".backup /tmp/users.db" && sqlite3 /opt/deadly/api-data/analytics.db ".backup /tmp/analytics.db"'` | `ls -la /tmp/{users,analytics}.db` on DO shows recent timestamps. |
| 2.3 | Ship snapshots to local then to Hetzner | `scp` DO→local→Hetzner. **Stop the HZ API and remove the existing DB+WAL+SHM trio first** (see Phase 1.5 WAL gotcha) — otherwise the stale WAL clobbers the snapshot on restart. Sequence: `docker compose stop api` → `rm -f /opt/deadly/api-data/{users,analytics}.db{,-wal,-shm}` → place new `users.db`/`analytics.db` → `chown deploy:deploy` → `docker compose start api`. The cutover script handles this. | File sizes match across the three locations; row counts on HZ match DO after restart. |
| 2.4 | Restart Hetzner API to load DBs | `ssh deploy@$HZ_IP 'cd /opt/deadly && docker compose restart api'` | `curl --resolve thedeadly.app:443:$HZ_IP https://thedeadly.app/api/health` returns 200. Spot-check that beta_applicants count matches DO snapshot count. |
| 2.5 | Render `Caddyfile.cutover` with `$HZ_IP` and ship to DO | `envsubst` or sed; copy into the running Caddy container; reload. | `ssh deploy@$DO_IP 'docker compose exec caddy caddy validate --config /etc/caddy/Caddyfile'` passes. |
| 2.6 | Reload DO Caddy | `ssh deploy@$DO_IP 'docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile'` | No errors in Caddy logs. `curl https://thedeadly.app/api/health` (real DNS, hits DO IP, proxies to Hetzner) returns 200. |
| 2.7 | End-to-end verification before DNS flip | Browser test: open `https://thedeadly.app`, log in (OAuth roundtrip via DO→Hetzner), favorite a show, check it persists. | Favorite shows up in Hetzner's DB: `ssh deploy@$HZ_IP 'sqlite3 /opt/deadly/api-data/users.db "SELECT * FROM favorite_shows ORDER BY added_at DESC LIMIT 5"'`. |

**At this point all real traffic is being served by Hetzner via DO Caddy as a proxy. DNS still points to DO. No user has seen an error.**

## Phase 3 — DNS flip (when you're confident)

**Status: ✓ Done 2026-05-07 (~17:30 UTC).** Surgical apex-only PATCH via direct curl (not the script — see learnings). Within ~15 min, ns73/ns74 (GoDaddy authoritative) and 1.1.1.1 / 8.8.8.8 / 9.9.9.9 all resolved `thedeadly.app` to `178.156.208.143`.

**Important correction to original plan:** `share.thedeadly.app` and `www.thedeadly.app` are **CNAMEs to `@`** in GoDaddy, not separate A records. Only the apex `@` A record needed to change; `share` and `www` followed automatically. The `phase_3_flip_dns` function in the cutover script tries to PATCH `share.thedeadly.app` as an A record and would have errored — patch needed in script before next migration.

**The PATCH that ran:**
```bash
KEY=$(cat .secrets/godaddy-key.txt)
curl -fsS -X PUT \
  -H "Authorization: sso-key $KEY" \
  -H "Content-Type: application/json" \
  -d '[{"data":"178.156.208.143","ttl":600}]' \
  "https://api.godaddy.com/v1/domains/thedeadly.app/records/A/@"
```

**GoDaddy authoritative lag.** When PATCHing, GoDaddy's control plane (UI + API GET) updates immediately, but `ns73`/`ns74` continue serving the old answer for ~10–15 min on their own internal schedule. Don't panic if `dig @ns73` lags after a 200 from the API — it'll catch up. Same quirk hit us earlier in the day when lowering TTL from 3600 → 600.

| # | What | How | Verify |
|---|------|-----|--------|
| 3.1 | PATCH GoDaddy apex A record | Direct `curl` (above). `share` and `www` are CNAMEs → `@`, no separate change needed. | `curl -H "Authorization: sso-key $KEY" .../records/A/@` returns the new IP. |
| 3.2 | Wait for authoritative NS to publish | Patience — control plane is instant, NS layer lags ~10–15 min. | `dig @ns73.domaincontrol.com thedeadly.app A` returns HZ IP. |
| 3.3 | Watch traffic shift | Tail Caddy logs on both. DO Caddy tapers; Hetzner Caddy ramps. | After ~600s past NS publish, DO traffic should be near zero. Same cert on both sides throughout — no trust warnings at any point. |

## Phase 4 — Decommission (T+24h)

**Earliest start: 2026-05-08 ~17:00 UTC** (24h after DNS flip). Wait so any cached resolvers fully age out and we're confident HZ has been stable solo.

### 4.1 — Confirm DO is receiving zero API traffic

```bash
ssh -i ssh-key-2026-03-15.key deploy@147.182.140.188 \
  'cd /opt/deadly && docker compose logs caddy --since 1h --tail 100 | grep -E "/(api|ws)/" | head -20'
```

Expect: empty (or only health-check noise). If real `/api/*` requests are still arriving, hold off on destroy — something has DO IP cached or hardcoded. Check mobile app builds in particular.

### 4.2 — Confirm Hetzner backups are working

The web-deploy workflow's backup step runs on prod deploys and pushes to B2. Confirm at least one HZ-origin backup exists:

```bash
aws s3 ls s3://deadly-backups/db/ --endpoint-url https://s3.us-west-004.backblazeb2.com \
  --profile <whichever profile reads B2_TFSTATE_KEY_ID/APP_KEY> | tail
```

If the latest backup pre-dates the cutover, kick a no-op deploy to force one: **Web - Deploy** with `environment=prod, provider=hetzner, ref=main, update_dns=false`.

### 4.3 — Destroy DO prod

Run **Web - Infra** workflow with `action=destroy, provider=digitalocean, environment=prod`. This frees the droplet and its public IP. Terraform state in B2 records the destroy.

### 4.4 — Destroy DO beta (if it was ever launched)

Same workflow, `environment=beta`. Probably a no-op — beta on DO was never used during this migration.

### 4.5 — Clean up `infra/digitalocean/*.tf`

Remove the legacy `digitalocean_droplet.server` and `alpha` resources from the Terraform files. Keep the DO module file present-but-empty (or as a stub) so `terraform init` still works if anyone runs against historical state. Don't blow away state.

### 4.6 — Optionally drop DO from CI

Edit `.github/workflows/infra-manage.yml` and `web-deploy.yml` to remove `digitalocean` from the `provider` choice list. Only do this once you're confident there's no fallback need (you can always add it back).

### 4.7 — Revoke DO API token

DO console → API → revoke `DO_API_TOKEN`. Then: `gh secret delete DO_API_TOKEN` (optional but tidy).

### 4.8 — Delete staged cert from `.secrets/`

```bash
rm -rf .secrets/le-cert/ .secrets/cutover-dbs/
```

After this, Hetzner is the sole holder of the prod private key. The `.secrets/cutover-dbs/` staging dir from the script also has copies of users.db / analytics.db — wipe those too.

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

**No longer applicable as of 2026-05-07** — DNS is flipped, DO API is stopped, HZ is the source of truth. The pre-Phase-3 rollback below is kept for reference only. If something goes wrong now, the recovery is "spin DO back up, restore last B2 backup, flip DNS back" — messy and lossy. Avoid.

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

## Phase 6 — Repo cleanup (delete migration artifacts)

Once Phase 4 (DO destroyed) and Phase 5 (cert renewal verified on HZ) are both done, this doc and the one-shot scripts have served their purpose. Delete them so they don't rot in the tree. Git history preserves them.

Run from repo root on a fresh branch:

```bash
git checkout -b chore/web-remove-migration-artifacts
git rm MIGRATION.md
git rm scripts/migrate-cutover.sh
git rm scripts/pull-cert-from-do.sh
git rm scripts/push-cert-to-hetzner.sh
git rm caddy/Caddyfile.cutover
# If Phase 4.5 left infra/digitalocean/ as an empty stub, remove it now:
git rm -r infra/digitalocean/   # only if no live state references it
git commit -m "chore(web): remove DO→Hetzner migration artifacts"
```

Sanity checks before merging:
- `grep -r "Caddyfile.cutover\|migrate-cutover\|pull-cert-from-do\|push-cert-to-hetzner" .github/ scripts/ caddy/` returns nothing.
- `.github/workflows/web-deploy.yml` and `infra-manage.yml` no longer reference `digitalocean` as a provider option (Phase 4.6).
- `gh secret list` shows no `DO_API_TOKEN` (Phase 4.7).
- `.secrets/le-cert/` and `.secrets/cutover-dbs/` are gone locally (Phase 4.8).

If any of those still reference DO, finish the relevant Phase 4 sub-step before this cleanup commit.

## Learnings (2026-05-07 cutover run)

Things we found out during the actual run that the plan didn't predict. **These are real and should be applied next time, or fixed in `scripts/migrate-cutover.sh` before any future migration.**

1. **`docker compose ps` hides stopped containers.** The original 2.1 verification (`docker compose ps api | grep -E "Exited|exited"`) failed even though the api was correctly stopped — `ps` without `-a` only shows running containers. Fixed in script to `docker compose ps -a api --format "{{.State}}" | grep -qi exited`. Cost: one false-alarm script abort with DO API already stopped (i.e. user-visible 502s prolonged ~1 min while we fixed and re-ran).

2. **Hardcoded table names in row-count fingerprint don't survive contact with reality.** Original implementation hardcoded `daily_rollups` — actual table is `analytics_daily_rollup`. Fixed by querying `sqlite_master` dynamically and counting every user table in each DB. The script now adapts to whatever schema is actually there. Cost: another false-alarm abort with DO already stopped, ~1 more min of 502s.

3. **`share.thedeadly.app` is a CNAME → `@`, not an A record.** `phase_3_flip_dns` in the script tries to PATCH it as an A record and would have errored. **This is unfixed in the script.** For this run we did the surgical apex-only PATCH manually and `share` + `www` followed via their CNAMEs. Before the next migration, fix the script to either (a) detect record type per host, or (b) only PATCH the apex when CNAMEs are configured to follow.

4. **GoDaddy authoritative NS lag is real and ~10–15 min.** Both for TTL changes earlier in the day and for the actual A-record flip. Control plane (UI + API GET) updates instantly; `ns73`/`ns74` lag. Don't assume something is broken when `dig @ns73` still shows the old answer right after a 200 from the GoDaddy API. Plan for it; don't re-PATCH thinking the first one didn't take.

5. **The two-stage row-count fingerprint additions caught nothing — and that was the goal.** Baseline (after 2.1) → snapshot (read from `/tmp/*.db` on DO after `.backup`) → HZ post-restart all matched exactly. This is the discipline you want: prove the data integrity rather than assume it. Worth keeping.

6. **The cutover proxy is *fully* transparent end-to-end.** Real Android client (v2.24.0) did `app_open` → `cold_start` → `playback_start` while DNS still pointed at DO; analytics rows landed on HZ with HZ-side `received_at` timestamps. Nothing in the client knows or cares. Same will be true on rollback if it ever happens.

7. **DO Caddyfile.cutover needed `caddy reload`, not restart.** The script does this correctly via `docker compose exec -T caddy caddy reload`. Reload is zero-downtime; the previous TLS connections drain naturally. Don't change this to a restart.

## Open risks

### Still active (deal with these)

- **Private key staged in `.secrets/`.** The LE private key currently lives in `.secrets/le-cert/` on the laptop. `.secrets/` is gitignored. **Delete in Phase 4.8.** Also `.secrets/cutover-dbs/` has copies of `users.db` / `analytics.db` from the cutover — same treatment.
- **Phase 5 cert renewal smoke test.** The cert HZ is currently serving was obtained from DO via copy. It will renew via ACME at T-30 days, but we haven't *proven* HZ's renewal path works. Force a renewal (Phase 5) before T-30 days so we don't find out at expiry.
- **`scripts/migrate-cutover.sh` `phase_3_flip_dns` is broken** for this domain's DNS shape (tries to PATCH `share.thedeadly.app` as an A record; it's a CNAME). Not blocking anything now since DNS is already flipped, but fix before any future migration touches this script.
- **`scripts/setup-infra-secrets.sh` is untested end-to-end.** During this migration we pushed `HCLOUD_TOKEN`, `GODADDY_KEY`, and `GODADDY_SECRET` manually via `gh secret set` to avoid risking the iOS/Android signing/release secrets that the script also touches. Before relying on the script for future onboarding or rotations, exercise it in full.

### Resolved (informational, no action needed)

- ~~OAuth provider redirect URIs~~: confirmed during soak — Google OAuth round-tripped via DO→HZ proxy, no re-registration needed. Now goes directly to HZ.
- ~~Stale cert in `.secrets/`~~: pull was done on the same day as push; cert had ~45 days remaining when shipped to HZ.
- ~~GHA workflows clobbering cutover Caddyfile~~: nobody pushed during the cutover window. Going forward the active Caddyfile on DO is moot since DO is being decommissioned.
- ~~Service worker caching~~: not a factor — no service worker shipped.
- **`scripts/setup-infra-secrets.sh` is untested end-to-end.** During this migration we pushed `HCLOUD_TOKEN`, `GODADDY_KEY`, and `GODADDY_SECRET` manually via `gh secret set` to avoid risking the iOS/Android signing/release secrets that the script also touches. Before relying on the script for future onboarding or rotations, exercise it in full and confirm every secret it manages (B2, DO, Hetzner, GoDaddy, SSH, plus any mobile signing material it covers) round-trips correctly without disrupting active mobile release pipelines.

## Cost summary

| Item                          | Cost                       |
|-------------------------------|----------------------------|
| prod (CPX11, 24/7)            | €4.35/mo (~$4.70)          |
| beta (CPX11, occasional)      | ~€0.01/hr while running    |
| GoDaddy DNS                   | $0 (existing)              |
| B2 (tfstate + backups)        | unchanged                  |

vs. current DO prod alone at ~$24/mo.

---

# Cutover script

`scripts/migrate-cutover.sh` orchestrates Phase 2 and (optionally) Phase 3. Each phase function in the script corresponds to a numbered step in this doc.

```
scripts/migrate-cutover.sh --do-ip <ip> --hz-ip <ip> [--flip-dns]
scripts/migrate-cutover.sh --do-ip <ip> --rollback
```

**Flags:**
- `--do-ip <ip>` — required
- `--hz-ip <ip>` — required unless `--rollback`
- `--flip-dns` — after Phase 2 + soak, also do Phase 3 (PATCH GoDaddy A records). Reads `.secrets/godaddy-key.txt` (KEY:SECRET pair). Without this flag, Phase 3 is skipped and you can flip DNS later via the **Web - Deploy** workflow with `update_dns=true`.
- `--rollback` — restore DO from `Caddyfile.original.bak` (saved on DO at swap time) and start the DO API. Only valid pre-DNS-flip.

**Interactive prompts** at: preflight (confirm IPs and mode), end of Phase 2 (`c` continue / `r` rollback / `w` wait & re-prompt), and (if `--flip-dns`) before the GoDaddy PATCH.

**Local tool deps:** `jq`, `envsubst`, `dig`, `ssh`/`scp`, `curl`, `aws` (for B2 backups in Phase 0.5). The script does not check for these — install them if missing (Fedora: `dnf install jq gettext bind-utils awscli2`).
