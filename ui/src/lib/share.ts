export function getShareBaseUrl(): string {
  if (typeof window !== "undefined") {
    const host = window.location.hostname;
    if (host === "beta.thedeadly.app" || host === "localhost") {
      return "https://share.beta.thedeadly.app";
    }
  }
  return "https://share.thedeadly.app";
}
