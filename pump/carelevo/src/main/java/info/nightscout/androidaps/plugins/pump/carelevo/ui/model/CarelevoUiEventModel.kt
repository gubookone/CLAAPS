package info.nightscout.androidaps.plugins.pump.carelevo.ui.model

import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event

sealed class CarelevoOverviewEvent : Event {

    data object NoAction : CarelevoOverviewEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoOverviewEvent()
    data object ShowMessageCarelevoIsNotConnected : CarelevoOverviewEvent()
    data object DiscardComplete : CarelevoOverviewEvent()
    data object DiscardFailed : CarelevoOverviewEvent()
    data object ResumePumpComplete : CarelevoOverviewEvent()
    data object ResumePumpFailed : CarelevoOverviewEvent()
    data object StopPumpComplete : CarelevoOverviewEvent()
    data object StopPumpFailed : CarelevoOverviewEvent()

    data object ClickPumpStopResumeBtn : CarelevoOverviewEvent()
    data object ShowPumpStopDurationSelectDialog : CarelevoOverviewEvent()
    data object ShowPumpResumeDialog : CarelevoOverviewEvent()
}

sealed class CarelevoConnectEvent : Event {

    data object NoAction : CarelevoConnectEvent()
    data object DiscardComplete : CarelevoConnectEvent()
    data object DiscardFailed : CarelevoConnectEvent()

}

sealed class CarelevoConnectPrepareEvent : Event {

    data object NoAction : CarelevoConnectPrepareEvent()
    data object ShowConnectDialog : CarelevoConnectPrepareEvent()
    data object ShowMessageScanFailed : CarelevoConnectPrepareEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoConnectPrepareEvent()
    data object ShowMessageScanIsWorking : CarelevoConnectPrepareEvent()
    data object ShowMessageSelectedDeviceIseEmpty : CarelevoConnectPrepareEvent()
    data object ShowMessageNotSetUserSettingInfo : CarelevoConnectPrepareEvent()

    data object ConnectComplete : CarelevoConnectPrepareEvent()
    data object ConnectFailed : CarelevoConnectPrepareEvent()

    data object DiscardComplete : CarelevoConnectPrepareEvent()
    data object DiscardFailed : CarelevoConnectPrepareEvent()
}

sealed class CarelevoConnectSafetyCheckEvent : Event {

    data object NoAction : CarelevoConnectSafetyCheckEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoConnectSafetyCheckEvent()
    data object ShowMessageCarelevoIsNotConnected : CarelevoConnectSafetyCheckEvent()
    data object SafetyCheckComplete : CarelevoConnectSafetyCheckEvent()
    data object SafetyCheckFailed : CarelevoConnectSafetyCheckEvent()
    data object DiscardComplete : CarelevoConnectSafetyCheckEvent()
    data object DiscardFailed : CarelevoConnectSafetyCheckEvent()

}

sealed class CarelevoConnectCannulaEvent : Event {

    data object NoAction : CarelevoConnectCannulaEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoConnectCannulaEvent()
    data object ShowMessageCarelevoIsNotConnected : CarelevoConnectCannulaEvent()
    data object ShowMessageProfileNotSet : CarelevoConnectCannulaEvent()
    data class CheckCannulaComplete(val result : Boolean) : CarelevoConnectCannulaEvent()
    data object CheckCannulaFailed : CarelevoConnectCannulaEvent()
    data object DiscardComplete : CarelevoConnectCannulaEvent()
    data object DiscardFailed : CarelevoConnectCannulaEvent()
    data object SetBasalComplete : CarelevoConnectCannulaEvent()
    data object SetBasalFailed : CarelevoConnectCannulaEvent()
}

sealed class CarelevoCommunicationCheckEvent : Event {

    data object NoAction : CarelevoCommunicationCheckEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoCommunicationCheckEvent()
    data object ShowMessagePatchAddressInvalid : CarelevoCommunicationCheckEvent()
    data object CommunicationCheckComplete : CarelevoCommunicationCheckEvent()
    data object CommunicationCheckFailed : CarelevoCommunicationCheckEvent()
    data object DiscardComplete : CarelevoCommunicationCheckEvent()
    data object DiscardFailed : CarelevoCommunicationCheckEvent()

}