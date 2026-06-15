// ADR-0010: client-local playback preferences (parity with mobile AppPreferences).
// Web has no preferences store, so these live in localStorage under `deadly_*`
// keys, like volume. Read directly — no context wiring needed.

const AUTO_ADVANCE_KEY = "deadly_auto_advance"; // legacy boolean (migrated below)
const ADVANCE_MODE_KEY = "deadly_advance_mode";

// What auto-advance does when a show ends (ADR-0010 Amendment), matching mobile:
// - "none": stop at the end of the show.
// - "showQueue": play the head of the Show Queue, then stop when it drains.
// - "chronological": play the next show by date, ignoring the queue.
export type AdvanceMode = "none" | "showQueue" | "chronological";

const MODES: AdvanceMode[] = ["none", "showQueue", "chronological"];

export function getAdvanceMode(): AdvanceMode {
  if (typeof window === "undefined") return "none";
  const raw = localStorage.getItem(ADVANCE_MODE_KEY);
  if (raw === "none" || raw === "showQueue" || raw === "chronological") return raw;
  // Migrate the old on/off flag: previously-on → Chronological (today's behavior).
  return localStorage.getItem(AUTO_ADVANCE_KEY) === "true" ? "chronological" : "none";
}

export function setAdvanceMode(mode: AdvanceMode): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(ADVANCE_MODE_KEY, mode);
}

/** Cycle none → Show Queue → Chronological → none (the ∞ control). */
export function cycleAdvanceMode(): AdvanceMode {
  const next = MODES[(MODES.indexOf(getAdvanceMode()) + 1) % MODES.length];
  setAdvanceMode(next);
  return next;
}

export const ADVANCE_MODE_LABEL: Record<AdvanceMode, string> = {
  none: "Off",
  showQueue: "Show Queue",
  chronological: "Chronological",
};

/** Roll into the next show when one ends ("Autoplay" on = mode is not Off). */
export function getAutoAdvanceEnabled(): boolean {
  return getAdvanceMode() !== "none";
}

export function setAutoAdvanceEnabled(value: boolean): void {
  setAdvanceMode(value ? "chronological" : "none");
}
