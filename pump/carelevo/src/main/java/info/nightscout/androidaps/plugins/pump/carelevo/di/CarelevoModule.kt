package info.nightscout.androidaps.plugins.pump.carelevo.di

import dagger.Module

@Module(includes = [
    CarelevoBleModule::class,
    CarelevoProtocolParserModule::class,
    CarelevoDataSourceModule::class,
    CarelevoDaoModule::class,
    CarelevoManagerModule::class,
    CarelevoRepositoryModule::class,
    CarelevoUseCaseModule::class,
    CarelevoFragmentModule::class,
    CarelevoViewModelModule::class,
    CarelevoActivityModule::class,
    CarelevoDialogModule::class
])
abstract class CarelevoModule {
}