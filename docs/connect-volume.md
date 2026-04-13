# Connect Volume Controls

> Per-device volume for Connect — the active device owns its volume, and any
> connected device can remotely adjust it via a slider.

## Design Decision: Command + Report, Not State

Volume is **not** stored in `ConnectState`. Instead it uses a fire-and-forget
relay pattern. This is a deliberate departure from the "everything in state"
model used for playback, and here's why:

**The problem with storing volume in state:**

If the server maintains a `deviceVolumes` map (or even a single `volume` field),
it becomes the source of truth for volume. But the *real* source of truth is the
device's audio system. The user can change volume via OS controls, hardware
buttons, or browser settings at any time — and the server's map becomes stale.
On the next state broadcast, the stale value would override the user's local
change.

**The solution: the active device is the volume authority.**

The server relays volume commands to the active device and relays volume reports
from the active device to everyone else. No volume is stored server-side. The
device's audio system is always the source of truth.

## Protocol

### Setting volume (any device → active device)

Any connected device can adjust the active device's volume:

```
Client → Server:   { type: "command", action: "volume", volume: 72 }
Server → Active:   { type: "volume", volume: 72 }
```

- The server relays to the current active device only.
- If there is no active device, the command is dropped.
- The active device applies the volume to its local audio output.
- No `deviceId` needed — the server knows who is active.

### Reporting volume (active device → all others)

The active device reports its current volume so remote sliders stay in sync:

```
Client → Server:   { type: "command", action: "volume_report", volume: 72 }
Server → Others:   { type: "volume_report", deviceId: "<reporter>", volume: 72 }
```

- The server relays to all devices *except* the reporter.
- The `deviceId` is included so clients know which device reported.

### When to report

The active device sends a `volume_report`:

1. **On becoming active** — so all remote sliders snap to this device's current
   volume.
2. **After applying a received volume command** — confirming the change to the
   device that requested it and syncing all others.

## Edge Cases

### Race during transfer

During the ~1s transfer window, there is no active device. Volume commands sent
during this window are dropped. Once the new active device reports its volume,
all sliders snap to the correct value. This is acceptable — the user is unlikely
to adjust volume during a transfer, and if they do, the slider self-corrects
within a second.

### Echo loop / slider fighting

When a remote device drags the slider:
1. Command sent → active device applies → active device reports back
2. Report arrives at the dragging device → could update slider mid-drag

Solution: each client suppresses incoming volume reports while the user is
actively dragging the slider. The slider uses local state during drag and syncs
from reports only when idle.

### OS / hardware volume changes on active device

If the active device's volume is changed outside the app (hardware buttons, OS
controls), the app may not detect it. On iOS and Android, the app controls
in-app audio volume (`StreamPlayer.volume`, `Player.volume`), which is
independent of system volume. On web, `HTMLAudioElement.volume` is similarly
independent of OS volume.

**Punt for now.** We control in-app volume only. System volume observation could
be added later if needed — iOS has `AVAudioSession.outputVolume`, Android has
`AudioManager` callbacks, web has no reliable mechanism.

## UI

A single volume slider appears in the device list / Connect sheet on all
platforms, positioned between the device list and the "Stop session" action.

- Only visible when a session is active and there is an active device.
- Slider range: 0–100 (mapped to 0.0–1.0 for audio APIs).
- Speaker icons on either side (muted / full).
- Debounced: ~150ms trailing debounce on slider drag to avoid flooding the
  WebSocket. The active device applies immediately on receipt.
