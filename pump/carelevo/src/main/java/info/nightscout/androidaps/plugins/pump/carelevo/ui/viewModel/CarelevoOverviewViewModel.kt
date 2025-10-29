package info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
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
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoPumpStopUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.model.CarelevoPumpStopRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoRequestPatchInfusionInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoOverviewEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoOverviewUiModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.type.CarelevoScreenType
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoOverviewViewModel @Inject constructor(
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val commandQueue: CommandQueue,
    private val aapsLogger: AAPSLogger,
    private val carelevoPatch: CarelevoPatch,
    private val carelevoPatchObserver: CarelevoPatchObserver,
    private val bleController: CarelevoBleController,
    private val aapsSchedulers: AapsSchedulers,
    private val patchDiscardUseCase: CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    private val pumpStopUseCase: CarelevoPumpStopUseCase,
    private val pumpResumeUseCase: CarelevoPumpResumeUseCase,
    private val requestPatchInfusionInfoUseCase: CarelevoRequestPatchInfusionInfoUseCase,
    private val alarmUseCase: CarelevoAlarmInfoUseCase
) : ViewModel() {

    private val _bleState = MutableLiveData<PatchState?>()
    val bleState: LiveData<PatchState?> get() = _bleState

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

    private val _tempBasalRate = MutableLiveData<String>()
    val tempBasalRate get() = _tempBasalRate

    private val _insulinRemains = MutableLiveData<String?>()
    val insulinRemains get() = _insulinRemains

    private val _totalDeliveredBasalAmount = MutableLiveData<Double?>()
    val totalDeliveredBasalAmount get() = _totalDeliveredBasalAmount

    private val _totalDeliveredBolusAmount = MutableLiveData<Double?>()
    val totalDeliveredBolusAmount get() = _totalDeliveredBolusAmount

    private val _runningRemainMinutes = MutableLiveData<Int?>()
    val runningRemainMinutes get() = _runningRemainMinutes

    private var _isCreated = false
    val isCreated get() = _isCreated

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var _isPumpStop = MutableLiveData(false)
    val isPumpStop get() = _isPumpStop

    private var _isCheckScreen = MutableStateFlow<CarelevoScreenType?>(null)
    val isCheckScreen get() = _isCheckScreen

    private val _hasUnacknowledgedAlarms = MutableStateFlow(false)
    val hasUnacknowledgedAlarms = _hasUnacknowledgedAlarms.asStateFlow()

    private val compositeDisposable = CompositeDisposable()

    init {

    }

    fun setIsCreated(isCreated: Boolean) {
        _isCreated = isCreated
    }

    fun observePatchInfo() {
        compositeDisposable += carelevoPatch.patchInfo
            .observeOn(aapsSchedulers.io)
            .flatMap { info ->
                val patchInfo = info?.getOrNull()
                if (patchInfo == null) {
                    aapsLogger.debug(LTag.PUMP, "[observePatchInfo] skip null/failure")
                    Observable.empty<CarelevoOverviewUiModel>()
                } else {
                    aapsLogger.debug(LTag.PUMP, "[observePatchInfo] state: $patchInfo")
                    updateCheckScreen(patchInfo)
                    Observable.just(buildUi(patchInfo)) // ← flatMap이니 OK
                }
            }
            .observeOn(aapsSchedulers.main)
            .doOnNext { ui -> updateState(ui) }
            .subscribe(
                { ui ->
                    aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::observePatchInfo] state : $ui")

                },
                { e ->
                    aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::observePatchInfo] onError", e)
                }
            )
    }

    private fun updateCheckScreen(patchInfo: CarelevoPatchInfoDomainModel) {
        val screenType = when {
            patchInfo.checkNeedle == false -> {
                val count = patchInfo.needleFailedCount
                if (count != null && count < 3) CarelevoScreenType.CANNULA_INSERTION else null
            }

            patchInfo.checkSafety == false -> CarelevoScreenType.SAFETY_CHECK
            else -> null
        }
        _isCheckScreen.tryEmit(screenType)
    }

    private fun updateState(ui: CarelevoOverviewUiModel) {
        _serialNumber.value = ui.serialNumber
        _lotNumber.value = ui.lotNumber
        _bootDateTime.value = ui.bootDateTimeUi
        _expirationTime.value = ui.expirationTime
        _infusionStatus.value = ui.infusionStatus
        _insulinRemains.value = ui.insulinRemainText
        _totalDeliveredBasalAmount.value = ui.totalBasal
        _totalDeliveredBolusAmount.value = ui.totalBolus
        _isPumpStop.value = ui.isPumpStopped
        _runningRemainMinutes.value = ui.runningRemainMinutes
    }

    private fun buildUi(info: CarelevoPatchInfoDomainModel): CarelevoOverviewUiModel {
        Log.d("", "[CarelevoOverviewViewModel::buildUi] info : $info")
        val bootLdt = parseBootDateTime(info.bootDateTime)
        val bootUi = bootLdt?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")) ?: ""

        val infusedBasal = (info.infusedTotalBasalAmount ?: 0.0)
            .toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
        val infusedBolus = (info.infusedTotalBolusAmount ?: 0.0)
            .toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()

        val remainMinutes = bootLdt?.let { getRemainMin(it) } ?: 0

        return CarelevoOverviewUiModel(
            serialNumber = info.manufactureNumber.orEmpty(),
            lotNumber = info.firmwareVersion.orEmpty(),
            bootDateTimeUi = bootUi,
            expirationTime = info.thresholdExpiry,
            infusionStatus = info.mode,
            insulinRemainText = "${info.insulinRemain} / ${info.insulinAmount} U",
            totalBasal = infusedBasal,
            totalBolus = infusedBolus,
            isPumpStopped = info.isStopped ?: false,
            runningRemainMinutes = remainMinutes
        )
    }

    fun observePatchState() {
        compositeDisposable += carelevoPatch.patchState
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { response ->
                    aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::observePatchState] state : ${response.getOrNull()}")
                    response?.getOrNull()?.let { patchState ->
                        _bleState.value = patchState
                        if (patchState == PatchState.NotConnectedNotBooting) {
                            onDisconnectValue()
                        } else {
                            _basalRate.value = carelevoPatch.profile.value?.getOrNull()?.getBasal() ?: 0.0
                        }
                    }
                },
                {
                    aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::observePatchState] doOnError called : $it")
                }
            )
    }

    fun observeInfusionInfo() {
        compositeDisposable += carelevoPatch.infusionInfo
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                val infusionInfo = it.getOrNull() ?: return@subscribe
                aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::observeInfusionInfo] basalInfusionInfo : ${infusionInfo.basalInfusionInfo}")
                aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::observeInfusionInfo] tempBasalInfusionInfo : ${infusionInfo.tempBasalInfusionInfo}")
                aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::observeInfusionInfo] immeBolusInfusionInfo : ${infusionInfo.immeBolusInfusionInfo}")
                aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::observeInfusionInfo] extendBolusInfusionInfo : ${infusionInfo.extendBolusInfusionInfo}")

                _tempBasalRate.value = it?.getOrNull()?.tempBasalInfusionInfo?.let { info ->
                    "${info.speed ?: 0.0}"
                } ?: "0.0"
            }
    }

    fun observeProfile() {
        compositeDisposable += carelevoPatch.profile
            .observeOn(aapsSchedulers.main)
            .subscribe {
                _basalRate.value = it?.getOrNull()?.getBasal() ?: 0.0
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
                        .sortedBy { it.createdAt }
                    aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::loadUnacknowledgedAlarms] alarms : $alarms")
                    _hasUnacknowledgedAlarms.value = alarms.isNotEmpty()

                }, { e ->
                    aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::loadUnacknowledgedAlarms] error : $e")
                })
    }

    fun initUnacknowledgedAlarms() {
        _hasUnacknowledgedAlarms.value = false
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is CarelevoOverviewEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event): Event {
        return when (event) {
            is CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled -> event
            is CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected -> event
            is CarelevoOverviewEvent.DiscardComplete -> event
            is CarelevoOverviewEvent.DiscardFailed -> event
            is CarelevoOverviewEvent.ResumePumpComplete -> event
            is CarelevoOverviewEvent.ResumePumpFailed -> event
            is CarelevoOverviewEvent.StopPumpComplete -> event
            is CarelevoOverviewEvent.StopPumpFailed -> event

            is CarelevoOverviewEvent.ClickPumpStopResumeBtn -> {
                resolvePumpStopResumeEvent()
            }

            else -> CarelevoOverviewEvent.NoAction
        }
    }

    private fun resolvePumpStopResumeEvent(): CarelevoOverviewEvent {
        return when (carelevoPatch.getPatchState()) {
            is PatchState.NotConnectedNotBooting -> {
                CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected
            }

            else -> {
                val isStop = carelevoPatch.patchInfo.value?.get()?.isStopped ?: false
                if (isStop) {
                    CarelevoOverviewEvent.ShowPumpResumeDialog
                } else {
                    CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog
                }
            }
        }
    }

    private fun setUiState(state: State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun startDiscardProcess() {
        if (!carelevoPatch.isCarelevoConnected()) {
            startPatchForceDiscard()
        } else {
            startPatchDiscard()
        }
    }

    private fun startPatchDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchDiscardUseCase.execute()
            .timeout(30000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { response -> handlePatchDiscardResponse(response) },
                { error -> handlePatchDiscardError(error) }
            )
    }

    private fun handlePatchDiscardResponse(response: ResponseResult<*>) {
        when (response) {
            is ResponseResult.Success -> {
                aapsLogger.debug(LTag.PUMP, "[startPatchDiscard] success")
                bleController.unBondDevice()
                carelevoPatch.releasePatch()
                triggerEvent(CarelevoOverviewEvent.DiscardComplete)
            }

            else -> {
                aapsLogger.debug(LTag.PUMP, "[startPatchDiscard] failed or error")
                triggerEvent(CarelevoOverviewEvent.DiscardFailed)
            }
        }
        setUiState(UiState.Idle)
    }

    private fun handlePatchDiscardError(error: Throwable) {
        aapsLogger.debug(LTag.PUMP, "[startPatchDiscard] error: $error")
        setUiState(UiState.Idle)
        triggerEvent(CarelevoOverviewEvent.DiscardFailed)
    }

    private fun startPatchForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { response -> handlePatchDiscardResponse(response) },
                { error -> handlePatchDiscardError(error) }
            )
    }

    fun startPumpStopProcess(stopMinute: Int) {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        val infusionInfo = carelevoPatch.infusionInfo.value?.getOrNull()
        val cancelExtendBolusResult = if (infusionInfo?.extendBolusInfusionInfo != null) {
            cancelExtendBolus()
        } else {
            true
        }
        val cancelTempBasalResult = if (infusionInfo?.tempBasalInfusionInfo != null) {
            cancelTempBasal()
        } else {
            true
        }

        aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpStopProcess] cancelTempBasalResult : $cancelTempBasalResult")
        aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpStopProcess] cancelExtendBolusResult : $cancelExtendBolusResult")

        if (cancelExtendBolusResult && cancelTempBasalResult) {
            compositeDisposable += pumpStopUseCase.execute(
                CarelevoPumpStopRequestModel(
                    durationMin = stopMinute
                )
            )
                .timeout(3000L, TimeUnit.MILLISECONDS)
                .observeOn(aapsSchedulers.io)
                .subscribeOn(aapsSchedulers.io)
                .doOnError {
                    aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpStopProcess] doOnError called : $it")
                    setUiState(UiState.Idle)
                    triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
                }
                .subscribe { response ->
                    when (response) {
                        is ResponseResult.Success -> {
                            aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpStopProcess] response success")
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
                            aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpStopProcess] response error ${response.e}")
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
                        }

                        else -> {
                            aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpStopProcess] response failed")
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
                        }
                    }
                }
        } else {
            aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpStopProcess] cancel temp or cancel extend failed")
            setUiState(UiState.Idle)
            triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
        }
    }

    private fun cancelTempBasal(): Boolean {
        return commandQueue.cancelTempBasal(true, null)
    }

    private fun cancelExtendBolus(): Boolean {
        return commandQueue.cancelExtended(null)
    }

    fun startPumpResume() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += pumpResumeUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpResume] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoOverviewEvent.ResumePumpFailed)
            }
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpResume] response success")
                        pumpSync.syncStopTemporaryBasalWithPumpId(
                            timestamp = dateUtil.now(),
                            endPumpId = dateUtil.now(),
                            pumpType = PumpType.CAREMEDI_CARELEVO,
                            pumpSerial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                        )
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.ResumePumpComplete)
                    }

                    is ResponseResult.Failure -> {}

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoOverviewViewModel::startPumpResume] response failed: ${response.e.message}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoOverviewEvent.ResumePumpFailed)
                    }
                }
            }
    }

    fun parseBootDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyMMddHHmm")
            LocalDateTime.parse(raw, formatter)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun onDisconnectValue() {
        _serialNumber.value = ""
        _lotNumber.value = ""
        _bootDateTime.value = ""
        _expirationTime.value = 0
        _infusionStatus.value = 0
        _insulinRemains.value = ""
        _totalDeliveredBasalAmount.value = 0.0
        _totalDeliveredBolusAmount.value = 0.0
        _isPumpStop.value = false
        _runningRemainMinutes.value = 0
        _tempBasalRate.value = "0.0"
        _basalRate.value = 0.0
    }

    private fun getRemainMin(createdAt: LocalDateTime): Int {
        val endAt = createdAt.plusDays(7)
        var remainMin = ChronoUnit.MINUTES.between(LocalDateTime.now(), endAt)

        if (LocalDateTime.now().isAfter(endAt)) {
            remainMin = ChronoUnit.MINUTES.between(endAt, LocalDateTime.now())
        }

        return remainMin.toInt()
    }

    fun refreshPatchInfusionInfo() {
        Log.d("plugin_test", "[CarelevoOverviewViewModel::refreshPatchInfusionInfo] : ${carelevoPatch.isBluetoothEnabled()}, ${carelevoPatch.isCarelevoConnected()}")
        if (!carelevoPatch.isBluetoothEnabled()) {
            return
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            return
        }

        compositeDisposable += requestPatchInfusionInfoUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoOverviewViewModel::refreshPatchInfusionInfo] response success")
                    }

                    is ResponseResult.Error -> {
                        Log.d("plugin_test", "[CarelevoOverviewViewModel::InfusionInfo] response error : ${response.e}")
                    }

                    else -> {
                        Log.d("plugin_test", "[CarelevoOverviewViewModel::InfusionInfo] response failed")
                    }
                }
            }
    }

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }
}