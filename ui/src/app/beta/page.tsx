"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";

type Step = "intro" | "form" | "result";
type ResultStatus = "invited" | "manual_review" | "waitlist_full";

export default function BetaPage() {
  const [step, setStepRaw] = useState<Step>("intro");
  const [slotsRemaining, setSlotsRemaining] = useState<number | null>(null);
  const [accepting, setAccepting] = useState(true);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resultStatus, setResultStatus] = useState<ResultStatus | null>(null);

  const setStep = useCallback((next: Step) => {
    setStepRaw(next);
    window.history.pushState({ step: next }, "", `/beta`);
  }, []);

  useEffect(() => {
    window.history.replaceState({ step: "intro" }, "", `/beta`);
    const onPopState = (e: PopStateEvent) => {
      const s = e.state?.step as Step | undefined;
      if (s) setStepRaw(s);
      else setStepRaw("intro");
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  const fetchConfig = useCallback(async () => {
    try {
      const res = await fetch("/api/beta/config");
      if (res.ok) {
        const data = await res.json();
        setSlotsRemaining(data.slotsRemaining);
        setAccepting(data.accepting);
      }
    } catch {
      // non-critical
    }
  }, []);

  useEffect(() => {
    fetchConfig();
  }, [fetchConfig]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const res = await fetch("/api/beta/apply", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, firstName, lastName }),
      });

      if (res.status === 429) {
        setError("Too many requests. Please try again later.");
        return;
      }

      if (res.status === 400) {
        const body = await res.json().catch(() => ({}));
        setError(body.error || "Invalid input.");
        return;
      }

      const body = await res.json();
      setResultStatus(body.status as ResultStatus);
      setStep("result");
    } catch {
      setError("Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  const isFull = slotsRemaining !== null && slotsRemaining === 0;

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      {step === "intro" && (
        <>
          <h1 className="text-3xl font-bold">Join the Beta</h1>
          <p className="text-white/70 leading-relaxed">
            The Deadly is currently in closed beta on Apple&apos;s TestFlight.
            Every Grateful Dead concert — setlists, recordings, and reviews for
            2,300+ shows from 1965 to 1995. Stream full shows, browse by year,
            venue, or song, and discover hidden gems from the vault.
          </p>
          <div className="space-y-4 rounded-lg border border-white/10 p-6">
            <h2 className="text-lg font-semibold">How it works</h2>
            <p className="text-white/60 leading-relaxed">
              To distribute beta builds, Apple requires us to add you as a
              limited member of our developer team (scoped only to this app).
              There are limited slots available.
            </p>
            <ol className="list-decimal list-inside space-y-2 text-white/60 text-sm leading-relaxed">
              <li>
                <span className="font-medium text-white/80">Submit your Apple ID email below.</span>
              </li>
              <li>
                <span className="font-medium text-white/80">Check your email for an App Store Connect invitation</span>{" "}
                from <span className="text-white/50 font-mono text-xs">no_reply@email.apple.com</span>.
                The subject will be &ldquo;You&apos;ve been invited to App Store Connect.&rdquo;
                Click &ldquo;Accept invitation&rdquo; and follow Apple&apos;s setup flow.
              </li>
              <li>
                <span className="font-medium text-white/80">Wait 1–2 minutes, then check for a second email:</span>{" "}
                a TestFlight invitation. Open TestFlight on your iPhone and install The Deadly.
              </li>
            </ol>
            <p className="text-white/40 text-xs">
              Both emails come from Apple, not from us. The first invitation
              expires in 3 days; the TestFlight invite expires in 14 days.
            </p>
          </div>
          <div className="flex items-center gap-4">
            {accepting ? (
              <button
                onClick={() => setStep("form")}
                className="rounded-lg bg-green-600 px-6 py-3 font-semibold text-white hover:bg-green-500 transition-colors"
              >
                Apply for Beta Access
              </button>
            ) : (
              <div className="rounded-lg border border-zinc-600 bg-zinc-800/50 px-6 py-3 text-white/60">
                Beta applications are currently closed.
              </div>
            )}
            <Link href="/" className="text-white/50 hover:text-white/80">
              Back to home
            </Link>
          </div>
        </>
      )}

      {step === "form" && (
        <>
          <h1 className="text-3xl font-bold">Apply for Beta Access</h1>
          {isFull && (
            <div className="rounded-lg border border-yellow-500/30 bg-yellow-500/10 p-4 text-yellow-200">
              Beta is currently full. Leave your email and we&apos;ll let you
              know if a slot opens.
            </div>
          )}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="firstName" className="mb-1 block text-sm text-white/60">
                  First name
                </label>
                <input
                  id="firstName"
                  type="text"
                  required
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  className="w-full rounded-lg border border-white/20 bg-white/5 px-4 py-2 text-white placeholder:text-white/30 focus:border-green-500 focus:outline-none"
                  placeholder="Jerry"
                />
              </div>
              <div>
                <label htmlFor="lastName" className="mb-1 block text-sm text-white/60">
                  Last name
                </label>
                <input
                  id="lastName"
                  type="text"
                  required
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  className="w-full rounded-lg border border-white/20 bg-white/5 px-4 py-2 text-white placeholder:text-white/30 focus:border-green-500 focus:outline-none"
                  placeholder="Garcia"
                />
              </div>
            </div>
            <div>
              <label htmlFor="email" className="mb-1 block text-sm text-white/60">
                Email
              </label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-lg border border-white/20 bg-white/5 px-4 py-2 text-white placeholder:text-white/30 focus:border-green-500 focus:outline-none"
                placeholder="jerry@example.com"
              />
              <p className="mt-1 text-xs text-white/40">
                Use the same email you use for your Apple ID.
              </p>
            </div>
            {error && (
              <p className="text-sm text-red-400">{error}</p>
            )}
            <div className="flex items-center gap-4 pt-2">
              <button
                type="submit"
                disabled={submitting}
                className="rounded-lg bg-green-600 px-6 py-3 font-semibold text-white hover:bg-green-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitting ? "Submitting..." : isFull ? "Join Waitlist" : "Apply"}
              </button>
              <button
                type="button"
                onClick={() => window.history.back()}
                className="text-white/50 hover:text-white/80"
              >
                Back
              </button>
            </div>
          </form>
        </>
      )}

      {step === "result" && resultStatus === "invited" && (
        <div className="space-y-6">
          <h1 className="text-3xl font-bold text-green-400">You&apos;re in!</h1>
          <div className="space-y-4">
            <p className="text-white/70 leading-relaxed">
              We&apos;ve sent your invitation. Here&apos;s what to expect:
            </p>
            <div className="space-y-3 rounded-lg border border-white/10 p-5">
              <div className="flex gap-3">
                <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-500/20 text-xs font-bold text-emerald-400">1</span>
                <p className="text-sm text-white/70">
                  <span className="font-medium text-white/90">Check your email now</span> for
                  an App Store Connect invitation from{" "}
                  <span className="font-mono text-xs text-white/50">no_reply@email.apple.com</span>.
                  The subject line is &ldquo;You&apos;ve been invited to App Store Connect.&rdquo;
                  Click &ldquo;Accept invitation&rdquo; and complete Apple&apos;s short setup.
                </p>
              </div>
              <div className="flex gap-3">
                <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-500/20 text-xs font-bold text-emerald-400">2</span>
                <p className="text-sm text-white/70">
                  <span className="font-medium text-white/90">Wait 1–2 minutes</span>, then
                  check for a second email: your TestFlight invitation. Open TestFlight on your
                  iPhone and install The Deadly.
                </p>
              </div>
            </div>
            <p className="text-xs text-white/40">
              Both emails come from Apple. The team invitation expires in 3 days;
              the TestFlight invite expires in 14 days.
            </p>
          </div>
          <Link href="/" className="inline-block text-white/50 hover:text-white/80">
            Back to home
          </Link>
        </div>
      )}

      {step === "result" && resultStatus === "manual_review" && (
        <div className="space-y-4">
          <h1 className="text-3xl font-bold">Thanks for applying!</h1>
          <p className="text-white/70 leading-relaxed">
            We&apos;ll review your request and email you shortly.
          </p>
          <Link href="/" className="inline-block text-white/50 hover:text-white/80">
            Back to home
          </Link>
        </div>
      )}

      {step === "result" && resultStatus === "waitlist_full" && (
        <div className="space-y-4">
          <h1 className="text-3xl font-bold">Beta is full right now</h1>
          <p className="text-white/70 leading-relaxed">
            We&apos;ve saved your email and will reach out if a slot opens.
          </p>
          <Link href="/" className="inline-block text-white/50 hover:text-white/80">
            Back to home
          </Link>
        </div>
      )}
    </div>
  );
}
