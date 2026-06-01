import { syncVersionLabel } from "@/lib/syncVersions";

// Shown in profile empty states. During the app-store rollout window, every
// existing app user is on a build older than the sync floor, so their data
// hasn't been pushed yet and the web profile looks empty. This explains the
// gap instead of letting it read as "you have nothing."
export default function SyncVersionNote({ className = "" }: { className?: string }) {
  return (
    <p className={`mx-auto mt-3 max-w-md text-xs text-white/35 ${className}`}>
      Syncing requires the latest app ({syncVersionLabel()}). Update from the
      App Store or Google Play and your data will appear here.
    </p>
  );
}
