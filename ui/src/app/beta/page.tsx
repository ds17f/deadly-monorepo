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
          the App Store — please follow the steps below to migrate off the
          TestFlight build. Your favorites, reviews, and preferences can come
          with you.
        </p>
        <div className="pt-2">
          <AppStoreBadge width={180} height={54} />
        </div>
      </div>

      <ol className="space-y-6">
        <li className="space-y-2 rounded-lg border border-white/10 p-6">
          <h2 className="text-lg font-semibold">
            <span className="mr-2 text-emerald-400">1.</span>
            Export your favorites from the beta app
          </h2>
          <p className="text-white/60 leading-relaxed text-sm">
            Open the TestFlight version of The Deadly and go to{" "}
            <span className="font-medium text-white/80">
              Settings → Favorites &amp; Data → Export Favorites
            </span>
            . You&apos;ll get a JSON file containing your favorites, reviews,
            and preferences. Save it somewhere you can find it later (Files
            app, iCloud Drive, AirDrop to yourself, or email).
          </p>
        </li>

        <li className="space-y-2 rounded-lg border border-white/10 p-6">
          <h2 className="text-lg font-semibold">
            <span className="mr-2 text-emerald-400">2.</span>
            Remove the TestFlight build
          </h2>
          <p className="text-white/60 leading-relaxed text-sm">
            Long-press The Deadly icon on your home screen and tap{" "}
            <span className="font-medium text-white/80">Remove App → Delete App</span>.
            You can also open TestFlight, tap The Deadly, scroll to the bottom,
            and tap{" "}
            <span className="font-medium text-white/80">Stop Testing</span>.
          </p>
        </li>

        <li className="space-y-2 rounded-lg border border-white/10 p-6">
          <h2 className="text-lg font-semibold">
            <span className="mr-2 text-emerald-400">3.</span>
            Install from the App Store
          </h2>
          <p className="text-white/60 leading-relaxed text-sm">
            Get the public release here:
          </p>
          <div className="pt-2">
            <AppStoreBadge width={160} height={48} />
          </div>
        </li>

        <li className="space-y-2 rounded-lg border border-white/10 p-6">
          <h2 className="text-lg font-semibold">
            <span className="mr-2 text-emerald-400">4.</span>
            Import your favorites
          </h2>
          <p className="text-white/60 leading-relaxed text-sm">
            Open the App Store version and go to{" "}
            <span className="font-medium text-white/80">
              Settings → Favorites &amp; Data → Import Favorites
            </span>
            . Pick the JSON file you exported in step 1 and your favorites,
            reviews, and preferences will be restored.
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
