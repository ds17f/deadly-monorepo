import { Suspense } from "react";
import FavoritesTab from "../_components/FavoritesTab";

// Favorites. The Shows/Songs sub-views and their toggle live in FavoritesTab;
// the active one is held in `?tab` (read via useSearchParams, hence the
// Suspense boundary required by static export).
export default function FavoritesPage() {
  return (
    <Suspense>
      <FavoritesTab />
    </Suspense>
  );
}
