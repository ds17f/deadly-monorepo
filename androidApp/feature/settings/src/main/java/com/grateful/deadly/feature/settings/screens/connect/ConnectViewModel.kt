package com.grateful.deadly.feature.settings.screens.connect

import androidx.lifecycle.ViewModel
import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.model.ConnectDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connectService: ConnectService,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val devices: StateFlow<List<ConnectDevice>> = connectService.devices
    val isConnected: StateFlow<Boolean> = connectService.isConnected
    val installId: String = appPreferences.installId
}
