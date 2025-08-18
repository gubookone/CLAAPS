package info.nightscout.androidaps.plugins.pump.carelevo.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.CarelevoConnectDialog
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.CarelevoInsulinInputDialog

@Module
abstract class CarelevoDialogModule {

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributesCarelevoInputInsulinDialog() : CarelevoInsulinInputDialog

    @FragmentScope
    @ContributesAndroidInjector
    abstract fun contributesCarelevoConnectDialog() : CarelevoConnectDialog
}