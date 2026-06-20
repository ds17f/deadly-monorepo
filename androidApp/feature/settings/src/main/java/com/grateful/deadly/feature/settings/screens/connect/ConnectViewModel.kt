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
    val activeDeviceVolume: StateFlow<Int> = connectService.activeDeviceVolume
    val installId: String = appPreferences.installId

    // ADR-0018: the three Connect-sheet modes are derived from these two flags —
    // server off ⇒ unavailable; server on + opted out ⇒ beta promo; both on ⇒
    // full device picker.
    val serverConnectEnabled: StateFlow<Boolean> = connectService.serverConnectEnabled
    val connectEnabled: StateFlow<Boolean> = appPreferences.connectEnabled

    /** Opt this device in/out of Connect (the per-device beta toggle). */
    fun setConnectEnabled(enabled: Boolean) {
        connectService.setEnabled(enabled)
    }

    /** Re-check the global kill switch (called when the Connect sheet opens). */
    fun refreshServerConnectEnabled() {
        connectService.refreshServerConnectEnabled()
    }

    fun sendVolume(volume: Int) {
        connectService.sendVolume(volume)
    }

    fun handleHardwareVolumeKey(delta: Int): Boolean =
        connectService.handleHardwareVolumeKey(delta)

    fun transferTo(deviceId: String) {
        connectService.sendTransfer(deviceId)
    }

    fun sendStop() {
        connectService.sendStop()
    }
}
