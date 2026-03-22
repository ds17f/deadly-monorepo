import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { getUsersDb } from "../db/users.js";
import { mintToken } from "./crypto.js";

export async function devTokenRoutes(app: FastifyInstance): Promise<void> {
  app.get("/api/auth/dev-token", {
    schema: {
      tags: ["auth"],
      summary: "Mint a Bearer token by email (development only)",
      querystring: {
        type: "object",
        required: ["email"],
        properties: {
          email: { type: "string" },
        },
      },
      response: {
        200: {
          type: "object",
          properties: {
            token: { type: "string" },
          },
        },
        404: {
          type: "object",
          properties: {
            error: { type: "string" },
          },
        },
      },
    },
  }, async (request: FastifyRequest<{ Querystring: { email: string } }>, reply: FastifyReply) => {
    const { email } = request.query;

    const db = getUsersDb();
    const account = db.prepare(
      `SELECT id, auth_user_id, email, name FROM accounts WHERE email = ?`
    ).get(email) as { id: string; auth_user_id: string; email: string; name: string | null } | undefined;

    if (!account) {
      return reply.code(404).send({ error: `No account found for email: ${email}` });
    }

    const token = await mintToken({
      accountId: account.id,
      authUserId: account.auth_user_id,
      email: account.email,
      name: account.name,
    });

    return reply.send({ token });
  });
}
