package info.nightscout.androidaps.plugins.pump.carelevo.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoCommunicationCheckFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoConnectCannulaFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoConnectFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoConnectPrepareFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoConnectSafetyCheckFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoOverviewFragment

@Module
abstract class CarelevoFragmentModule {

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoOverviewFragment() : CarelevoOverviewFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectFragment() : CarelevoConnectFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectPrepareFragment() : CarelevoConnectPrepareFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectSafetyCheckFragment() : CarelevoConnectSafetyCheckFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectCannulaFragment() : CarelevoConnectCannulaFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoCommunicationCheckFragment() : CarelevoCommunicationCheckFragment
}