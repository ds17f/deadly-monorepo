import Image from "next/image";

const APP_STORE_BADGE_URL =
  "https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg";

export default function AppStoreBadge({
  width = 140,
  height = 42,
}: {
  width?: number;
  height?: number;
}) {
  return (
    <div className="relative inline-block overflow-hidden" style={{ width, height }}>
      <Image
        src={APP_STORE_BADGE_URL}
        alt="Download on the App Store"
        width={width}
        height={height}
        unoptimized
        className="opacity-30 grayscale"
      />
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="rotate-[-15deg] rounded border-2 border-deadly-red px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-deadly-red">
          Coming Soon
        </span>
      </div>
    </div>
  );
}
