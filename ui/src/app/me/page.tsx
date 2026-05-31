import RecentTab from "./_components/RecentTab";

// Recent is the default tab and lives at the /me index. The content is a
// client shell that fetches fresh data from /api/user/recent on mount.
export default function MePage() {
  return <RecentTab />;
}
