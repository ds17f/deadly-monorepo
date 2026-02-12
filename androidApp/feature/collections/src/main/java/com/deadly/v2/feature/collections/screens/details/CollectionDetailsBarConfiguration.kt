package com.deadly.v2.feature.collections.screens.details

/**
 * Bar configuration for Collection Details screen
 * 
 * Provides simple bar configuration following V2 design patterns
 */
object CollectionDetailsBarConfiguration {
    
    /**
     * Get bar configuration for collection details screen
     * 
     * @param collectionName Name of the collection to display in title
     * @param onNavigateBack Callback for back navigation
     */
    fun getCollectionDetailsBarConfig(
        collectionName: String = "Collection",
        onNavigateBack: () -> Unit = {}
    ): com.deadly.v2.core.design.scaffold.BarConfiguration {
        return com.deadly.v2.core.design.scaffold.BarConfiguration(
            topBar = null, // Use default AppScaffold top bar
            bottomBar = com.deadly.v2.core.design.scaffold.BottomBarConfig(visible = true),
            miniPlayer = com.deadly.v2.core.design.scaffold.MiniPlayerConfig(visible = true)
        )
    }
}