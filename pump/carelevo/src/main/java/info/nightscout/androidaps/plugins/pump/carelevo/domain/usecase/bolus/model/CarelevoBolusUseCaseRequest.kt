package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequset

data class StartImmeBolusInfusionRequestModel(
    val actionSeq : Int,
    val volume : Double
) : CarelevoUseCaseRequset

data class StartExtendBolusInfusionRequestModel(
    val volume : Double,
    val minutes : Int
) : CarelevoUseCaseRequset