package info.nightscout.androidaps.plugins.pump.carelevo.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.carelevo.ui.activities.CarelevoActivity
import info.nightscout.androidaps.plugins.pump.carelevo.ui.activities.CarelevoAlarmActivity

@Module
abstract class CarelevoActivityModule {

    @ActivityScope
    @ContributesAndroidInjector
    internal abstract fun contributeCarelevoActivity(): CarelevoActivity

    @ActivityScope
    @ContributesAndroidInjector
    internal abstract fun contributeCarelevoAlarmActivity(): CarelevoAlarmActivity
}