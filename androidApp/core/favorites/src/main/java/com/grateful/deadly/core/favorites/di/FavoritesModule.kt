package com.grateful.deadly.core.favorites.di

import com.grateful.deadly.core.api.favorites.FavoritesService
import com.grateful.deadly.core.api.favorites.ReviewService
import com.grateful.deadly.core.favorites.service.FavoritesServiceImpl
import com.grateful.deadly.core.favorites.service.ReviewServiceImpl
import com.grateful.deadly.core.favorites.repository.FavoritesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

/**
 * FavoritesModule - Hilt dependency injection for Favorites services
 *
 * Following architecture patterns with service-oriented design.
 * Uses SingletonComponent for application-scoped services with the
 * real FavoritesServiceImpl implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FavoritesModule {

    @Binds
    @Singleton
    abstract fun bindFavoritesService(
        impl: FavoritesServiceImpl
    ): FavoritesService

    @Binds
    @Singleton
    abstract fun bindReviewService(
        impl: ReviewServiceImpl
    ): ReviewService

    companion object {

        /**
         * Provide application-scoped CoroutineScope for FavoritesService operations
         */
        @Provides
        @Singleton
        @Named("FavoritesApplicationScope")
        fun provideFavoritesApplicationScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
    }
}
