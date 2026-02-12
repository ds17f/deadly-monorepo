package com.deadly.v2.core.theme.di

import android.content.Context
import com.deadly.v2.core.theme.BuiltinThemeProvider
import com.deadly.v2.core.theme.ThemeManager
import com.deadly.v2.core.theme.ZipThemeProvider
import com.deadly.v2.core.theme.api.ThemeAssetProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {
    
    @Provides
    @Singleton
    fun provideBuiltinThemeProvider(): BuiltinThemeProvider {
        return BuiltinThemeProvider()
    }
    
    // Note: ZipThemeProvider.Factory is provided automatically by Hilt 
    // since ZipThemeProvider uses @AssistedInject. No manual binding needed.
    
    @Provides
    @Singleton
    fun provideThemeManager(
        builtinProvider: BuiltinThemeProvider,
        zipProviderFactory: ZipThemeProvider.Factory,
        @ApplicationContext context: Context
    ): ThemeManager {
        return ThemeManager(builtinProvider, zipProviderFactory, context)
    }
    
    /**
     * Provide the current theme asset provider through DI.
     * This allows design components to access themed assets without circular dependencies.
     */
    @Provides
    @Singleton
    fun provideThemeAssetProvider(themeManager: ThemeManager): ThemeAssetProvider {
        return themeManager.getCurrentProvider()
    }
}