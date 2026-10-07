package vn.ggpower.inverter.data.device

import kotlinx.coroutines.delay
import vn.ggpower.inverter.domain.device.BatteryState
import vn.ggpower.inverter.domain.device.BatterySummary
import vn.ggpower.inverter.domain.device.DeviceUnit
import vn.ggpower.inverter.domain.device.InverterSummary
import vn.ggpower.inverter.domain.project.ProjectStatus
import javax.inject.Inject
import javax.inject.Singleton

/** Dữ liệu mẫu màn 5 (html/Device.dc.html; serial theo DataInverter.dc.html — quyết 02/10/2026). */
@Singleton
class FakeDeviceRepository @Inject constructor() : DeviceRepository {

    var failure: DeviceLoadException.Kind? = null

    override suspend fun getDevices(projectId: String): List<DeviceUnit> {
        delay(FAKE_DELAY_MS)
        failure?.let { throw DeviceLoadException(it) }
        return SAMPLE
    }

    companion object {
        private const val FAKE_DELAY_MS = 400L

        val INVERTER = InverterSummary(
            name = "Inverter hybrid 5 kW",
            serial = "GGI5K-25031501",
            status = ProjectStatus.Online,
            powerKw = 4.2,
            temperatureC = 42.0,
            firmware = "v1.3.2",
        )

        val BATTERY = BatterySummary(
            name = "Pin S16 Pre",
            state = BatteryState.Charging,
            hoursRemaining = 2.0,
            socPercent = 76,
            powerKw = 1.1,
            voltageV = 51.2,
            sohPercent = 98,
        )

        val SAMPLE = listOf(DeviceUnit(INVERTER, BATTERY))
    }
}
