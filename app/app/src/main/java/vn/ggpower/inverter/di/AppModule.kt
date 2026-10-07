package vn.ggpower.inverter.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.ggpower.inverter.BuildConfig
import javax.inject.Named
import java.time.Clock

/** Phụ thuộc chung của app. [Clock] tách riêng để test được logic theo thời gian (cột "hiện tại", kỳ đang xem). */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /** `BuildConfig.CONFIG_READ_ONLY` (AGENTS.md §7), tách ra để test ánh xạ control. */
    @Provides
    @Named(CONFIG_READ_ONLY)
    fun provideConfigReadOnly(): Boolean = BuildConfig.CONFIG_READ_ONLY

    const val CONFIG_READ_ONLY = "configReadOnly"
}
