package vn.ggpower.inverter.data.alert

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import vn.ggpower.inverter.domain.alert.AlertDetail
import vn.ggpower.inverter.domain.alert.AlertSeverity
import vn.ggpower.inverter.domain.alert.AlertSummary
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Dữ liệu mẫu màn 10 (html/Alerts.dc.html). Giữ trạng thái trong bộ nhớ để màn 10a đánh dấu đã xử lý được. */
@Singleton
class FakeAlertRepository @Inject constructor() : AlertRepository {

    private val _alerts = MutableStateFlow(SAMPLE)
    var failure: AlertLoadException.Kind? = null

    /** Luồng không trễ, cho Màn hình chính đếm cảnh báo chưa xử lý. */
    val current: Flow<List<AlertSummary>> get() = _alerts

    override fun alerts(projectId: String): Flow<List<AlertSummary>> = flow {
        delay(FAKE_DELAY_MS)
        failure?.let { throw AlertLoadException(it) }
        emitAll(_alerts)
    }

    override fun detail(projectId: String, alertId: String): Flow<AlertDetail> = flow {
        delay(FAKE_DELAY_MS)
        failure?.let { throw AlertLoadException(it) }
        val base = FakeAlertDetails.ALL[alertId] ?: throw AlertLoadException(AlertLoadException.Kind.Server)
        emitAll(_alerts.map { list -> base.copy(resolved = list.firstOrNull { it.id == alertId }?.resolved ?: base.resolved) }.distinctUntilChanged())
    }

    override suspend fun setResolved(projectId: String, alertId: String, resolved: Boolean) {
        delay(FAKE_DELAY_MS / 2)
        failure?.let { throw AlertLoadException(it) }
        setResolved(alertId, resolved)
    }

    fun setResolved(alertId: String, resolved: Boolean) {
        _alerts.value = _alerts.value.map { if (it.id == alertId) it.copy(resolved = resolved) else it }
    }

    companion object {
        private const val FAKE_DELAY_MS = 400L

        /** Chi tiết mẫu cho Preview và màn trạng thái debug. */
        fun detailSample(id: String): AlertDetail = FakeAlertDetails.ALL.getValue(id)

        val SAMPLE = listOf(
            AlertSummary("grid", AlertSeverity.Error, "Mất kết nối lưới điện", "Inverter chuyển sang cấp điện từ pin.", LocalDateTime.of(2026, 9, 24, 7, 12), resolved = false),
            AlertSummary("temp", AlertSeverity.Warning, "Nhiệt độ pin cao (45 °C)", "Công suất sạc được giảm tạm thời.", LocalDateTime.of(2026, 9, 23, 14, 30), resolved = false),
            AlertSummary("fw", AlertSeverity.Info, "Có firmware mới v1.3.3", "Cập nhật sẽ chạy tự động lúc 02:00.", LocalDateTime.of(2026, 9, 22, 9, 5), resolved = true),
            AlertSummary("pv", AlertSeverity.Warning, "Điện áp PV thấp", "Chuỗi PV1 dưới ngưỡng trong 25 phút.", LocalDateTime.of(2026, 9, 20, 16, 48), resolved = true),
        )
    }
}
