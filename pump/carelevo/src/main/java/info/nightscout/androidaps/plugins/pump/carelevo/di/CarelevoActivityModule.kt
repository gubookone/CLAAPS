package info.nightscout.androidaps.plugins.pump.carelevo.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.carelevo.ui.activities.CarelevoActivity

@Module
abstract class CarelevoActivityModule {

    @ActivityScope
    @ContributesAndroidInjector
    internal abstract fun contributeCarelevoActivity() : CarelevoActivity
}