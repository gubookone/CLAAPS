package info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.Connect
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.Disconnect
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.AlarmClearPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.AlarmClearRequestUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.model.AlarmClearUseCaseRequest
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.AlarmEvent
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoAlarmViewModel @Inject constructor(
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,
    private val alarmUseCase: CarelevoAlarmInfoUseCase,
    private val alarmClearRequestUseCase: AlarmClearRequestUseCase,
    private val alarmClearPatchDiscardUseCase: AlarmClearPatchDiscardUseCase,
    private val carelevoPumpResumeUseCase: CarelevoPumpResumeUseCase
) : ViewModel() {

    private val _alarmQueue = MutableStateFlow<List<CarelevoAlarmInfo>>(emptyList())
    val alarmQueue = _alarmQueue.asStateFlow()

    private val _alarmQueueEmptyEvent = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val alarmQueueEmptyEvent = _alarmQueueEmptyEvent.asSharedFlow()

    private val _event = MutableEventFlow<AlarmEvent>()
    val event = _event.asEventFlow()

    var alarmInfo: CarelevoAlarmInfo? = null

    private val compositeDisposable = CompositeDisposable()

    private fun isPatchConnected(): Boolean {
        return carelevoPatch.isCarelevoConnected()
    }

    private fun getConnectedAddress(): String? {
        return carelevoPatch.getPatchInfoAddress()
    }

    fun triggerEvent(event: AlarmEvent) {
        when (event) {
            is AlarmEvent.ClearAlarm -> startAlarmClearProcess(event.info)
            else -> Unit
        }
    }

    fun loadUnacknowledgedAlarms() {
        compositeDisposable += alarmUseCase.getAlarmsOnce()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { optionalList ->
                    val alarms = optionalList.orElse(emptyList())
                        .filter { !it.isAcknowledged }
                        .sortedWith(
                            compareBy<CarelevoAlarmInfo> { it.alarmType.code }
                                .thenBy { it.createdAt }
                        )

                    _alarmQueue.value = alarms.also {
                        if (it.isEmpty()) {
                            _alarmQueueEmptyEvent.tryEmit(Unit)
                        }
                    }

                }, { e ->
                    Log.e("AlarmVM", "getAlarmsOnce error", e)
                })
    }

    private fun startAlarmClearProcess(info: CarelevoAlarmInfo) {
        alarmInfo = info
        val alarmType = info.alarmType
        val alarmCause = info.cause

        Log.d("alarm_test", "[AlarmViewModel::startAlarmClearProcess] alarmType : $alarmType")
        Log.d("alarm_test", "[AlarmViewModel::startAlarmClearProcess] alarmCause : $alarmCause")

        when (alarmCause) {
            AlarmCause.ALARM_WARNING_LOW_INSULIN,
            AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
            AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE,
            AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
            AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING,
            AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED,
            AlarmCause.ALARM_WARNING_PATCH_EXPIRED,
            AlarmCause.ALARM_WARNING_PATCH_ERROR,
            AlarmCause.ALARM_WARNING_PUMP_CLOGGED,
            AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR,
            AlarmCause.ALARM_WARNING_LOW_BATTERY -> {
                if (isPatchConnected()) {
                    startAlarmClearPatchDiscardProcess(info)
                } else {
                    startAlarmClearPatchForceQuitProcess()
                }
            }

            AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF -> {
                if (isPatchConnected()) {
                    startAlarmClearRequestProcess(info)
                    startInfusionResumeProcess(info) // 앱 미사용 주입 차단 시 Resume해주는 알람기능
                } else {
                    triggerEvent(AlarmEvent.ShowToastMessage(R.string.alarm_feat_msg_check_patch_connect))
                }
            }

            AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT -> {
                if (isPatchConnected()) {
                    startAlarmClearRequestProcess(info)
                    startInfusionResumeProcess(info) // 앱 미사용 주입 차단 시 Resume해주는 알람기능
                } else {
                    triggerEvent(AlarmEvent.ShowToastMessage(R.string.alarm_feat_msg_check_patch_connect))
                }
            }

            AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1,
            AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
            AlarmCause.ALARM_ALERT_APP_NO_USE,
            AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE,
            AlarmCause.ALARM_ALERT_LOW_BATTERY,
            AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE,
            AlarmCause.ALARM_NOTICE_LOW_INSULIN,
            AlarmCause.ALARM_NOTICE_PATCH_EXPIRED,
            AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK -> {
                if (isPatchConnected()) {
                    startAlarmClearRequestProcess(info)
                } else {
                    startAlarmAlertAbnormalClearProcess(info, alarmCause)
                }
            }

            AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED -> {
                startAlarmAlertAbnormalClearProcess(info, alarmCause)
            }

            AlarmCause.ALARM_ALERT_BLUETOOTH_OFF -> {
                startAlarmAlertAbnormalClearProcess(info, alarmCause)
            }

            AlarmCause.ALARM_NOTICE_BG_CHECK,
            AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED,
            AlarmCause.ALARM_NOTICE_LGS_START,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN,
            AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING -> {
                //startAlarmUpdateProcess()
            }

            AlarmCause.ALARM_UNKNOWN -> {
                if (alarmType == AlarmType.WARNING) {
                    if (isPatchConnected()) {
                        startAlarmClearPatchDiscardProcess(info)
                    } else {
                        startAlarmClearPatchForceQuitProcess()
                    }
                } else {
                    //startAlarmUpdateProcess()
                }
            }
        }
    }

    fun startAlarmClearRequestProcess(info: CarelevoAlarmInfo) {
        viewModelScope.launch {
            compositeDisposable += alarmClearRequestUseCase.execute(AlarmClearUseCaseRequest(alarmId = info.alarmId, alarmType = info.alarmType, alarmCause = info.cause))
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe(
                    {
                        Log.d("AlarmVM", "Success to acknowledge alarm ${info.alarmId}")
                        acknowledgeAndRemoveAlarm(info.alarmId)
                    }, { e ->
                        Log.e("AlarmVM", "Failed to acknowledge alarm ${info.alarmId}", e)
                    })
        }
    }

    private fun startAlarmAlertAbnormalClearProcess(info: CarelevoAlarmInfo, alarmCause: AlarmCause) {
        viewModelScope.launch {
            compositeDisposable += alarmUseCase.acknowledgeAlarm(info.alarmId)
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe(
                    {
                        when (alarmCause) {
                            AlarmCause.ALARM_ALERT_BLUETOOTH_OFF -> {
                                startReconnect(info.alarmId)
                                viewModelScope.launch {
                                    _event.emit(AlarmEvent.RequestBluetoothEnable)
                                }
                            }

                            else -> acknowledgeAndRemoveAlarm(info.alarmId)
                        }

                    }, { error ->

                    })
        }
    }

    private fun startAlarmClearPatchDiscardProcess(info: CarelevoAlarmInfo) {
        viewModelScope.launch {
            compositeDisposable += alarmClearPatchDiscardUseCase.execute(AlarmClearUseCaseRequest(alarmId = info.alarmId, alarmType = info.alarmType, alarmCause = info.cause))
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe(
                    {
                        startAlarmClearPatchForceQuitProcess()
                    }, { e ->
                        Log.e("AlarmVM", "Failed to acknowledge alarm ${info.alarmId}", e)
                    })
        }
    }

    private fun startInfusionResumeProcess(info: CarelevoAlarmInfo) {
        viewModelScope.launch {
            compositeDisposable += carelevoPumpResumeUseCase.execute()
                .timeout(30L, TimeUnit.SECONDS)
                .observeOn(aapsSchedulers.io)
                .subscribeOn(aapsSchedulers.io)
                .doOnError {
                    aapsLogger.debug(LTag.PUMP, "[AlarmViewModel::startPumpResume] doOnError called : $it")
                }
                .subscribe { response ->
                    when (response) {
                        is ResponseResult.Success -> {
                            aapsLogger.debug(LTag.PUMP, "[AlarmViewModel::startPumpResume] response success")
                            pumpSync.syncStopTemporaryBasalWithPumpId(
                                timestamp = dateUtil.now(),
                                endPumpId = dateUtil.now(),
                                pumpType = PumpType.CAREMEDI_CARELEVO,
                                pumpSerial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                            )
                        }

                        is ResponseResult.Failure -> {}

                        is ResponseResult.Error -> {
                            aapsLogger.debug(LTag.PUMP, "[AlarmViewModel::startPumpResume] response failed: ${response.e.message}")
                        }
                    }
                }
        }
    }

    private fun startAlarmClearPatchForceQuitProcess() {
        val address = getConnectedAddress()
        address?.let {
            bleController.clearBond(it)
            compositeDisposable += bleController.execute(Disconnect(address))
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe(
                    { result ->
                        Log.d("AlarmVM", "[AlarmViewModel::startAlarmClearPatchForceQuitProcess] result : $result")
                        bleController.unBondDevice()
                        carelevoPatch.flushPatchInformation()
                        clearAllAlarms()
                    }, { e ->
                        Log.e("AlarmVM", "Disconnect failed", e)
                    })
        } ?: run {
            bleController.unBondDevice()
            carelevoPatch.flushPatchInformation()
            clearAllAlarms()
        }
    }

    fun acknowledgeAndRemoveAlarm(alarmId: String) {
        _alarmQueue.value = alarmQueue.value.toMutableList().apply {
            removeAll { it.alarmId == alarmId }
        }
        if (alarmQueue.value.isEmpty()) {
            viewModelScope.launch {
                _alarmQueueEmptyEvent.emit(Unit)
            }
        }
    }

    private fun startReconnect(alarmId: String) {
        carelevoPatch.patchInfo.value?.getOrNull()?.let {
            compositeDisposable += bleController.execute(Connect(it.address.uppercase()))
                .observeOn(aapsSchedulers.io)
                .subscribe { result ->
                    when (result) {
                        is CommandResult.Success -> {
                            Log.d("connect_test", "[AlarmViewModel::startReconnect] connect result success")
                            acknowledgeAndRemoveAlarm(alarmId)
                        }

                        else -> {
                            Log.d("connect_test", "[AlarmViewModel::startReconnect] connect result failed")
                        }
                    }
                }
        }
    }

    fun clearAllAlarms() {
        compositeDisposable += alarmUseCase.clearAlarms()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    _alarmQueue.value = emptyList()
                    val ok = _alarmQueueEmptyEvent.tryEmit(Unit)
                    Log.d("AlarmVM", "[AlarmViewModel::clearAllAlarms] emit empty event: $ok")
                },
                { e ->
                    Log.e("AlarmVM", "[AlarmViewModel::clearAllAlarms] clearAllAlarms error", e)
                })
    }
}