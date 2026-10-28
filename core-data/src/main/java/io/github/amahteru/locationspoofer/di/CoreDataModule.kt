package io.github.amahteru.locationspoofer.di

import io.github.amahteru.locationspoofer.data.db.AppDatabase
import io.github.amahteru.locationspoofer.data.motion.ConfigMotionSink
import io.github.amahteru.locationspoofer.data.motion.MotionController
import io.github.amahteru.locationspoofer.data.state.SpoofingState
import io.github.amahteru.locationspoofer.data.repository.LocationRepository
import io.github.amahteru.locationspoofer.data.repository.SettingsRepository
import io.github.amahteru.locationspoofer.data.repository.WifiRepository
import io.github.amahteru.locationspoofer.utils.ConfigManager
import io.github.amahteru.locationspoofer.utils.EnvironmentScanner
import io.github.amahteru.locationspoofer.utils.LSPosedManager
import io.github.amahteru.locationspoofer.utils.OpenCellIdClient
import io.github.amahteru.locationspoofer.utils.RootManager
import io.github.amahteru.locationspoofer.utils.SettingsManager
import io.github.amahteru.locationspoofer.utils.WigleClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreDataModule = module {
    single { RootManager() }
    single { ConfigManager(androidContext(), get()) }
    single { LSPosedManager() }
    single { SettingsManager(androidContext()) }
    single { EnvironmentScanner(androidContext()) }

    single { WigleClient() }
    single { OpenCellIdClient() }
    single { WifiRepository(get()) }

    single { LocationRepository(get(), get(), get(), get(), get(), get()) }
    single { SettingsRepository(get()) }
    single {
        val settings = get<SettingsManager>()
        MotionController(
            sink = ConfigMotionSink(get(), get()),
            realismParams = {
                (SpoofingState.realismLevel.takeIf { it >= 0 } ?: settings.realismLevel) to
                    (SpoofingState.speedFluctuationPct.takeIf { it >= 0 } ?: settings.speedFluctuationPct)
            }
        )
    }

    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().environmentDao() }
    single { get<AppDatabase>().savedRouteDao() }
}
