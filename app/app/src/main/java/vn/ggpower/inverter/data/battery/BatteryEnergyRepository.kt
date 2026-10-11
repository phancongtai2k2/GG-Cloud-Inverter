package vn.ggpower.inverter.data.battery

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.domain.battery.BatteryEnergy
import vn.ggpower.inverter.domain.home.BatteryContent
import javax.inject.Inject
import javax.inject.Singleton

/** Màn 11. Chưa có hợp đồng API (OPEN_QUESTIONS 02/10: "nguồn năng lượng pin"). */
interface BatteryEnergyRepository {
    /** Luồng cập nhật định kỳ; lỗi tải ném [BatteryEnergyLoadException]. */
    fun batteryEnergy(projectId: String): Flow<BatteryEnergy>
}

class BatteryEnergyLoadException(val noConnection: Boolean) : Exception()

/** Dữ liệu mẫu BatteryEnergy.dc.html; thông số lấy từ mẫu pin màn 17 (cùng số liệu). */
@Singleton
class FakeBatteryEnergyRepository @Inject constructor() : BatteryEnergyRepository {
    var failure: Boolean? = null

    override fun batteryEnergy(projectId: String): Flow<BatteryEnergy> = flow {
        delay(FAKE_DELAY_MS)
        failure?.let { throw BatteryEnergyLoadException(noConnection = it) }
        while (true) {
            emit(SAMPLE)
            delay(FakeTelemetryRepository.PERIOD_MS)
        }
    }

    companion object {
        private const val FAKE_DELAY_MS = 400L

        val SAMPLE: BatteryEnergy = FakeTelemetryRepository.BATTERY.let { b ->
            BatteryEnergy(
                name = "S16 Pre",
                socPercent = b.socPercent,
                powerKw = 1.1,
                content = BatteryContent(capacityKwh = 16.0, fromPvKwh = 10.2, fromGridKwh = 1.9),
                voltageV = b.packVoltageInverterV,
                currentA = b.packCurrentA,
                temperatureC = b.tempBmsC,
                sohPercent = b.sohPercent,
                cycles = b.cycles,
                cellDeltaMv = if (b.cellMaxMv != null && b.cellMinMv != null) b.cellMaxMv - b.cellMinMv else null,
                chargeLimitSocPercent = 95,
                dischargeLimitSocPercent = 10,
            )
        }
    }
}
