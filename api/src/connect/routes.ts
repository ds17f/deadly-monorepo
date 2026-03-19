import type { FastifyInstance } from "fastify";
import { requireAuth } from "../auth/middleware.js";
import { registerDevice, unregisterDevice, relayCommand, relayTransfer, broadcastPosition, setActiveSession, clearActiveSession, getActiveSession, getDevicesForUser } from "./registry.js";
import { upsertPlaybackPosition } from "../db/userdata.js";
import type { ConnectMessage, RegisterMessage, CommandMessage, TransferMessage, PositionUpdateMessage, SessionUpdateMessage, SessionPlayOnMessage } from "./types.js";

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
          registerDevice(
            { ...reg.device, userId },
            socket as unknown as import("ws").WebSocket,
          );
          break;
        }

        case "command": {
          const cmd = msg as CommandMessage;
          if (!deviceId) {
            socket.send(JSON.stringify({ type: "error", message: "Register first" }));
            return;
          }
          relayCommand(userId, deviceId, cmd.targetDeviceId, cmd.command);
          break;
        }

        case "transfer": {
          const xfer = msg as TransferMessage;
          if (!deviceId) {
            socket.send(JSON.stringify({ type: "error", message: "Register first" }));
            return;
          }
          relayTransfer(userId, deviceId, xfer.targetDeviceId, xfer.state);
          break;
        }

        case "position_update": {
          const pos = msg as PositionUpdateMessage;
          if (!deviceId) return;
          broadcastPosition(userId, deviceId, pos.state);
          // Persist to DB
          upsertPlaybackPosition(userId, {
            showId: pos.state.showId,
            recordingId: pos.state.recordingId,
            trackIndex: pos.state.trackIndex,
            positionMs: pos.state.positionMs,
          });
          break;
        }

        case "session_update": {
          const su = msg as SessionUpdateMessage;
          if (!deviceId) return;
          if (su.state.status === "stopped") {
            clearActiveSession(userId, deviceId);
          } else {
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
          if (!deviceId) return;
          const current = getActiveSession(userId);
          if (current) {
            setActiveSession(userId, {
              ...current,
              deviceId,
              deviceName,
              deviceType,
              updatedAt: Date.now(),
            });
          }
          break;
        }

        case "session_play_on": {
          const spo = msg as SessionPlayOnMessage;
          if (!deviceId) return;
          // Find the target device info
          const targetDevice = getDevicesForUser(userId).find(d => d.deviceId === spo.targetDeviceId);
          if (!targetDevice) {
            socket.send(JSON.stringify({ type: "error", message: "Target device not found" }));
            return;
          }
          setActiveSession(userId, {
            deviceId: targetDevice.deviceId,
            deviceName: targetDevice.name,
            deviceType: targetDevice.type,
            state: spo.state,
            updatedAt: Date.now(),
          });
          break;
        }

        default:
          socket.send(JSON.stringify({ type: "error", message: `Unknown message type` }));
      }
    });

    socket.on("close", () => {
      if (deviceId) {
        unregisterDevice(userId, deviceId);
      }
    });
  });
}
