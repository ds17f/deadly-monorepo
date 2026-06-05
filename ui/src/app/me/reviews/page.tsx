import ReviewsTab from "../_components/ReviewsTab";

// Reviews live under the profile at /me/reviews. Client shell that fetches
// fresh data from /api/user/reviews on mount.
export default function ReviewsPage() {
  return <ReviewsTab />;
}
