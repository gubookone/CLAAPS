package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequset

data class CarelevoConnectNewPatchRequestModel(
    val volume : Int,
    val expiry : Int,
    val remains : Int,
    val maxBasalSpeed : Double,
    val maxVolume : Double
) : CarelevoUseCaseRequset