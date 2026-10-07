package vn.ggpower.inverter.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.ggpower.inverter.data.account.FakeProfileRepository
import vn.ggpower.inverter.data.account.ProfileRepository
import vn.ggpower.inverter.data.alert.AlertRepository
import vn.ggpower.inverter.data.battery.BatteryEnergyRepository
import vn.ggpower.inverter.data.location.AndroidLocationProvider
import vn.ggpower.inverter.data.location.LocationProvider
import vn.ggpower.inverter.data.project.AddProjectRepository
import vn.ggpower.inverter.data.project.FakeAddProjectRepository
import vn.ggpower.inverter.data.battery.FakeBatteryEnergyRepository
import vn.ggpower.inverter.data.alert.FakeAlertRepository
import vn.ggpower.inverter.data.auth.AuthRepository
import vn.ggpower.inverter.data.auth.FakeAuthRepository
import vn.ggpower.inverter.data.config.ConfigRepository
import vn.ggpower.inverter.data.config.FakeConfigRepository
import vn.ggpower.inverter.data.device.DeviceRepository
import vn.ggpower.inverter.data.device.FakeDeviceRepository
import vn.ggpower.inverter.data.home.FakeHomeRepository
import vn.ggpower.inverter.data.telemetry.FakeTelemetryRepository
import vn.ggpower.inverter.data.telemetry.TelemetryRepository
import vn.ggpower.inverter.data.home.HomeRepository
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.data.project.ProjectRepository
import vn.ggpower.inverter.data.explore.ExploreRepository
import vn.ggpower.inverter.data.explore.FakeExploreRepository
import vn.ggpower.inverter.data.support.FakeSupportContactRepository
import vn.ggpower.inverter.data.support.SupportContactRepository
import vn.ggpower.inverter.data.support.FakeSupportTicketRepository
import vn.ggpower.inverter.data.support.SupportTicketRepository
import vn.ggpower.inverter.data.warranty.FakeWarrantyRepository
import vn.ggpower.inverter.data.warranty.WarrantyRepository

/** Gắn repository. Giai đoạn này đều là bản Fake (AGENTS.md §7). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAuthRepository(impl: FakeAuthRepository): AuthRepository

    @Binds
    abstract fun bindProjectRepository(impl: FakeProjectRepository): ProjectRepository

    @Binds
    abstract fun bindHomeRepository(impl: FakeHomeRepository): HomeRepository

    @Binds
    abstract fun bindDeviceRepository(impl: FakeDeviceRepository): DeviceRepository

    @Binds
    abstract fun bindTelemetryRepository(impl: FakeTelemetryRepository): TelemetryRepository

    @Binds
    abstract fun bindAlertRepository(impl: FakeAlertRepository): AlertRepository

    @Binds
    abstract fun bindLocationProvider(impl: AndroidLocationProvider): LocationProvider

    @Binds
    abstract fun bindAddProjectRepository(impl: FakeAddProjectRepository): AddProjectRepository

    @Binds
    abstract fun bindBatteryEnergyRepository(impl: FakeBatteryEnergyRepository): BatteryEnergyRepository

    @Binds
    abstract fun bindConfigRepository(impl: FakeConfigRepository): ConfigRepository

    @Binds
    abstract fun bindProfileRepository(impl: FakeProfileRepository): ProfileRepository

    @Binds
    abstract fun bindSupportContactRepository(impl: FakeSupportContactRepository): SupportContactRepository

    @Binds
    abstract fun bindExploreRepository(impl: FakeExploreRepository): ExploreRepository

    @Binds
    abstract fun bindSupportTicketRepository(impl: FakeSupportTicketRepository): SupportTicketRepository

    @Binds
    abstract fun bindWarrantyRepository(impl: FakeWarrantyRepository): WarrantyRepository
}
