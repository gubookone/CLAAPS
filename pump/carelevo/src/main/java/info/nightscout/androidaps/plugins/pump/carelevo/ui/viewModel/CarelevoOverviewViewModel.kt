package info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoPumpStopUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.model.CarelevoPumpStopRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoOverviewEvent
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt

class CarelevoOverviewViewModel @Inject constructor(
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val commandQueue : CommandQueue,

    private val carelevoPatch : CarelevoPatch,
    private val bleController : CarelevoBleController,
    private val aapsSchedulers: AapsSchedulers,
    private val patchDiscardUseCase : CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase : CarelevoPatchForceDiscardUseCase,
    private val pumpStopUseCase : CarelevoPumpStopUseCase,
    private val pumpResumeUseCase : CarelevoPumpResumeUseCase
) : ViewModel() {

    private val _bleState = MutableLiveData<Boolean?>(null)
    val bleState get() = _bleState

    private val _serialNumber = MutableLiveData<String>()
    val serialNumber get() = _serialNumber

    private val _lotNumber = MutableLiveData<String>()
    val lotNumber get() = _lotNumber

    private val _bootDateTime = MutableLiveData<String>()
    val bootDateTime get() = _bootDateTime

    private val _expirationTime = MutableLiveData<Int?>()
    val expirationTime get() = _expirationTime

    private val _infusionStatus = MutableLiveData<Int?>()
    val infusionStatus get() = _infusionStatus

    private val _basalRate = MutableLiveData<Double>()
    val basalRate get() = _basalRate

    private val _tempBasalRate = MutableLiveData<Double>()
    val tempBasalRate get() = _tempBasalRate

    private val _insulinRemains = MutableLiveData<Double?>()
    val insulinRemains get() = _insulinRemains

    private val _totalDeliveredInsulinAmount = MutableLiveData<Double?>()
    val totalDeliveredInsulinAmount get() = _totalDeliveredInsulinAmount

    private var _isCreated = false
    val isCreated get() = _isCreated

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState : MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var _isPumpStop = MutableLiveData<Boolean>(false)
    val isPumpStop get() = _isPumpStop

    private val compositeDisposable = CompositeDisposable()

    init {
        Log.d("connect_test", "[CarelevoOverviewViewModel::init] init called")
        Log.d("connect_test", "[CarelevoOverviewViewModel::init] compositeDisposable is disposed : ${compositeDisposable.isDisposed}")
    }


    fun setIsCreated(isCreated : Boolean) {
        _isCreated = isCreated
    }

    fun observePatchInfo() {
        compositeDisposable += carelevoPatch.patchInfo
            .doOnError {
                Log.d("connect_test", "[CarelevoOverviewViewModel::observePatchInfo] doOnError called : $it")
            }
            .observeOn(aapsSchedulers.main)
            .subscribe {
                Log.d("connect_test", "[CarelevoOverviewViewModel::observePatchInfo] info : ${it.getOrNull()}")
                val currentThread = Thread.currentThread()
                Log.d("connect_test", "[CarelevoOverviewViewModel::observePatchInfo] current thread name : ${currentThread.name}")
                _serialNumber.value = it?.getOrNull()?.manufactureNumber ?: ""
                _lotNumber.value = it?.getOrNull()?.manufactureNumber ?: ""
                _bootDateTime.value = it?.getOrNull()?.bootDateTime ?: ""
                _expirationTime.value = it?.getOrNull()?.thresholdExpiry
                _infusionStatus.value = it?.getOrNull()?.mode
                _insulinRemains.value = it?.getOrNull()?.insulinRemain
                val inputInsulin = it?.getOrNull()?.insulinAmount ?: 0
                val totalAmount = inputInsulin - (it?.getOrNull()?.insulinRemain ?: 0.0)
                // val totalAmount = (it?.getOrNull()?.infusedTotalBasalAmount ?: 0.0) + (it?.getOrNull()?.infusedTotalBolusAmount ?: 0.0)
                _totalDeliveredInsulinAmount.value = totalAmount
                _isPumpStop.value = it?.getOrNull()?.isStopped ?: false
            }
    }

    fun observePatchState() {
        compositeDisposable += carelevoPatch.patchState
            .doOnError {
                Log.d("connect_test", "[CarelevoOverviewViewModel::observePatchState] doOnError called : $it")
            }
            .observeOn(aapsSchedulers.main)
            .subscribe {
                Log.d("connect_test", "[CarelevoOverviewViewModel::observePatchState] state : ${it.getOrNull()}")
                it?.getOrNull()?.let {
                    when(it) {
                        is PatchState.ConnectedBooted -> _bleState.value = true
                        is PatchState.NotConnectedNotBooting -> _bleState.value = null
                        else -> _bleState.value = false
                    }
                }
            }
    }

    fun observeInfusionInfo() {
        compositeDisposable += carelevoPatch.infusionInfo
            .observeOn(aapsSchedulers.main)
            .subscribe {
                _tempBasalRate.value = it?.getOrNull()?.tempBasalInfusionInfo?.speed ?: 0.0
            }
    }

    fun observeProfile() {
        compositeDisposable += carelevoPatch.profile
            .observeOn(aapsSchedulers.main)
            .subscribe {
                _basalRate.value = it?.getOrNull()?.getBasal() ?: 0.0
            }
    }

    fun triggerEvent(event : Event) {
        viewModelScope.launch {
            when(event) {
                is CarelevoOverviewEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event) : Event {
        return when(event) {
            is CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled -> event
            is CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected -> event
            is CarelevoOverviewEvent.DiscardComplete -> event
            is CarelevoOverviewEvent.DiscardFailed -> event
            is CarelevoOverviewEvent.ResumePumpComplete -> event
            is CarelevoOverviewEvent.ResumePumpFailed -> event
            is CarelevoOverviewEvent.StopPumpComplete -> event
            is CarelevoOverviewEvent.StopPumpFailed -> event
            is CarelevoOverviewEvent.ClickPumpStopResumeBtn -> {
                when(carelevoPatch.getPatchState()) {
                    is PatchState.NotConnectedNotBooting -> {
                        CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected
                    }
                    else -> {
                        val isStop = carelevoPatch.patchInfo.value?.get()?.isStopped ?: false
                        if(isStop) {
                            CarelevoOverviewEvent.ShowPumpResumeDialog
                        } else {
                            CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog
                        }
                    }
                }
            }
            else -> CarelevoOverviewEvent.NoAction
        }
    }

    private fun setUiState(state : State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun startDiscardProcess() {
        if(!carelevoPatch.isCarelevoConnected()) {
            startPatchForceDiscard()
        } else {
            startPatchDiscard()
        }
    }

    private fun startPatchDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                Log.d("connect_test", "[CarelevoOverviewViewModel::startPatchDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoOverviewEvent.DiscardFailed)
            }
            .subscribe { response ->
                when(response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoOverviewViewModel::startPatchDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.DiscardComplete)
                    }
                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoOverviewVewModel::startPatchDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.DiscardFailed)
                    }
                    else -> {
                        Log.d("connect_test", "[CarelevoOverviewViewModel::startPatchDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.DiscardFailed)
                    }
                }
            }
    }

    private fun startPatchForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                Log.d("connect_test", "[CarelevoOverviewViewModel::startPatchForceDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoOverviewEvent.DiscardFailed)
            }
            .subscribe { response ->
                when(response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoOverviewViewModel::startPatchForceDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.DiscardComplete)
                    }
                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoOverviewViewModel::startPatchForceDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.DiscardFailed)
                    }
                    else -> {
                        Log.d("connect_test", "[CarelevoOverviewViewModel::startPatchForceDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.DiscardFailed)
                    }
                }
            }
    }

    fun startPumpStopProcess(stopMinute : Int) {
        if(!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if(!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        val infusionInfo = carelevoPatch.infusionInfo.value?.getOrNull()
        val cancelExtendBolusResult = if(infusionInfo?.extendBolusInfusionInfo != null) {
            cancelExtendBolus()
        } else {
            true
        }
        val cancelTempBasalResult = if(infusionInfo?.tempBasalInfusionInfo != null) {
            cancelTempBasal()
        } else {
            true
        }

        Log.d("stop_pump_test", "[CarelevoOverviewViewModel::startPumpStopProcess] cancelTempBasalResult : $cancelTempBasalResult")
        Log.d("stop_pump_test", "[CarelevoOverviewViewModel::startPumpStopProcess] cancelExtendBolusResult : $cancelExtendBolusResult")

        if(cancelExtendBolusResult && cancelTempBasalResult) {
            compositeDisposable += pumpStopUseCase.execute(
                CarelevoPumpStopRequestModel(
                    durationMin = stopMinute
                )
            )
                .timeout(3000L, TimeUnit.MILLISECONDS)
                .observeOn(aapsSchedulers.io)
                .subscribeOn(aapsSchedulers.io)
                .doOnError {
                    Log.d("stop_pump_test", "[CarelevoOverviewViewModel::startPumpStopProcess] doOnError called : $it")
                    setUiState(UiState.Idle)
                    triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
                }
                .subscribe { response ->
                    when(response) {
                        is ResponseResult.Success -> {
                            Log.d("stop_pump_test", "[CarelevoOverviewViewModel::startPumpStopProcess] response success")
                            pumpSync.syncTemporaryBasalWithPumpId(
                                timestamp = dateUtil.now(),
                                rate = 0.0,
                                duration = T.mins(stopMinute.toLong()).msecs(),
                                isAbsolute = true,
                                type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                                pumpId = dateUtil.now(),
                                pumpType = PumpType.CAREMEDI_CARELEVO,
                                pumpSerial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                            )
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoOverviewEvent.StopPumpComplete)
                        }
                        is ResponseResult.Error -> {
                            Log.d("stop_pump_test", "[CarelevoOverviewViewModel::startPumpStopProcess] response error ${response.e}")
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
                        }
                        else -> {
                            Log.d("stop_pump_test", "[CarelevoOverviewViewModel::startPumpStopProcess] response failed")
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
                        }
                    }
                }
        } else {
            Log.d("pump_stop_test", "[CarelevoOverviewViewModel::startPumpStopProcess] cancel temp or cancel extend failed")
            setUiState(UiState.Idle)
            triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
        }
    }

    private fun cancelTempBasal() : Boolean {
        return commandQueue.cancelTempBasal(true, null)
    }

    private fun cancelExtendBolus() : Boolean {
        return commandQueue.cancelExtended(null)
    }

    fun startPumpResume() {
        if(!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        if(!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += pumpResumeUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                Log.d("resume_pump_test", "[CarelevoOverviewViewModel::startPumpResume] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoOverviewEvent.ResumePumpFailed)
            }
            .subscribe { response ->
                when(response) {
                    is ResponseResult.Success -> {
                        Log.d("resume_pump_test", "[CarlevoOverviewViewModel::startPumpResume] response success")
                        pumpSync.syncStopTemporaryBasalWithPumpId(
                            timestamp = dateUtil.now(),
                            endPumpId = dateUtil.now(),
                            pumpType = PumpType.CAREMEDI_CARELEVO,
                            pumpSerial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                        )
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.ResumePumpComplete)
                    }
                    is ResponseResult.Error -> {
                        Log.d("resume_pump_test", "[CarelevoOverviewViewModel::startPumpResume] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.ResumePumpFailed)
                    }
                    else -> {
                        Log.d("resume_pump_test", "[CarelevoOverviewViewModel::startPumpResume] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.ResumePumpFailed)
                    }
                }
            }
    }

    override fun onCleared() {
        Log.d("connect_test", "[CarelevoOverviewViewModel::onCleared] onCleared called")
        compositeDisposable.clear()
        Log.d("connect_test", "[CarelevoOverviewViewModel::onCleared] compositeDisposable is disposed : ${compositeDisposable.isDisposed}")
        super.onCleared()
    }
}