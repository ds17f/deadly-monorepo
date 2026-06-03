"use client";

import { useEffect, useMemo, useState } from "react";
import { fetchReviews } from "@/lib/userDataApi";
import type { ShowReview } from "@/types/userdata";
import { useUserData } from "@/contexts/UserDataContext";
import LibraryView from "@/components/library/LibraryView";
import { reviewToItem } from "@/components/library/libraryItem";

// Reviews as the full library surface. Remove = delete the review (with
// confirmation), routed through UserDataContext so it persists and the
// hero/rail markers update.
export default function ReviewsTab() {
  const { removeReview } = useUserData();
  const [state, setState] = useState<"loading" | "error" | "ready">("loading");
  const [reviews, setReviews] = useState<ShowReview[]>([]);

  useEffect(() => {
    let cancelled = false;
    fetchReviews()
      .then((s) => {
        if (cancelled) return;
        setReviews(s);
        setState("ready");
      })
      .catch(() => {
        if (!cancelled) setState("error");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const items = useMemo(() => reviews.map(reviewToItem), [reviews]);

  return (
    <LibraryView
      kind="review"
      loadState={state}
      items={items}
      emptyTitle="Reviews"
      emptyHint="Rate and review a show — on this site or any device — and it'll show up here."
      actions={{
        remove: {
          label: "Delete Review",
          confirmTitle: "Delete this review?",
          confirmMessage: (item) =>
            `Your review of "${item.dateLabel}" will be deleted.`,
          onRemove: (item) => removeReview(item.showId),
        },
      }}
    />
  );
}
