// Minimum app versions that push local data (favorites, recents) to the
// server. Builds older than these never sync, so a signed-in user on an
// older app sees an empty web profile until they update. This is the single
// source of truth for that floor — update it when the release lands.
//
// Floor = the first release containing the sync-push code (recents push +
// startup backfill), i.e. the next minor after the versions live at the time
// it shipped: iOS 2.31.0 → 2.32.0, Android 2.30.0 → 2.31.0.
export const MIN_SYNC_VERSION = {
  ios: "2.32.0",
  android: "2.31.0",
} as const;

/** Human-readable floor, e.g. "iOS 2.32+ or Android 2.31+". */
export function syncVersionLabel(): string {
  const ios = MIN_SYNC_VERSION.ios.replace(/\.0$/, "");
  const android = MIN_SYNC_VERSION.android.replace(/\.0$/, "");
  return `iOS ${ios}+ or Android ${android}+`;
}
