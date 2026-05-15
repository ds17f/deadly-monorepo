#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.11"
# dependencies = ["mitmproxy>=10"]
# ///
"""Convert a mitmproxy `.flow` capture into WireMock mappings.

Reads an HTTP capture produced by `mitmdump -w <file>` and emits, into
`hermetic/fixtures/`:

  mappings/<name>.json   — one WireMock stub mapping per flow
  __files/<name>.<ext>   — response bodies (binary or large text)

Header sanitization is allowlist-based: only safe response headers
survive (no Set-Cookie, no auth, no server-identifying noise).

206 Partial Content responses are skipped with a warning — Range
requests will be served by WireMock dynamically from full-body
captures (see DEAD-349).

Usage:
    uv run hermetic/scripts/flow_to_wiremock.py <flow_file> [options]

    # or from the repo root:
    make capture-convert FLOW=hermetic/fixtures/captures/2026-05-15.flow
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

from mitmproxy import io as mitm_io
from mitmproxy.http import HTTPFlow

# Response headers we preserve in mappings. Anything else is dropped.
# Note: accept-ranges is deliberately NOT included — WireMock returns full
# bodies regardless of Range request, so advertising range support would
# mislead clients. Range-aware serving may be revisited in a later ticket.
RESPONSE_HEADER_ALLOWLIST = {
    "content-type",
    "content-length",
    "etag",
    "last-modified",
    "cache-control",
    "vary",
}
# Allowed-by-prefix (archive.org informational headers, useful in logs)
RESPONSE_HEADER_ALLOWLIST_PREFIXES = ("x-archive-",)

INLINE_BODY_MAX_BYTES = 8 * 1024
INLINE_BODY_CONTENT_TYPES = {
    "application/json",
    "text/plain",
    "text/html",
    "text/xml",
    "application/xml",
}

CONTENT_TYPE_TO_EXT = {
    "application/json": ".json",
    "application/xml": ".xml",
    "text/xml": ".xml",
    "text/html": ".html",
    "text/plain": ".txt",
    "audio/mpeg": ".mp3",
    "audio/mp3": ".mp3",
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
}


def safe_name(url_path: str) -> str:
    """Turn a URL path into a filesystem-safe stem (≤ 80 chars, unique-ish)."""
    s = url_path.lstrip("/")
    s = re.sub(r"[^a-zA-Z0-9._-]+", "_", s).strip("_")
    if not s:
        s = "root"
    if len(s) > 80:
        digest = hashlib.sha1(url_path.encode()).hexdigest()[:8]
        s = f"{s[:71]}-{digest}"
    return s


def filter_response_headers(headers) -> dict[str, str]:
    out: dict[str, str] = {}
    for k, v in headers.items():
        lk = k.lower()
        if lk in RESPONSE_HEADER_ALLOWLIST or any(
            lk.startswith(p) for p in RESPONSE_HEADER_ALLOWLIST_PREFIXES
        ):
            out[k] = v
    return out


def content_type_of(headers) -> str:
    return (headers.get("content-type") or "").split(";")[0].strip().lower()


def convert_flow(
    flow: HTTPFlow,
    mappings_dir: Path,
    files_dir: Path,
    name_counter: dict[str, int],
) -> dict:
    req = flow.request
    resp = flow.response
    if resp is None:
        return {"skipped": True, "reason": "no response", "url": req.url}
    if resp.status_code == 206:
        return {
            "skipped": True,
            "reason": "206 Partial Content (Range responses re-served from full body)",
            "url": req.url,
        }

    stem = safe_name(req.path)
    name_counter[stem] = name_counter.get(stem, 0) + 1
    name = stem if name_counter[stem] == 1 else f"{stem}-{name_counter[stem]}"

    ctype = content_type_of(resp.headers)
    body = resp.content or b""

    response: dict = {
        "status": resp.status_code,
        "headers": filter_response_headers(resp.headers),
    }

    if (
        len(body) <= INLINE_BODY_MAX_BYTES
        and ctype in INLINE_BODY_CONTENT_TYPES
    ):
        try:
            response["body"] = body.decode("utf-8")
        except UnicodeDecodeError:
            file_name = f"{name}.bin"
            (files_dir / file_name).write_bytes(body)
            response["bodyFileName"] = file_name
    else:
        ext = CONTENT_TYPE_TO_EXT.get(ctype, ".bin")
        file_name = name if name.endswith(ext) else f"{name}{ext}"
        (files_dir / file_name).write_bytes(body)
        response["bodyFileName"] = file_name

    # WireMock matches against the path the *app* sends. With hermetic mode
    # on, the app rewrites every outbound URL to `/<original-host>/<path>`
    # (see HermeticInterceptor on Android, HermeticURLProtocol on iOS), so the
    # mapping must match that shape — not the original captured path.
    host = (req.host or "").lower()
    original_path = req.path  # path + query
    if host and not original_path.startswith(f"/{host}/") and original_path != f"/{host}":
        match_url = f"/{host}{original_path}"
    else:
        match_url = original_path

    mapping = {
        "request": {
            "method": req.method,
            "url": match_url,
        },
        "response": response,
    }

    mapping_path = mappings_dir / f"{name}.json"
    mapping_path.write_text(json.dumps(mapping, indent=2) + "\n")
    return {"written": str(mapping_path), "host": req.host}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("flow_file", type=Path, help="Path to mitmproxy .flow file")
    p.add_argument(
        "--mappings-dir",
        type=Path,
        default=Path("hermetic/fixtures/mappings"),
        help="Where to write WireMock mapping JSON (default: hermetic/fixtures/mappings)",
    )
    p.add_argument(
        "--files-dir",
        type=Path,
        default=Path("hermetic/fixtures/__files"),
        help="Where to write response body files (default: hermetic/fixtures/__files)",
    )
    p.add_argument(
        "--host-filter",
        action="append",
        default=None,
        help="Only convert flows whose host matches this substring (repeatable). "
             "Default: convert all hosts.",
    )
    args = p.parse_args()

    if not args.flow_file.exists():
        print(f"error: flow file not found: {args.flow_file}", file=sys.stderr)
        return 2

    args.mappings_dir.mkdir(parents=True, exist_ok=True)
    args.files_dir.mkdir(parents=True, exist_ok=True)

    counter: dict[str, int] = {}
    written = 0
    skipped = 0

    with open(args.flow_file, "rb") as f:
        reader = mitm_io.FlowReader(f)
        for flow in reader.stream():
            if not isinstance(flow, HTTPFlow):
                continue
            if args.host_filter and not any(h in (flow.request.host or "") for h in args.host_filter):
                continue
            result = convert_flow(flow, args.mappings_dir, args.files_dir, counter)
            if result.get("skipped"):
                skipped += 1
                print(f"  skip: {result['reason']}: {result['url']}", file=sys.stderr)
            else:
                written += 1
                print(f"  wrote: {result['written']}  (host={result.get('host')})")

    print(f"\nDone. {written} mapping(s) written, {skipped} skipped.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
