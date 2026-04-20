/**
 * Dev-only script: end-to-end test against real App Store Connect.
 *
 * Usage:
 *   source .env && npx tsx scripts/test-asc.ts <email> [firstName] [lastName]
 *
 * Requires APP_STORE_CONNECT_* env vars to be set.
 */

import {
  inviteUser,
  deleteInvitation,
  listInvitations,
  listUsers,
} from "../src/apple/appstoreconnect.js";

const email = process.argv[2];
if (!email) {
  console.error("Usage: npx tsx scripts/test-asc.ts <email> [firstName] [lastName]");
  process.exit(1);
}

const firstName = process.argv[3] ?? "Test";
const lastName = process.argv[4] ?? "User";

async function main() {
  console.log(`\n--- Inviting ${email} ---`);
  const result = await inviteUser({ email, firstName, lastName });
  console.log("inviteUser result:", JSON.stringify(result, null, 2));

  if (!result.ok) {
    console.log("Invite was not created (conflict). Listing current state...");
  }

  console.log("\n--- Current invitations ---");
  const invitations = await listInvitations();
  console.log(`Found ${invitations.length} invitation(s)`);
  for (const inv of invitations) {
    console.log(`  ${inv.id}: ${inv.attributes.email} (expires ${inv.attributes.expirationDate})`);
  }

  console.log("\n--- Current users ---");
  const users = await listUsers();
  console.log(`Found ${users.length} user(s)`);
  for (const u of users) {
    console.log(`  ${u.id}: ${u.attributes.username} [${u.attributes.roles.join(", ")}]`);
  }

  if (result.ok) {
    console.log(`\n--- Deleting invitation ${result.invitationId} ---`);
    await deleteInvitation(result.invitationId);
    console.log("Deleted successfully.");

    console.log("\n--- Invitations after delete ---");
    const after = await listInvitations();
    const still = after.find((i) => i.id === result.invitationId);
    console.log(still ? "WARNING: invitation still present" : "Confirmed: invitation removed");
  }

  console.log("\nDone.");
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});
