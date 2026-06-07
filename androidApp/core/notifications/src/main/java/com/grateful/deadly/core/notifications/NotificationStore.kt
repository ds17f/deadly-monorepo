package com.grateful.deadly.core.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private var persisted: Persisted = load()

    private val _notifications = MutableStateFlow(persisted.messages)
    /** The full local cache. Use [active]/[dismissed]/[unreadCount] to derive views. */
    val notifications: StateFlow<List<CachedNotification>> = _notifications.asStateFlow()

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
     * Merge a fetch result into the store and prune. On a cold start (no cursor
     * yet) the batch is marked seen-not-dismissed so a fresh user isn't slammed
     * with unread badges — the messages appear in the inbox but don't nag.
     * Subsequent deltas arrive unseen (they raise the badge).
     */
    fun merge(result: NotificationFetchResult) {
        val coldStart = persisted.cursor == 0L
        val now = System.currentTimeMillis()
        val byId = persisted.messages.associateBy { it.id }.toMutableMap()

        for (m in result.messages) {
            val existing = byId[m.id]
            byId[m.id] = CachedNotification(
                id = m.id,
                title = m.title,
                body = m.body,
                level = m.level,
                createdAt = m.createdAt,
                expiresAt = m.expiresAt,
                // Preserve local state across re-fetches; default for new arrivals.
                seenAt = existing?.seenAt ?: if (coldStart) now else null,
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

    /** Remove a message from the active queue (stays in the archive). */
    fun dismiss(id: Long) {
        val now = System.currentTimeMillis()
        if (persisted.messages.none { it.id == id && it.dismissedAt == null }) return
        val updated = persisted.messages.map {
            if (it.id == id && it.dismissedAt == null) it.copy(dismissedAt = now) else it
        }
        commit(persisted.copy(messages = updated))
    }
}
