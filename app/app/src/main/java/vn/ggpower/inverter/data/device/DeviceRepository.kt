package vn.ggpower.inverter.data.device

import vn.ggpower.inverter.domain.device.DeviceUnit

/** Lỗi khi tải danh sách thiết bị. Chưa có hợp đồng API nên chỉ phân loại. */
class DeviceLoadException(val kind: Kind) : Exception() {
    enum class Kind { NoConnection, Server }
}

interface DeviceRepository {
    /** Thiết bị của dự án (màn 5). Ném [DeviceLoadException] khi lỗi. Chưa có hợp đồng API (OPEN_QUESTIONS 02/10/2026). */
    suspend fun getDevices(projectId: String): List<DeviceUnit>
}
