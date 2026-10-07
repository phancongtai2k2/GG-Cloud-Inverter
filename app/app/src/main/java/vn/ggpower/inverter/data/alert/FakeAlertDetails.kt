package vn.ggpower.inverter.data.alert

import vn.ggpower.inverter.domain.alert.AlertChart
import vn.ggpower.inverter.domain.telemetry.FieldUnit
import vn.ggpower.inverter.domain.alert.AlertDetail
import vn.ggpower.inverter.domain.alert.AlertEndKind
import vn.ggpower.inverter.domain.alert.AlertSeverity
import java.time.LocalDateTime

/**
 * Chi tiết mẫu màn 10a (html/AlertDetail.dc.html `ALERTS`). Mã bit để "[MÃ BIT]" và nguyên nhân / hướng xử lý lấy
 * nguyên từ prototype cho tới khi có bảng ánh xạ SRNE và nội dung của Phòng EMS (OPEN_QUESTIONS).
 */
internal object FakeAlertDetails {
    private const val PROJECT = "Nhà của bạn"
    private const val INVERTER = "Inverter hybrid 5 kW"

    /** 13 điểm cách 2,5 phút, điểm 6 là thời điểm cảnh báo (prototype: t0 = −15 phút). */
    private fun chart(label: String, unit: FieldUnit, kind: AlertChart.ChartKind, at: LocalDateTime, values: List<Double>, note: String) =
        AlertChart(label, unit, kind, values, at.minusMinutes(15), 150, 6, note)

    private val gridAt = LocalDateTime.of(2026, 9, 24, 7, 12)
    private val tempAt = LocalDateTime.of(2026, 9, 23, 14, 30)
    private val pvAt = LocalDateTime.of(2026, 9, 20, 16, 48)

    val ALL: Map<String, AlertDetail> = listOf(
        AlertDetail(
            id = "grid", severity = AlertSeverity.Error, title = "Mất kết nối lưới điện",
            description = "Inverter chuyển sang cấp điện từ pin S16 Pre cho tải.",
            device = INVERTER, projectName = PROJECT, startedAt = gridAt, endedAt = null, endKind = null, resolved = false,
            code = "0x200 CurrErrReg · bit [MÃ BIT]", codeName = "Mất điện áp lưới AC đầu vào (Grid loss)",
            chart = chart(
                "Điện áp lưới", FieldUnit.Volt, AlertChart.ChartKind.Grid, gridAt,
                listOf(231.0, 230.0, 232.0, 229.0, 231.0, 230.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                "Điện áp lưới về 0 V lúc 07:12. Pin đang cấp 3,1 kW cho tải, SOC 76 % — đủ khoảng 6 giờ.",
            ),
            causes = listOf(
                "Điện lưới khu vực bị cắt hoặc sự cố đường dây.",
                "Aptomat phía lưới (AC đầu vào inverter) bị ngắt.",
                "Dây đấu lưới vào inverter bị lỏng.",
            ),
            steps = listOf(
                "Kiểm tra nhà bên cạnh có điện không. Nếu cả khu vực mất điện, chỉ cần chờ — hệ thống tự chuyển về lưới khi có điện lại.",
                "Nếu khu vực có điện: kiểm tra và bật lại aptomat AC đầu vào của inverter.",
                "Lưới đã có điện mà cảnh báo vẫn còn sau 15 phút: tạo yêu cầu hỗ trợ kỹ thuật.",
            ),
        ),
        AlertDetail(
            id = "temp", severity = AlertSeverity.Warning, title = "Nhiệt độ pin cao (45 °C)",
            description = "Công suất sạc được giảm tạm thời để bảo vệ pin.",
            device = "Pin S16 Pre", projectName = PROJECT, startedAt = tempAt, endedAt = null, endKind = null, resolved = false,
            code = "BMS · cảnh báo nhiệt độ cao", codeName = "Nhiệt độ cell cao nhất vượt ngưỡng 45 °C",
            chart = chart(
                "Nhiệt độ cell cao nhất", FieldUnit.Celsius, AlertChart.ChartKind.Battery, tempAt,
                listOf(38.0, 39.0, 40.0, 41.0, 42.0, 43.0, 45.0, 46.0, 46.0, 45.0, 45.0, 44.0, 44.0),
                "Nhiệt độ vượt 45 °C lúc 14:30, cao nhất 46 °C.",
            ),
            causes = listOf(
                "Nhiệt độ môi trường cao, pin đặt ở nơi nắng chiếu hoặc kín gió.",
                "Sạc công suất lớn liên tục trong giờ nóng.",
                "Quạt hoặc khe thông gió của tủ pin bị che.",
            ),
            steps = listOf(
                "Đảm bảo khu vực đặt pin thông thoáng, không có nắng chiếu trực tiếp.",
                "Có thể giảm dòng sạc tối đa trong Cấu hình dự án.",
                "Nếu nhiệt độ vượt 50 °C hoặc cảnh báo lặp lại nhiều ngày: tạo yêu cầu hỗ trợ kỹ thuật.",
            ),
        ),
        AlertDetail(
            id = "fw", severity = AlertSeverity.Info, title = "Có firmware mới v1.3.3",
            description = "Cập nhật được cài tự động lúc 02:00, không cần thao tác.",
            device = INVERTER, projectName = PROJECT, startedAt = LocalDateTime.of(2026, 9, 22, 9, 5),
            endedAt = LocalDateTime.of(2026, 9, 23, 2, 4), endKind = AlertEndKind.AutoUpdated, resolved = true,
            code = "FW v1.3.2 → v1.3.3", codeName = "Cải thiện độ ổn định khi chuyển lưới – pin, sửa lỗi hiển thị SOC.",
            chart = null,
            causes = listOf("Nhà sản xuất phát hành bản firmware mới."),
            steps = listOf("Không cần thao tác. Hệ thống tự cài vào giờ thấp điểm."),
        ),
        AlertDetail(
            id = "pv", severity = AlertSeverity.Warning, title = "Điện áp PV thấp",
            description = "Chuỗi PV1 dưới ngưỡng khởi động trong 25 phút.",
            device = "$INVERTER · PV1", projectName = PROJECT, startedAt = pvAt,
            endedAt = LocalDateTime.of(2026, 9, 20, 17, 13), endKind = AlertEndKind.AutoRecovered, resolved = true,
            code = "0x200 CurrErrReg · bit [MÃ BIT]", codeName = "Điện áp chuỗi PV1 thấp hơn ngưỡng khởi động",
            chart = chart(
                "Điện áp PV1", FieldUnit.Volt, AlertChart.ChartKind.Pv, pvAt,
                listOf(310.0, 296.0, 280.0, 262.0, 240.0, 205.0, 150.0, 142.0, 138.0, 150.0, 165.0, 180.0, 200.0),
                "Điện áp PV1 giảm theo chiều tối và mây dày.",
            ),
            causes = listOf(
                "Trời nhiều mây hoặc chiều tối, bức xạ thấp.",
                "Tấm pin bị che bóng hoặc bám bẩn.",
                "Đầu nối MC4 của chuỗi PV1 tiếp xúc kém.",
            ),
            steps = listOf(
                "Nếu xảy ra lúc chiều tối hoặc trời mưa: đây là hiện tượng bình thường.",
                "Kiểm tra tấm pin có bị che bóng, lá cây hoặc bụi bẩn.",
                "Nếu lặp lại vào giữa trưa nắng: tạo yêu cầu hỗ trợ kỹ thuật để kiểm tra chuỗi PV1.",
            ),
        ),
    ).associateBy { it.id }
}
