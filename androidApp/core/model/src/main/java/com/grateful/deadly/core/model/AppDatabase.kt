package com.grateful.deadly.core.model

import javax.inject.Qualifier

/**
 * Hilt qualifier to distinguish database DAOs for dependency injection.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppDatabase
