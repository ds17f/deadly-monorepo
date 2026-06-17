import type { FastifyInstance, FastifyRequest } from "fastify";
import { resolveUser } from "../auth/middleware.js";
import {
  registerDevice,
  unregisterDevice,
  handleHeartbeat,
  handleLoad,
  handlePlay,
  handlePause,
  handleSeek,
  handleNext,
  handlePrev,
  handleTransfer,
  handlePosition,
  handleStop,
  handleAnnounceNext,
  handleCancelAdvance,
  handleAdvanceNow,
  handleVolume,
  handleVolumeReport,
  startHeartbeatSweep,
} from "./state.js";
import type { ClientMessage, DeviceType, SessionTrack } from "./types.js";

export async function connectRoutes(app: FastifyInstance): Promise<void> {
  startHeartbeatSweep();

  app.get("/ws/connect", { websocket: true }, (socket, request) => {
    let userId: string | null = null;
    let registeredDeviceId: string | null = null;

    // Auth via session cookie (sent automatically by browser on same-origin WS).
    // Resolves asynchronously; messages arriving before it resolves await the same promise.
    const authPromise = resolveUser(request).then((user) => {
      if (!user) {
        socket.close(4003, "Unauthorized");
        return null;
      }
      userId = user.id;
      request.log.info({ userId }, "ws/connect: authenticated");
      return user;
    });

    socket.on("message", async (raw: Buffer | string) => {
      // Ensure auth is resolved before processing
      if (!userId) {
        const user = await authPromise;
        if (!user) return;
      }

      let msg: ClientMessage;
      try {
        msg = JSON.parse(typeof raw === "string" ? raw : raw.toString("utf-8"));
      } catch {
        return;
      }

      switch (msg.type) {
        case "register": {
          if (!msg.deviceId || !msg.deviceType || !msg.deviceName) return;
          registeredDeviceId = msg.deviceId;
          // ADR-0011 §3: additive, optional. Absent ⇒ legacy ⇒ 0.
          const protocolVersion = typeof msg.protocolVersion === "number" ? msg.protocolVersion : 0;
          const appVersion = typeof msg.appVersion === "string" ? msg.appVersion : null;
          registerDevice(userId!, msg.deviceId, msg.deviceType as DeviceType, msg.deviceName, socket, protocolVersion, appVersion);
          break;
        }

        case "heartbeat": {
          if (!registeredDeviceId) return;
          // ADR-0011 Chunk B: optional ownership-lease payload. Present only when
          // the client has audio loaded locally (it carries recordingId).
          const lease = typeof msg.recordingId === "string"
            ? {
                playing: msg.playing === true,
                recordingId: msg.recordingId,
                trackIndex: typeof msg.trackIndex === "number" ? msg.trackIndex : 0,
                positionMs: typeof msg.positionMs === "number" ? msg.positionMs : 0,
              }
            : undefined;
          handleHeartbeat(userId!, registeredDeviceId, lease);
          break;
        }

        case "time_sync": {
          // Stateless clock-offset probe. Echo clientTs, stamp serverTs.
          // Stamping at send time (not receive) so the server-side processing
          // delay is included in the round-trip rather than skewing the offset.
          if (typeof msg.clientTs !== "number") return;
          if (socket.readyState === socket.OPEN) {
            socket.send(JSON.stringify({
              type: "time_sync",
              clientTs: msg.clientTs,
              serverTs: Date.now(),
            }));
          }
          break;
        }

        case "command": {
          if (!registeredDeviceId || !msg.action) return;
          request.log.info({ userId, deviceId: registeredDeviceId, action: msg.action }, "ws/connect: command");
          switch (msg.action) {
            case "load": {
              const { showId, recordingId, tracks, trackIndex, positionMs, durationMs } = msg as Record<string, unknown>;
              if (typeof showId !== "string" || typeof recordingId !== "string" || !Array.isArray(tracks)) return;
              handleLoad(userId!, registeredDeviceId, socket, {
                showId,
                recordingId,
                tracks: tracks as SessionTrack[],
                trackIndex: typeof trackIndex === "number" ? trackIndex : 0,
                positionMs: typeof positionMs === "number" ? positionMs : 0,
                durationMs: typeof durationMs === "number" ? durationMs : 0,
                date: (msg as Record<string, unknown>).date as string | null,
                venue: (msg as Record<string, unknown>).venue as string | null,
                location: (msg as Record<string, unknown>).location as string | null,
                autoplay: (msg as Record<string, unknown>).autoplay as boolean | undefined,
              });
              break;
            }
            case "play": {
              handlePlay(userId!, registeredDeviceId, socket);
              break;
            }
            case "pause": {
              handlePause(userId!);
              break;
            }
            case "stop": {
              handleStop(userId!);
              break;
            }
            case "announce_next": {
              const { showId, deadline } = msg as Record<string, unknown>;
              if (typeof showId !== "string" || typeof deadline !== "number") return;
              handleAnnounceNext(userId!, registeredDeviceId, { showId, deadline });
              break;
            }
            case "cancel_advance": {
              handleCancelAdvance(userId!);
              break;
            }
            case "advance_now": {
              handleAdvanceNow(userId!);
              break;
            }
            case "seek": {
              const { trackIndex, positionMs, durationMs } = msg as Record<string, unknown>;
              if (typeof trackIndex !== "number") return;
              handleSeek(userId!, {
                trackIndex,
                positionMs: typeof positionMs === "number" ? positionMs : 0,
                durationMs: typeof durationMs === "number" ? durationMs : undefined,
              });
              break;
            }
            case "next": {
              handleNext(userId!);
              break;
            }
            case "prev": {
              handlePrev(userId!);
              break;
            }
            case "transfer": {
              const { targetDeviceId } = msg as Record<string, unknown>;
              if (typeof targetDeviceId !== "string") return;
              handleTransfer(userId!, registeredDeviceId, socket, targetDeviceId);
              break;
            }
            case "position": {
              const { positionMs, durationMs } = msg as Record<string, unknown>;
              if (typeof positionMs !== "number") return;
              handlePosition(userId!, registeredDeviceId, positionMs, typeof durationMs === "number" ? durationMs : undefined);
              break;
            }
            case "volume": {
              const { volume } = msg as Record<string, unknown>;
              if (typeof volume !== "number") return;
              handleVolume(userId!, registeredDeviceId, socket, volume);
              break;
            }
            case "volume_report": {
              const { volume } = msg as Record<string, unknown>;
              if (typeof volume !== "number") return;
              handleVolumeReport(userId!, registeredDeviceId, volume);
              break;
            }
          }
          break;
        }
      }
    });

    socket.on("close", () => {
      if (userId && registeredDeviceId) {
        unregisterDevice(userId, registeredDeviceId);
      }
    });
  });
}
