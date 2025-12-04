package info.nightscout.androidaps.plugins.pump.carelevo.ui.model

data class CarelevoOverviewUiModel(
    val serialNumber: String,
    val lotNumber: String,
    val bootDateTimeUi: String,
    val expirationTime: String,     // 타입은 실제에 맞게
    val infusionStatus: Int?,
    val insulinRemainText: String,
    val totalBasal: Double,
    val totalBolus: Double,
    val isPumpStopped: Boolean,
    val runningRemainMinutes: Int
)
