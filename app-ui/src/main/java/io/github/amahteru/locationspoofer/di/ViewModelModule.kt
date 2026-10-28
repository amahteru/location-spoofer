package io.github.amahteru.locationspoofer.di

import io.github.amahteru.locationspoofer.viewmodel.ManageDataViewModel
import io.github.amahteru.locationspoofer.viewmodel.MainViewModel
import io.github.amahteru.locationspoofer.viewmodel.UpdateViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { MainViewModel(get(), get(), get(), get(), get(), get(), get(), get(), androidApplication()) }
    viewModel { UpdateViewModel(androidApplication()) }
    viewModel { ManageDataViewModel(get()) }
}
