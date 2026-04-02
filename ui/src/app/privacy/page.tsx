import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy — The Deadly",
  description:
    "Privacy Policy for The Deadly app. Learn about our anonymous usage analytics and your privacy choices.",
  openGraph: {
    title: "Privacy Policy — The Deadly",
    description:
      "Privacy Policy for The Deadly app. Learn about our anonymous usage analytics and your privacy choices.",
    type: "website",
  },
};

export default function PrivacyPage() {
  return (
    <article className="mx-auto max-w-2xl py-8">
      <h1 className="mb-2 text-center text-3xl font-bold">Privacy Policy</h1>
      <p className="mb-8 text-center text-white/50">The Deadly</p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Introduction
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        This Privacy Policy describes how TheDeadly (&ldquo;we,&rdquo;
        &ldquo;our,&rdquo; or &ldquo;us&rdquo;) handles information in our
        mobile application (&ldquo;App&rdquo;) available on iOS and Android
        platforms.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Anonymous Usage Analytics
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        To help us understand how the App is used and improve the experience, we
        collect anonymous usage analytics. Analytics are{" "}
        <strong className="text-white/80">enabled by default</strong> and can be
        turned off at any time in the App&apos;s Settings under Preferences.
      </p>
      <p className="mb-3 leading-relaxed text-white/60">
        <strong className="text-white/80">What we collect:</strong>
      </p>
      <ul className="mb-3 list-disc pl-6 leading-relaxed text-white/60">
        <li>
          A random install ID — a UUID generated on your device, not linked to
          your identity, Apple ID, Google account, or any personal information
        </li>
        <li>
          Basic usage events such as app opens, playback actions, searches, and
          feature usage
        </li>
        <li>App version and platform (iOS or Android)</li>
      </ul>
      <p className="mb-3 leading-relaxed text-white/60">
        <strong className="text-white/80">What we do NOT collect:</strong>
      </p>
      <ul className="mb-3 list-disc pl-6 leading-relaxed text-white/60">
        <li>No personal information, names, emails, or account details</li>
        <li>No device identifiers, hardware IDs, or advertising IDs</li>
        <li>No IP addresses (not stored or logged)</li>
        <li>No location data</li>
        <li>No listening history tied to your identity</li>
      </ul>
      <p className="mb-3 leading-relaxed text-white/60">
        Analytics data is sent to our self-hosted server and is never shared
        with or sold to third parties.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Your Choices
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        You can opt out of anonymous analytics at any time by toggling
        &ldquo;Anonymous Usage Data&rdquo; off in the App&apos;s Settings. When
        opted out, no analytics events are collected or transmitted. Your install
        ID is retained locally on your device in case you opt back in, but is
        never sent while analytics are disabled.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Third-Party Services
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        Our App does not integrate with any third-party analytics, advertising,
        or tracking services. All analytics data is processed on our own
        infrastructure.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Data Storage
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        Anonymous analytics data is stored on our self-hosted server. It
        contains no personal information and cannot be used to identify
        individual users. No user data is stored on third-party servers.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Children&apos;s Privacy
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        Our App does not knowingly collect any personal information from
        children under the age of 13 or any other age group. The anonymous
        analytics described above contain no personal information and cannot
        identify any user, regardless of age.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Changes to This Privacy Policy
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        We may update this Privacy Policy from time to time. Any changes will be
        posted on this page with an updated effective date.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Contact Us
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        If you have any questions about this Privacy Policy, please contact us
        at:
      </p>
      <p className="mb-3 leading-relaxed text-white/60">
        <strong className="text-white/80">Email:</strong>{" "}
        <a
          href="mailto:developer@thedeadly.app"
          className="text-white/50 hover:text-white/80"
        >
          developer@thedeadly.app
        </a>
      </p>

      <p className="mt-8 border-t border-white/10 pt-4 text-sm italic text-white/30">
        Effective Date: October 2, 2025 &middot; Last updated: March 22, 2026
      </p>
    </article>
  );
}
