package com.grateful.deadly.feature.settings.screens.connect

import androidx.lifecycle.ViewModel
import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.model.ConnectDevice
import com.grateful.deadly.core.model.ConnectState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connectService: ConnectService,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val devices: StateFlow<List<ConnectDevice>> = connectService.devices
    val connectState: StateFlow<ConnectState?> = connectService.connectState
    val isConnected: StateFlow<Boolean> = connectService.isConnected
    val isActiveDevice: StateFlow<Boolean> = connectService.isActiveDevice
    val pendingTransfer: StateFlow<String?> = connectService.pendingTransfer
    val installId: String = appPreferences.installId

    fun transferTo(deviceId: String) {
        connectService.sendTransfer(deviceId)
    }
}
