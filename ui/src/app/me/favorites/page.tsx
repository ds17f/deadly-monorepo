import FavoritesTab from "../_components/FavoritesTab";

// Favorite shows. Client shell that fetches the enriched
// GET /api/user/favorites/shows on mount. The heart toggle itself lives on
// the show page (FavoriteButton); this is the read-only list.
export default function FavoritesPage() {
  return <FavoritesTab />;
}
