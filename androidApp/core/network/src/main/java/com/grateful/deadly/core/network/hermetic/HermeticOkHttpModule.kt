package com.grateful.deadly.core.network.hermetic

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the shared base [OkHttpClient] that has the
 * [HermeticInterceptor] applied. Per-upstream network modules inject
 * this and call `.newBuilder()` to add their own quirks (timeouts,
 * logging, User-Agent), while inheriting the hermetic rewrite logic
 * and (eventually) any other cross-cutting interceptors we add.
 *
 * Calling `.newBuilder()` on an OkHttpClient clones interceptors AND
 * shares the underlying connection pool + dispatcher — so this isn't
 * just code dedup, it also lets all upstreams share resources.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseOkHttp

@Module
@InstallIn(SingletonComponent::class)
object HermeticOkHttpModule {

    @Provides
    @Singleton
    @BaseOkHttp
    fun provideBaseOkHttpClient(
        hermeticInterceptor: HermeticInterceptor,
        hostCheckInterceptor: HostCheckInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            // Order matters: hermetic rewrites first, host-check verifies the
            // rewritten URL actually targets the hermetic host. Anything that
            // slips past the rewrite (a new client not built from @BaseOkHttp,
            // an idempotence edge case) is caught here and thrown loudly.
            .addInterceptor(hermeticInterceptor)
            .addInterceptor(hostCheckInterceptor)
            .build()
}
