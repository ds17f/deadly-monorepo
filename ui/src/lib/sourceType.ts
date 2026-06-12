// Single source of truth for recording source-type badges. Catalog data is
// inconsistently cased (e.g. "MATRIX" vs "Matrix", "SBD"), so every lookup
// normalizes to uppercase. Acronyms (SBD/FM/AUD) display as-is; multi-letter
// words get title-cased so they don't shout ("MATRIX" -> "Matrix").

const SOURCE_COLORS: Record<string, string> = {
  SBD: "bg-deadly-highlight text-white",
  FM: "bg-deadly-highlight text-white",
  MATRIX: "bg-deadly-highlight text-white",
  REMASTER: "bg-deadly-highlight text-white",
  AUD: "bg-amber-700 text-white",
  UNKNOWN: "bg-white/20 text-white/70",
};

// Override the default uppercase rendering for types that read better cased.
const SOURCE_LABELS: Record<string, string> = {
  MATRIX: "Matrix",
  REMASTER: "Remaster",
  UNKNOWN: "Unknown",
};

export function sourceColors(type: string): string {
  return SOURCE_COLORS[(type ?? "").toUpperCase()] ?? SOURCE_COLORS.UNKNOWN;
}

export function sourceLabel(type: string): string {
  const key = (type ?? "").toUpperCase();
  return SOURCE_LABELS[key] ?? (key || "Unknown");
}
