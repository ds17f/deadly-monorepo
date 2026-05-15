package com.grateful.deadly.core.network.hermetic

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last-line-of-defense check: when hermetic mode is on, every outbound
 * request through a `@BaseOkHttp` client *must* target the configured
 * hermetic host. If anything slips past [HermeticInterceptor] — a new
 * code path that uses a non-shared `OkHttpClient`, an idempotence-guard
 * edge case, or a base URL parse failure — this interceptor catches it
 * and throws loudly with the offending URL in the error message.
 *
 * Installed AFTER [HermeticInterceptor] in the chain so it sees the
 * already-rewritten URL.
 *
 * When hermetic mode is off (`effectiveHermeticBaseUrl == null`) the
 * interceptor is a no-op — normal production / dev traffic passes
 * through unmodified.
 *
 * iOS doesn't have a direct counterpart at this layer because
 * `HermeticURLProtocol` *is* the rewriting + check for URLSession
 * traffic, and AVPlayer/CFNetwork escapes are detected externally
 * (test-time, via absence of expected traffic in WireMock's journal).
 *
 * Tracked in DEAD-353.
 */
@Singleton
class HostCheckInterceptor @Inject constructor(
    private val config: HermeticConfigProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val configuredBase = config.effectiveHermeticBaseUrl?.toHttpUrlOrNull()
            ?: return chain.proceed(chain.request()) // hermetic mode off → no-op

        val request = chain.request()
        val url = request.url
        if (url.host == configuredBase.host && url.port == configuredBase.port) {
            return chain.proceed(request)
        }

        val msg = "Hermetic mode escape: outbound request ${request.method} ${url} did not " +
                "target ${configuredBase.host}:${configuredBase.port}. The request bypassed " +
                "HermeticInterceptor — check that its OkHttpClient was built from @BaseOkHttp."
        Log.e(TAG, msg)
        throw IOException(msg)
    }

    companion object {
        private const val TAG = "HostCheckInterceptor"
    }
}
