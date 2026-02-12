package com.grateful.deadly.feature.collections.screens.details

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
    ): com.grateful.deadly.core.design.scaffold.BarConfiguration {
        return com.grateful.deadly.core.design.scaffold.BarConfiguration(
            topBar = null, // Use default AppScaffold top bar
            bottomBar = com.grateful.deadly.core.design.scaffold.BottomBarConfig(visible = true),
            miniPlayer = com.grateful.deadly.core.design.scaffold.MiniPlayerConfig(visible = true)
        )
    }
}