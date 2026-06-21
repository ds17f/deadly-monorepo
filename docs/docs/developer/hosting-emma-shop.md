# Hosting: Emma Shop (Comet Tail Crafts)

deadly's Caddy is the **only** thing on the box that binds `:80`/`:443` and
terminates TLS. A second, unrelated app — **emma-shop** (Comet Tail Crafts, a
small e-commerce site, repo `ds17f/emma-shop`) — runs on the same Hetzner box and
is published **through deadly's Caddy**. This integration is **built into deadly's
normal deploy**: a standard `web-deploy` (beta or prod) sets up everything on
deadly's side, idempotently, and never affects deadly's own sites.

> **Why deadly maintainers care:** emma-shop is otherwise fully independent (its
> own image, compose stack, deploy, volumes, database). The *only* shared surfaces
> are (1) a Docker network and (2) one Caddy site block. The pieces below keep that
> coupling safe on **both** environments.

## The coupling (two things, both in this repo)

1. **A shared external Docker network `web`.** deadly's `caddy` service joins it
   (in addition to `default`); the deploy creates it idempotently. emma-shop joins
   the same network. Nothing else of deadly's touches it.
2. **One additive Caddy site block** for `{$SHOP_ADDRESS}` that reverse-proxies to
   `emma-shop:3000`. Caddy auto-issues + renews the Let's Encrypt cert for that
   host, stored alongside deadly's certs in the existing `caddy_data` volume.

```
  Caddy (deadly's image, owns :80/:443, auto Let's Encrypt)
    ├── {$SITE_ADDRESS} / {$SHARE_ADDRESS}  → deadly ui/api  (network: default)
    └── {$SHOP_ADDRESS}                      → emma-shop:3000 (network: web)   ◄── added
```

## How it stays safe across beta AND prod

One caddy image (with one baked Caddyfile) serves both environments, so the shop
host is **environment-driven** — not hardcoded — but can **never be empty**:

- The shop host comes from `SHOP_ADDRESS`, a per-environment GitHub Actions
  **variable**. Set it only where the shop is hosted (**prod** = the real domain).
- Where it's unset (**beta**), `docker-compose.yml` substitutes a default:
  `SHOP_ADDRESS=${SHOP_ADDRESS:-shop.localhost}`. `*.localhost` is an **inert,
  internal-cert** host — Caddy never requests a public cert for it and nothing
  routes to it, so beta is completely unaffected.
- Because compose always passes a non-empty value, the Caddyfile's `{$SHOP_ADDRESS}`
  block is **always valid** — it can never collapse into an empty site address
  (which would be a fatal Caddy error that took deadly down too).

## What's wired in (already applied on this branch)

### 1. `docker-compose.yml` — caddy on `web`, with a safe default host
```yaml
  caddy:
    environment:
      - SHOP_ADDRESS=${SHOP_ADDRESS:-shop.localhost}   # real domain on prod; inert default elsewhere
    networks:
      - default   # MUST stay — this is how Caddy reaches api/ui/ws
      - web       # shared external net to reach the emma-shop container

networks:
  web:
    external: true   # never created by this compose; deploy makes it first
```

### 2. `Caddyfile` — one additive block (baked into the caddy image)
```caddyfile
{$SHOP_ADDRESS} {
	reverse_proxy emma-shop:3000
}
```
Pushing the `Caddyfile` triggers `build-images.yml` (it watches `Caddyfile`) to
rebuild + push the caddy image; `web-deploy.yml` then redeploys it.

### 3. `.github/workflows/web-deploy.yml` — idempotent + env-aware
- Creates the network before bringing the stack up:
  `docker network create web 2>/dev/null || true` (idempotent; runs every deploy,
  both envs). Without it, `compose up` would fail because `web` is `external`.
- Writes `SHOP_ADDRESS=${{ vars.SHOP_ADDRESS }}` into the server `.env` (empty on
  beta → compose default applies).

## What a deploy does — and doesn't

- **`make web-deploy ENV=prod`** (or beta) prepares deadly's side completely:
  network up, caddy joined to it, shop route present, `SHOP_ADDRESS` set. It is
  **idempotent** — re-running changes nothing.
- It does **not** start the shop container. emma-shop is deployed by **its own**
  pipeline (`ds17f/emma-shop`), which joins the same `web` network. Until then,
  `{$SHOP_ADDRESS}` simply returns 502 — deadly's sites are unaffected.

## Rules that keep deadly safe (each avoidable)

1. **Network first:** the deploy's `docker network create web || true` must run
   before `compose up`. Already wired; if you hand-run compose, create it yourself.
2. **Never drop `default`** from the caddy service — with only `web`, Caddy can't
   reach `api`/`ui` and **deadly's site 502s**.
3. **Keep the shop host env-driven with the compose default** — don't hardcode a
   domain (breaks the other env) and don't remove the `:-shop.localhost` fallback
   (an unset var would then yield an empty, fatal site address).
4. **Keep the block additive** — never edit the `{$SITE_ADDRESS}` /
   `{$SHARE_ADDRESS}` blocks.
5. **Prod prerequisites:** `shop.<domain>` A record → box IP and `:80` reachable
   before first hit so Caddy can complete the ACME challenge. A failed shop cert
   does not affect deadly's existing certs (separate entries in `caddy_data`).

## Blast radius if the shop misbehaves

- emma-shop binds **no host ports** (`expose: 3000` only) — can't collide with
  Caddy's `:80`/`:443`.
- Its database/uploads live on its **own** volumes (`emma_db`, `emma_uploads`),
  separate from `caddy_data`/`redis_data`/`api-data`.
- If the shop container is down, Caddy returns 502 **for `{$SHOP_ADDRESS}` only**;
  deadly's sites keep working.

## To actually publish the shop on prod

1. Set repo **variable** `SHOP_ADDRESS` (prod environment) = the shop's real domain.
2. DNS A record `shop.<domain>` → box IP.
3. Run `web-deploy` (prod) — deadly side is now ready.
4. Deploy emma-shop from its own repo (joins `web`). Cert issues on first hit.

The mirror of this page lives in `ds17f/emma-shop` → `DEPLOY.md` (Part B).
