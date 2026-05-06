import Link from "next/link";
import AppStoreBadge from "@/components/AppStoreBadge";

export const metadata = {
  title: "Migrate from the Beta — The Deadly",
  description:
    "The Deadly is now on the App Store. Here's how to move off the TestFlight beta without losing your favorites.",
};

export default function BetaPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <div className="space-y-3">
        <h1 className="text-3xl font-bold">The Deadly is on the App Store</h1>
        <p className="text-white/70 leading-relaxed">
          Thank you for being part of the closed beta. The app is now live on
          the App Store — moving over takes about 30 seconds. Your favorites,
          reviews, and preferences come with you automatically.
        </p>
        <div className="pt-2">
          <AppStoreBadge width={180} height={54} />
        </div>
      </div>

      <ol className="space-y-6">
        <li className="space-y-2 rounded-lg border border-white/10 p-6">
          <h2 className="text-lg font-semibold">
            <span className="mr-2 text-emerald-400">1.</span>
            Install from the App Store
          </h2>
          <p className="text-white/60 leading-relaxed text-sm">
            Tap the badge below to install the public release. It&apos;ll
            replace your TestFlight build in place — all your data stays put.
          </p>
          <div className="pt-2">
            <AppStoreBadge width={160} height={48} />
          </div>
        </li>

        <li className="space-y-2 rounded-lg border border-white/10 p-6">
          <h2 className="text-lg font-semibold">
            <span className="mr-2 text-emerald-400">2.</span>
            Delete TestFlight (optional)
          </h2>
          <p className="text-white/60 leading-relaxed text-sm">
            Once you&apos;ve confirmed the App Store version is working, you
            can remove the TestFlight app from your home screen. That&apos;s
            it — no export/import needed.
          </p>
        </li>
      </ol>

      <div className="rounded-lg border border-white/10 p-6 text-sm text-white/60">
        Trouble migrating? Drop by our subreddit at{" "}
        <a
          href="https://www.reddit.com/r/thedeadlyapp/"
          target="_blank"
          rel="noopener noreferrer"
          className="text-emerald-400 hover:text-emerald-300"
        >
          r/thedeadlyapp
        </a>{" "}
        and we&apos;ll help you out.
      </div>

      <Link href="/" className="inline-block text-white/50 hover:text-white/80">
        Back to home
      </Link>
    </div>
  );
}
