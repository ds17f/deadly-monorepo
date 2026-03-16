import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy — The Deadly",
  description:
    "Privacy Policy for The Deadly app. We do not collect, store, or sell any personal data.",
  openGraph: {
    title: "Privacy Policy — The Deadly",
    description:
      "Privacy Policy for The Deadly app. We do not collect any personal data.",
    type: "website",
    url: "https://share.thedeadly.app/privacy",
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
        Information We Collect
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        <strong className="text-white/80">
          We do not collect any personal information or data from users of our
          App.
        </strong>
      </p>
      <p className="mb-3 leading-relaxed text-white/60">
        TheDeadly is designed to operate without collecting, storing, or
        transmitting any personal data, usage analytics, or device information.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Third-Party Services
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        Our App does not integrate with any third-party services, including:
      </p>
      <ul className="mb-3 list-disc pl-6 leading-relaxed text-white/60">
        <li>Analytics services</li>
        <li>Advertising networks</li>
        <li>Crash reporting tools</li>
        <li>Social media platforms</li>
        <li>Cloud storage services</li>
      </ul>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Data Storage
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        Since we do not collect any data, there is no user data stored on our
        servers or any third-party servers.
      </p>

      <h2 className="mb-3 mt-7 text-xl font-semibold text-white/90">
        Children&apos;s Privacy
      </h2>
      <p className="mb-3 leading-relaxed text-white/60">
        Our App does not collect any information from children under the age of
        13 or any other age group. The App is safe for users of all ages.
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
        Effective Date: October 2, 2025 &middot; Last updated: October 2, 2025
      </p>
    </article>
  );
}
