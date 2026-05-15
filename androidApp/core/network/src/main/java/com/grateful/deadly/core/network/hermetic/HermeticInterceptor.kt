package com.grateful.deadly.core.network.hermetic

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that, when hermetic mode is enabled, rewrites every
 * outbound HTTP(S) request to target the hermetic server, pushing the
 * original host into the URL as the first path segment.
 *
 *   https://archive.org/metadata/foo  →  http://10.0.2.2:8090/archive.org/metadata/foo
 *   https://api.github.com/repos/...  →  http://10.0.2.2:8090/api.github.com/repos/...
 *
 * One WireMock instance can serve any number of upstreams; mappings live
 * under matching `/<host>/...` paths.
 *
 * Reads [AppPreferences.effectiveHermeticBaseUrl] fresh on every request,
 * so flipping the toggle in Developer Settings takes effect immediately
 * for subsequent calls — no DI scope reset required.
 *
 * Tracked in DEAD-351. See `hermetic/README.md` for the broader story.
 */
@Singleton
class HermeticInterceptor @Inject constructor(
    private val config: HermeticConfigProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val baseUrl = config.effectiveHermeticBaseUrl
            ?: return chain.proceed(chain.request())

        val hermeticBase = baseUrl.toHttpUrlOrNull()
        if (hermeticBase == null) {
            Log.w(TAG, "Invalid hermetic base URL '$baseUrl' — passing request through unmodified")
            return chain.proceed(chain.request())
        }

        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        // Idempotence: don't rewrite if the request is already targeting the hermetic host.
        if (originalUrl.host == hermeticBase.host && originalUrl.port == hermeticBase.port) {
            return chain.proceed(originalRequest)
        }

        val builder = hermeticBase.newBuilder()
            .addPathSegment(originalUrl.host)
        for (segment in originalUrl.pathSegments) {
            if (segment.isNotEmpty()) {
                builder.addPathSegment(segment)
            }
        }
        originalUrl.encodedQuery?.let { builder.encodedQuery(it) }
        originalUrl.encodedFragment?.let { builder.encodedFragment(it) }
        val rewrittenUrl = builder.build()

        Log.d(TAG, "$originalUrl → $rewrittenUrl")

        val rewrittenRequest = originalRequest.newBuilder()
            .url(rewrittenUrl)
            .build()
        return chain.proceed(rewrittenRequest)
    }

    companion object {
        private const val TAG = "HermeticInterceptor"
    }
}
