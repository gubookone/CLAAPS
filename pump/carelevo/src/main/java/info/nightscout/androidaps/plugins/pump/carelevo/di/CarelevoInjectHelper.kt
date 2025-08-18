package info.nightscout.androidaps.plugins.pump.carelevo.di

import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
annotation class CarelevoPluginQualifier

@MustBeDocumented
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FragmentScope

@MustBeDocumented
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class ActivityScope