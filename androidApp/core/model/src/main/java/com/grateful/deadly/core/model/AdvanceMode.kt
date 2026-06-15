package com.grateful.deadly.core.model

/**
 * What auto-advance does when the current show ends (ADR-0010 Amendment).
 *
 * Three explicit modes (the "Autoplay" ∞ control cycles through them):
 * - [NONE]: stop at the end of the show.
 * - [SHOW_QUEUE]: play the head of the Show Queue, then stop when it drains
 *   (curation ran out — don't spill into uncurated territory).
 * - [CHRONOLOGICAL]: play the next show by date, ignoring the queue.
 */
enum class AdvanceMode(val displayName: String) {
    NONE("Off"),
    SHOW_QUEUE("Show Queue"),
    CHRONOLOGICAL("Chronological");

    /** The ∞ control cycles None → Show Queue → Chronological → None. */
    fun next(): AdvanceMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): AdvanceMode =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
