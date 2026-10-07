package vn.ggpower.inverter.data.telemetry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import vn.ggpower.inverter.domain.project.ProjectStatus
import vn.ggpower.inverter.domain.telemetry.BatteryTelemetry
import vn.ggpower.inverter.domain.telemetry.BmsFlag
import vn.ggpower.inverter.domain.telemetry.BmsFlagKind
import vn.ggpower.inverter.domain.telemetry.DeviceKind
import vn.ggpower.inverter.domain.telemetry.HistoryField
import vn.ggpower.inverter.domain.telemetry.HistoryRange
import vn.ggpower.inverter.domain.telemetry.HistorySeries
import vn.ggpower.inverter.domain.telemetry.InverterTelemetry
import java.time.LocalDateTime
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Dữ liệu mẫu màn 17 (html/DataInverter.dc.html). Phát lại mỗi 5 s với thời điểm cập nhật mới. */
@Singleton
class FakeTelemetryRepository @Inject constructor(private val clock: Clock) : TelemetryRepository {

    var failure: TelemetryLoadException.Kind? = null

    override fun inverter(projectId: String): Flow<InverterTelemetry> = flow {
        delay(FAKE_DELAY_MS)
        while (true) {
            failure?.let { throw TelemetryLoadException(it) }
            emit(INVERTER.copy(updatedAt = clock.instant().minusSeconds(3)))
            delay(PERIOD_MS)
        }
    }

    override fun battery(projectId: String): Flow<BatteryTelemetry> = flow {
        delay(FAKE_DELAY_MS)
        while (true) {
            failure?.let { throw TelemetryLoadException(it) }
            emit(BATTERY.copy(updatedAt = clock.instant().minusSeconds(3)))
            delay(PERIOD_MS)
        }
    }

    override suspend fun history(
        projectId: String,
        device: DeviceKind,
        range: HistoryRange,
        fields: List<HistoryField>,
        now: LocalDateTime,
    ): HistorySeries {
        delay(FAKE_DELAY_MS)
        failure?.let { throw TelemetryLoadException(it) }
        return FakeHistory.series(range, fields, now)
    }

    companion object {
        private const val FAKE_DELAY_MS = 400L
        const val PERIOD_MS = 5_000L

        /** Cờ mẫu: chỉ "Nhiệt độ sạc cao" (cảnh báo) đang bật, như DataBattery.dc.html; các bit khác chưa có tên. */
        private val FLAGS = BmsFlagKind.entries.flatMap { kind ->
            List(32) { bit ->
                val sample = kind == BmsFlagKind.Alarm && bit == 3
                BmsFlag(kind, bit, if (sample) "Nhiệt độ sạc cao" else null, active = sample)
            }
        }

        /** Dữ liệu mẫu màn 17 pin (html/DataBattery.dc.html). */
        val BATTERY = BatteryTelemetry(
            name = "Pin S16 Pre",
            serial = "S16P-25031588",
            status = ProjectStatus.Online,
            updatedAt = java.time.Instant.parse("2026-09-24T02:41:00Z"),
            socPercent = 76.0, sohPercent = 98.0, cycles = 312,
            packVoltageInverterV = 51.2, packVoltageBmsV = 51.0, packCurrentA = 21.5,
            tempBmsC = 29.0, tempInverterC = 30.0,
            chargeLimitVoltageV = 56.8, chargeLimitCurrentA = 100.0, dischargeLimitCurrentA = 100.0, chargeLimitMode = 1,
            cellMaxMv = 3352.0, cellMaxIndex = 7, cellMinMv = 3340.0, cellMinIndex = 12, cellTempMaxC = 31.0, cellTempMinC = 28.0,
            packsOnline = 1, packsChargeBlocked = 0, packsDischargeBlocked = 0, packsOffline = 0,
            systemFaults = emptyList(), flags = FLAGS,
            manufacturer = "[TÊN HÃNG]", softwareVersion = "2.14", hardwareVersion = "1.3", protocolVersion = "2.11",
        )

        val INVERTER = InverterTelemetry(
            name = "Inverter hybrid 5 kW",
            serial = "GGI5K-25031501",
            status = ProjectStatus.Online,
            updatedAt = java.time.Instant.parse("2026-09-24T02:41:00Z"),
            machineState = "Hoà lưới · đang sạc pin",
            pv1VoltageV = 386.4, pv1CurrentA = 6.2, pv1PowerW = 2395.0,
            pv2VoltageV = 372.0, pv2CurrentA = 4.9, pv2PowerW = 1805.0,
            gridVoltageV = 229.6, gridCurrentA = 0.0, gridFrequencyHz = 50.01,
            gridPowerW = 0.0,
            outputVoltageV = 230.1, outputCurrentA = 13.5,
            upsLoadW = 3100.0, loadApparentVa = 3260.0, loadRatioPercent = 62.0, homeLoadW = 0.0,
            gridChargeCurrentA = 0.0, pvChargeCurrentA = 21.5,
            tempDcDcC = 41.0, tempDcAcC = 44.0, tempTransformerC = 39.0, tempAmbientC = 32.0,
            activeErrors = emptyList(),
        )
    }
}
