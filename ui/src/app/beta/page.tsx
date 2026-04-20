"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";

type Step = "intro" | "form" | "result";
type ResultStatus = "invited" | "manual_review" | "waitlist_full";

export default function BetaPage() {
  const [step, setStepRaw] = useState<Step>("intro");
  const [slotsRemaining, setSlotsRemaining] = useState<number | null>(null);
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
            To join, we&apos;ll add you as a limited member of our Apple
            Developer team (scoped to only this app). You&apos;ll receive two
            emails from Apple: one to join the team, then a TestFlight invite.
            There are limited slots.
          </p>
          <div className="space-y-4 rounded-lg border border-white/10 p-6">
            <h2 className="text-lg font-semibold">What is The Deadly?</h2>
            <p className="text-white/60 leading-relaxed">
              Every Grateful Dead concert — setlists, recordings, and reviews
              for 2,300+ shows from 1965 to 1995. Stream full shows, browse by
              year, venue, or song, and discover hidden gems from the vault.
            </p>
          </div>
          <div className="flex items-center gap-4">
            <button
              onClick={() => setStep("form")}
              className="rounded-lg bg-green-600 px-6 py-3 font-semibold text-white hover:bg-green-500 transition-colors"
            >
              Apply for Beta Access
            </button>
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
        <div className="space-y-4">
          <h1 className="text-3xl font-bold text-green-400">You&apos;re in!</h1>
          <p className="text-white/70 leading-relaxed">
            Check your email — Apple will send your team invitation shortly.
            Accept it, then open TestFlight to install The Deadly. The invite
            expires in 14 days.
          </p>
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
