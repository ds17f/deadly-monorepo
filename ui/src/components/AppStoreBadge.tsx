import Image from "next/image";

const APP_STORE_BADGE_URL =
  "https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg";
const APP_STORE_URL =
  "https://apps.apple.com/us/app/thedeadly/id6753330346";

export default function AppStoreBadge({
  width = 140,
  height = 42,
}: {
  width?: number;
  height?: number;
}) {
  return (
    <a
      href={APP_STORE_URL}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-block"
      style={{ width, height }}
    >
      <Image
        src={APP_STORE_BADGE_URL}
        alt="Download on the App Store"
        width={width}
        height={height}
        unoptimized
      />
    </a>
  );
}
