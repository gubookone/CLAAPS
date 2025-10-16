package info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultFailed
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model.SetBasalProgramRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchCannulaInsertionCheckUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectCannulaEvent
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoPatchCannulaInsertionViewModel @Inject constructor(
    private val pumpSync: PumpSync,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,
    private val patchCannulaInsertionCheckUseCase: CarelevoPatchCannulaInsertionCheckUseCase,
    private val patchDiscardUseCase: CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    private val setBasalProgramUseCase: CarelevoSetBasalProgramUseCase,
    private val carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase
) : ViewModel() {

    private val _isNeedleInsert: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isNeedleInsert = _isNeedleInsert.asStateFlow()

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var _isCreated = false
    val isCreated get() = _isCreated

    private val compositeDisposable = CompositeDisposable()

    fun setIsCreated(isCreated: Boolean) {
        _isCreated = isCreated
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is CarelevoConnectCannulaEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event): Event {
        return when (event) {
            is CarelevoConnectCannulaEvent.ShowMessageBluetoothNotEnabled -> event
            is CarelevoConnectCannulaEvent.ShowMessageCarelevoIsNotConnected -> event
            is CarelevoConnectCannulaEvent.ShowMessageProfileNotSet -> event
            is CarelevoConnectCannulaEvent.CheckCannulaComplete -> event
            is CarelevoConnectCannulaEvent.CheckCannulaFailed -> event
            is CarelevoConnectCannulaEvent.DiscardComplete -> event
            is CarelevoConnectCannulaEvent.DiscardFailed -> event
            is CarelevoConnectCannulaEvent.SetBasalComplete -> event
            is CarelevoConnectCannulaEvent.SetBasalFailed -> event
            else -> CarelevoConnectCannulaEvent.NoAction
        }
    }

    private fun setUiState(state: State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun observePatchInfo() {
        compositeDisposable += carelevoPatch.patchInfo
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe {
                val patchInfo = it?.getOrNull() ?: return@subscribe
                Log.d("observePatchInfo", "patchInfo needle Insert: $patchInfo")
                _isNeedleInsert.tryEmit(patchInfo.checkNeedle ?: false)

                val failedCount = patchInfo.needleFailedCount ?: 0
                if (failedCount >= 3) {
                    recordNeedleInsertFailAlarm()
                }
            }
    }

    fun startCheckCannula() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectCannulaEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoConnectCannulaEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += patchCannulaInsertionCheckUseCase.execute()
            .timeout(10000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startCheckCannula] doOnError called $it")
                setUiState(UiState.Idle)
                val failedCount = carelevoPatch.patchInfo.value?.getOrNull()?.needleFailedCount ?: return@doOnError
                triggerEvent(CarelevoConnectCannulaEvent.CheckCannulaFailed(failedCount))
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startCheckCannula] response success result ==> $result")
                        setUiState(UiState.Idle)
                        if (result is ResultSuccess) {
                            triggerEvent(CarelevoConnectCannulaEvent.CheckCannulaComplete(true))
                        } else if (result is ResultFailed) {
                            val failedCount = carelevoPatch.patchInfo.value?.getOrNull()?.needleFailedCount ?: return@subscribe
                            triggerEvent(CarelevoConnectCannulaEvent.CheckCannulaFailed(failedCount))
                        }
                    }

                    is ResponseResult.Error   -> {
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startCheckCannula] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        val failedCount = carelevoPatch.patchInfo.value?.getOrNull()?.needleFailedCount ?: return@subscribe
                        triggerEvent(CarelevoConnectCannulaEvent.CheckCannulaFailed(failedCount))
                    }

                    else                      -> {
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startCheckCannula] response failed")
                        setUiState(UiState.Idle)
                        val failedCount = carelevoPatch.patchInfo.value?.getOrNull()?.needleFailedCount ?: return@subscribe
                        triggerEvent(CarelevoConnectCannulaEvent.CheckCannulaFailed(failedCount))
                    }
                }
            }
    }

    fun startSetBasal() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectCannulaEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoConnectCannulaEvent.ShowMessageCarelevoIsNotConnected)
            return
        }
        if (carelevoPatch.profile.value == null) {
            triggerEvent(CarelevoConnectCannulaEvent.ShowMessageProfileNotSet)
            return
        }

        carelevoPatch.profile.value?.getOrNull()?.let { profile ->
            setUiState(UiState.Loading)
            compositeDisposable += setBasalProgramUseCase.execute(SetBasalProgramRequestModel(profile))
                .timeout(10000L, TimeUnit.MILLISECONDS)
                .observeOn(aapsSchedulers.io)
                .subscribeOn(aapsSchedulers.io)
                .doOnError {
                    setUiState(UiState.Idle)
                    triggerEvent(CarelevoConnectCannulaEvent.SetBasalFailed)
                }.subscribe { response ->
                    when (response) {
                        is ResponseResult.Success -> {
                            Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startSetBasal] response success")
                            pumpSync.connectNewPump(true)
                            pumpSync.insertTherapyEventIfNewWithTimestamp(
                                timestamp = System.currentTimeMillis(),
                                type = TE.Type.INSULIN_CHANGE,
                                pumpType = PumpType.CAREMEDI_CARELEVO,
                                pumpSerial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                            )
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoConnectCannulaEvent.SetBasalComplete)
                        }

                        is ResponseResult.Error   -> {
                            Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startSetBasal] response error : ${response.e}")
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoConnectCannulaEvent.SetBasalFailed)
                        }

                        else                      -> {
                            Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startSetBasal] response failed")
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoConnectCannulaEvent.SetBasalFailed)
                        }
                    }
                }
        } ?: run {
            triggerEvent(CarelevoConnectCannulaEvent.ShowMessageProfileNotSet)
        }
    }

    fun startDiscardProcess() {
        if (!carelevoPatch.isCarelevoConnected()) {
            startForceDiscard()
        } else {
            startDiscard()
        }
    }

    private fun startDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectCannulaEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectCannulaEvent.DiscardComplete)
                    }

                    is ResponseResult.Error   -> {
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectCannulaEvent.DiscardFailed)
                    }

                    else                      -> {
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectCannulaEvent.DiscardFailed)
                    }
                }
            }
    }

    private fun startForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startForceDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectCannulaEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startForceDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectCannulaEvent.DiscardComplete)
                    }

                    is ResponseResult.Error   -> {
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startForceDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectCannulaEvent.DiscardFailed)
                    }

                    else                      -> {
                        Log.d("connect_test", "[CarelevoConnectCannulaViewModel::startForceDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectCannulaEvent.DiscardFailed)
                    }
                }
            }
    }

    private fun recordNeedleInsertFailAlarm() {
        val info = CarelevoAlarmInfo(
            alarmId = System.currentTimeMillis().toString(),
            alarmType = AlarmType.WARNING,
            cause = AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR,
            createdAt = LocalDateTime.now().toString(),
            updatedAt = LocalDateTime.now().toString(),
            isAcknowledged = false,

            )
        compositeDisposable += carelevoAlarmInfoUseCase.upsertAlarm(info)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                { Log.d("alarm", "upsert complete") },
                { e -> Log.e("alarm", "upsert error", e) }
            )
    }

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }
}