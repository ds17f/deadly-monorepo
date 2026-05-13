# Playback Logging

This package emits structured logs through Apple's unified logging system
(`os.Logger`). The conventions below exist so that logs from a real user's
device are findable, readable, and complete — both when streamed live and when
captured later via the in-app **Send Bug Report** flow.

## Subsystem and categories

All `Logger` instances in this package use:

| Subsystem            | Category          | Source file                  |
|----------------------|-------------------|------------------------------|
| `SwiftAudioStreamEx` | `StreamPlayer`    | `StreamPlayer.swift`         |
| `SwiftAudioStreamEx` | `Engine`          | `AudioStreamEngine.swift`    |
| `SwiftAudioStreamEx` | `AudioSession`    | `AudioSessionManager.swift`  |
| `SwiftAudioStreamEx` | `RemoteCommand`   | `RemoteCommandManager.swift` |
| `SwiftAudioStreamEx` | `NowPlaying`      | `NowPlayingManager.swift`    |

The app shell uses subsystem `com.grateful.deadly` with various categories.

## The `[PB]` filter tag

Every log line in the **playback** code path begins with the literal string
`[PB]` in the message body (e.g. `[PB] didStartPlaying entry=… idx=2/15 …`).

This is intentional belt-and-suspenders alongside subsystem filtering:

- Console.app's subsystem filter is fiddly and easy to misconfigure.
- The in-app log viewer accepts a substring filter (`filterContains: "[PB]"`).
- The `log` CLI's predicate language can use `eventMessage CONTAINS "[PB]"`.

If you add a new log line to the playback path, **include the `[PB]` tag**.

## Log levels — what persists to disk

Apple's unified logging treats `.debug` and `.info` as ephemeral by default;
they only appear in live streams and **are not captured in user bug reports**.
Anything we want users to be able to ship later must be `.notice` or higher.

| Level      | Use for                                      | Persisted? |
|------------|----------------------------------------------|------------|
| `.debug`   | Verbose dev-only diagnostics                 | No         |
| `.info`    | Routine operations no one needs in a report  | No         |
| `.notice`  | State transitions (queue load, advance, …)   | **Yes**    |
| `.warning` | Unexpected but recovered (URL mismatch, …)   | **Yes**    |
| `.error`   | Fatal-to-the-operation failures              | **Yes**    |
| `.fault`   | Reserved — programmer errors                 | **Yes**    |

Rule of thumb in this package: **anything in a transition path is `.notice`**.

## Privacy — `.public` annotations are mandatory

`Logger` redacts interpolated values as `<private>` in Release builds **by
default**. Without explicit annotation, every URL, index, and error string in a
shipped log would look like:

```
[PB] didStartPlaying entry=<private> idx=<private>/<private> …
```

…which makes the entire bug-report flow useless. So:

**Every interpolation in a playback log line must carry `, privacy: .public`.**

```swift
logger.notice("[PB] skipTo \(before, privacy: .public) → \(index, privacy: .public)")
```

Values we treat as `.public`:

- Archive.org URLs and `lastPathComponent`s (these are already public).
- Queue indices, track counts, generation tokens, booleans.
- Error case names, `localizedDescription`.

If you ever interpolate something genuinely sensitive (auth tokens, user
emails), use `, privacy: .private` explicitly and add a comment.

## Reading logs

### Live from a tethered Mac

```sh
log stream \
  --predicate 'subsystem == "SwiftAudioStreamEx" AND eventMessage CONTAINS "[PB]"' \
  --level info
```

Or Console.app → select the device → filter by subsystem.

### From a user's device

The in-app flow is the practical option for production:

1. Settings → **Send Bug Report**, or tap **Send Bug Report** in a playback
   error alert.
2. The screen renders a `BugReportView` that reads the last hour of unified
   logs via `LogExport.exportRecentLogs(...)`.
3. User taps **Share** to send the captured `.txt` (Mail/Messages/AirDrop/etc.)
   or **Copy** for clipboard paste.

The export is plain text. The first lines include app version, build, iOS
version, device model, and the subsystem filter so the report is
self-describing. No telemetry pipeline is involved — logs only leave the device
if the user explicitly shares them.

## Adding new log lines — checklist

- [ ] Starts with `[PB] ` (if it's in the playback path).
- [ ] Uses `.notice` or higher if it's a transition we'd want in a bug report.
- [ ] Every interpolation has `, privacy: .public` (unless genuinely sensitive).
- [ ] Includes enough context to be useful in isolation — at minimum
      "what happened" and the queue index it happened at.
