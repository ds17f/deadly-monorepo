import type { FastifyInstance, FastifyRequest } from "fastify";
import { resolveUser } from "../auth/middleware.js";
import { decodeJwt } from "../auth/crypto.js";
import { getAppUserById } from "../db/users.js";
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

async function authenticateWs(
  request: FastifyRequest,
): Promise<{ id: string } | null> {
  // Try normal resolveUser (Bearer header + session cookie)
  const user = await resolveUser(request);
  if (user) return user;

  // Fallback: token query param for browsers
  const token = (request.query as Record<string, string>)?.token;
  if (token) {
    const payload = await decodeJwt(token);
    if (payload?.accountId) {
      const appUser = getAppUserById(payload.accountId as string);
      return {
        id: payload.accountId as string,
        ...(appUser ? { isAdmin: appUser.is_admin === 1 } : {}),
      };
    }
  }

  return null;
}

export async function connectRoutes(app: FastifyInstance): Promise<void> {
  startHeartbeatSweep();

  app.get("/ws/connect", { websocket: true }, (socket, request) => {
    let userId: string | null = null;
    let registeredDeviceId: string | null = null;

    // Auth resolves asynchronously; messages arriving before it resolves are
    // handled by awaiting the same promise.
    const authPromise = authenticateWs(request).then((user) => {
      if (!user) {
        socket.close(4003, "Unauthorized");
        return null;
      }
      userId = user.id;
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
