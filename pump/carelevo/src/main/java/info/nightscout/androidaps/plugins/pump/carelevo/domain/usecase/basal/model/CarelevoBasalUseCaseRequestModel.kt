package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model

import app.aaps.core.interfaces.profile.Profile
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequset

data class SetBasalProgramRequestModel(
    val profile : Profile
) : CarelevoUseCaseRequset

data class StartTempBasalInfusionRequestModel(
    val isUnit : Boolean,
    val speed : Double? = null,
    val percent : Int? = null,
    val minutes : Int
) : CarelevoUseCaseRequset