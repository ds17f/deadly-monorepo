package com.deadly.v2.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadly.v2.feature.splash.model.Phase
import com.deadly.v2.feature.splash.service.SplashService
import com.deadly.v2.feature.splash.service.SplashUiState
import com.deadly.v2.feature.splash.service.InitResult
import com.deadly.v2.core.database.service.DatabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for SplashV2 screen
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val splashService: SplashService
) : ViewModel() {
    
    val uiState: StateFlow<SplashUiState> = splashService.uiState
    
    init {
        initializeV2Database()
    }
    
    private fun initializeV2Database() {
        viewModelScope.launch {
            try {
                // Always show progress first and let the service handle restoration/import
                // The service will check for database ZIP, existing data, etc.
                
                // Show progress and start initialization
                splashService.updateUiState(
                    showProgress = true,
                    message = "Initializing V2 database..."
                )
                
                // Collect progress updates
                launch {
                    splashService.getV2Progress().collect { progress ->
                        val message = when (progress.phase) {
                            Phase.IDLE -> "Preparing V2 database..."
                            Phase.CHECKING -> "Checking existing data..."
                            Phase.USING_LOCAL -> "Using local files..."
                            Phase.DOWNLOADING -> "Downloading files..."
                            Phase.EXTRACTING -> "Extracting data files..."
                            Phase.IMPORTING_SHOWS -> "Importing shows (${progress.processedShows}/${progress.totalShows})"
                            Phase.COMPUTING_VENUES -> "Computing venue statistics..."
                            Phase.IMPORTING_RECORDINGS -> "Importing recordings (${progress.processedRecordings}/${progress.totalRecordings})"
                            Phase.COMPLETED -> "V2 database ready!"
                            Phase.ERROR -> "V2 database error"
                        }
                        
                        splashService.updateUiState(
                            showProgress = progress.phase != Phase.COMPLETED && progress.phase != Phase.ERROR,
                            showError = progress.phase == Phase.ERROR,
                            message = message,
                            errorMessage = progress.error,
                            progress = progress
                        )
                        
                        if (progress.phase == Phase.COMPLETED) {
                            splashService.updateUiState(isReady = true)
                        }
                    }
                }
                
                // Start the initialization
                val result = splashService.initializeV2Database()
                
                when (result) {
                    is InitResult.Success -> {
                        // Handle immediate success (e.g., database already initialized)
                        splashService.updateUiState(
                            isReady = true,
                            showProgress = false,
                            message = "V2 database ready: ${result.showsImported} shows loaded"
                        )
                    }
                    is InitResult.Error -> {
                        splashService.updateUiState(
                            showError = true,
                            showProgress = false,
                            message = "V2 database initialization failed",
                            errorMessage = result.error
                        )
                    }
                }
                
            } catch (e: Exception) {
                splashService.updateUiState(
                    showError = true,
                    showProgress = false,
                    message = "V2 database initialization failed",
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun retryInitialization() {
        splashService.retryInitialization(viewModelScope)
    }
    
    fun skipInitialization() {
        splashService.skipInitialization()
    }
    
    fun abortInitialization() {
        splashService.abortInitialization()
    }
    
    fun selectDatabaseSource(source: DatabaseManager.DatabaseSource) {
        splashService.selectDatabaseSource(source, viewModelScope)
    }
}