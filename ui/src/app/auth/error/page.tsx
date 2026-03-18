"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Image from "next/image";
import Link from "next/link";

const messages: Record<string, { title: string; description: string }> = {
  Configuration: {
    title: "Sign-in canceled",
    description:
      "The sign-in process was interrupted. This usually happens when the authorization window is closed.",
  },
  AccessDenied: {
    title: "Access denied",
    description: "You do not have permission to sign in.",
  },
  Verification: {
    title: "Link expired",
    description: "The sign-in link is no longer valid. Please try again.",
  },
};

const fallback = {
  title: "Something went wrong",
  description: "An unexpected error occurred during sign-in.",
};

function ErrorContent() {
  const searchParams = useSearchParams();
  const error = searchParams.get("error") || "";
  const { title, description } = messages[error] ?? fallback;

  return (
    <div className="w-full max-w-sm rounded-xl border border-white/10 bg-deadly-surface p-8 shadow-lg">
      <div className="mb-6 flex flex-col items-center text-center">
        <Image
          src="/logo.png"
          alt="The Deadly"
          width={64}
          height={64}
          className="mb-4"
        />
        <h1 className="text-2xl font-bold text-white">{title}</h1>
        <p className="mt-2 text-sm text-white/50">{description}</p>
      </div>

      <div className="flex flex-col gap-3">
        <Link
          href="/signin"
          className="flex w-full items-center justify-center rounded-lg bg-white px-4 py-3 text-sm font-medium text-gray-800 transition hover:bg-gray-100"
        >
          Try again
        </Link>
        <Link
          href="/"
          className="flex w-full items-center justify-center rounded-lg border border-white/10 px-4 py-3 text-sm font-medium text-white/70 transition hover:border-white/20 hover:text-white"
        >
          Go home
        </Link>
      </div>
    </div>
  );
}

export default function AuthErrorPage() {
  return (
    <div className="flex min-h-screen items-start justify-center bg-deadly-bg px-4 pt-[15vh]">
      <Suspense>
        <ErrorContent />
      </Suspense>
    </div>
  );
}
