# ios-log-server

A tiny zero-dependency HTTP server for pulling iOS logs off a device on the
same LAN — no Mac, Xcode console, or USB tether required. You paste a bug-report
dump (or any text) into a web form on the phone; it lands on this machine as
NDJSON and is echoed to the console for live reading.

Useful when debugging on-device behavior that only reproduces away from a
desk — Connect / playback / reconnect issues, backgrounded/locked states, etc.

## Run

From the repo root:

```bash
python3 tools/ios-log-server/log_server.py
```

It listens on `0.0.0.0:8088` and writes to `ios-logs.ndjson` in the current
directory. Override either:

```bash
PORT=9000 LOGFILE=/tmp/repro.ndjson python3 tools/ios-log-server/log_server.py
```

Find this machine's LAN IP so you know where to point the phone:

```bash
ip -4 addr show scope global | grep "inet "
```

If the phone can't reach it, open the port on the Fedora firewall for the
session:

```bash
sudo firewall-cmd --add-port=8088/tcp
```

## Capture a log

1. On the iPhone (same Wi-Fi), open Safari to `http://<LAN-IP>:8088/`.
2. Paste the log into the textbox. Optionally tag it in the **label** field
   (e.g. `connect reconnect repro`) — the label is stored with the record.
3. Hit **Submit**. The form clears and shows `sent ✓`; the server appends the
   record and prints it to its console.

You can also POST directly:

```bash
curl -X POST http://<LAN-IP>:8088/log -H 'X-Label: smoke' -d 'some log text'
```

## Read the captures

Each line of `ios-logs.ndjson` is one record:
`{ts, client, path, label, len, body}`. `body` is the parsed JSON if the POST
was JSON, otherwise the raw text. Quick pretty-print of the latest bug report:

```bash
python3 - <<'PY'
import json
recs = [json.loads(l) for l in open("ios-logs.ndjson") if l.strip()]
r = recs[-1]
print(f"=== {r['ts']}  label={r['label']!r}  {r['len']}B ===")
print(r["body"] if isinstance(r["body"], str) else json.dumps(r["body"], indent=2))
PY
```

## Notes

- HTTP only. If you ever POST from in-app `URLSession` (rather than Safari)
  with ATS enabled, a plain-`http://` host needs an ATS exception — Safari
  doesn't care, so the paste-in form is the path of least resistance.
- Captured `*.ndjson` files are git-ignored (see `.gitignore`); they're repro
  scratch, not source.
