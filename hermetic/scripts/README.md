# hermetic/scripts

Tools for turning captured network traffic into WireMock fixtures.

## `flow_to_wiremock.py`

Reads a mitmproxy `.flow` capture and emits WireMock mapping JSON
(into `hermetic/fixtures/mappings/`) plus binary response bodies
(into `hermetic/fixtures/__files/`).

### Run via Make

```bash
make capture-convert FLOW=hermetic/fixtures/captures/<file>.flow
```

### Run directly

It's a PEP 723 single-file script — `uv run` auto-installs `mitmproxy`:

```bash
uv run hermetic/scripts/flow_to_wiremock.py <flow_file> \
  [--mappings-dir hermetic/fixtures/mappings] \
  [--files-dir    hermetic/fixtures/__files] \
  [--host-filter archive.org]
```

### What it does

- One WireMock mapping per HTTP flow.
- Response headers are **allowlisted** — `Set-Cookie`, `Authorization`,
  server-identifying noise, etc. are dropped. Only `Content-Type`,
  `Content-Length`, `ETag`, `Last-Modified`, `Cache-Control`, `Vary`,
  and `x-archive-*` survive.
- Small text bodies (≤ 8 KB, JSON / plain / HTML / XML) are inlined into
  the mapping as `body`. Anything else goes to `__files/<name>.<ext>`
  and is referenced via `bodyFileName`.
- `206 Partial Content` responses are skipped with a warning — Range
  requests will be served by WireMock from full-body captures in a
  later ticket.
- `accept-ranges` is intentionally **not** preserved, because WireMock
  returns full bodies regardless of Range requests. Advertising range
  support would mislead clients.

### Filename derivation

The URL path is sanitized into a filesystem-safe stem (non-alphanumerics
collapsed to `_`, truncated to 80 chars with an 8-char hash suffix if
longer). Same-path different-response collisions get a `-2`, `-3`, etc.
suffix.

## Adding more scripts here

Anything that operates on captures or fixtures belongs in this dir.
Use PEP 723 inline metadata (the `# /// script ... # ///` block at the
top of `flow_to_wiremock.py`) so `uv run` self-installs dependencies —
no separate `pyproject.toml` to maintain.
