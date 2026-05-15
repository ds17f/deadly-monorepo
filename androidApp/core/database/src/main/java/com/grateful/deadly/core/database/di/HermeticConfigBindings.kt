package com.grateful.deadly.core.database.di

import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.network.hermetic.HermeticConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [AppPreferences] (in `core:database`) as the implementation of
 * [HermeticConfigProvider] (declared in `core:network`).
 *
 * The interface lives in `core:network` so [com.grateful.deadly.core.network.hermetic.HermeticInterceptor]
 * can depend on it without `core:network` taking on a dep on `core:database`
 * (which would create a cycle — `core:database` already depends on
 * `core:network`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HermeticConfigBindings {

    @Binds
    @Singleton
    abstract fun bindHermeticConfigProvider(impl: AppPreferences): HermeticConfigProvider
}
