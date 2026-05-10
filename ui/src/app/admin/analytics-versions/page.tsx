"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

interface WatershedEntry {
  event: string;
  prop?: string;
  ios: string | null;
  android: string | null;
  notes?: string;
  ticket?: string;
}

type SortKey = "event" | "prop" | "ios" | "android" | "ticket";

function VersionCell({ value }: { value: string | null }) {
  if (value === null) return <span className="text-zinc-600">—</span>;
  if (value === "TBD") {
    return (
      <span className="rounded bg-yellow-600/20 px-2 py-0.5 text-xs font-medium text-yellow-300">
        TBD
      </span>
    );
  }
  if (value.startsWith("removed")) {
    return <span className="text-xs text-zinc-400">{value}</span>;
  }
  return <span className="font-mono text-sm text-zinc-200">{value}</span>;
}

function TicketLink({ ticket }: { ticket?: string }) {
  if (!ticket) return <span className="text-zinc-600">—</span>;
  return (
    <a
      href={`https://linear.app/grateful-deadly/issue/${ticket}`}
      target="_blank"
      rel="noopener noreferrer"
      className="text-deadly-blue hover:underline"
    >
      {ticket}
    </a>
  );
}

export default function AnalyticsVersionsPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [entries, setEntries] = useState<WatershedEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>("event");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");

  useEffect(() => {
    if (authLoading) return;
    if (!user?.isAdmin) {
      router.replace("/");
      return;
    }
    fetch("/api/analytics/watershed", { credentials: "include" })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = (await res.json()) as { entries: WatershedEntry[] };
        setEntries(data.entries);
        setError(null);
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Failed to load"),
      )
      .finally(() => setLoading(false));
  }, [authLoading, user?.isAdmin, router]);

  const sorted = useMemo(() => {
    const copy = [...entries];
    copy.sort((a, b) => {
      const av = (a[sortKey] ?? "") as string;
      const bv = (b[sortKey] ?? "") as string;
      const cmp = av.localeCompare(bv);
      return sortDir === "asc" ? cmp : -cmp;
    });
    return copy;
  }, [entries, sortKey, sortDir]);

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(sortDir === "asc" ? "desc" : "asc");
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  };

  const headerCell = (key: SortKey, label: string) => (
    <th
      className="cursor-pointer px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-zinc-400 hover:text-zinc-200"
      onClick={() => toggleSort(key)}
    >
      {label}
      {sortKey === key && (
        <span className="ml-1 text-zinc-500">{sortDir === "asc" ? "▲" : "▼"}</span>
      )}
    </th>
  );

  if (authLoading || loading) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-zinc-400">Loading…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-red-400">Error: {error}</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-deadly-bg p-6">
      <div className="mx-auto max-w-6xl">
        <h1 className="mb-2 text-2xl font-semibold text-zinc-100">
          Analytics Event Watershed
        </h1>
        <p className="mb-6 max-w-3xl text-sm text-zinc-400">
          When each event/prop became reliable on each platform. Use this to
          decide how far back a query can trust a given field — pre-watershed
          rows may be missing or misreporting it. Update{" "}
          <code className="rounded bg-zinc-800 px-1.5 py-0.5 text-xs text-zinc-300">
            api/src/analytics-watershed.ts
          </code>{" "}
          whenever you ship an event-emit change.
        </p>

        <div className="overflow-hidden rounded-lg border border-zinc-800">
          <table className="w-full text-sm">
            <thead className="bg-zinc-900/60">
              <tr>
                {headerCell("event", "Event")}
                {headerCell("prop", "Prop")}
                {headerCell("ios", "iOS")}
                {headerCell("android", "Android")}
                <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-zinc-400">
                  Notes
                </th>
                {headerCell("ticket", "Ticket")}
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {sorted.map((e, i) => (
                <tr key={`${e.event}-${e.prop ?? "_event"}-${i}`} className="hover:bg-zinc-900/30">
                  <td className="px-3 py-2 font-mono text-xs text-zinc-200">
                    {e.event}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs text-zinc-300">
                    {e.prop ?? <span className="text-zinc-600">(event)</span>}
                  </td>
                  <td className="px-3 py-2"><VersionCell value={e.ios} /></td>
                  <td className="px-3 py-2"><VersionCell value={e.android} /></td>
                  <td className="px-3 py-2 text-xs text-zinc-400">
                    {e.notes ?? ""}
                  </td>
                  <td className="px-3 py-2 text-xs">
                    <TicketLink ticket={e.ticket} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
