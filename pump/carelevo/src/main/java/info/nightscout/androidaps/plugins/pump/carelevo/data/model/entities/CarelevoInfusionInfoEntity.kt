package info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities

import app.aaps.core.interfaces.logging.L
import org.joda.time.DateTime

data class CarelevoInfusionInfoEntity(
    val basalInfusionInfo : CarelevoBasalInfusionInfoEntity? = null,
    val tempBasalInfusionInfo : CarelevoTempBasalInfusionInfoEntity? = null,
    val immeBolusInfusionInfo : CarelevoImmeBolusInfusionInfoEntity? = null,
    val extendBolusInfusionInfo : CarelevoExtendBolusInfusionInfoEntity? = null
)

data class CarelevoBasalSegmentInfusionInfoEntity(
    val createdAt : String,
    val updatedAt : String,
    val startTime : Int,
    val endTime : Int,
    val speed : Double
)

data class CarelevoBasalInfusionInfoEntity(
    val infusionId : String,
    val address : String,
    val mode : Int,
    val createdAt : String = DateTime.now().toString(),
    val updatedAt : String = DateTime.now().toString(),
    val segments : List<CarelevoBasalSegmentInfusionInfoEntity>,
    val isStop : Boolean
)

data class CarelevoTempBasalInfusionInfoEntity(
    val infusionId: String,
    val address : String,
    val mode : Int,
    val createdAt: String = DateTime.now().toString(),
    val updatedAt: String = DateTime.now().toString(),
    val percent : Int? = null,
    val speed : Double? = null,
    val infusionDurationMin : Int? = null
)

data class CarelevoImmeBolusInfusionInfoEntity(
    val infusionId : String,
    val address : String,
    val mode : Int,
    val createdAt : String = DateTime.now().toString(),
    val updatedAt : String = DateTime.now().toString(),
    val volume : Double? = null,
    val infusionDurationSeconds : Int? = null
)

data class CarelevoExtendBolusInfusionInfoEntity(
    val infusionId : String,
    val address : String,
    val mode : Int,
    val createdAt : String = DateTime.now().toString(),
    val updatedAt : String = DateTime.now().toString(),
    val volume : Double? = null,
    val speed : Double? = null,
    val infusionDurationMin : Int? = null
)