package com.grateful.deadly.core.network.hermetic

/**
 * Read-only view of the app's hermetic-mode configuration, consumed by
 * [HermeticInterceptor]. Implemented by `AppPreferences` in `core:database`;
 * defined here in `core:network` to avoid a `core:network` → `core:database`
 * dependency cycle (database already depends on network).
 */
interface HermeticConfigProvider {

    /**
     * The currently-active hermetic server base URL (e.g.
     * `http://10.0.2.2:8090`), or `null` when hermetic mode is disabled or
     * no URL has been configured. Read fresh on every outbound request.
     */
    val effectiveHermeticBaseUrl: String?
}
