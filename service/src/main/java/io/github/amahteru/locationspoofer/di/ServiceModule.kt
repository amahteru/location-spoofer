package io.github.amahteru.locationspoofer.di

import io.github.amahteru.locationspoofer.data.repository.SpoofingServiceController
import io.github.amahteru.locationspoofer.service.SpoofingServiceControllerImpl
import org.koin.dsl.module

val serviceModule = module {
    single<SpoofingServiceController> { SpoofingServiceControllerImpl() }
}
