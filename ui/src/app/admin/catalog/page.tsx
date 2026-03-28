"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback, useRef } from "react";

interface ArtistSummary {
  id: string;
  name: string;
  short_name: string | null;
  ia_collection: string | null;
  is_active: number;
  show_count: number;
  recording_count: number;
  data_sources: string;
}

interface PipelineRun {
  id: number;
  artist_id: string;
  collector_type: string;
  started_at: number;
  completed_at: number | null;
  status: string;
  records_processed: number;
  records_created: number;
  error_message: string | null;
}

interface CatalogStats {
  total_artists: number;
  total_shows: number;
  total_recordings: number;
  total_collections: number;
  db_size_bytes: number;
  per_artist: {
    artist_id: string;
    artist_name: string;
    show_count: number;
    recording_count: number;
    last_updated: number | null;
  }[];
}

interface PipelineStatus {
  artist_id: string;
  artist_name: string;
  last_run: PipelineRun | null;
}

interface CollectorInfo {
  type: string;
  name: string;
  description: string;
  help: string;
}

export default function CatalogAdmin() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [artists, setArtists] = useState<ArtistSummary[]>([]);
  const [stats, setStats] = useState<CatalogStats | null>(null);
  const [pipelineStatus, setPipelineStatus] = useState<PipelineStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshingArtist, setRefreshingArtist] = useState<string | null>(null);
  const [refreshMessage, setRefreshMessage] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [collectors, setCollectors] = useState<CollectorInfo[]>([]);

  const fetchData = useCallback(async () => {
    try {
      const [artistsRes, statsRes, statusRes, collectorsRes] = await Promise.all([
        fetch("/api/artists", { credentials: "include" }),
        fetch("/api/admin/stats", { credentials: "include" }),
        fetch("/api/admin/pipeline/status", { credentials: "include" }),
        fetch("/api/admin/collectors", { credentials: "include" }),
      ]);

      if (statsRes.status === 403) {
        setError("Forbidden");
        return;
      }

      if (!artistsRes.ok || !statsRes.ok || !statusRes.ok) {
        throw new Error("Failed to load catalog data");
      }

      setArtists(await artistsRes.json());
      setStats(await statsRes.json());
      setPipelineStatus(await statusRes.json());
      if (collectorsRes.ok) setCollectors(await collectorsRes.json());
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
    // Auto-refresh every 5 seconds to pick up import progress
    const interval = setInterval(fetchData, 5000);
    return () => clearInterval(interval);
  }, [authLoading, user?.isAdmin, router, fetchData]);

  const handleRefresh = async (artistId: string) => {
    setRefreshingArtist(artistId);
    setRefreshMessage(null);
    try {
      const res = await fetch(`/api/admin/catalog/refresh/${artistId}`, {
        method: "POST",
        credentials: "include",
      });
      const data = await res.json();
      if (!res.ok) {
        setRefreshMessage(`Error: ${data.error ?? res.statusText}`);
      } else {
        setRefreshMessage(`${data.message} (run #${data.run_id})`);
        // Refresh pipeline status after a short delay to let import start
        setTimeout(fetchData, 2000);
      }
    } catch (e) {
      setRefreshMessage(e instanceof Error ? e.message : "Request failed");
    } finally {
      setRefreshingArtist(null);
    }
  };

  const handleSetCollectorType = async (artistId: string, currentDataSources: string, collectorType: string) => {
    const ds = JSON.parse(currentDataSources || "{}");
    if (collectorType) {
      ds.collector_type = collectorType;
    } else {
      delete ds.collector_type;
    }
    try {
      await fetch(`/api/admin/catalog/artists/${artistId}`, {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ data_sources: ds }),
      });
      fetchData();
    } catch {
      // silently fail — user will see stale data
    }
  };

  if (authLoading || (!user?.isAdmin && !error)) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-zinc-400">Loading...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-deadly-red">{error}</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-zinc-400">Loading catalog...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-deadly-bg p-6 max-w-5xl mx-auto">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-2xl font-bold text-deadly-red">Catalog</h1>
        <a href="/admin" className="text-sm text-zinc-400 hover:text-zinc-200">
          Admin
        </a>
      </div>

      {/* DB Stats */}
      {stats && (
        <section className="mb-8">
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
            Database
          </h2>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <MetricCard label="Artists" value={stats.total_artists} />
            <MetricCard label="Shows" value={stats.total_shows.toLocaleString()} />
            <MetricCard label="Recordings" value={stats.total_recordings.toLocaleString()} />
            <MetricCard label="DB Size" value={`${(stats.db_size_bytes / 1024 / 1024).toFixed(1)} MB`} />
          </div>
        </section>
      )}

      {/* Artists */}
      <section className="mb-8">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider">
            Artists
          </h2>
          <button
            onClick={() => setShowAddForm((v) => !v)}
            className="text-xs font-medium px-3 py-1 rounded bg-zinc-700 text-zinc-200 hover:bg-zinc-600 transition-colors"
          >
            {showAddForm ? "Cancel" : "+ Add Artist"}
          </button>
        </div>

        {showAddForm && (
          <AddArtistForm collectors={collectors} onCreated={() => { setShowAddForm(false); fetchData(); }} />
        )}

        {artists.length === 0 && !showAddForm ? (
          <p className="text-zinc-500">No artists yet. Add one to get started.</p>
        ) : (
          <div className="bg-deadly-surface rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-700">
                  <th className="text-left text-zinc-400 px-4 py-2">Artist</th>
                  <th className="text-right text-zinc-400 px-4 py-2">Shows</th>
                  <th className="text-right text-zinc-400 px-4 py-2">Recordings</th>
                  <th className="text-left text-zinc-400 px-4 py-2">Collector</th>
                  <th className="text-right text-zinc-400 px-4 py-2">Last Import</th>
                  <th className="text-right text-zinc-400 px-4 py-2"></th>
                </tr>
              </thead>
              <tbody>
                {artists.map((artist) => {
                  const status = pipelineStatus.find((p) => p.artist_id === artist.id);
                  const lastRun = status?.last_run;
                  const ds = JSON.parse(artist.data_sources || "{}");
                  const currentCollector = ds.collector_type || "";
                  const collectorInfo = collectors.find((c) => c.type === currentCollector);
                  return (
                    <tr key={artist.id} className="border-b border-zinc-800 last:border-0">
                      <td className="px-4 py-3">
                        <div className="text-zinc-200 font-medium">{artist.name}</div>
                        <div className="text-xs text-zinc-500">{artist.id}</div>
                      </td>
                      <td className="px-4 py-3 text-right text-zinc-300">
                        {artist.show_count.toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-right text-zinc-300">
                        {artist.recording_count.toLocaleString()}
                      </td>
                      <td className="px-4 py-3">
                        <select
                          value={currentCollector}
                          onChange={(e) => handleSetCollectorType(artist.id, artist.data_sources, e.target.value)}
                          className="bg-zinc-800 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-300 focus:outline-none focus:ring-1 focus:ring-deadly-red"
                          title={collectorInfo?.description}
                        >
                          <option value="">Not configured</option>
                          {collectors.map((c) => (
                            <option key={c.type} value={c.type}>{c.name}</option>
                          ))}
                        </select>
                        {!currentCollector && (
                          <div className="text-xs text-amber-500 mt-0.5">No importer set</div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {lastRun ? (
                          <div>
                            <span className={`text-xs px-1.5 py-0.5 rounded ${
                              lastRun.status === "completed" ? "bg-green-900/50 text-green-400"
                                : lastRun.status === "running" ? "bg-blue-900/50 text-blue-400"
                                : "bg-red-900/50 text-red-400"
                            }`}>
                              {lastRun.status}
                            </span>
                            <div className="text-xs text-zinc-500 mt-1">
                              {lastRun.completed_at
                                ? new Date(lastRun.completed_at * 1000).toLocaleDateString()
                                : "in progress"}
                            </div>
                            {lastRun.status === "completed" && (
                              <div className="text-xs text-zinc-600">
                                {lastRun.records_created} created
                              </div>
                            )}
                            {lastRun.error_message && (
                              <div className="text-xs text-red-400 mt-0.5 truncate max-w-48" title={lastRun.error_message}>
                                {lastRun.error_message}
                              </div>
                            )}
                          </div>
                        ) : (
                          <span className="text-xs text-zinc-600">never</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleRefresh(artist.id)}
                          disabled={refreshingArtist === artist.id}
                          className="px-3 py-1.5 text-xs font-medium rounded bg-deadly-red/20 text-deadly-red hover:bg-deadly-red/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                        >
                          {refreshingArtist === artist.id ? "Importing..." : "Import"}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {refreshMessage && (
          <div className="mt-3 text-sm text-zinc-300 bg-deadly-surface rounded px-4 py-2">
            {refreshMessage}
          </div>
        )}
      </section>

      {/* Pipeline Console */}
      <PipelineConsole pipelineStatus={pipelineStatus} />

      {/* Pipeline History */}
      <section className="mb-8">
        <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
          Recent Pipeline Runs
        </h2>
        <PipelineHistory />
      </section>
    </div>
  );
}

function PipelineConsole({ pipelineStatus }: { pipelineStatus: PipelineStatus[] }) {
  const [logs, setLogs] = useState<{ t: number; msg: string }[]>([]);
  const [activeRunId, setActiveRunId] = useState<number | null>(null);
  const [done, setDone] = useState(false);
  const [minimized, setMinimized] = useState(false);
  const logEndRef = useRef<HTMLDivElement>(null);
  const sinceRef = useRef(0);

  // Find any running pipeline
  const runningStatus = pipelineStatus.find((p) => p.last_run?.status === "running");
  const runId = runningStatus?.last_run?.id ?? null;
  const artistName = runningStatus?.artist_name ?? null;

  // Reset when a new run starts
  useEffect(() => {
    if (runId && runId !== activeRunId) {
      setActiveRunId(runId);
      setLogs([]);
      setDone(false);
      setMinimized(false);
      sinceRef.current = 0;
    } else if (!runId && activeRunId && done) {
      // Keep showing logs after completion, don't clear
    }
  }, [runId, activeRunId, done]);

  // Poll logs
  useEffect(() => {
    if (!activeRunId) return;

    const poll = async () => {
      try {
        const res = await fetch(
          `/api/admin/pipeline/runs/${activeRunId}/logs?since=${sinceRef.current}`,
          { credentials: "include" },
        );
        if (!res.ok) return;
        const data = await res.json() as { logs: { t: number; msg: string }[]; done: boolean };
        if (data.logs.length > 0) {
          setLogs((prev) => [...prev, ...data.logs]);
          sinceRef.current = data.logs[data.logs.length - 1].t;
        }
        if (data.done) setDone(true);
      } catch { /* ignore */ }
    };

    poll();
    const interval = setInterval(poll, 1500);
    return () => clearInterval(interval);
  }, [activeRunId]);

  // Auto-scroll
  useEffect(() => {
    if (!minimized) logEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [logs, minimized]);

  // Nothing to show
  if (!activeRunId || (done && logs.length === 0)) return null;

  return (
    <section className="mb-8">
      <div className="bg-zinc-950 border border-zinc-800 rounded-lg overflow-hidden font-mono">
        {/* Title bar */}
        <div className="flex items-center justify-between px-3 py-1.5 bg-zinc-900 border-b border-zinc-800">
          <div className="flex items-center gap-2">
            <span className={`inline-block w-2 h-2 rounded-full ${done ? "bg-zinc-500" : "bg-green-500 animate-pulse"}`} />
            <span className="text-xs text-zinc-400">
              Pipeline {done ? "completed" : "running"}
              {artistName && ` — ${artistName}`}
              {activeRunId && ` (#${activeRunId})`}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setMinimized((v) => !v)}
              className="text-xs text-zinc-500 hover:text-zinc-300 transition-colors px-1"
            >
              {minimized ? "expand" : "minimize"}
            </button>
            {done && (
              <button
                onClick={() => { setActiveRunId(null); setLogs([]); }}
                className="text-xs text-zinc-500 hover:text-zinc-300 transition-colors px-1"
              >
                close
              </button>
            )}
          </div>
        </div>
        {/* Log output */}
        {!minimized && (
          <div className="p-3 max-h-64 overflow-y-auto text-xs leading-relaxed">
            {logs.map((log, i) => (
              <div key={i} className="flex gap-2">
                <span className="text-zinc-600 shrink-0 select-none">
                  {new Date(log.t).toLocaleTimeString()}
                </span>
                <span className={log.msg.startsWith("ERROR") ? "text-red-400" : "text-zinc-300"}>
                  {log.msg}
                </span>
              </div>
            ))}
            {!done && logs.length === 0 && (
              <span className="text-zinc-600">Waiting for output...</span>
            )}
            <div ref={logEndRef} />
          </div>
        )}
      </div>
    </section>
  );
}

function PipelineHistory() {
  const [runs, setRuns] = useState<PipelineRun[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchRuns = () =>
      fetch("/api/admin/pipeline/runs?limit=20", { credentials: "include" })
        .then((r) => r.json())
        .then(setRuns)
        .catch(() => {})
        .finally(() => setLoading(false));
    fetchRuns();
    const interval = setInterval(fetchRuns, 5000);
    return () => clearInterval(interval);
  }, []);

  if (loading) return <p className="text-zinc-500 text-sm">Loading...</p>;
  if (runs.length === 0) return <p className="text-zinc-500 text-sm">No pipeline runs yet.</p>;

  return (
    <div className="bg-deadly-surface rounded-lg overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-zinc-700">
            <th className="text-left text-zinc-400 px-4 py-2">#</th>
            <th className="text-left text-zinc-400 px-4 py-2">Artist</th>
            <th className="text-left text-zinc-400 px-4 py-2">Type</th>
            <th className="text-left text-zinc-400 px-4 py-2">Status</th>
            <th className="text-right text-zinc-400 px-4 py-2">Processed</th>
            <th className="text-right text-zinc-400 px-4 py-2">Created</th>
            <th className="text-right text-zinc-400 px-4 py-2">When</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr key={run.id} className="border-b border-zinc-800 last:border-0">
              <td className="px-4 py-2 text-zinc-500">{run.id}</td>
              <td className="px-4 py-2 text-zinc-300">{run.artist_id}</td>
              <td className="px-4 py-2 text-zinc-400">{run.collector_type}</td>
              <td className="px-4 py-2">
                <span className={`text-xs px-1.5 py-0.5 rounded ${
                  run.status === "completed" ? "bg-green-900/50 text-green-400"
                    : run.status === "running" ? "bg-blue-900/50 text-blue-400"
                    : "bg-red-900/50 text-red-400"
                }`}>
                  {run.status}
                </span>
              </td>
              <td className="px-4 py-2 text-right text-zinc-300">{run.records_processed}</td>
              <td className="px-4 py-2 text-right text-zinc-300">{run.records_created}</td>
              <td className="px-4 py-2 text-right text-zinc-500 text-xs">
                {run.completed_at
                  ? new Date(run.completed_at * 1000).toLocaleString()
                  : run.started_at
                    ? new Date(run.started_at * 1000).toLocaleString()
                    : "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

interface IASearchResult {
  identifier: string;
  title: string;
  description: string;
  item_count: number;
}

interface IACollectionDetail {
  identifier: string;
  title: string;
  creator: string | null;
  description: string;
  image_url: string | null;
  item_count: number;
  active_from: number | null;
  active_to: number | null;
  is_active: boolean;
  subjects: string[];
}

function AddArtistForm({ collectors, onCreated }: { collectors: CollectorInfo[]; onCreated: () => void }) {
  const [phase, setPhase] = useState<"search" | "form">("search");

  // Search state
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<IASearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [loadingCollection, setLoadingCollection] = useState<string | null>(null);

  // Form state
  const [form, setForm] = useState({
    id: "",
    name: "",
    short_name: "",
    ia_collection: "",
    description: "",
    image_url: "",
    active_from: "",
    active_to: "",
    is_active: false,
    collector_type: "",
    data_source_setlists: "",
  });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setSearching(true);
    setSearchError(null);
    setSearchResults([]);
    try {
      const res = await fetch(
        `/api/admin/catalog/search-archive?q=${encodeURIComponent(searchQuery.trim())}`,
        { credentials: "include" },
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: IASearchResult[] = await res.json();
      setSearchResults(data);
      if (data.length === 0) setSearchError("No collections found. Try a different search or enter manually.");
    } catch (e) {
      setSearchError(e instanceof Error ? e.message : "Search failed");
    } finally {
      setSearching(false);
    }
  };

  const handleSelectCollection = async (identifier: string) => {
    setLoadingCollection(identifier);
    try {
      const res = await fetch(
        `/api/admin/catalog/archive-collection/${encodeURIComponent(identifier)}`,
        { credentials: "include" },
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const detail: IACollectionDetail = await res.json();

      const name = detail.creator || detail.title;
      const autoId = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");

      setForm({
        id: autoId,
        name,
        short_name: "",
        ia_collection: detail.identifier,
        description: detail.description.slice(0, 500),
        image_url: detail.image_url ?? "",
        active_from: detail.active_from ? String(detail.active_from) : "",
        active_to: detail.active_to ? String(detail.active_to) : "",
        is_active: detail.is_active,
        collector_type: "",
        data_source_setlists: "",
      });
      setPhase("form");
    } catch (e) {
      setSearchError(e instanceof Error ? e.message : "Failed to load collection");
    } finally {
      setLoadingCollection(null);
    }
  };

  const handleManualEntry = () => {
    setForm({
      id: "", name: "", short_name: "", ia_collection: "",
      description: "", image_url: "", active_from: "", active_to: "",
      is_active: false, collector_type: "", data_source_setlists: "",
    });
    setPhase("form");
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target;
    setForm((f) => ({
      ...f,
      [name]: type === "checkbox" ? (e.target as HTMLInputElement).checked : value,
    }));
  };

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const name = e.target.value;
    const autoId = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
    setForm((f) => ({ ...f, name, id: autoId }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setFormError(null);

    const dataSources: Record<string, string> = { primary: "archive.org" };
    if (form.collector_type) dataSources.collector_type = form.collector_type;
    if (form.data_source_setlists) dataSources.setlists = form.data_source_setlists;
    if (form.ia_collection) dataSources.archive_collection = form.ia_collection;

    try {
      const res = await fetch("/api/admin/catalog/artists", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: form.id,
          name: form.name,
          short_name: form.short_name || undefined,
          ia_collection: form.ia_collection || undefined,
          description: form.description || undefined,
          image_url: form.image_url || undefined,
          active_from: form.active_from ? Number(form.active_from) : undefined,
          active_to: form.active_to ? Number(form.active_to) : undefined,
          is_active: form.is_active,
          data_sources: dataSources,
        }),
      });

      if (!res.ok) {
        const data = await res.json();
        setFormError(data.error ?? `HTTP ${res.status}`);
        return;
      }

      onCreated();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : "Request failed");
    } finally {
      setSubmitting(false);
    }
  };

  const inputClass = "w-full bg-zinc-800 border border-zinc-700 rounded px-3 py-1.5 text-sm text-zinc-200 placeholder-zinc-500 focus:outline-none focus:ring-1 focus:ring-deadly-red";
  const labelClass = "block text-xs text-zinc-400 mb-1";

  // ── Phase A: Search IA ──────────────────────────────────────

  if (phase === "search") {
    return (
      <div className="bg-deadly-surface rounded-lg p-4 mb-4">
        <div className="flex gap-2 mb-3">
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            placeholder="Search Internet Archive collections..."
            className={inputClass}
            autoFocus
          />
          <button
            onClick={handleSearch}
            disabled={searching || !searchQuery.trim()}
            className="px-4 py-1.5 text-sm font-medium rounded bg-deadly-red text-white hover:bg-deadly-red/80 disabled:opacity-50 disabled:cursor-not-allowed transition-colors whitespace-nowrap"
          >
            {searching ? "Searching..." : "Search"}
          </button>
        </div>

        {searchError && <p className="text-sm text-red-400 mb-3">{searchError}</p>}

        {searchResults.length > 0 && (
          <div className="space-y-2 mb-3 max-h-80 overflow-y-auto">
            {searchResults.map((result) => (
              <div
                key={result.identifier}
                className="flex items-start justify-between gap-3 p-3 rounded bg-zinc-800/50 hover:bg-zinc-800 transition-colors"
              >
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-zinc-200">{result.title}</div>
                  <div className="text-xs text-zinc-500 mt-0.5">
                    {result.identifier} &middot; {result.item_count.toLocaleString()} recordings
                  </div>
                  {result.description && (
                    <div className="text-xs text-zinc-400 mt-1 line-clamp-2">
                      {result.description.replace(/<[^>]*>/g, "").slice(0, 200)}
                    </div>
                  )}
                </div>
                <button
                  onClick={() => handleSelectCollection(result.identifier)}
                  disabled={loadingCollection === result.identifier}
                  className="px-3 py-1 text-xs font-medium rounded bg-zinc-700 text-zinc-200 hover:bg-zinc-600 disabled:opacity-50 transition-colors whitespace-nowrap"
                >
                  {loadingCollection === result.identifier ? "Loading..." : "Select"}
                </button>
              </div>
            ))}
          </div>
        )}

        <button
          onClick={handleManualEntry}
          className="text-xs text-zinc-500 hover:text-zinc-300 transition-colors"
        >
          Enter manually instead
        </button>
      </div>
    );
  }

  // ── Phase B: Form (auto-filled or manual) ───────────────────

  return (
    <form onSubmit={handleSubmit} className="bg-deadly-surface rounded-lg p-4 mb-4">
      <div className="flex items-center justify-between mb-3">
        <button
          type="button"
          onClick={() => setPhase("search")}
          className="text-xs text-zinc-500 hover:text-zinc-300 transition-colors"
        >
          &larr; Back to search
        </button>
        {form.ia_collection && (
          <span className="text-xs text-zinc-500">
            From IA: {form.ia_collection}
          </span>
        )}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className={labelClass}>Name *</label>
          <input name="name" value={form.name} onChange={handleNameChange} required placeholder="Grateful Dead" className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>ID (auto-generated)</label>
          <input name="id" value={form.id} onChange={handleChange} required placeholder="grateful-dead" className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Short Name</label>
          <input name="short_name" value={form.short_name} onChange={handleChange} placeholder="GD" className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>IA Collection</label>
          <input name="ia_collection" value={form.ia_collection} onChange={handleChange} placeholder="GratefulDead" className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Active From (year)</label>
          <input name="active_from" value={form.active_from} onChange={handleChange} type="number" placeholder="1965" className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Active To (year, blank = still active)</label>
          <input name="active_to" value={form.active_to} onChange={handleChange} type="number" placeholder="" className={inputClass} />
        </div>
        <div className="sm:col-span-2">
          <label className={labelClass}>Description</label>
          <textarea name="description" value={form.description} onChange={handleChange} rows={2} placeholder="Brief description of the artist..." className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Setlist Source (optional)</label>
          <select name="data_source_setlists" value={form.data_source_setlists} onChange={handleChange} className={inputClass}>
            <option value="">None</option>
            <option value="setlist.fm">setlist.fm</option>
          </select>
        </div>
        <div className="flex items-center gap-2 pt-5">
          <input type="checkbox" name="is_active" checked={form.is_active} onChange={handleChange} id="is_active" className="rounded border-zinc-600" />
          <label htmlFor="is_active" className="text-sm text-zinc-300">Currently active band</label>
        </div>

        {/* Collector Type */}
        <div className="sm:col-span-2 border-t border-zinc-700 pt-4 mt-2">
          <label className={labelClass}>Collector / Importer</label>
          <select name="collector_type" value={form.collector_type} onChange={handleChange} className={inputClass}>
            <option value="">None — configure later</option>
            {collectors.map((c) => (
              <option key={c.type} value={c.type}>{c.name}</option>
            ))}
          </select>
          {form.collector_type ? (
            <p className="text-xs text-zinc-400 mt-2">
              {collectors.find((c) => c.type === form.collector_type)?.description}
            </p>
          ) : (
            <p className="text-xs text-zinc-500 mt-2">
              Determines how show and recording data is imported for this artist.
              You can set this after creation from the artist table.
              {collectors.length > 0 && " Available importers:"}
            </p>
          )}
          {!form.collector_type && collectors.map((c) => (
            <div key={c.type} className="mt-2 p-2 rounded bg-zinc-800/50 text-xs">
              <span className="text-zinc-300 font-medium">{c.name}</span>
              <span className="text-zinc-500"> — {c.description}</span>
              <p className="text-zinc-600 mt-1">{c.help}</p>
            </div>
          ))}
        </div>
      </div>

      {formError && <p className="text-sm text-red-400 mt-3">{formError}</p>}

      <div className="mt-4 flex justify-end">
        <button
          type="submit"
          disabled={submitting || !form.id || !form.name}
          className="px-4 py-2 text-sm font-medium rounded bg-deadly-red text-white hover:bg-deadly-red/80 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {submitting ? "Creating..." : "Add Artist"}
        </button>
      </div>
    </form>
  );
}

function MetricCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="bg-deadly-surface rounded-lg p-4">
      <p className="text-xs text-zinc-400 uppercase tracking-wider mb-1">{label}</p>
      <p className="text-3xl font-bold text-white">{value}</p>
    </div>
  );
}
