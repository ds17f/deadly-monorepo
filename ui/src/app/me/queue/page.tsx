import QueueTab from "../_components/QueueTab";

// The Show Queue (backlog) under the profile at /me/queue. Server-backed,
// shared with the mobile apps via /api/user/backlog.
export default function QueuePage() {
  return <QueueTab />;
}
