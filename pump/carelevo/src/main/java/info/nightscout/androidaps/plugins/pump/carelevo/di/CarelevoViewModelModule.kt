package info.nightscout.androidaps.plugins.pump.carelevo.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoViewModelFactory
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.ViewModelKey
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoCommunicationCheckViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoConnectCannulaViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoConnectPrepareViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoConnectSafetyCheckViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoConnectViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoOverviewViewModel
import javax.inject.Provider

@Module
abstract class CarelevoViewModelModule {

    companion object {

        @Provides
        @CarelevoPluginQualifier
        fun provideViewModelFactory(
            @CarelevoPluginQualifier viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
        ) : ViewModelProvider.Factory {
            return CarelevoViewModelFactory(viewModels)
        }
    }

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoOverviewViewModel::class)
    abstract fun bindCarelevoOverviewViewModel(carelevoOverviewViewModel : CarelevoOverviewViewModel) : ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoConnectViewModel::class)
    abstract fun bindCarelevoConnectViewModel(carelevoConnectViewModel : CarelevoConnectViewModel) : ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoConnectPrepareViewModel::class)
    abstract fun bindCarelevoConnectPrepareViewModel(carelevoConnectPrepareViewModel: CarelevoConnectPrepareViewModel) : ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoConnectSafetyCheckViewModel::class)
    abstract fun bindCarelevoConnectSafetyCheckViewModel(carelevoConnectSafetyCheckViewModel : CarelevoConnectSafetyCheckViewModel) : ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoConnectCannulaViewModel::class)
    abstract fun bindCarelevoConnectCannulaViewModel(carelevoConnectCannulaViewModel : CarelevoConnectCannulaViewModel) : ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoCommunicationCheckViewModel::class)
    abstract fun bindCarelevoCommunicationCheckViewModel(carelevoCommunicationCheckViewModel : CarelevoCommunicationCheckViewModel) : ViewModel
}