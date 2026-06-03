export function getShareBaseUrl(): string {
  if (typeof window !== "undefined") {
    const host = window.location.hostname;
    if (host === "beta.thedeadly.app" || host === "localhost") {
      return "https://share.beta.thedeadly.app";
    }
  }
  return "https://share.thedeadly.app";
}

// The canonical public URL for a show (or a specific recording within it).
// Shared by the hero's copy-link action and the QR sheet so they never drift.
export function buildShareUrl(showId: string, recordingId?: string | null): string {
  const base = getShareBaseUrl();
  return recordingId
    ? `${base}/shows/${showId}/recording/${recordingId}`
    : `${base}/shows/${showId}`;
}

// Copy text to the clipboard, returning whether it succeeded. The async
// Clipboard API only exists in secure contexts (https / localhost), so when
// it's missing or rejects — e.g. testing over a LAN IP on http — we fall back
// to the legacy execCommand("copy") path, which works on plain origins.
export async function copyToClipboard(text: string): Promise<boolean> {
  if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // fall through to the legacy path below
    }
  }

  if (typeof document === "undefined") return false;

  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.top = "0";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    ta.setSelectionRange(0, text.length); // iOS Safari needs an explicit range
    const ok = document.execCommand("copy");
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}
