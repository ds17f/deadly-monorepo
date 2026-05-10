/**
 * Analytics event-version watershed reference.
 *
 * Records when each event or prop became reliable on each platform, so
 * historical queries can date-gate (or rather version-gate) rows that
 * predate the fix. Without this reference, in six months no one will
 * remember that pre-2.28 iOS sent NULL for `listened_ms`, and aggregate
 * queries will silently dilute post-fix metrics with pre-fix junk.
 *
 * **Update this file whenever you ship a change that changes what an
 * event emits.** It costs nothing now, saves a future debugging session.
 *
 * Versioning rules:
 *  - `prop` omitted → the entire event is new on that platform. Filter
 *    rows by `app_version >= ios|android` for that platform.
 *  - `prop` set → the field was added/fixed at that version. Pre-watershed
 *    rows of the event still exist but lack (or misreport) that prop.
 *  - `"TBD"` → the version this fix will ship in is not yet cut. Update at
 *    release time.
 *
 * The admin UI at `/admin/analytics-versions` renders this list as a table.
 */

export interface WatershedEntry {
  event: string;
  /** Omit when the entire event is new (not a prop addition to an existing event). */
  prop?: string;
  /** Version like "2.28.0", "TBD" if unreleased, or `null` if N/A on iOS. */
  ios: string | null;
  /** Version like "2.26.0", "TBD" if unreleased, or `null` if N/A on Android. */
  android: string | null;
  /** Free-form context. Reference Linear tickets here. */
  notes?: string;
  /** Optional Linear ticket id (e.g. "DEAD-314") for direct linking from the UI. */
  ticket?: string;
}

export const ANALYTICS_WATERSHED: readonly WatershedEntry[] = [
  // ── DEAD-311 mobile bundle ─────────────────────────────────────────
  {
    event: "playback_end",
    prop: "listened_ms, duration_ms",
    ios: "TBD",
    android: "TBD",
    notes: "Earlier iOS sent NULL for listened_ms and currentTime for duration_ms.",
    ticket: "DEAD-314",
  },
  {
    event: "playback_end",
    prop: "reason",
    ios: "TBD",
    android: "TBD",
    notes: "Added across both platforms.",
    ticket: "DEAD-329",
  },
  {
    event: "playback_start",
    prop: "source",
    ios: "TBD",
    android: "TBD",
    notes: "Distinguishes user-initiated, auto-advance, restore, search-result.",
    ticket: "DEAD-321",
  },
  {
    event: "search",
    prop: "selected_index",
    ios: "TBD",
    android: "TBD",
    ticket: "DEAD-321",
  },
  {
    event: "feature_use",
    prop: "target_type, target_id, category",
    ios: "TBD",
    android: "TBD",
    notes: "Standardized mobile feature_use shape; server allowlist updated in DEAD-323.",
    ticket: "DEAD-324",
  },
  {
    event: "feature_use",
    prop: "provider, error_reason",
    ios: "TBD",
    android: "TBD",
    notes: "Auth-journey props for sign_in_attempt|success|failure. error_reason uses a sanitized vocabulary (cancelled, network, server_<code>, etc.) — no provider strings.",
    ticket: "DEAD-322",
  },
  {
    event: "playback_error",
    ios: "TBD",
    android: "TBD",
    ticket: "DEAD-306",
  },
  {
    event: "playback_stall",
    ios: "TBD",
    android: "TBD",
    notes: ">3s mid-playback rebuffer.",
    ticket: "DEAD-306",
  },
  {
    event: "download_complete",
    ios: "TBD",
    android: "TBD",
    notes: "Show-level; emitted once all per-track downloads settle as completed.",
    ticket: "DEAD-322",
  },
  {
    event: "download_failed",
    ios: "TBD",
    android: "TBD",
    notes: "Show-level; emitted if any per-track download fails after all settle.",
    ticket: "DEAD-322",
  },
  {
    event: "open_menu",
    ios: null,
    android: "removed in 2.26.0",
    notes: "Low-signal nav event; dropped from emit.",
    ticket: "DEAD-325",
  },
  {
    event: "view_collections",
    ios: null,
    android: "removed in 2.26.0",
    notes: "Low-signal nav event; dropped from emit. iOS never emitted.",
    ticket: "DEAD-325",
  },
] as const;
