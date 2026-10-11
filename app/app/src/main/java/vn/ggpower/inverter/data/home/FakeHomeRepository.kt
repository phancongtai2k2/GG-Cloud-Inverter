package vn.ggpower.inverter.data.home

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.data.config.FakeConfigRepository
import vn.ggpower.inverter.domain.alert.unresolvedCount
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.home.BatteryContent
import vn.ggpower.inverter.domain.home.EnergySnapshot
import vn.ggpower.inverter.domain.home.PeriodSelection
import vn.ggpower.inverter.domain.home.StatsPeriod
import vn.ggpower.inverter.domain.project.ProjectStatus
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dữ liệu mẫu Màn hình chính (Main.png, html/Main.dc.html; đặc tả §3.4). Đường trong ngày lấy từ mảng toạ độ của
 * prototype (đổi ra kW / A / %), là ngày mẫu 24/09/2026 tới 09:41; trả về như nhau cho mọi ngày.
 */
@Singleton
class FakeHomeRepository @Inject constructor(
    private val configRepository: FakeConfigRepository,
    private val alertRepository: FakeAlertRepository,
) : HomeRepository {

    /** Chế độ và SOC dự phòng lấy từ cấu hình (màn 16), số cảnh báo chưa xử lý từ màn 10, để các màn luôn khớp. */
    override fun overview(projectId: String): Flow<HomeOverview> = flow {
        delay(FAKE_DELAY_MS)
        emitAll(configRepository.configFlow.combine(alertRepository.current) { c, a -> c to a })
    }.map { (config, alerts) ->
        val project = FakeProjectRepository.SAMPLE.firstOrNull { it.id == projectId }
        SAMPLE.copy(
            projectName = project?.name ?: SAMPLE.projectName,
            status = project?.status ?: SAMPLE.status,
            mode = config.mode,
            backupSocPercent = config.backupSocPercent,
            unresolvedAlerts = alerts.unresolvedCount(),
        )
    }

    override suspend fun dayCurves(projectId: String, date: LocalDate): DayCurves {
        delay(FAKE_DELAY_MS)
        return SAMPLE_DAY
    }

    override suspend fun energyBuckets(projectId: String, selection: PeriodSelection): List<EnergyBucket> {
        delay(FAKE_DELAY_MS)
        val sample = if (selection.period == StatsPeriod.Year) ENERGY_YEAR else ENERGY_MONTH
        return List(selection.bucketCount) { sample[it % sample.size] }
    }

    override suspend fun periodSummary(projectId: String, selection: PeriodSelection): PeriodSummary {
        delay(FAKE_DELAY_MS)
        return when (selection.period) {
            StatsPeriod.Day -> SUMMARY_DAY
            StatsPeriod.Month -> SUMMARY_MONTH
            StatsPeriod.Year -> SUMMARY_YEAR
        }
    }

    override suspend fun yieldBuckets(projectId: String, selection: PeriodSelection): List<YieldBucket> {
        delay(FAKE_DELAY_MS)
        val sample = when (selection.period) {
            StatsPeriod.Day -> YIELD_DAY
            StatsPeriod.Month -> YIELD_MONTH
            StatsPeriod.Year -> YIELD_YEAR
        }
        // Tháng có 28–31 ngày: lặp lại dữ liệu mẫu 30 ngày cho đủ số cột.
        return List(selection.bucketCount) { sample[it % sample.size] }
    }

    companion object {
        private const val FAKE_DELAY_MS = 400L

        val SAMPLE = HomeOverview(
            projectName = "Nhà của bạn",
            status = ProjectStatus.Online,
            unresolvedAlerts = 2,
            // 18,6 kWh hôm nay, cao hơn 18 % so với hôm qua.
            yieldTodayKwh = 18.6,
            yieldYesterdayKwh = 15.76,
            energy = EnergySnapshot(pvKw = 4.2, gridKw = 0.5, batteryKw = 1.6, loadKw = 3.1, batterySocPercent = 76),
            mode = OperatingMode.SelfConsumption,
            backupSocPercent = 20,
            battery = BatteryContent(capacityKwh = 16.0, fromPvKwh = 10.2, fromGridKwh = 1.9),
            co2TodayKg = 10.4,
        )

        // Bảng chỉ số tổng hợp đặc tả 4.8.
        val SUMMARY_DAY = PeriodSummary(consumptionKwh = 14.2, selfUsePercent = 82, savingVnd = 48_000, co2Kg = 10.4)
        val SUMMARY_MONTH = PeriodSummary(consumptionKwh = 435.0, selfUsePercent = 82, savingVnd = 1_273_480, co2Kg = 274.0)
        val SUMMARY_YEAR = PeriodSummary(consumptionKwh = 5500.0, selfUsePercent = 82, savingVnd = 15_043_600, co2Kg = 3240.0)

        // 40 điểm: 00:00, 00:15 … 09:30, rồi 09:41 (thời điểm hiện tại của prototype).
        private val MINUTES = List(39) { it * 15 } + (9 * 60 + 41)
        private val PV = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.09, 0.24, 0.41, 0.61, 0.85, 1.09, 1.35, 1.63, 1.92, 2.23, 2.54, 2.88, 3.21, 3.56, 3.93, 4.2)
        private val GRID = List(24) { 0.2 } + List(16) { 0.0 }
        private val BAT = List(22) { -0.6 } + listOf(-0.61, -0.66, -0.92, -1.07, -1.35, -1.6, -1.56, -1.13, -0.44, 0.25, 0.76, 1.05, 1.11, 1.01, 0.9, 0.89, 1.03, 1.1)
        private val LOAD = List(22) { 0.8 } + listOf(0.81, 0.86, 1.01, 1.31, 1.76, 2.21, 2.41, 2.22, 1.79, 1.38, 1.15, 1.19, 1.44, 1.85, 2.32, 2.68, 2.9, 3.1)
        private val AMPS = List(21) { -11.7 } + listOf(-11.8, -12.1, -13.0, -17.9, -21.0, -26.3, -31.1, -30.5, -22.1, -8.6, 5.0, 14.9, 20.4, 21.7, 19.9, 17.5, 17.3, 20.2, 21.5)
        private val SOC = listOf(99, 98, 98, 97, 96, 95, 94, 93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 84, 83, 82, 81, 80, 79, 78, 76, 75, 73, 71, 68, 66, 65, 65, 66, 67, 69, 70, 72, 73, 75, 76)

        val SAMPLE_DAY = DayCurves(
            power = MINUTES.indices.map { PowerSample(MINUTES[it], PV[it], GRID[it], BAT[it], LOAD[it]) },
            battery = MINUTES.indices.map { BatterySample(MINUTES[it], AMPS[it], SOC[it]) },
        )

        private fun buckets(self: List<Double>, chg: List<Double>, exp: List<Double>) =
            self.indices.map { YieldBucket(self[it], chg[it], exp[it]) }

        val YIELD_DAY = buckets(
            listOf(0.0, 0.0, 0.285, 1.819, 2.904, 3.314, 3.179, 2.481, 1.079, 0.192, 0.0, 0.0),
            listOf(0.0, 0.0, 0.015, 0.281, 0.631, 0.795, 0.739, 0.48, 0.121, 0.008, 0.0, 0.0),
            listOf(0.0, 0.0, 0.0, 0.0, 0.065, 0.092, 0.083, 0.039, 0.0, 0.0, 0.0, 0.0),
        )
        val YIELD_MONTH = buckets(
            listOf(15.738, 17.082, 17.17, 15.875, 13.071, 10.662, 5.755, 7.843, 14.736, 15.23, 13.832, 11.681, 10.662, 12.07, 15.184, 16.907, 17.388, 16.462, 9.868, 12.61, 12.764, 14.511, 15.738, 15.646, 13.756, 10.82, 9.226, 10.266, 13.3, 15.784),
            listOf(3.428, 4.249, 4.305, 3.507, 2.429, 1.738, 0.645, 1.057, 2.964, 3.142, 2.668, 2.019, 1.738, 2.13, 3.116, 4.136, 4.449, 3.858, 1.532, 2.29, 2.336, 2.889, 3.428, 3.375, 2.644, 1.78, 1.374, 1.634, 2.5, 3.454),
            listOf(0.334, 1.169, 1.224, 0.417, 0.0, 0.0, 0.0, 0.0, 0.0, 0.028, 0.0, 0.0, 0.0, 0.0, 0.0, 1.058, 1.364, 0.779, 0.0, 0.0, 0.0, 0.0, 0.334, 0.278, 0.0, 0.0, 0.0, 0.0, 0.0, 0.362),
        )
        /**
         * Năng lượng tháng / năm suy từ dữ liệu mẫu prototype (sản lượng tách 3 phần + tải): pin sạc = PV sạc pin;
         * pin xả = 90 % lượng sạc nhưng không vượt phần tải PV chưa đáp ứng; lưới ròng = tải − PV tự dùng − pin xả −
         * PV lên lưới. SOC trung bình dao động quanh 70 %.
         */
        private fun energy(yields: List<YieldBucket>, load: List<Double>) = yields.mapIndexed { i, y ->
            val discharge = minOf(y.chargeKwh * 0.9, (load[i] - y.selfKwh).coerceAtLeast(0.0))
            EnergyBucket(
                pvKwh = y.totalKwh,
                gridNetKwh = load[i] - y.selfKwh - discharge - y.exportKwh,
                batteryChargeKwh = y.chargeKwh,
                batteryDischargeKwh = discharge,
                loadKwh = load[i],
                averageSocPercent = (70 + 8 * kotlin.math.sin(i * 0.7)).toInt(),
            )
        }

        private val LOAD_MONTH = listOf(15.5, 15.8, 15.5, 14.6, 13.5, 14.6, 14.2, 12.4, 13.2, 14.2, 15.2, 15.7, 17.7, 17.1, 14.0, 13.0, 12.3, 12.2, 12.8, 15.7, 16.8, 15.5, 15.8, 15.4, 14.5, 13.5, 14.6, 14.2, 12.4, 13.2)
        private val LOAD_YEAR = listOf(410.0, 385.0, 430.0, 465.0, 520.0, 540.0, 548.0, 535.0, 436.0, 420.0, 400.0, 415.0)

        val YIELD_YEAR = buckets(
            listOf(338.629, 355.639, 425.372, 421.743, 419.261, 424.593, 414.901, 420.143, 425.372, 393.81, 363.326, 341.731),
            listOf(59.371, 64.361, 86.628, 95.923, 102.025, 88.666, 83.099, 84.857, 86.628, 76.19, 66.674, 60.269),
            listOf(0.0, 0.0, 0.0, 30.334, 49.715, 6.741, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        )

        val ENERGY_MONTH by lazy { energy(YIELD_MONTH, LOAD_MONTH) }
        val ENERGY_YEAR by lazy { energy(YIELD_YEAR, LOAD_YEAR) }
    }
}
