import RecentTab from "../_components/RecentTab";

// Recent now lives under the profile at /me/recent. The content is a
// client shell that fetches fresh data from /api/user/recent on mount.
export default function RecentPage() {
  return <RecentTab />;
}
