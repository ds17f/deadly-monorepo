import type { FastifyInstance, FastifyRequest } from "fastify";
import { resolveUser } from "../auth/middleware.js";
import { decodeJwt } from "../auth/crypto.js";
import { getAppUserById } from "../db/users.js";
import {
  registerDevice,
  unregisterDevice,
  handleHeartbeat,
  startHeartbeatSweep,
} from "./state.js";
import type { ClientMessage, DeviceType } from "./types.js";

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
          // Not handled in this ticket — future implementation
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
