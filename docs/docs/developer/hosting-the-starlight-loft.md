# Hosting The Starlight Loft

The Starlight Loft (`ds17f/the-starlight-loft`) is a separate, independently-deployed
Next.js app fronted by deadly's Caddy via the shared external `web` network —
**exactly the same pattern as the shop**. For the full rationale, safety model, and
gotchas, read [Hosting Emma Shop](hosting-emma-shop.md); this page only records the
loft-specific bits.

## What's wired here (additive, deadly's + shop's sites untouched)

- **Caddy block** (`Caddyfile`): `{$LOFT_ADDRESS} { reverse_proxy starlight-loft:3000 }`.
- **Redirect block** (`Caddyfile`): `{$LOFT_WWW_ADDRESS}, {$LOFT_ALT_ADDRESS}`
  301 to `https://{$LOFT_ADDRESS}` — covers `www.` and the secondary domain
  (`starlightloft.com`) at launch (loft repo ADR-010).
- **Compose** (`docker-compose.yml`): `LOFT_ADDRESS=${LOFT_ADDRESS:-loft.localhost}`
  (+ `LOFT_WWW_ADDRESS`/`LOFT_ALT_ADDRESS` with distinct `*.localhost` defaults)
  on the `caddy` service — inert defaults so the blocks are always valid
  and never request a public cert where the loft isn't hosted.
- **Deploy** (`web-deploy.yml`): all three are read from per-environment
  GitHub **variables** and written into the server `.env`.

The loft container (`starlight-loft`, `expose: 3000`, joined to `web`) is deployed
from its own repo — deadly only provides the route.

## Specifics

- Container name Caddy targets: **`starlight-loft:3000`**.
- Public host variable: **`LOFT_ADDRESS`** (e.g. `beta.thestarlightloft.com` on
  beta, `thestarlightloft.com` on prod).
- Redirect host variables (set on prod at launch, unset elsewhere):
  **`LOFT_WWW_ADDRESS`** = `www.thestarlightloft.com`,
  **`LOFT_ALT_ADDRESS`** = `starlightloft.com`. Each needs its own DNS record
  pointing at the box (`:80` reachable for ACME). `www.` is a separate variable —
  not derived from `LOFT_ADDRESS` — so beta never requests a cert for a
  `www.beta.…` name that has no DNS record.

## To publish the loft (per environment)

1. Set the GitHub **variable** `LOFT_ADDRESS` on the target environment = the real
   domain (leave unset elsewhere → inert default).
2. DNS A record for that domain → box IP, `:80` reachable for ACME.
3. `make web-deploy ENV=<env>` — deadly's Caddy picks up the route.
4. Deploy the loft from `ds17f/the-starlight-loft` (`make deploy ENV=<env>`); it
   joins `web` and the cert issues on first hit.

Until the loft container is up, `{$LOFT_ADDRESS}` returns 502 **only for that host**
— deadly's and the shop's sites are unaffected.
