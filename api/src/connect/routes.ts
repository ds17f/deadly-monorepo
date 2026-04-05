import type { FastifyInstance } from "fastify";
import { requireAuth } from "../auth/middleware.js";
import { registerDevice, unregisterDevice, relayPlayOn, broadcastPosition, setActiveSession, clearActiveSession, getActiveSession, getDevicesForUser, updateUserState, deleteUserState, getUserState, sendSessionStop } from "./registry.js";
import { upsertPlaybackPosition } from "../db/userdata.js";
import type { ConnectMessage, RegisterMessage, CommandMessage, PositionUpdateMessage, SessionUpdateMessage, SessionPlayOnMessage, SessionClaimMessage } from "./types.js";
import { DEFAULT_CONFIG } from "./types.js";

const log = (tag: string, msg: string) => console.log(`[Connect] ${tag} ${msg}`);

export async function connectRoutes(app: FastifyInstance): Promise<void> {
  app.get("/ws/connect", {
    schema: { tags: ["connect"], summary: "WebSocket Connect endpoint" },
    websocket: true,
    preHandler: requireAuth,
  }, (socket, request) => {
    const userId = request.user!.id;
    let deviceId: string | null = null;
    let deviceName = "Unknown";
    let deviceType: "ios" | "android" | "web" = "web";

    socket.on("message", (raw: { toString(): string }) => {
      let msg: ConnectMessage;
      try {
        msg = JSON.parse(raw.toString()) as ConnectMessage;
      } catch {
        socket.send(JSON.stringify({ type: "error", message: "Invalid JSON" }));
        return;
      }

      switch (msg.type) {
        case "register": {
          const reg = msg as RegisterMessage;
          deviceId = reg.device.deviceId;
          deviceName = reg.device.name;
          deviceType = reg.device.type;
          log("register", `${deviceName} (${deviceType}) id=${deviceId.slice(0, 8)}`);
          registerDevice(
            { ...reg.device, userId },
            socket as unknown as import("ws").WebSocket,
          );
          // Send config immediately so clients use server-controlled values
          socket.send(JSON.stringify({ type: "config", config: DEFAULT_CONFIG }));
          break;
        }

        case "command": {
          const cmd = msg as CommandMessage;
          if (!deviceId) {
            socket.send(JSON.stringify({ type: "error", message: "Register first" }));
            return;
          }
          const action = cmd.command.action;
          const state = getUserState(userId);
          if (!state) {
            socket.send(JSON.stringify({ type: "error", message: "No active session" }));
            return;
          }

          log("command", `from=${deviceName}: ${action}${cmd.command.seekMs != null ? ` seekMs=${cmd.command.seekMs}` : ""}`);

          if (action === "play" || action === "pause") {
            updateUserState(userId, { isPlaying: action === "play" });
          } else if (action === "stop") {
            updateUserState(userId, {
              activeDeviceId: null,
              activeDeviceName: null,
              activeDeviceType: null,
              isPlaying: false,
            });
          } else if (action === "seek" && cmd.command.seekMs != null) {
            updateUserState(userId, { positionMs: cmd.command.seekMs });
          } else if (action === "next") {
            const tracks = state.tracks;
            const newIndex = tracks
              ? Math.min(state.trackIndex + 1, tracks.length - 1)
              : state.trackIndex + 1;
            if (newIndex === state.trackIndex) break; // Already at last track
            const newTrack = tracks?.[newIndex];
            updateUserState(userId, {
              trackIndex: newIndex,
              positionMs: 0,
              ...(newTrack ? {
                trackTitle: newTrack.title,
                durationMs: Math.floor(newTrack.duration * 1000),
              } : {}),
            });
          } else if (action === "prev") {
            const tracks = state.tracks;
            const newIndex = Math.max(state.trackIndex - 1, 0);
            if (newIndex === state.trackIndex) break; // Already at first track — client handles restart via seek
            const newTrack = tracks?.[newIndex];
            updateUserState(userId, {
              trackIndex: newIndex,
              positionMs: 0,
              ...(newTrack ? {
                trackTitle: newTrack.title,
                durationMs: Math.floor(newTrack.duration * 1000),
              } : {}),
            });
          }
          break;
        }

        case "position_update": {
          const pos = msg as PositionUpdateMessage;
          if (!deviceId) return;
          log("pos_update", `from=${deviceName}: track=${pos.state.trackIndex} pos=${pos.state.positionMs}ms status=${pos.state.status}`);
          broadcastPosition(userId, deviceId, pos.state);
          // Persist to DB
          upsertPlaybackPosition(userId, {
            showId: pos.state.showId,
            recordingId: pos.state.recordingId,
            trackIndex: pos.state.trackIndex,
            positionMs: pos.state.positionMs,
            date: pos.state.date,
            venue: pos.state.venue,
            location: pos.state.location,
          });
          break;
        }

        case "session_update": {
          const su = msg as SessionUpdateMessage;
          if (!deviceId) return;
          log("session_update", `from=${deviceName}: status=${su.state.status} track=${su.state.trackIndex} pos=${su.state.positionMs}ms dur=${su.state.durationMs ?? "?"}ms`);

          // Store tracks if provided
          const tracksPatch = su.state.tracks ? { tracks: su.state.tracks } : {};

          const currentState = getUserState(userId);
          const isActiveDevice = !currentState?.activeDeviceId || currentState.activeDeviceId === deviceId;

          if (su.state.status === "stopped") {
            // Only park state if this device currently owns it; otherwise just ignore
            if (isActiveDevice) {
              updateUserState(userId, {
                showId: su.state.showId,
                recordingId: su.state.recordingId,
                trackIndex: su.state.trackIndex,
                positionMs: su.state.positionMs,
                durationMs: su.state.durationMs,
                trackTitle: su.state.trackTitle,
                date: su.state.date,
                venue: su.state.venue,
                location: su.state.location,
                activeDeviceId: null,
                activeDeviceName: null,
                activeDeviceType: null,
                isPlaying: false,
                ...tracksPatch,
              });
            }
            // Persist to DB on stop regardless
            upsertPlaybackPosition(userId, {
              showId: su.state.showId,
              recordingId: su.state.recordingId,
              trackIndex: su.state.trackIndex,
              positionMs: su.state.positionMs,
              date: su.state.date,
              venue: su.state.venue,
              location: su.state.location,
            });
            // Legacy
            clearActiveSession(userId, deviceId);
          } else if (!isActiveDevice) {
            // Another device has claimed the session — ignore paused/playing updates from this device
            log("session_update", `ignored from=${deviceName}: session owned by ${currentState?.activeDeviceName ?? "unknown"}`);
          } else if (su.state.status === "paused") {
            // Paused: keep device set but not playing
            updateUserState(userId, {
              showId: su.state.showId,
              recordingId: su.state.recordingId,
              trackIndex: su.state.trackIndex,
              positionMs: su.state.positionMs,
              durationMs: su.state.durationMs,
              trackTitle: su.state.trackTitle,
              date: su.state.date,
              venue: su.state.venue,
              location: su.state.location,
              activeDeviceId: deviceId,
              activeDeviceName: deviceName,
              activeDeviceType: deviceType,
              isPlaying: false,
              ...tracksPatch,
            });
            // Legacy
            setActiveSession(userId, {
              deviceId,
              deviceName,
              deviceType,
              state: su.state,
              updatedAt: Date.now(),
            });
          } else {
            // Playing
            updateUserState(userId, {
              showId: su.state.showId,
              recordingId: su.state.recordingId,
              trackIndex: su.state.trackIndex,
              positionMs: su.state.positionMs,
              durationMs: su.state.durationMs,
              trackTitle: su.state.trackTitle,
              date: su.state.date,
              venue: su.state.venue,
              location: su.state.location,
              activeDeviceId: deviceId,
              activeDeviceName: deviceName,
              activeDeviceType: deviceType,
              isPlaying: true,
              ...tracksPatch,
            });
            // Legacy
            setActiveSession(userId, {
              deviceId,
              deviceName,
              deviceType,
              state: su.state,
              updatedAt: Date.now(),
            });
          }
          break;
        }

        case "session_claim": {
          const scm = msg as SessionClaimMessage;
          if (!deviceId) return;
          log("session_claim", `from=${deviceName}${scm.state ? ` show=${scm.state.showId} track=${scm.state.trackIndex}` : ""}`);
          // Stop the old active device if different from the claimer
          const claimState = getUserState(userId);
          if (claimState?.activeDeviceId && claimState.activeDeviceId !== deviceId) {
            sendSessionStop(userId, claimState.activeDeviceId);
          }
          const current = getActiveSession(userId);
          if (current) {
            setActiveSession(userId, {
              ...current,
              deviceId,
              deviceName,
              deviceType,
              ...(scm.state ? { state: scm.state } : {}),
              updatedAt: Date.now(),
            });
          }
          // Build user state update — include show fields when state is provided
          const claimTracksPatch = scm.state?.tracks ? { tracks: scm.state.tracks } : {};
          updateUserState(userId, {
            activeDeviceId: deviceId,
            activeDeviceName: deviceName,
            activeDeviceType: deviceType,
            isPlaying: scm.state ? scm.state.status === "playing" : (claimState?.isPlaying ?? false),
            ...(scm.state ? {
              showId: scm.state.showId,
              recordingId: scm.state.recordingId,
              trackIndex: scm.state.trackIndex,
              positionMs: scm.state.positionMs,
              date: scm.state.date,
              venue: scm.state.venue,
              location: scm.state.location,
            } : {}),
            ...claimTracksPatch,
          });
          break;
        }

        case "session_play_on": {
          const spo = msg as SessionPlayOnMessage;
          if (!deviceId) return;
          log("session_play_on", `from=${deviceName} → target=${spo.targetDeviceId.slice(0, 8)}: status=${spo.state.status} track=${spo.state.trackIndex} pos=${spo.state.positionMs}ms`);
          // Stop the old active device if different from the play-on target
          const playOnState = getUserState(userId);
          if (playOnState?.activeDeviceId && playOnState.activeDeviceId !== spo.targetDeviceId) {
            sendSessionStop(userId, playOnState.activeDeviceId);
          }
          // Find the target device info
          const targetDevice = getDevicesForUser(userId).find(d => d.deviceId === spo.targetDeviceId);
          if (!targetDevice) {
            socket.send(JSON.stringify({ type: "error", message: "Target device not found" }));
            return;
          }
          // Forward play_on to the target device
          const senderDevice = getDevicesForUser(userId).find(d => d.deviceId === deviceId);
          relayPlayOn(userId, deviceId, spo.targetDeviceId, spo.state, senderDevice?.name ?? "another device");
          setActiveSession(userId, {
            deviceId: targetDevice.deviceId,
            deviceName: targetDevice.name,
            deviceType: targetDevice.type,
            state: spo.state,
            updatedAt: Date.now(),
          });
          // Store tracks if provided
          const playOnTracksPatch = spo.state.tracks ? { tracks: spo.state.tracks } : {};
          // Also update user state
          updateUserState(userId, {
            showId: spo.state.showId,
            recordingId: spo.state.recordingId,
            trackIndex: spo.state.trackIndex,
            positionMs: spo.state.positionMs,
            date: spo.state.date,
            venue: spo.state.venue,
            location: spo.state.location,
            activeDeviceId: targetDevice.deviceId,
            activeDeviceName: targetDevice.name,
            activeDeviceType: targetDevice.type,
            isPlaying: spo.state.status === "playing",
            ...playOnTracksPatch,
          });
          break;
        }

        case "state_clear": {
          if (!deviceId) return;
          log("state_clear", `from=${deviceName}`);
          deleteUserState(userId);
          // Also clear legacy
          clearActiveSession(userId);
          break;
        }

        default:
          socket.send(JSON.stringify({ type: "error", message: `Unknown message type` }));
      }
    });

    socket.on("close", () => {
      if (deviceId) {
        log("disconnect", `${deviceName} (${deviceType})`);
        unregisterDevice(userId, deviceId);
      }
    });
  });
}
