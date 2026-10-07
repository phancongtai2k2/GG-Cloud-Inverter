package vn.ggpower.inverter.ui.devices

import vn.ggpower.inverter.domain.device.DeviceUnit

sealed interface DevicesUiState {
    data object Loading : DevicesUiState
    data class Error(val noConnection: Boolean) : DevicesUiState
    data class Content(val units: List<DeviceUnit>) : DevicesUiState
}
