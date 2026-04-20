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
  auto_approve: boolean;
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
  const [settings, setSettings] = useState<BetaSettings>({ auto_approve: true, slot_cap: 100, last_synced_at: null });
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
  const [sortKey, setSortKey] = useState<string>("created_at");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");

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
  }, [authLoading, user?.isAdmin, router, fetchData]);

  const toggleAutoApprove = async () => {
    const res = await fetch("/api/admin/beta/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ auto_approve: !settings.auto_approve }),
    });
    if (res.ok) {
      const data = await res.json();
      setSettings(data);
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
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold">Beta Applicants</h1>
          <div className="flex gap-2">
            <button
              onClick={syncFromASC}
              disabled={syncing}
              className="px-3 py-1.5 bg-deadly-surface border border-zinc-700 rounded text-sm hover:border-zinc-500 disabled:opacity-50"
            >
              {syncing ? "Syncing..." : "Sync from ASC"}
            </button>
            <button
              onClick={() => setShowAddForm(!showAddForm)}
              className="px-3 py-1.5 bg-deadly-surface border border-zinc-700 rounded text-sm hover:border-zinc-500"
            >
              + Add Applicant
            </button>
          </div>
        </div>

        {/* Settings bar */}
        <div className="bg-deadly-surface rounded-lg p-4 flex items-center gap-6 border border-zinc-800">
          <label className="flex items-center gap-2 cursor-pointer">
            <span className="text-sm text-zinc-400">Auto-approve</span>
            <button
              onClick={toggleAutoApprove}
              className={`w-10 h-5 rounded-full relative inline-flex items-center transition-colors ${
                settings.auto_approve ? "bg-green-600" : "bg-zinc-600"
              }`}
            >
              <span
                className={`w-4 h-4 rounded-full bg-white transition-transform ${
                  settings.auto_approve ? "translate-x-[22px]" : "translate-x-[2px]"
                }`}
              />
            </button>
          </label>

          <div className="flex items-center gap-3 flex-1 max-w-xs">
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

          {(!settings.auto_approve || slotsUsed >= settings.slot_cap) && (
            <div className="text-sm px-3 py-1 bg-yellow-600/20 text-yellow-400 rounded">
              {slotsUsed >= settings.slot_cap
                ? "Slots full — new applicants will be waitlisted"
                : "Auto-approve is off — applicants need manual approval"}
            </div>
          )}
        </div>

        {/* Add form */}
        {showAddForm && (
          <form onSubmit={addApplicant} className="bg-deadly-surface rounded-lg p-4 border border-zinc-800 flex gap-3 items-end">
            <div className="flex-1">
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

        {/* Table */}
        <div className="bg-deadly-surface rounded-lg border border-zinc-800 overflow-x-auto">
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
