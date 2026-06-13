#!/usr/bin/env python3
"""Tiny log-capture server for iOS on-device debugging.

GET  /     -> a dark paste-in web form (open from the phone or desktop).
POST /log  -> body appended to ios-logs.ndjson (one record per line) and
              echoed to stdout.

Built for grabbing iOS bug-report dumps off a device on the same LAN
without a Mac/Xcode console attached. Accepts any content type; JSON
bodies are pretty-printed to the console, everything else captured raw.
An optional X-Label header (set via the form's label field) is stored
alongside each record so you can tag separate repros.

  python3 tools/ios-log-server/log_server.py

Override the port or output file with env vars:

  PORT=9000 LOGFILE=/tmp/repro.ndjson python3 .../log_server.py
"""
import datetime
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOGFILE = os.environ.get("LOGFILE", "ios-logs.ndjson")
PORT = int(os.environ.get("PORT", "8088"))

PAGE = """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>iOS log capture</title>
<style>
  body { font-family: -apple-system, system-ui, sans-serif; margin: 0;
         background: #111; color: #eee; padding: 16px; }
  h1 { font-size: 16px; font-weight: 600; margin: 0 0 12px; }
  textarea { width: 100%; box-sizing: border-box; height: 55vh;
             font-family: ui-monospace, Menlo, monospace; font-size: 13px;
             background: #1c1c1e; color: #eee; border: 1px solid #333;
             border-radius: 8px; padding: 10px; }
  .row { display: flex; gap: 10px; align-items: center; margin-top: 12px; }
  input[type=text] { flex: 1; background: #1c1c1e; color: #eee;
             border: 1px solid #333; border-radius: 8px; padding: 8px; font-size: 14px; }
  button { background: #0a84ff; color: #fff; border: 0; border-radius: 8px;
           padding: 10px 18px; font-size: 15px; font-weight: 600; }
  #status { margin-top: 10px; font-size: 13px; color: #8e8e93; }
</style></head>
<body>
  <h1>iOS log capture</h1>
  <textarea id="log" placeholder="Paste logs here..."></textarea>
  <div class="row">
    <input type="text" id="label" placeholder="optional label (e.g. 'iphone auto-advance repro')">
    <button onclick="send()">Submit</button>
  </div>
  <div id="status"></div>
<script>
async function send() {
  const log = document.getElementById('log');
  const label = document.getElementById('label').value;
  const status = document.getElementById('status');
  if (!log.value.trim()) { status.textContent = 'nothing to send'; return; }
  status.textContent = 'sending...';
  try {
    const r = await fetch('/log', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain', 'X-Label': label },
      body: log.value,
    });
    if (r.ok) {
      status.textContent = 'sent ✓ (' + new Date().toLocaleTimeString() + ')';
      log.value = '';
    } else {
      status.textContent = 'server error: ' + r.status;
    }
  } catch (e) { status.textContent = 'failed: ' + e; }
}
</script>
</body></html>
"""


class Handler(BaseHTTPRequestHandler):
    def _client(self):
        return self.client_address[0]

    def do_GET(self):
        body = PAGE.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b""
        ts = datetime.datetime.now().isoformat(timespec="milliseconds")

        text = raw.decode("utf-8", errors="replace")
        # Try to parse JSON for nicer console output, but always store raw.
        parsed = None
        try:
            parsed = json.loads(text)
        except Exception:
            pass

        label = self.headers.get("X-Label", "")
        record = {
            "ts": ts,
            "client": self._client(),
            "path": self.path,
            "label": label,
            "len": length,
            "body": parsed if parsed is not None else text,
        }
        with open(LOGFILE, "a") as f:
            f.write(json.dumps(record) + "\n")

        # Console echo
        sys.stdout.write(f"\n=== {ts}  {self._client()}  {self.path}  ({length}B) ===\n")
        if parsed is not None:
            sys.stdout.write(json.dumps(parsed, indent=2) + "\n")
        else:
            sys.stdout.write(text + "\n")
        sys.stdout.flush()

        self.send_response(200)
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, *args):
        pass  # silence default request logging; we do our own


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Listening on 0.0.0.0:{PORT} -> {LOGFILE}")
    server.serve_forever()
