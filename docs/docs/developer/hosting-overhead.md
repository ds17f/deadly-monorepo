# Hosting Overhead

Overhead (`ds17f/airplane`) is a separate flight tracker. It records the
aircraft that people can hear over a neighborhood. It is deployed by its own
repository and published through deadly's Caddy on the shared external `web`
network. This is **the same pattern as the shop and the loft**. For the full
reasons, the safety model and the problems to avoid, read
[Hosting Emma Shop](hosting-emma-shop.md). This page gives only the items that
are specific to Overhead.

## What is set up here (additive; the other sites do not change)

- **Caddy block** (`Caddyfile`): `{$AIRPLANE_ADDRESS} { reverse_proxy airplane:8000 }`.
- **Compose** (`docker-compose.yml`):
  `AIRPLANE_ADDRESS=${AIRPLANE_ADDRESS:-airplane.localhost}` on the `caddy`
  service. The inert default keeps the block valid. Caddy does not request a
  public certificate for it.
- **Deploy** (`web-deploy.yml`): reads the per-environment GitHub **variable**
  `AIRPLANE_ADDRESS` and writes it into the server `.env`.

The Overhead container (`airplane`, `expose: 8000`, on `web`, no host ports)
comes from its own repository. deadly gives only the route.

## Specific items

- Container name for Caddy: **`airplane:8000`**.
- Public host variable: **`AIRPLANE_ADDRESS`** = `overhead.makersundays.com`
  on prod. It is not set on other environments.
- Authentication: the app does HTTP Basic auth itself (secret
  `OVERHEAD_PASSWORD` in `ds17f/airplane`). Caddy only forwards the requests.
- Data: SQLite in `/opt/airplane/data` on the box. It is separate from the
  deadly volumes.
- Memory: the container has a limit of 256 MB.

## To publish Overhead (one environment)

1. Set the GitHub **variable** `AIRPLANE_ADDRESS` on the target environment.
2. Make a DNS A record for that host to the box IP. Port 80 must be open for
   ACME.
3. `make web-deploy ENV=<env>`. deadly's Caddy gets the route.
4. Deploy Overhead from `ds17f/airplane` (`make publish`). The container joins
   `web`. Caddy gets the certificate at the first request.

Until the Overhead container runs, `{$AIRPLANE_ADDRESS}` gives 502 **only for
that host**. The deadly, shop and loft sites are not affected.
