package com.deadly.v2.core.model

import javax.inject.Qualifier

/**
 * Hilt qualifier to distinguish V2 database DAOs from V1 database DAOs.
 * 
 * This prevents injection conflicts when both V1 and V2 database modules
 * are present in the same application, ensuring V2 services get V2 DAOs
 * that point to the correct V2 database instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class V2Database