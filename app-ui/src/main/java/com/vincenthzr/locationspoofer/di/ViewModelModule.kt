package com.vincenthzr.locationspoofer.di

import com.vincenthzr.locationspoofer.viewmodel.ManageDataViewModel
import com.vincenthzr.locationspoofer.viewmodel.MainViewModel
import com.vincenthzr.locationspoofer.viewmodel.UpdateViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { MainViewModel(get(), get(), get(), get(), get(), get(), get(), get(), androidApplication()) }
    viewModel { UpdateViewModel(androidApplication()) }
    viewModel { ManageDataViewModel(get()) }
}
