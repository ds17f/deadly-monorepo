import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { requireAdmin } from "../auth/middleware.js";
import { notify } from "../notify.js";
import {
  listApplicants,
  listApplicantsByStatus,
  getApplicantById,
  getApplicantByEmail,
  insertApplicant,
  updateApplicantStatus,
  getSettings,
  setSetting,
  countSlotsUsed,
  tryReserveSlot,
} from "../db/beta.js";
import {
  inviteUser,
  deleteUser,
  deleteInvitation,
  listInvitations,
  listUsers,
  getBetaTesterByEmail,
  createBetaTester,
  addTesterToBetaGroup,
} from "../apple/appstoreconnect.js";

// ── Rate limiting for public beta apply (3 req/hour/IP) ─────────────

const applyRateLimitWindow = 3_600_000;
const applyRateLimitMax = 3;
const applyBuckets = new Map<string, { count: number; resetAt: number }>();

function isApplyRateLimited(ip: string): boolean {
  const now = Date.now();
  const bucket = applyBuckets.get(ip);
  if (!bucket || now > bucket.resetAt) {
    applyBuckets.set(ip, { count: 1, resetAt: now + applyRateLimitWindow });
    return false;
  }
  bucket.count++;
  return bucket.count > applyRateLimitMax;
}

setInterval(() => {
  const now = Date.now();
  for (const [ip, bucket] of applyBuckets) {
    if (now > bucket.resetAt) applyBuckets.delete(ip);
  }
}, 300_000).unref();

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export async function betaRoutes(app: FastifyInstance): Promise<void> {
  // ── Public endpoints ────────────────────────────────────────────────

  // GET /api/beta/config
  app.get(
    "/api/beta/config",
    {
      schema: {
        tags: ["beta"],
        summary: "Get public beta config (slots remaining)",
      },
    },
    async () => {
      const settings = getSettings();
      const slotCap = Number(settings.slot_cap ?? "100");
      const slotsUsed = countSlotsUsed();
      const slotsRemaining = Math.max(0, slotCap - slotsUsed);
      const accepting = settings.accepting_applications !== "false";
      return { open: slotsRemaining > 0, slotsRemaining, accepting };
    },
  );

  // POST /api/beta/apply
  app.post(
    "/api/beta/apply",
    {
      schema: {
        tags: ["beta"],
        summary: "Public beta application",
        body: {
          type: "object",
          required: ["email", "firstName", "lastName"],
          properties: {
            email: { type: "string" },
            firstName: { type: "string" },
            lastName: { type: "string" },
          },
        },
      },
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const settings = getSettings();
      if (settings.accepting_applications === "false") {
        return reply.code(403).send({ error: "Beta applications are currently closed" });
      }

      const ip = request.ip;
      if (isApplyRateLimited(ip)) {
        return reply.code(429).send({ error: "Rate limit exceeded" });
      }

      const { email: rawEmail, firstName, lastName } = request.body as {
        email: string;
        firstName: string;
        lastName: string;
      };

      const email = rawEmail.trim().toLowerCase();
      if (!EMAIL_RE.test(email)) {
        return reply.code(400).send({ error: "Invalid email format" });
      }
      if (!firstName.trim() || !lastName.trim()) {
        return reply.code(400).send({ error: "First and last name are required" });
      }

      const ua = request.headers["user-agent"] ?? null;
      const result = tryReserveSlot(email, firstName.trim(), lastName.trim(), ip, ua);

      if (!result.ok) {
        if (result.reason === "duplicate") {
          const existing = getApplicantByEmail(email)!;
          const statusMap: Record<string, string> = {
            pending: "manual_review",
            invited: "invited",
            member: "invited",
            installed: "invited",
            error: "manual_review",
            expired: "manual_review",
            removed: "manual_review",
            rejected: "manual_review",
          };
          return { status: statusMap[existing.status] ?? "manual_review" };
        }

        // slots_full — save to waitlist
        insertApplicant(email, firstName.trim(), lastName.trim(), ip, ua);
        const waitlisted = getApplicantByEmail(email);
        if (waitlisted) {
          updateApplicantStatus(waitlisted.id, "pending", { last_error: "slots_full" });
        }
        if (settings.notify_on_signup !== "false") {
          notify("Beta waitlisted", `${email} — slots full`, "warn");
        }
        return { status: "waitlist_full" };
      }

      // Slot reserved
      if (settings.auto_approve === "true") {
        try {
          const invResult = await inviteUser({ email, firstName: firstName.trim(), lastName: lastName.trim() });
          if (invResult.ok) {
            updateApplicantStatus(result.id, "invited", {
              asc_invitation_id: invResult.invitationId,
              invited_at: Math.floor(Date.now() / 1000),
            });
            if (settings.notify_on_signup !== "false") {
              notify("Beta auto-approved", email);
            }
            return { status: "invited" };
          }
          if (settings.notify_on_error !== "false") {
            notify("Beta auto-approve failed", `${email}: ${invResult.reason}`, "warn");
          }
          return { status: "manual_review" };
        } catch (err) {
          const message = err instanceof Error ? err.message : "Unknown error";
          if (settings.notify_on_error !== "false") {
            notify("Beta auto-approve error", `${email}: ${message}`, "error");
          }
          return { status: "manual_review" };
        }
      }

      if (settings.notify_on_signup !== "false") {
        notify("New beta application", `${email} — needs manual review`);
      }
      return { status: "manual_review" };
    },
  );

  // ── Admin endpoints ─────────────────────────────────────────────────

  // GET /api/admin/beta/applicants
  app.get(
    "/api/admin/beta/applicants",
    {
      schema: {
        tags: ["beta"],
        summary: "List all beta applicants (admin)",
      },
      preHandler: requireAdmin,
    },
    async () => {
      const applicants = listApplicants();
      const slotsUsed = countSlotsUsed();
      const settings = getSettings();
      const slotCap = Number(settings.slot_cap ?? "100");
      return { applicants, slotsUsed, slotCap };
    },
  );

  // POST /api/admin/beta/applicants — admin-create for testing
  app.post(
    "/api/admin/beta/applicants",
    {
      schema: {
        tags: ["beta"],
        summary: "Create a test applicant (admin)",
        body: {
          type: "object",
          required: ["email", "firstName", "lastName"],
          properties: {
            email: { type: "string" },
            firstName: { type: "string" },
            lastName: { type: "string" },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const { email, firstName, lastName } = request.body as {
        email: string;
        firstName: string;
        lastName: string;
      };
      const existing = getApplicantByEmail(email);
      let applicant;
      if (existing) {
        if (existing.status !== "removed" && existing.status !== "rejected") {
          return reply.code(409).send({ error: "Applicant with this email already exists" });
        }
        updateApplicantStatus(existing.id, "pending", {
          last_error: null,
          asc_invitation_id: null,
          asc_user_id: null,
          invited_at: null,
          removed_at: null,
        });
        applicant = getApplicantById(existing.id)!;
      } else {
        applicant = insertApplicant(email, firstName, lastName, null, null);
      }

      const settings = getSettings();
      if (settings.auto_approve === "true") {
        try {
          const result = await inviteUser({ email, firstName, lastName });
          if (result.ok) {
            updateApplicantStatus(applicant.id, "invited", {
              asc_invitation_id: result.invitationId,
              invited_at: Math.floor(Date.now() / 1000),
            });
            const updated = getApplicantById(applicant.id)!;
            return reply.code(201).send(updated);
          }
          updateApplicantStatus(applicant.id, "error", { last_error: result.reason });
        } catch (err) {
          const message = err instanceof Error ? err.message : "Unknown error";
          updateApplicantStatus(applicant.id, "error", { last_error: message });
        }
      }

      const final = getApplicantById(applicant.id)!;
      return reply.code(201).send(final);
    },
  );

  // POST /api/admin/beta/applicants/:id/approve
  app.post(
    "/api/admin/beta/applicants/:id/approve",
    {
      schema: {
        tags: ["beta"],
        summary: "Approve a pending applicant (admin)",
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const { id } = request.params as { id: string };
      const applicant = getApplicantById(id);
      if (!applicant) return reply.code(404).send({ error: "Not found" });
      if (applicant.status !== "pending") {
        return reply.code(409).send({ error: `Cannot approve from status '${applicant.status}'` });
      }

      try {
        const result = await inviteUser({
          email: applicant.email,
          firstName: applicant.first_name ?? "",
          lastName: applicant.last_name ?? "",
        });

        if (!result.ok) {
          updateApplicantStatus(id, "error", { last_error: result.reason });
          return reply.code(409).send({ error: result.reason });
        }

        updateApplicantStatus(id, "invited", {
          asc_invitation_id: result.invitationId,
          invited_at: Math.floor(Date.now() / 1000),
        });
        return { ok: true };
      } catch (err) {
        const message = err instanceof Error ? err.message : "Unknown error";
        updateApplicantStatus(id, "error", { last_error: message });
        return reply.code(502).send({ error: message });
      }
    },
  );

  // POST /api/admin/beta/applicants/:id/reject
  app.post(
    "/api/admin/beta/applicants/:id/reject",
    {
      schema: {
        tags: ["beta"],
        summary: "Reject a pending applicant (admin)",
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const { id } = request.params as { id: string };
      const applicant = getApplicantById(id);
      if (!applicant) return reply.code(404).send({ error: "Not found" });
      if (applicant.status !== "pending") {
        return reply.code(409).send({ error: `Cannot reject from status '${applicant.status}'` });
      }
      updateApplicantStatus(id, "rejected");
      return { ok: true };
    },
  );

  // DELETE /api/admin/beta/applicants/:id
  app.delete(
    "/api/admin/beta/applicants/:id",
    {
      schema: {
        tags: ["beta"],
        summary: "Remove an applicant and revoke ASC access (admin)",
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const { id } = request.params as { id: string };
      const applicant = getApplicantById(id);
      if (!applicant) return reply.code(404).send({ error: "Not found" });

      try {
        if (applicant.asc_user_id) {
          await deleteUser(applicant.asc_user_id);
        } else if (applicant.asc_invitation_id) {
          await deleteInvitation(applicant.asc_invitation_id);
        }
      } catch (err) {
        const message = err instanceof Error ? err.message : "Unknown error";
        updateApplicantStatus(id, "error", { last_error: message });
        const removeSettings = getSettings();
        if (removeSettings.notify_on_error !== "false") {
          notify("Beta remove failed", `${applicant.email}: ${message}`, "error");
        }
        return reply.code(502).send({ error: message });
      }

      updateApplicantStatus(id, "removed", { removed_at: Math.floor(Date.now() / 1000) });
      return { ok: true };
    },
  );

  // POST /api/admin/beta/applicants/:id/retry
  app.post(
    "/api/admin/beta/applicants/:id/retry",
    {
      schema: {
        tags: ["beta"],
        summary: "Retry invitation for errored/expired applicant (admin)",
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const { id } = request.params as { id: string };
      const applicant = getApplicantById(id);
      if (!applicant) return reply.code(404).send({ error: "Not found" });
      if (applicant.status !== "error" && applicant.status !== "expired") {
        return reply.code(409).send({ error: `Cannot retry from status '${applicant.status}'` });
      }

      try {
        const result = await inviteUser({
          email: applicant.email,
          firstName: applicant.first_name ?? "",
          lastName: applicant.last_name ?? "",
        });

        if (!result.ok) {
          updateApplicantStatus(id, "error", { last_error: result.reason });
          return reply.code(409).send({ error: result.reason });
        }

        updateApplicantStatus(id, "invited", {
          asc_invitation_id: result.invitationId,
          invited_at: Math.floor(Date.now() / 1000),
          last_error: null,
        });
        return { ok: true };
      } catch (err) {
        const message = err instanceof Error ? err.message : "Unknown error";
        updateApplicantStatus(id, "error", { last_error: message });
        return reply.code(502).send({ error: message });
      }
    },
  );

  // GET /api/admin/beta/settings
  app.get(
    "/api/admin/beta/settings",
    {
      schema: {
        tags: ["beta"],
        summary: "Get beta settings (admin)",
      },
      preHandler: requireAdmin,
    },
    async () => {
      const settings = getSettings();
      return {
        accepting_applications: settings.accepting_applications !== "false",
        auto_approve: settings.auto_approve === "true",
        sync_enabled: settings.sync_enabled !== "false",
        notify_on_signup: settings.notify_on_signup !== "false",
        notify_on_error: settings.notify_on_error !== "false",
        notify_on_capacity: settings.notify_on_capacity !== "false",
        slot_cap: Number(settings.slot_cap ?? "100"),
        last_synced_at: settings.last_synced_at ? Number(settings.last_synced_at) : null,
      };
    },
  );

  // PUT /api/admin/beta/settings
  app.put(
    "/api/admin/beta/settings",
    {
      schema: {
        tags: ["beta"],
        summary: "Update beta settings (admin)",
        body: {
          type: "object",
          properties: {
            accepting_applications: { type: "boolean" },
            auto_approve: { type: "boolean" },
            sync_enabled: { type: "boolean" },
            notify_on_signup: { type: "boolean" },
            notify_on_error: { type: "boolean" },
            notify_on_capacity: { type: "boolean" },
            slot_cap: { type: "number" },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest) => {
      const { accepting_applications, auto_approve, sync_enabled, notify_on_signup, notify_on_error, notify_on_capacity, slot_cap } = request.body as {
        accepting_applications?: boolean;
        auto_approve?: boolean;
        sync_enabled?: boolean;
        notify_on_signup?: boolean;
        notify_on_error?: boolean;
        notify_on_capacity?: boolean;
        slot_cap?: number;
      };
      if (accepting_applications !== undefined) {
        setSetting("accepting_applications", String(accepting_applications));
      }
      if (auto_approve !== undefined) {
        setSetting("auto_approve", String(auto_approve));
      }
      if (sync_enabled !== undefined) {
        setSetting("sync_enabled", String(sync_enabled));
      }
      if (notify_on_signup !== undefined) {
        setSetting("notify_on_signup", String(notify_on_signup));
      }
      if (notify_on_error !== undefined) {
        setSetting("notify_on_error", String(notify_on_error));
      }
      if (notify_on_capacity !== undefined) {
        setSetting("notify_on_capacity", String(notify_on_capacity));
      }
      if (slot_cap !== undefined) {
        setSetting("slot_cap", String(slot_cap));
      }
      const settings = getSettings();
      return {
        accepting_applications: settings.accepting_applications !== "false",
        auto_approve: settings.auto_approve === "true",
        sync_enabled: settings.sync_enabled !== "false",
        notify_on_signup: settings.notify_on_signup !== "false",
        notify_on_error: settings.notify_on_error !== "false",
        notify_on_capacity: settings.notify_on_capacity !== "false",
        slot_cap: Number(settings.slot_cap ?? "100"),
        last_synced_at: settings.last_synced_at ? Number(settings.last_synced_at) : null,
      };
    },
  );

  // POST /api/admin/beta/test-notification
  app.post(
    "/api/admin/beta/test-notification",
    {
      schema: {
        tags: ["beta"],
        summary: "Send a test Slack notification (admin)",
      },
      preHandler: requireAdmin,
    },
    async () => {
      notify("Test notification", "Slack notifications are working");
      return { ok: true };
    },
  );

  // POST /api/admin/beta/sync — pull current state from App Store Connect
  app.post(
    "/api/admin/beta/sync",
    {
      schema: {
        tags: ["beta"],
        summary: "Sync invitations and users from App Store Connect (admin)",
      },
      preHandler: requireAdmin,
    },
    async (_request: FastifyRequest, reply: FastifyReply) => {
      try {
        const result = await runBetaSync();
        return result;
      } catch (err) {
        const message = err instanceof Error ? err.message : "Unknown error";
        return reply.code(502).send({ error: message });
      }
    },
  );
}

export async function runBetaSync(): Promise<{
  ok: true;
  synced_at: number;
  created: number;
  updated: number;
  transitioned_to_member: number;
  transitioned_to_expired: number;
  added_to_beta_group: number;
}> {
  const betaGroupId = process.env.APP_STORE_CONNECT_BETA_GROUP_ID;
  if (!betaGroupId) {
    throw new Error("APP_STORE_CONNECT_BETA_GROUP_ID not configured");
  }

  const [invitations, users] = await Promise.all([
    listInvitations(),
    listUsers(),
  ]);

  const now = Math.floor(Date.now() / 1000);
  let created = 0;
  let updated = 0;

  // Phase 1: Full populate — ASC is source of truth
  const invitedEmails = new Set<string>();

  for (const inv of invitations) {
    const email = inv.attributes.email.toLowerCase();
    invitedEmails.add(email);
    const existing = getApplicantByEmail(email);
    if (existing) {
      const validInvitedAt = existing.invited_at && existing.invited_at < 2_000_000_000
        ? existing.invited_at : null;
      updateApplicantStatus(existing.id, "invited", {
        first_name: inv.attributes.firstName,
        last_name: inv.attributes.lastName,
        asc_invitation_id: inv.id,
        invited_at: validInvitedAt,
      });
      updated++;
    } else {
      const applicant = insertApplicant(email, inv.attributes.firstName, inv.attributes.lastName, null, null);
      updateApplicantStatus(applicant.id, "invited", { asc_invitation_id: inv.id });
      created++;
    }
  }

  const usersByEmail = new Map<string, typeof users[number]>();

  let addedToBetaGroup = 0;

  for (const user of users) {
    const email = user.attributes.username.toLowerCase();
    usersByEmail.set(email, user);
    const existing = getApplicantByEmail(email);
    if (existing) {
      const wasInvited = existing.status === "invited";
      const status = existing.status === "installed" ? "installed" as const : "member" as const;
      const validMemberAt = existing.member_at && existing.member_at < 2_000_000_000
        ? existing.member_at
        : (existing.status !== "member" && existing.status !== "installed" ? now : null);
      updateApplicantStatus(existing.id, status, {
        first_name: user.attributes.firstName,
        last_name: user.attributes.lastName,
        asc_user_id: user.id,
        member_at: validMemberAt,
        last_error: null,
      });
      updated++;

      if (wasInvited) {
        try {
          let betaTester = await getBetaTesterByEmail(email);
          if (betaTester) {
            await addTesterToBetaGroup(betaGroupId, betaTester.id);
          } else {
            betaTester = await createBetaTester(
              email,
              user.attributes.firstName,
              user.attributes.lastName,
              betaGroupId,
            );
          }
          addedToBetaGroup++;
        } catch (err) {
          updateApplicantStatus(existing.id, "member", {
            last_error: `Beta group add failed: ${err instanceof Error ? err.message : "Unknown"}`,
          });
        }
      }
    } else {
      const applicant = insertApplicant(email, user.attributes.firstName, user.attributes.lastName, null, null);
      updateApplicantStatus(applicant.id, "member", { asc_user_id: user.id, member_at: now });
      created++;
    }
  }

  // Phase 2: Reconcile transitions — detect invited rows whose invitation vanished
  const invitedRows = listApplicantsByStatus("invited");
  let transitionedToMember = 0;
  let transitionedToExpired = 0;

  for (const row of invitedRows) {
    const email = row.email.toLowerCase();

    if (invitedEmails.has(email)) {
      continue;
    }

    const user = usersByEmail.get(email);
    if (user) {
      updateApplicantStatus(row.id, "member", {
        asc_user_id: user.id,
        member_at: now,
      });
      transitionedToMember++;

      try {
        let betaTester = await getBetaTesterByEmail(email);
        if (betaTester) {
          await addTesterToBetaGroup(betaGroupId, betaTester.id);
        } else {
          betaTester = await createBetaTester(
            email,
            user.attributes.firstName,
            user.attributes.lastName,
            betaGroupId,
          );
        }
        addedToBetaGroup++;
      } catch (err) {
        updateApplicantStatus(row.id, "member", {
          last_error: `Beta group add failed: ${err instanceof Error ? err.message : "Unknown"}`,
        });
      }
    } else {
      updateApplicantStatus(row.id, "expired");
      transitionedToExpired++;
    }
  }

  setSetting("last_synced_at", String(now));

  const settings = getSettings();
  const slotCap = Number(settings.slot_cap ?? "100");
  const slotsUsed = countSlotsUsed();
  const pct = Math.round((slotsUsed / slotCap) * 100);
  if (pct >= 90 && settings.notify_on_capacity !== "false") {
    notify("Beta slots at " + pct + "%", `${slotsUsed}/${slotCap} slots used`, "warn");
  }

  return {
    ok: true,
    synced_at: now,
    created,
    updated,
    transitioned_to_member: transitionedToMember,
    transitioned_to_expired: transitionedToExpired,
    added_to_beta_group: addedToBetaGroup,
  };
}

export function startBetaSyncSchedule(): void {
  if (!process.env.APP_STORE_CONNECT_BETA_GROUP_ID) {
    console.warn("Beta sync disabled: APP_STORE_CONNECT_BETA_GROUP_ID not set");
    return;
  }

  const run = () => {
    const settings = getSettings();
    if (settings.sync_enabled === "false") return;
    runBetaSync().catch((err) => {
      const message = err instanceof Error ? err.message : "Unknown error";
      const s = getSettings();
      if (s.notify_on_error !== "false") {
        notify("Beta sync error", message, "error");
      }
    });
  };

  run();
  setInterval(run, 60_000).unref();
}
