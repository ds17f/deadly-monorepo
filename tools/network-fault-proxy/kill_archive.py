"""mitmproxy addon that drops archive.org audio range requests on demand.

Usage:
    uvx --from mitmproxy mitmproxy -s tools/network-fault-proxy/kill_archive.py --listen-port 8080

Control from another terminal:
    touch /tmp/kill_archive   # start dropping the NEXT matching request
    rm /tmp/kill_archive      # stop dropping

The flag file is checked per-request, so you can flip it during playback to
simulate a single connection failure mid-stream. Matches HTTPS too — install
mitmproxy's CA cert on the iPhone via http://mitm.it after routing through
the proxy.

Only kills .mp3 / .flac requests against *.archive.org, so other app
traffic (auth, metadata, image fetches) is unaffected.
"""

import os
from mitmproxy import ctx, http

FLAG_PATH = "/tmp/kill_archive"


class KillArchive:
    def response(self, flow: http.HTTPFlow) -> None:
        if not os.path.exists(FLAG_PATH):
            return
        host = flow.request.pretty_host
        if "archive.org" not in host:
            return
        path = flow.request.path
        if not (path.endswith(".mp3") or path.endswith(".flac")):
            return
        ctx.log.error(f"[KILL] {flow.request.method} {flow.request.url}")
        flow.response.status_code = 503
        flow.response.headers["content-type"] = "text/plain"
        flow.response.content = b"network-fault-proxy: simulated failure"


addons = [KillArchive()]
