"use client";

/** Tiny floating switcher so we can flip between mockup variants. Throwaway. */

import Link from "next/link";
import { usePathname } from "next/navigation";

const VARIANTS = [
  { href: "/mockup", label: "Signed in" },
  { href: "/mockup/logged-out", label: "Logged out" },
  { href: "/mockup/show", label: "Show detail" },
  { href: "/mockup/mobile", label: "Mobile" },
];

export default function MockupSwitch() {
  const pathname = usePathname();
  return (
    <div className="flex items-center gap-1 rounded-full bg-white/10 p-1 text-xs">
      {VARIANTS.map((v) => {
        const active = pathname === v.href;
        return (
          <Link
            key={v.href}
            href={v.href}
            className={`rounded-full px-3 py-1 font-medium transition ${
              active ? "bg-white text-black" : "text-white/60 hover:text-white"
            }`}
          >
            {v.label}
          </Link>
        );
      })}
    </div>
  );
}
