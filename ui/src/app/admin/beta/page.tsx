"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback, useMemo } from "react";

interface BetaApplicant {
  id: string;
  email: string;
  first_name: string | null;
  last_name: string | null;
  status: string;
  asc_invitation_id: string | null;
  asc_user_id: string | null;
  last_error: string | null;
  created_at: number;
  invited_at: number | null;
  member_at: number | null;
  installed_at: number | null;
  removed_at: number | null;
}

interface BetaSettings {
  accepting_applications: boolean;
  auto_approve: boolean;
  sync_enabled: boolean;
  notify_on_signup: boolean;
  notify_on_error: boolean;
  notify_on_capacity: boolean;
  slot_cap: number;
  last_synced_at: number | null;
}

const STATUS_COLORS: Record<string, string> = {
  pending: "bg-yellow-600/20 text-yellow-400",
  invited: "bg-blue-600/20 text-blue-400",
  member: "bg-purple-600/20 text-purple-400",
  installed: "bg-green-600/20 text-green-400",
  expired: "bg-red-600/20 text-red-400",
  error: "bg-red-600/20 text-red-400",
  rejected: "bg-zinc-600/20 text-zinc-400",
  removed: "bg-zinc-600/20 text-zinc-400",
};

function formatTs(ts: number | null): string {
  if (!ts) return "—";
  return new Date(ts * 1000).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function BetaPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [applicants, setApplicants] = useState<BetaApplicant[]>([]);
  const [settings, setSettings] = useState<BetaSettings>({ accepting_applications: true, auto_approve: true, sync_enabled: true, notify_on_signup: true, notify_on_error: true, notify_on_capacity: true, slot_cap: 100, last_synced_at: null });
  const [showNotifyPanel, setShowNotifyPanel] = useState(false);
  const [slotsUsed, setSlotsUsed] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [addEmail, setAddEmail] = useState("");
  const [addFirst, setAddFirst] = useState("");
  const [addLast, setAddLast] = useState("");
  const [syncing, setSyncing] = useState(false);
  const [testingSend, setTestingSend] = useState(false);
  const [sortKey, setSortKey] = useState<string>("created_at");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [appRes, setRes] = await Promise.all([
        fetch("/api/admin/beta/applicants", { credentials: "include" }),
        fetch("/api/admin/beta/settings", { credentials: "include" }),
      ]);
      if (appRes.status === 403) {
        setError("Forbidden");
        return;
      }
      if (!appRes.ok || !setRes.ok) throw new Error("Failed to load");
      const appData = await appRes.json();
      const setData = await setRes.json();
      setApplicants(appData.applicants);
      setSlotsUsed(appData.slotsUsed);
      setSettings(setData);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (authLoading) return;
    if (!user?.isAdmin) {
      router.replace("/");
      return;
    }
    fetchData();
    const interval = setInterval(fetchData, 15_000);
    return () => clearInterval(interval);
  }, [authLoading, user?.isAdmin, router, fetchData]);

  const toggleSetting = async (key: "accepting_applications" | "auto_approve" | "sync_enabled" | "notify_on_signup" | "notify_on_error" | "notify_on_capacity") => {
    const res = await fetch("/api/admin/beta/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ [key]: !settings[key] }),
    });
    if (res.ok) {
      const data = await res.json();
      setSettings(data);
    }
  };

  const testNotification = async () => {
    setTestingSend(true);
    try {
      const res = await fetch("/api/admin/beta/test-notification", {
        method: "POST",
        credentials: "include",
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        alert(body.error || `Test failed (${res.status})`);
      }
    } finally {
      setTestingSend(false);
    }
  };

  const syncFromASC = async () => {
    setSyncing(true);
    try {
      const res = await fetch("/api/admin/beta/sync", {
        method: "POST",
        credentials: "include",
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        alert(body.error || `Sync failed (${res.status})`);
      }
      await fetchData();
    } finally {
      setSyncing(false);
    }
  };

  const doAction = async (id: string, action: string, method = "POST") => {
    setActionLoading(id);
    try {
      const url = action === "remove"
        ? `/api/admin/beta/applicants/${id}`
        : `/api/admin/beta/applicants/${id}/${action}`;
      const res = await fetch(url, {
        method: action === "remove" ? "DELETE" : method,
        credentials: "include",
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        alert(body.error || `Action failed (${res.status})`);
      }
      await fetchData();
    } finally {
      setActionLoading(null);
      setConfirmRemove(null);
    }
  };

  const addApplicant = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch("/api/admin/beta/applicants", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ email: addEmail, firstName: addFirst, lastName: addLast }),
    });
    if (res.ok) {
      setAddEmail("");
      setAddFirst("");
      setAddLast("");
      setShowAddForm(false);
      await fetchData();
    } else {
      const body = await res.json().catch(() => ({}));
      alert(body.error || "Failed to add applicant");
    }
  };

  const toggleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir(sortDir === "asc" ? "desc" : "asc");
    } else {
      setSortKey(key);
      setSortDir(key === "email" || key === "name" || key === "status" ? "asc" : "desc");
    }
  };

  const sortedApplicants = useMemo(() => {
    const sorted = [...applicants].sort((a, b) => {
      let aVal: string | number | null;
      let bVal: string | number | null;
      switch (sortKey) {
        case "email":
          aVal = a.email;
          bVal = b.email;
          break;
        case "name":
          aVal = [a.first_name, a.last_name].filter(Boolean).join(" ");
          bVal = [b.first_name, b.last_name].filter(Boolean).join(" ");
          break;
        case "status":
          aVal = a.status;
          bVal = b.status;
          break;
        case "created_at":
          aVal = a.created_at;
          bVal = b.created_at;
          break;
        case "invited_at":
          aVal = a.invited_at;
          bVal = b.invited_at;
          break;
        case "member_at":
          aVal = a.member_at;
          bVal = b.member_at;
          break;
        case "installed_at":
          aVal = a.installed_at;
          bVal = b.installed_at;
          break;
        default:
          return 0;
      }
      if (aVal == null && bVal == null) return 0;
      if (aVal == null) return 1;
      if (bVal == null) return -1;
      if (aVal < bVal) return sortDir === "asc" ? -1 : 1;
      if (aVal > bVal) return sortDir === "asc" ? 1 : -1;
      return 0;
    });
    return sorted;
  }, [applicants, sortKey, sortDir]);

  if (authLoading || loading) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-zinc-400">Loading...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-red-400">{error}</p>
      </div>
    );
  }

  const slotsPercent = Math.min(100, Math.round((slotsUsed / settings.slot_cap) * 100));

  return (
    <div className="min-h-screen bg-deadly-bg text-zinc-100 p-6">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Nav */}
        <nav className="flex gap-4 text-sm">
          <a href="/admin/analytics" className="text-zinc-400 hover:text-zinc-200">
            Analytics
          </a>
          <span className="text-deadly-red font-medium">Beta</span>
        </nav>

        {/* Header */}
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-2xl font-bold">Beta Applicants</h1>
          <div className="flex flex-wrap gap-2 justify-end">
            <button
              onClick={syncFromASC}
              disabled={syncing}
              className="px-3 py-1.5 bg-deadly-surface border border-zinc-700 rounded text-sm hover:border-zinc-500 disabled:opacity-50"
            >
              {syncing ? "Syncing..." : <><span className="md:hidden">Sync</span><span className="hidden md:inline">Sync from ASC</span></>}
            </button>
            <button
              onClick={() => setShowAddForm(!showAddForm)}
              className="px-3 py-1.5 bg-deadly-surface border border-zinc-700 rounded text-sm hover:border-zinc-500"
            >
              <span className="md:hidden">+ Add</span><span className="hidden md:inline">+ Add Applicant</span>
            </button>
          </div>
        </div>

        {/* Settings bar */}
        <div className="bg-deadly-surface rounded-lg p-4 flex flex-wrap items-center gap-6 border border-zinc-800">
          {([
            { key: "accepting_applications" as const, label: "Accepting" },
            { key: "auto_approve" as const, label: "Auto-approve" },
            { key: "sync_enabled" as const, label: "Sync" },
          ]).map(({ key, label }) => (
            <label key={key} className="flex items-center gap-2 cursor-pointer">
              <span className="text-sm text-zinc-400">{label}</span>
              <button
                onClick={() => toggleSetting(key)}
                className={`w-10 h-5 rounded-full relative inline-flex items-center transition-colors ${
                  settings[key] ? "bg-green-600" : "bg-zinc-600"
                }`}
              >
                <span
                  className={`w-4 h-4 rounded-full bg-white transition-transform ${
                    settings[key] ? "translate-x-[22px]" : "translate-x-[2px]"
                  }`}
                />
              </button>
            </label>
          ))}
          <button
            onClick={() => setShowNotifyPanel(!showNotifyPanel)}
            className={`px-3 py-1 text-sm rounded border ${
              showNotifyPanel
                ? "border-zinc-500 text-zinc-200 bg-zinc-700"
                : "border-zinc-700 text-zinc-400 hover:border-zinc-500 hover:text-zinc-300"
            }`}
          >
            Notifications
          </button>

          <div className="flex items-center gap-3 w-full md:w-auto md:flex-1 md:max-w-xs">
            <span className="text-sm text-zinc-400 whitespace-nowrap">
              Slots: {slotsUsed} / {settings.slot_cap}
            </span>
            <div className="flex-1 h-2 bg-zinc-700 rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all ${
                  slotsPercent >= 90 ? "bg-red-500" : slotsPercent >= 70 ? "bg-yellow-500" : "bg-green-500"
                }`}
                style={{ width: `${slotsPercent}%` }}
              />
            </div>
          </div>

          {settings.last_synced_at && (
            <span className="text-xs text-zinc-500 whitespace-nowrap">
              Synced {formatTs(settings.last_synced_at)}
            </span>
          )}

          {(!settings.accepting_applications || !settings.auto_approve || slotsUsed >= settings.slot_cap) && (
            <div className="text-sm px-3 py-1 bg-yellow-600/20 text-yellow-400 rounded">
              {!settings.accepting_applications
                ? "Applications closed — form is hidden"
                : slotsUsed >= settings.slot_cap
                  ? "Slots full — new applicants will be waitlisted"
                  : "Auto-approve is off — applicants need manual approval"}
            </div>
          )}
        </div>

        {/* Add form */}
        {showAddForm && (
          <form onSubmit={addApplicant} className="bg-deadly-surface rounded-lg p-4 border border-zinc-800 flex flex-wrap md:flex-nowrap gap-3 items-end">
            <div className="w-full md:flex-1">
              <label className="block text-xs text-zinc-400 mb-1">Email</label>
              <input
                type="email"
                required
                value={addEmail}
                onChange={(e) => setAddEmail(e.target.value)}
                className="w-full bg-zinc-800 border border-zinc-700 rounded px-3 py-1.5 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-400 mb-1">First name</label>
              <input
                type="text"
                required
                value={addFirst}
                onChange={(e) => setAddFirst(e.target.value)}
                className="bg-zinc-800 border border-zinc-700 rounded px-3 py-1.5 text-sm w-32"
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-400 mb-1">Last name</label>
              <input
                type="text"
                required
                value={addLast}
                onChange={(e) => setAddLast(e.target.value)}
                className="bg-zinc-800 border border-zinc-700 rounded px-3 py-1.5 text-sm w-32"
              />
            </div>
            <button
              type="submit"
              className="px-4 py-1.5 bg-deadly-red text-white rounded text-sm hover:bg-red-700"
            >
              Add
            </button>
            <button
              type="button"
              onClick={() => setShowAddForm(false)}
              className="px-3 py-1.5 text-zinc-400 text-sm hover:text-zinc-200"
            >
              Cancel
            </button>
          </form>
        )}

        {/* Notifications panel */}
        {showNotifyPanel && (
          <div className="bg-deadly-surface rounded-lg p-4 border border-zinc-800 space-y-3">
            <h3 className="text-sm font-medium text-zinc-300">Slack Notifications</h3>
            <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-3">
              {([
                { key: "notify_on_signup" as const, label: "Signups", desc: "New applications & approvals" },
                { key: "notify_on_error" as const, label: "Errors", desc: "Failures & sync issues" },
                { key: "notify_on_capacity" as const, label: "Capacity", desc: "Slot usage warnings (90%+)" },
              ]).map(({ key, label, desc }) => (
                <label key={key} className="flex items-center gap-3 p-2 rounded hover:bg-zinc-800/50 cursor-pointer">
                  <button
                    onClick={() => toggleSetting(key)}
                    className={`w-10 h-5 rounded-full relative inline-flex items-center transition-colors shrink-0 ${
                      settings[key] ? "bg-green-600" : "bg-zinc-600"
                    }`}
                  >
                    <span
                      className={`w-4 h-4 rounded-full bg-white transition-transform ${
                        settings[key] ? "translate-x-[22px]" : "translate-x-[2px]"
                      }`}
                    />
                  </button>
                  <div>
                    <div className="text-sm text-zinc-200">{label}</div>
                    <div className="text-xs text-zinc-500">{desc}</div>
                  </div>
                </label>
              ))}
            </div>
            <div className="pt-2 border-t border-zinc-800">
              <button
                onClick={testNotification}
                disabled={testingSend}
                className="px-3 py-1.5 text-xs text-zinc-400 border border-zinc-700 rounded hover:border-zinc-500 hover:text-zinc-300 disabled:opacity-50"
              >
                {testingSend ? "Sending..." : "Send test ping"}
              </button>
            </div>
          </div>
        )}

        {/* Mobile list */}
        <div className="md:hidden space-y-1">
          {sortedApplicants.length === 0 && (
            <div className="bg-deadly-surface rounded-lg border border-zinc-800 px-4 py-8 text-center text-zinc-500">
              No applicants yet
            </div>
          )}
          {sortedApplicants.map((a) => {
            const isExpanded = expandedId === a.id;
            return (
              <div key={a.id} className="bg-deadly-surface rounded-lg border border-zinc-800 overflow-hidden">
                <button
                  onClick={() => setExpandedId(isExpanded ? null : a.id)}
                  className="w-full px-4 py-3 flex items-center gap-3 text-left"
                >
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-mono truncate">{a.email}</p>
                  </div>
                  <span className={`px-2 py-0.5 rounded text-xs font-medium shrink-0 ${STATUS_COLORS[a.status] ?? ""}`}>
                    {a.status}
                  </span>
                  <svg
                    className={`w-4 h-4 text-zinc-500 shrink-0 transition-transform ${isExpanded ? "rotate-180" : ""}`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
                {isExpanded && (
                  <div className="px-4 pb-3 space-y-2 border-t border-zinc-800 pt-2">
                    <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                      {([a.first_name, a.last_name].filter(Boolean).join(" ")) && (
                        <>
                          <span className="text-zinc-500">Name</span>
                          <span>{[a.first_name, a.last_name].filter(Boolean).join(" ")}</span>
                        </>
                      )}
                      <span className="text-zinc-500">Applied</span>
                      <span className="text-zinc-400">{formatTs(a.created_at)}</span>
                      {a.invited_at && (
                        <>
                          <span className="text-zinc-500">Invited</span>
                          <span className="text-zinc-400">{formatTs(a.invited_at)}</span>
                        </>
                      )}
                      {a.member_at && (
                        <>
                          <span className="text-zinc-500">Joined</span>
                          <span className="text-zinc-400">{formatTs(a.member_at)}</span>
                        </>
                      )}
                      {a.installed_at && (
                        <>
                          <span className="text-zinc-500">Installed</span>
                          <span className="text-zinc-400">{formatTs(a.installed_at)}</span>
                        </>
                      )}
                    </div>
                    {a.last_error && (
                      <p className="text-xs text-red-400 break-all">{a.last_error}</p>
                    )}
                    <div className="pt-1">
                      <RowActions
                        applicant={a}
                        loading={actionLoading === a.id}
                        confirmRemove={confirmRemove === a.id}
                        onAction={(action) => doAction(a.id, action)}
                        onConfirmRemove={() => setConfirmRemove(a.id)}
                        onCancelRemove={() => setConfirmRemove(null)}
                      />
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Desktop table */}
        <div className="hidden md:block bg-deadly-surface rounded-lg border border-zinc-800 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-800 text-zinc-400 text-left">
                {[
                  { key: "email", label: "Email" },
                  { key: "name", label: "Name" },
                  { key: "status", label: "Status" },
                  { key: "created_at", label: "Applied" },
                  { key: "invited_at", label: "Invited" },
                  { key: "member_at", label: "Joined" },
                  { key: "installed_at", label: "Installed" },
                ].map((col) => (
                  <th
                    key={col.key}
                    className="px-4 py-3 font-medium cursor-pointer hover:text-zinc-200 select-none"
                    onClick={() => toggleSort(col.key)}
                  >
                    {col.label}
                    {sortKey === col.key && (
                      <span className="ml-1">{sortDir === "asc" ? "↑" : "↓"}</span>
                    )}
                  </th>
                ))}
                <th className="px-4 py-3 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {sortedApplicants.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-zinc-500">
                    No applicants yet
                  </td>
                </tr>
              )}
              {sortedApplicants.map((a) => (
                <tr key={a.id} className="border-b border-zinc-800/50 hover:bg-zinc-800/30">
                  <td className="px-4 py-3 font-mono text-xs">{a.email}</td>
                  <td className="px-4 py-3">
                    {[a.first_name, a.last_name].filter(Boolean).join(" ") || "—"}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${STATUS_COLORS[a.status] ?? ""}`}>
                      {a.status}
                    </span>
                    {a.last_error && (
                      <span className="ml-2 text-xs text-red-400" title={a.last_error}>
                        ⚠
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs text-zinc-400">{formatTs(a.created_at)}</td>
                  <td className="px-4 py-3 text-xs text-zinc-400">{formatTs(a.invited_at)}</td>
                  <td className="px-4 py-3 text-xs text-zinc-400">{formatTs(a.member_at)}</td>
                  <td className="px-4 py-3 text-xs text-zinc-400">{formatTs(a.installed_at)}</td>
                  <td className="px-4 py-3">
                    <RowActions
                      applicant={a}
                      loading={actionLoading === a.id}
                      confirmRemove={confirmRemove === a.id}
                      onAction={(action) => doAction(a.id, action)}
                      onConfirmRemove={() => setConfirmRemove(a.id)}
                      onCancelRemove={() => setConfirmRemove(null)}
                    />
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

function RowActions({
  applicant,
  loading,
  confirmRemove,
  onAction,
  onConfirmRemove,
  onCancelRemove,
}: {
  applicant: BetaApplicant;
  loading: boolean;
  confirmRemove: boolean;
  onAction: (action: string) => void;
  onConfirmRemove: () => void;
  onCancelRemove: () => void;
}) {
  if (loading) return <span className="text-xs text-zinc-500">...</span>;

  if (confirmRemove) {
    return (
      <div className="flex items-center gap-2">
        <span className="text-xs text-red-400">Delete ASC access?</span>
        <button
          onClick={() => onAction("remove")}
          className="px-2 py-0.5 bg-red-600 text-white rounded text-xs hover:bg-red-700"
        >
          Confirm
        </button>
        <button
          onClick={onCancelRemove}
          className="px-2 py-0.5 text-zinc-400 text-xs hover:text-zinc-200"
        >
          Cancel
        </button>
      </div>
    );
  }

  const buttons: Array<{ label: string; action: string; danger?: boolean; confirm?: boolean }> = [];

  switch (applicant.status) {
    case "pending":
      buttons.push({ label: "Approve", action: "approve" });
      buttons.push({ label: "Reject", action: "reject" });
      buttons.push({ label: "Remove", action: "remove", danger: true, confirm: true });
      break;
    case "invited":
    case "member":
    case "installed":
      buttons.push({ label: "Remove", action: "remove", danger: true, confirm: true });
      break;
    case "error":
      buttons.push({ label: "Retry", action: "retry" });
      buttons.push({ label: "Remove", action: "remove", danger: true, confirm: true });
      break;
    case "expired":
      buttons.push({ label: "Re-invite", action: "retry" });
      buttons.push({ label: "Remove", action: "remove", danger: true, confirm: true });
      break;
    case "rejected":
      buttons.push({ label: "Remove", action: "remove", danger: true, confirm: true });
      break;
  }

  if (buttons.length === 0) return null;

  return (
    <div className="flex gap-1.5">
      {buttons.map((b) => (
        <button
          key={b.action}
          onClick={() => (b.confirm ? onConfirmRemove() : onAction(b.action))}
          className={`px-2 py-0.5 rounded text-xs ${
            b.danger
              ? "bg-red-600/20 text-red-400 hover:bg-red-600/40"
              : "bg-zinc-700 text-zinc-300 hover:bg-zinc-600"
          }`}
        >
          {b.label}
        </button>
      ))}
    </div>
  );
}
