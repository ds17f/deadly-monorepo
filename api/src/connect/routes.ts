import type { FastifyInstance, FastifyRequest } from "fastify";
import { resolveUser } from "../auth/middleware.js";
import {
  registerDevice,
  unregisterDevice,
  handleHeartbeat,
  handleLoad,
  handlePlay,
  handlePause,
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
          registerDevice(userId!, msg.deviceId, msg.deviceType as DeviceType, msg.deviceName, socket);
          break;
        }

        case "heartbeat": {
          if (!registeredDeviceId) return;
          handleHeartbeat(userId!, registeredDeviceId);
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
