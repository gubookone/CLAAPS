package info.nightscout.androidaps.plugins.pump.carelevo.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoCommunicationCheckFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoOverviewFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoPatchAttachFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoPatchConnectFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoPatchConnectionFlowFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoPatchNeedleInsertionFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoPatchSafetyCheckFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoPatchStartFragment

@Module
abstract class CarelevoFragmentModule {

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoOverviewFragment(): CarelevoOverviewFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectFragment(): CarelevoPatchConnectionFlowFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectPrepareFragment(): CarelevoPatchStartFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectPatchFragment(): CarelevoPatchConnectFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectSafetyCheckFragment(): CarelevoPatchSafetyCheckFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCCarelevoPatchAttachFragmentFragment(): CarelevoPatchAttachFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoConnectCannulaFragment(): CarelevoPatchNeedleInsertionFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCarelevoCommunicationCheckFragment(): CarelevoCommunicationCheckFragment
}