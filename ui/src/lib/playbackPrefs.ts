// ADR-0010: client-local playback preferences (parity with mobile AppPreferences).
// Web has no preferences store, so these live in localStorage under `deadly_*`
// keys, like volume. Read directly — no context wiring needed.

const AUTO_ADVANCE_KEY = "deadly_auto_advance";

/** Roll into the next show when one ends. On by default (missing = enabled). */
export function getAutoAdvanceEnabled(): boolean {
  if (typeof window === "undefined") return true;
  return localStorage.getItem(AUTO_ADVANCE_KEY) !== "false";
}

export function setAutoAdvanceEnabled(value: boolean): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(AUTO_ADVANCE_KEY, value ? "true" : "false");
}
