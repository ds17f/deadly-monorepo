import TabPlaceholder from "./_components/TabPlaceholder";

// Recent is the default tab and lives at the /me index — no redirect, so
// this works cleanly under static export. Other tabs are sub-routes.
export default function MePage() {
  return (
    <TabPlaceholder
      title="Recent shows"
      copy="Play something on any device to fill this in."
    />
  );
}
