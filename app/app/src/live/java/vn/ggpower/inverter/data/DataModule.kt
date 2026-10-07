package vn.ggpower.inverter.data

import dagger.Binds
import vn.ggpower.inverter.data.remote.LiveAddProjectRepository
import vn.ggpower.inverter.data.remote.LiveAlertRepository
import vn.ggpower.inverter.data.remote.LiveAuthRepository
import vn.ggpower.inverter.data.remote.LiveWarrantyRepository
import vn.ggpower.inverter.data.remote.LiveSupportTicketRepository
import vn.ggpower.inverter.data.remote.LiveExploreRepository
import vn.ggpower.inverter.data.remote.LiveSupportContactRepository
import vn.ggpower.inverter.data.remote.LiveProfileRepository
import vn.ggpower.inverter.data.remote.LiveConfigRepository
import vn.ggpower.inverter.data.remote.LiveBatteryEnergyRepository
import vn.ggpower.inverter.data.remote.LiveDeviceRepository
import vn.ggpower.inverter.data.remote.LiveHomeRepository
import vn.ggpower.inverter.data.remote.LiveProjectRepository
import vn.ggpower.inverter.data.remote.LiveTelemetryRepository
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.ggpower.inverter.data.account.ProfileRepository
import vn.ggpower.inverter.data.alert.AlertRepository
import vn.ggpower.inverter.data.battery.BatteryEnergyRepository
import vn.ggpower.inverter.data.location.AndroidLocationProvider
import vn.ggpower.inverter.data.location.LocationProvider
import vn.ggpower.inverter.data.project.AddProjectRepository
import vn.ggpower.inverter.data.auth.AuthRepository
import vn.ggpower.inverter.data.config.ConfigRepository
import vn.ggpower.inverter.data.device.DeviceRepository
import vn.ggpower.inverter.data.telemetry.TelemetryRepository
import vn.ggpower.inverter.data.home.HomeRepository
import vn.ggpower.inverter.data.project.ProjectRepository
import vn.ggpower.inverter.data.explore.ExploreRepository
import vn.ggpower.inverter.data.support.SupportContactRepository
import vn.ggpower.inverter.data.support.SupportTicketRepository
import vn.ggpower.inverter.data.warranty.WarrantyRepository

/** Gắn repository của bản `live`: mọi nguồn dữ liệu gọi backend (data/remote). Bản `mock` dùng các bản Fake. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAuthRepository(impl: LiveAuthRepository): AuthRepository

    @Binds
    abstract fun bindProjectRepository(impl: LiveProjectRepository): ProjectRepository

    @Binds
    abstract fun bindHomeRepository(impl: LiveHomeRepository): HomeRepository

    @Binds
    abstract fun bindDeviceRepository(impl: LiveDeviceRepository): DeviceRepository

    @Binds
    abstract fun bindTelemetryRepository(impl: LiveTelemetryRepository): TelemetryRepository

    @Binds
    abstract fun bindAlertRepository(impl: LiveAlertRepository): AlertRepository

    @Binds
    abstract fun bindLocationProvider(impl: AndroidLocationProvider): LocationProvider

    @Binds
    abstract fun bindAddProjectRepository(impl: LiveAddProjectRepository): AddProjectRepository

    @Binds
    abstract fun bindBatteryEnergyRepository(impl: LiveBatteryEnergyRepository): BatteryEnergyRepository

    @Binds
    abstract fun bindConfigRepository(impl: LiveConfigRepository): ConfigRepository

    @Binds
    abstract fun bindProfileRepository(impl: LiveProfileRepository): ProfileRepository

    @Binds
    abstract fun bindSupportContactRepository(impl: LiveSupportContactRepository): SupportContactRepository

    @Binds
    abstract fun bindExploreRepository(impl: LiveExploreRepository): ExploreRepository

    @Binds
    abstract fun bindSupportTicketRepository(impl: LiveSupportTicketRepository): SupportTicketRepository

    @Binds
    abstract fun bindWarrantyRepository(impl: LiveWarrantyRepository): WarrantyRepository
}
