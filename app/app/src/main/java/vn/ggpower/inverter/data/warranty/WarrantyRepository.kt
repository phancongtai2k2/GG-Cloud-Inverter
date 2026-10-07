package vn.ggpower.inverter.data.warranty

import kotlinx.coroutines.delay
import vn.ggpower.inverter.domain.warranty.DeviceWarranty
import vn.ggpower.inverter.domain.warranty.ProjectWarranty
import vn.ggpower.inverter.domain.warranty.WarrantyChannel
import vn.ggpower.inverter.domain.warranty.WarrantyEntryStatus
import vn.ggpower.inverter.domain.warranty.WarrantyHistoryEntry
import vn.ggpower.inverter.domain.warranty.WarrantyWork
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Lỗi khi tải thông tin bảo hành. Chưa có hợp đồng API nên chỉ phân loại. */
class WarrantyLoadException(val kind: Kind) : Exception() {
    enum class Kind { NoConnection, Server }
}

interface WarrantyRepository {
    /** Bảo hành các thiết bị và lịch sử bảo hành của dự án (màn 9). Ném [WarrantyLoadException] khi lỗi. Chưa có hợp đồng API (OPEN_QUESTIONS 03/10/2026). */
    suspend fun getWarranty(projectId: String): ProjectWarranty
}

/** Dữ liệu mẫu màn 9 (khối `<script>` và phần lịch sử của html/Warranty.dc.html, bảng "Dữ liệu mẫu" đặc tả §3.9). */
@Singleton
class FakeWarrantyRepository @Inject constructor() : WarrantyRepository {

    var failure: WarrantyLoadException.Kind? = null

    override suspend fun getWarranty(projectId: String): ProjectWarranty {
        delay(FAKE_DELAY_MS)
        failure?.let { throw WarrantyLoadException(it) }
        return SAMPLE
    }

    companion object {
        private const val FAKE_DELAY_MS = 400L

        /** Ngày prototype dựng ảnh: "Còn 46 ngày" tới 09/11/2026. Preview và khoá debug dùng ngày này để so với PNG. */
        val DESIGN_TODAY: LocalDate = LocalDate.of(2026, 9, 24)

        val SAMPLE = ProjectWarranty(
            projectName = "Nhà của bạn",
            devices = listOf(
                DeviceWarranty("Inverter hybrid 5 kW", "GGI5K-25031501", LocalDate.of(2025, 3, 15), LocalDate.of(2030, 3, 15)),
                DeviceWarranty("Pin S16 Pre", "S16P-25031588", LocalDate.of(2025, 3, 15), LocalDate.of(2035, 3, 15)),
                DeviceWarranty("Đồng hồ đo thông minh", "GGM-24110902", LocalDate.of(2024, 11, 9), LocalDate.of(2026, 11, 9)),
            ),
            history = listOf(
                WarrantyHistoryEntry(
                    title = "Thay cảm biến nhiệt độ pin",
                    device = "Pin S16 Pre",
                    work = WarrantyWork.Repair("#BH-2606-0012"),
                    date = LocalDate.of(2026, 6, 12),
                    channel = WarrantyChannel.OnSite,
                    status = WarrantyEntryStatus.Completed,
                ),
                WarrantyHistoryEntry(
                    title = "Cập nhật firmware theo bảo hành",
                    device = "Inverter hybrid 5 kW",
                    work = WarrantyWork.Firmware("v1.3.1", "v1.3.2"),
                    date = LocalDate.of(2026, 4, 3),
                    channel = WarrantyChannel.Remote,
                    status = WarrantyEntryStatus.Completed,
                ),
            ),
        )
    }
}
