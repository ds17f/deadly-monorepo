package com.grateful.deadly.core.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local cache + cursor for in-app messages, persisted as a single JSON blob in
 * its own SharedPreferences file. This is the mobile-faithful equivalent of the
 * web client's `localStorage` store — load all, filter in memory, tiny volume.
 *
 * All seen/dismissed state lives here and is never sent to the server. Faithful
 * port of the merge/prune/mark logic in `ui/src/lib/notifications.ts`.
 */
@Singleton
class NotificationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        private const val TAG = "NotificationStore"
        private const val PREFS = "notifications"
        private const val KEY_STORE = "store_v1"
        // Drop dismissed messages from the local cache after this long.
        private const val DISMISSED_TTL_MS = 90L * 24 * 60 * 60 * 1000
    }

    @Serializable
    private data class Persisted(
        val cursor: Long = 0,
        val messages: List<CachedNotification> = emptyList(),
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** This app's version name, for targeting eligibility (decision E). */
    val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    private var persisted: Persisted = load()

    private val _notifications = MutableStateFlow(persisted.messages)
    /** The full local cache. Use [active]/[dismissed]/[unreadCount] to derive views. */
    val notifications: StateFlow<List<CachedNotification>> = _notifications.asStateFlow()

    // Fires when a delta (not a cold start) brings new eligible unread messages,
    // so the UI can show a transient toast/snackbar. Replays the latest to a
    // late collector that connects right after a background pull.
    private val _newArrivals = MutableSharedFlow<NewArrival>(replay = 1, extraBufferCapacity = 4)
    val newArrivals: SharedFlow<NewArrival> = _newArrivals.asSharedFlow()

    val cursor: Long get() = persisted.cursor

    private fun load(): Persisted {
        val raw = prefs.getString(KEY_STORE, null) ?: return Persisted()
        return try {
            json.decodeFromString(Persisted.serializer(), raw)
        } catch (e: Exception) {
            Log.w(TAG, "failed to load store, resetting", e)
            Persisted()
        }
    }

    private fun commit(next: Persisted) {
        persisted = next
        prefs.edit().putString(KEY_STORE, json.encodeToString(Persisted.serializer(), next)).apply()
        _notifications.value = next.messages
    }

    /**
     * Merge a fetch result into the store and prune. v2 (decision G): new
     * arrivals — including the cold-start batch — start **unread**; targeting +
     * expiry keep the backlog relevant and "Mark all read" handles volume.
     * A non-cold delta that adds eligible unread messages emits [newArrivals]
     * so the UI can toast.
     *
     * Returns the genuinely-new, eligible messages added by this merge (for any
     * reason, including cold start) so the caller can emit per-message
     * `notification_received` analytics. Empty when nothing new arrived.
     */
    fun merge(result: NotificationFetchResult): List<CachedNotification> {
        val coldStart = persisted.cursor == 0L
        val now = System.currentTimeMillis()
        val knownIds = persisted.messages.mapTo(HashSet()) { it.id }
        val byId = persisted.messages.associateBy { it.id }.toMutableMap()

        for (m in result.messages) {
            val existing = byId[m.id]
            byId[m.id] = CachedNotification(
                id = m.id,
                title = m.title,
                body = m.body,
                level = m.level,
                category = m.category,
                minVersion = m.minVersion,
                maxVersion = m.maxVersion,
                platforms = m.platforms,
                createdAt = m.createdAt,
                expiresAt = m.expiresAt,
                // Preserve local state across re-fetches; new arrivals start unread.
                seenAt = existing?.seenAt,
                dismissedAt = existing?.dismissedAt,
            )
        }

        // Prune: expired messages and long-dismissed ones.
        val pruned = byId.values.filterNot { m ->
            val expired = m.expiresAt != null && m.expiresAt * 1000 < now
            val staleDismissed = m.dismissedAt != null && now - m.dismissedAt > DISMISSED_TTL_MS
            expired || staleDismissed
        }

        commit(Persisted(cursor = maxOf(persisted.cursor, result.cursor), messages = pruned))

        // Genuinely new, eligible messages added by this merge — the basis for
        // both the toast (delta only) and `notification_received` analytics (any).
        val fresh = result.messages
            .filter { it.id !in knownIds }
            .mapNotNull { byId[it.id] }
            .filter { it.isEligible(appVersion) && it.dismissedAt == null }
            .sortedByDescending { it.createdAt }

        // Toast signal: only on a delta (never the cold-start backlog).
        if (!coldStart && fresh.isNotEmpty()) {
            _newArrivals.tryEmit(
                NewArrival(title = fresh.first().title, count = fresh.size, key = fresh.first().id),
            )
        }

        return fresh
    }

    /** Mark a single message read ("tap to read" — opening its detail). */
    fun markRead(id: Long) {
        val now = System.currentTimeMillis()
        if (persisted.messages.none { it.id == id && it.seenAt == null }) return
        val updated = persisted.messages.map {
            if (it.id == id && it.seenAt == null) it.copy(seenAt = now) else it
        }
        commit(persisted.copy(messages = updated))
    }

    /** Clear the unread badge: stamp every unseen message as seen. */
    fun markAllSeen() {
        val now = System.currentTimeMillis()
        if (persisted.messages.none { it.seenAt == null }) return
        val updated = persisted.messages.map {
            if (it.seenAt == null) it.copy(seenAt = now) else it
        }
        commit(persisted.copy(messages = updated))
    }

    /** Archive (remove from the active queue; stays in the archive). */
    fun dismiss(id: Long) {
        val now = System.currentTimeMillis()
        if (persisted.messages.none { it.id == id && it.dismissedAt == null }) return
        val updated = persisted.messages.map {
            if (it.id == id && it.dismissedAt == null) it.copy(dismissedAt = now) else it
        }
        commit(persisted.copy(messages = updated))
    }

    /** Archive every active eligible message at once. */
    fun archiveAll() {
        val now = System.currentTimeMillis()
        val updated = persisted.messages.map {
            if (it.isEligible(appVersion) && it.dismissedAt == null) it.copy(dismissedAt = now) else it
        }
        if (updated == persisted.messages) return
        commit(persisted.copy(messages = updated))
    }
}

/**
 * A toast-worthy batch of new arrivals from a delta pull. [key] is the newest
 * message id — a stable token so the UI can dedupe and not re-toast the same
 * arrival on recomposition/rotation (the flow replays its last value).
 */
data class NewArrival(val title: String, val count: Int, val key: Long)
