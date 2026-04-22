"use client";

import { useEffect, useState } from "react";

export default function CollapsibleSection({
  title,
  defaultOpen = true,
  forceOpen,
  onDetail,
  children,
}: {
  title: string;
  defaultOpen?: boolean;
  forceOpen?: boolean;
  onDetail?: () => void;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);

  useEffect(() => {
    if (forceOpen !== undefined) setOpen(forceOpen);
  }, [forceOpen]);

  return (
    <section className="mb-6">
      <div className="flex items-center gap-2 mb-3">
        <button
          onClick={() => setOpen(!open)}
          className="flex items-center gap-2 group text-left"
        >
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider group-hover:text-zinc-200 transition-colors">
            {title}
          </h2>
          <span className="text-xs text-zinc-600 group-hover:text-zinc-400 transition-colors">
            {open ? "▲" : "▼"}
          </span>
        </button>
        {onDetail && (
          <button
            onClick={onDetail}
            className="text-xs text-zinc-600 hover:text-deadly-blue transition-colors"
          >
            detail &rarr;
          </button>
        )}
      </div>
      {open && children}
    </section>
  );
}
