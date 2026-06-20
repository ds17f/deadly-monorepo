"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";

interface BugReport {
  id: string;
  user_id: string | null;
  user_email: string | null;
  note: string | null;
  platform: string | null;
  app_version: string | null;
  os_version: string | null;
  device: string | null;
  install_id: string | null;
  size_bytes: number;
  created_at: number;
}

function formatTs(ts: number): string {
  return new Date(ts * 1000).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function BugReportsAdminPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [items, setItems] = useState<BugReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [preview, setPreview] = useState<{ id: string; text: string } | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      const res = await fetch("/api/admin/bug-reports", { credentials: "include" });
      if (res.status === 403) {
        setError("Forbidden");
        return;
      }
      if (!res.ok) throw new Error("Failed to load");
      const data = await res.json();
      setItems(data.reports);
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

  const remove = async (id: string) => {
    const res = await fetch(`/api/admin/bug-reports/${id}`, {
      method: "DELETE",
      credentials: "include",
    });
    if (!res.ok && res.status !== 204) {
      const b = await res.json().catch(() => ({}));
      alert(b.error || `Delete failed (${res.status})`);
    }
    setConfirmDelete(null);
    if (preview?.id === id) setPreview(null);
    await fetchData();
  };

  const openPreview = async (id: string) => {
    if (preview?.id === id) {
      setPreview(null);
      return;
    }
    setPreviewLoading(true);
    try {
      const res = await fetch(`/api/admin/bug-reports/${id}`, { credentials: "include" });
      if (!res.ok) throw new Error(`Failed (${res.status})`);
      setPreview({ id, text: await res.text() });
    } catch (e) {
      alert(e instanceof Error ? e.message : "Failed to load logs");
    } finally {
      setPreviewLoading(false);
    }
  };

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

  return (
    <div className="min-h-screen bg-deadly-bg text-zinc-100 p-6">
      <div className="max-w-3xl mx-auto space-y-6">
        <nav className="flex gap-4 text-sm">
          <a href="/admin" className="text-zinc-400 hover:text-zinc-200">Admin</a>
          <span className="text-deadly-red font-medium">Bug Reports</span>
        </nav>

        <div>
          <h1 className="text-2xl font-bold">Bug Reports</h1>
          <p className="mt-1 text-sm text-zinc-400">
            Logs submitted from the apps. Preview inline, download the raw file, or
            delete. Files are stored privately on the server and only reachable here.
          </p>
        </div>

        <div className="space-y-2">
          {items.length === 0 ? (
            <div className="bg-deadly-surface rounded-lg border border-zinc-800 px-4 py-8 text-center text-zinc-500">
              No bug reports yet.
            </div>
          ) : (
            items.map((r) => (
              <div key={r.id} className="bg-deadly-surface rounded-lg border border-zinc-800 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      {r.platform && (
                        <span className="px-2 py-0.5 rounded text-xs font-medium bg-blue-600/20 text-blue-300 capitalize">{r.platform}</span>
                      )}
                      {r.app_version && (
                        <span className="px-2 py-0.5 rounded text-xs font-medium bg-zinc-700/40 text-zinc-300">v{r.app_version}</span>
                      )}
                      <span className="text-xs text-zinc-500">{formatTs(r.created_at)}</span>
                      <span className="text-xs text-zinc-600">{formatSize(r.size_bytes)}</span>
                    </div>
                    <p className="mt-1.5 text-sm text-zinc-200">
                      {r.user_email ? (
                        <span className="font-medium">{r.user_email}</span>
                      ) : (
                        <span className="text-zinc-500 italic">anonymous</span>
                      )}
                    </p>
                    {(r.device || r.os_version) && (
                      <p className="mt-0.5 text-xs text-zinc-500">
                        {[r.device, r.os_version].filter(Boolean).join(" · ")}
                      </p>
                    )}
                    {r.note && (
                      <p className="mt-1.5 text-sm text-zinc-300 whitespace-pre-wrap break-words bg-zinc-900/40 rounded px-2 py-1.5 border border-zinc-800">
                        {r.note}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      onClick={() => openPreview(r.id)}
                      className="px-2 py-0.5 rounded text-xs bg-zinc-700/40 text-zinc-300 hover:bg-zinc-700/70"
                    >
                      {preview?.id === r.id ? "Hide" : "Preview"}
                    </button>
                    <a
                      href={`/api/admin/bug-reports/${r.id}`}
                      download={`bugreport-${r.id}.txt`}
                      className="px-2 py-0.5 rounded text-xs bg-green-600/20 text-green-400 hover:bg-green-600/40"
                    >
                      Download
                    </a>
                    {confirmDelete === r.id ? (
                      <>
                        <button onClick={() => remove(r.id)} className="px-2 py-0.5 bg-red-600 text-white rounded text-xs hover:bg-red-700">Confirm</button>
                        <button onClick={() => setConfirmDelete(null)} className="px-2 py-0.5 text-zinc-400 text-xs hover:text-zinc-200">Cancel</button>
                      </>
                    ) : (
                      <button
                        onClick={() => setConfirmDelete(r.id)}
                        className="px-2 py-0.5 rounded text-xs bg-red-600/20 text-red-400 hover:bg-red-600/40"
                      >
                        Delete
                      </button>
                    )}
                  </div>
                </div>

                {preview?.id === r.id && (
                  <pre className="mt-3 max-h-96 overflow-auto rounded bg-zinc-950 border border-zinc-800 p-3 text-xs text-zinc-300 whitespace-pre-wrap break-words">
                    {previewLoading ? "Loading…" : preview.text}
                  </pre>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
