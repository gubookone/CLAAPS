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
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoOverviewViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchConnectViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchConnectionFlowViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchNeedleInsertionViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchSafetyCheckViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchStartViewModel
import javax.inject.Provider

@Module
abstract class CarelevoViewModelModule {

    companion object {

        @Provides
        @CarelevoPluginQualifier
        fun provideViewModelFactory(
            @CarelevoPluginQualifier viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
        ): ViewModelProvider.Factory {
            return CarelevoViewModelFactory(viewModels)
        }
    }

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoOverviewViewModel::class)
    abstract fun bindCarelevoOverviewViewModel(carelevoOverviewViewModel: CarelevoOverviewViewModel): ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoPatchConnectionFlowViewModel::class)
    abstract fun bindCarelevoConnectViewModel(carelevoConnectViewModel: CarelevoPatchConnectionFlowViewModel): ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoPatchStartViewModel::class)
    abstract fun bindCarelevoConnectPrepareViewModel(carelevoConnectPrepareViewModel: CarelevoPatchStartViewModel): ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoPatchSafetyCheckViewModel::class)
    abstract fun bindCarelevoConnectSafetyCheckViewModel(carelevoConnectSafetyCheckViewModel: CarelevoPatchSafetyCheckViewModel): ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoPatchNeedleInsertionViewModel::class)
    abstract fun bindCarelevoConnectCannulaViewModel(carelevoConnectCannulaViewModel: CarelevoPatchNeedleInsertionViewModel): ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoCommunicationCheckViewModel::class)
    abstract fun bindCarelevoCommunicationCheckViewModel(carelevoCommunicationCheckViewModel: CarelevoCommunicationCheckViewModel): ViewModel

    @Binds
    @IntoMap
    @CarelevoPluginQualifier
    @ViewModelKey(CarelevoPatchConnectViewModel::class)
    abstract fun bindCarelevoConnectPatchViewModel(viewModel: CarelevoPatchConnectViewModel): ViewModel
}