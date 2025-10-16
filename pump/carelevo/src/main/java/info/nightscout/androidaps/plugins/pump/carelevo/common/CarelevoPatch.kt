package info.nightscout.androidaps.plugins.pump.carelevo.common

import android.util.Log
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BleState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.DeviceModuleState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isAvailable
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isPeripheralConnected
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.AlertReportResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.BasalInfusionResumeResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.FinishPulseReportResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.InfusionInfoReportResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.InfusionModeResult.Companion.commandToCode
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.NoticeReportResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PatchResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.PumpStateResult.Companion.commandToCode
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.WarningReportResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.math.min

class CarelevoPatch @Inject constructor(
    private val bleController: CarelevoBleController,
    private val patchObserver: CarelevoPatchObserver,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val sp: SP,
    private val preferences: Preferences,
    private val infusionInfoMonitorUseCase: CarelevoInfusionInfoMonitorUseCase,
    private val patchInfoMonitorUseCase: CarelevoPatchInfoMonitorUseCase,
    private val userSettingInfoMonitorUseCase: CarelevoUserSettingInfoMonitorUseCase,

    private val patchRptInfusionInfoProcessUseCase: CarelevoPatchRptInfusionInfoProcessUseCase,

    private val updateMaxBolusDoseUseCase: CarelevoUpdateMaxBolusDoseUseCase,
    private val updateLowInsulinNoticeAmountUseCase: CarelevoUpdateLowInsulinNoticeAmountUseCase,

    private val createUserSettingInfoUseCase: CarelevoCreateUserSettingInfoUseCase,
    private val carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase
) {

    private val bleDisposable = CompositeDisposable()

    private val infoDisposable = CompositeDisposable()

    private var _isWorking = false
    val isWorking get() = _isWorking

    private val _btState: BehaviorSubject<Optional<BleState>> = BehaviorSubject.create()
    val btState get() = _btState

    private var _lastBtState: BleState? = null
    val lastBtState get() = _lastBtState

    private val _patchState: BehaviorSubject<Optional<PatchState>> = BehaviorSubject.create()
    val patchState get() = _patchState

    private val _isConnected: BehaviorSubject<Boolean> = BehaviorSubject.createDefault(false)
    val isConnected get() = _isConnected

    private val _connectedAddress: BehaviorSubject<Optional<String>> = BehaviorSubject.create()
    val connectedAddress get() = _connectedAddress

    private val _patchInfo: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>> = BehaviorSubject.create()
    val patchInfo get() = _patchInfo

    private val _infusionInfo: BehaviorSubject<Optional<CarelevoInfusionInfoDomainModel>> = BehaviorSubject.create()
    val infusionInfo get() = _infusionInfo

    private val _userSettingInfo: BehaviorSubject<Optional<CarelevoUserSettingInfoDomainModel>> = BehaviorSubject.create()
    val userSettingInfo get() = _userSettingInfo

    private val _profile: BehaviorSubject<Optional<Profile>> = BehaviorSubject.create()
    val profile get() = _profile

    fun initPatch() {
        if (!isWorking) {
            observeBleState()
            observeChangeState()
            observePatch()
            observePatchInfo()
            observeInfusionInfo()
            observeUserSettingInfo()
            observeSyncPatch()
            _isWorking = true
        }
    }

    fun isCarelevoConnected(): Boolean {
        val address = connectedAddress.value?.getOrNull()
        val isConnected = isConnected.value ?: false
        val validAddress = patchInfo.value?.getOrNull()?.address

        Log.d("patch_test", "[CarelevoPatchRx::isCarelevoConnected] address : $address")
        Log.d("patch_test", "[CarelevoPatchRx::isCarelevoConnected] isConnected : $isConnected")
        Log.d("patch_test", "[CarelevoPatchRx::isCarelevoConnected] validAddress : $validAddress")

        return address != null && validAddress != null && isConnected && address.lowercase() == validAddress.lowercase()
    }

    fun getPatchInfoAddress(): String? {
        return patchInfo.value?.getOrNull()?.address
    }

    fun getPatchState(): PatchState {
        val isPatchValid = patchInfo.value?.getOrNull()?.let { true } ?: false
        val isPeripheralConnected = btState.value?.getOrNull()?.isPeripheralConnected() ?: false

        Log.d("patch_state", "[CarelevoPatchRx::getPatchState] isPatchValid : $isPatchValid")
        Log.d("patch_state", "[CarelevoPatchRx::getPatchState] isPeripheralConnected : $isPeripheralConnected")

        val result = when {
            isPeripheralConnected && isPatchValid   -> PatchState.ConnectedBooted
            isPeripheralConnected && !isPatchValid  -> PatchState.NotConnectedNotBooting
            !isPeripheralConnected && isPatchValid  -> PatchState.NotConnectedBooted
            !isPeripheralConnected && !isPatchValid -> PatchState.NotConnectedNotBooting
            else                                    -> PatchState.NotConnectedNotBooting
        }

        Log.d("patch_state", "[CarelevoPatchRx::getPatchState] result : $result")

        return result
    }

    private fun observeChangeState() {
        Log.d("patch_test", "[CarelevoPatchRx::observeChangeState] observeChangeState called")
        bleDisposable += BehaviorSubject.combineLatest(
            btState,
            patchInfo
        ) { _bt, _ ->
            val btAvailable = _bt.getOrNull()?.isAvailable()
            val btPeripheralConnected = _bt.getOrNull()?.isPeripheralConnected()

            Log.d("patch_state", "[CarelevoPatchRx::changeState] btAvailable : $btAvailable")
            Log.d("patch_state", "[CarelevoPatchRx::changeState] btPeripheralConnected : $btPeripheralConnected")

            var result = getPatchState()
            if (result == PatchState.ConnectedBooted) {
                if (btAvailable == false) {
                    result = PatchState.NotConnectedBooted
                }
            }

            Log.d("patch_state", "[CarelevoPatchRx::changeState] result : $result")

            _isConnected.onNext(btPeripheralConnected ?: false)
            _connectedAddress.onNext(Optional.ofNullable(bleController.getConnectedAddress()))
            _patchState.onNext(Optional.ofNullable(result))

            when (result) {
                is PatchState.NotConnectedNotBooting -> {
                    Log.d("patch_test", "[CarelevoPatch::observeChangeState] patch state is no connection")
                }

                is PatchState.ConnectedBooted        -> {
                    Log.d("patch_test", "[CarelevoPatch::observeChangeState] patch state is ConnectedBooted")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                }

                else                                 -> {
                    Log.d("patch_test", "[CarelevoPatch::observeChangeState] patch state is disconnected")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                }
            }

            result
        }
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnComplete {
                Log.d("patch_test", "[CarelevoPatchRx::observeChangeState] doOnComplete called")
            }
            .doOnError {
                it.printStackTrace()
                Log.d("patch_test", "[CarelevoPatchRx::observeChangeState] doOnError called : $it")
            }
            .subscribe {
                Log.d("patch_test", "[CarelevoPatchRx::observeChangeState] result : $it")
            }
    }

    fun isBluetoothEnabled(): Boolean {
        return btState.value?.getOrNull()?.let {
            it.isEnabled == DeviceModuleState.DEVICE_STATE_ON
        } ?: false
    }

    //===================================================================================================
    fun setProfile(profile: Profile?) {
        _profile.onNext(Optional.ofNullable(profile))
    }

    fun checkIsSameProfile(newProfile: Profile?): Boolean {
        val setProfile = profile.value?.getOrNull() ?: return false
        return newProfile?.let {
            if (it.getBasalValues().size != setProfile.getBasalValues().size) {
                Log.d("plugin_test", "[CarelevoPatch::checkIsSameProfile] it's different with new profile basal segment size and set profile basal segment size")
                return false
            }

            for (i in it.getBasalValues().indices) {
                if (TimeUnit.SECONDS.toMinutes(it.getBasalValues()[i].timeAsSeconds.toLong()) != TimeUnit.SECONDS.toMinutes(setProfile.getBasalValues()[i].timeAsSeconds.toLong())) {
                    Log.d("plugin_test", "[CarelevoPatch::checkIsSameProfile] it's different with new profile segment duration and set profile duration")
                    return false
                }
                if (!nearlyEqual(it.getBasalValues()[i].value.toFloat(), setProfile.getBasalValues()[i].value.toFloat(), 0.0000001f)) {
                    Log.d("plugin_test", "[CarelevoPatch::checkIsSameProfile] it's different with new profile segment value and set profile segment value")
                    return false
                }
            }
            return true
        } ?: return false
    }

    private fun nearlyEqual(a: Float, b: Float, epsilon: Float): Boolean {
        val absA = abs(a)
        val absB = abs(b)
        val diff = abs(a - b)
        return if (a == b) {
            true
        } else if (a == 0f || b == 0f || absA + absB < java.lang.Float.MIN_NORMAL) {
            diff < epsilon * java.lang.Float.MIN_NORMAL
        } else {
            diff / min(absA + absB, Float.MAX_VALUE) < epsilon
        }
    }

    private fun observeBleState() {
        bleDisposable += CarelevoBleSource.bluetoothState
            .observeOn(aapsSchedulers.io)
            .distinctUntilChanged()
            .subscribe { state ->
                Log.d("ble_observer", "[CarelevoPatchRx::observeBleState] state : $state")
                if (state.isEnabled == DeviceModuleState.DEVICE_STATE_OFF) {
                    if (_lastBtState != null && _lastBtState?.isEnabled != DeviceModuleState.DEVICE_STATE_OFF) {
                        bleController.checkGatt()
                        bleController.clearOnlyGatt()
                        handleAlarm("alert", value = null, cause = AlarmCause.ALARM_ALERT_BLUETOOTH_OFF)
                    }

                }

                _lastBtState = state
                _btState.onNext(Optional.ofNullable(state))

            }
    }

    fun releasePatch() {
        flushPatchInformation()
    }

    fun flushPatchInformation() {
        bleController.clearGatt()
    }

    private fun observePatch() {
        bleDisposable += patchObserver.patchResponseEvent
            .observeOn(aapsSchedulers.io)
            .subscribe {
                proceedPatchEvent(it)
            }
    }

    private fun proceedPatchEvent(model: PatchResultModel) {
        when (model) {
            is BasalInfusionResumeResultModel -> {}

            is FinishPulseReportResultModel   -> {}

            is WarningReportResultModel       -> handleAlarm("warning", model.value, model.cause)
            is AlertReportResultModel         -> handleAlarm("alert", model.value, model.cause)
            is NoticeReportResultModel        -> handleAlarm("notice", model.value, model.cause)

            is InfusionInfoReportResultModel  -> {
                bleDisposable += patchRptInfusionInfoProcessUseCase.execute(
                    CarelevoPatchRptInfusionInfoRequestModel(
                        runningMinute = model.runningMinutes,
                        remains = model.remains,
                        infusedTotalBasalAmount = model.infusedTotalBasalAmount,
                        infusedTotalBolusAmount = model.infusedTotalBolusAmount,
                        pumpState = model.pumpState.commandToCode(),
                        mode = model.mode.commandToCode(),
                        currentInfusedProgramVolume = model.currentInfusedProgramVolume,
                        realInfusedTime = model.realInfusedTime
                    )
                )
                    .timeout(3000L, TimeUnit.MILLISECONDS)
                    .observeOn(aapsSchedulers.io)
                    .subscribeOn(aapsSchedulers.io)
                    .subscribe { response ->
                        when (response) {
                            is ResponseResult.Success -> {
                                Log.d("ble_observer", "[CarelevoPatchRx::proceedPatchEvent] response success")
                            }

                            is ResponseResult.Error   -> {
                                Log.d("ble_observer", "[CarelevoPatchRx::proceedPatchEvent] response error : ${response.e}")
                            }

                            else                      -> {
                                Log.d("ble_observer", "[CarelevoPatchRx::proceedPatchEvent] response failed")
                            }
                        }
                    }
            }
        }
    }

    private fun handleAlarm(modelType: String, value: Int?, cause: AlarmCause) {
        Log.d("ble_observer", "[CarelevoPatchRx::proceedPatchEvent] $modelType report : $value, $cause")
        val info = CarelevoAlarmInfo(
            alarmId = System.currentTimeMillis().toString(),
            alarmType = cause.alarmType,
            cause = cause,
            value = value,
            createdAt = LocalDateTime.now().toString(),
            updatedAt = LocalDateTime.now().toString(),
            isAcknowledged = false
        )
        bleDisposable += carelevoAlarmInfoUseCase.upsertAlarm(info)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                { Log.d("alarm", "upsert complete") },
                { e -> Log.e("alarm", "upsert error", e) }
            )
    }

    private fun observeInfusionInfo() {
        infoDisposable += infusionInfoMonitorUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoInfusionInfoDomainModel?
                        Log.d("info_observer", "[CarelevoPatchRx::observeInfusionInfo] response success result ==> $result")
                        _infusionInfo.onNext(Optional.ofNullable(result))
                    }

                    is ResponseResult.Error   -> {
                        Log.d("info_observer", "[CarelevoPatchRx::observeInfusionInfo] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("info_observer", "[CarelevoPatchRx::observeInfusionInfo] response failed")
                    }
                }
            }
    }

    private fun observePatchInfo() {
        infoDisposable += patchInfoMonitorUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoPatchInfoDomainModel?
                        Log.d("info_observer", "[CarelevoPatchRx::observePatchInfo] response success result ==> $result")
                        _patchInfo.onNext(Optional.ofNullable(result))
                    }

                    is ResponseResult.Error   -> {
                        Log.d("info_observer", "[CarelevoPatchRx::observePatchInfo] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("info_observer", "[CarelevoPatchRx::observePatchInfo] response failed")
                    }
                }
            }
    }

    private fun observeUserSettingInfo() {
        infoDisposable += userSettingInfoMonitorUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoUserSettingInfoDomainModel?
                        Log.d("info_observer", "[CarelevoPatchRx::observeUserSettingInfo] response success result ==> $result")
                        _userSettingInfo.onNext(Optional.ofNullable(result))
                        if (result == null) {
                            createUserSettingInfo()
                        }
                    }

                    is ResponseResult.Error   -> {
                        Log.d("info_observer", "[CarelevoPatchRx::observeUserSettingInfo] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("info_observer", "[CarelevoPatchRx::observeUserSettingInfo] response failed")
                    }
                }
            }
    }

    private fun observeSyncPatch() {
        infoDisposable += Observable.combineLatest(
            patchState,
            infusionInfo,
            userSettingInfo
        ) { state, infusionInfo, userSettingInfo ->
            if (state.getOrNull() is PatchState.ConnectedBooted && (infusionInfo.getOrNull()?.extendBolusInfusionInfo == null || infusionInfo.getOrNull()?.immeBolusInfusionInfo == null) && (userSettingInfo?.getOrNull()?.needMaxBolusDoseSyncPatch == true)) {
                updateMaxBolusDose(userSettingInfo.getOrNull()?.maxBolusDose ?: 0.0)
            }

            if (state.getOrNull() is PatchState.ConnectedBooted && (userSettingInfo?.getOrNull()?.needLowInsulinNoticeAmountSyncPatch == true)) {
                updateLowInsulinNoticeAmount(userSettingInfo.getOrNull()?.lowInsulinNoticeAmount ?: 0)
            }
        }.observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe {
                Log.d("info_observer", "[CarelevoPatchRx::observeSyncPatch] response success")
            }
    }

    private fun updateMaxBolusDose(maxBolusDose: Double) {
        infoDisposable += updateMaxBolusDoseUseCase.execute(
            CarelevoUserSettingInfoRequestModel(
                patchState = patchState.value?.getOrNull(),
                maxBolusDose = maxBolusDose
            )
        )
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("info_observer", "[CarelevoPatchRx::updateMaxBolusDose] response success")
                    }

                    is ResponseResult.Error   -> {
                        Log.d("info_observer", "[CarelevoPatchRx::updateMaxBolusDose] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("info_observer", "[CarelevoPatchRx::updateMaxBolusDose] response failed")
                    }
                }
            }
    }

    private fun updateLowInsulinNoticeAmount(lowInsulinNoticeAmount: Int) {
        infoDisposable += updateLowInsulinNoticeAmountUseCase.execute(
            CarelevoUserSettingInfoRequestModel(
                patchState = patchState.value?.getOrNull(),
                lowInsulinNoticeAmount = lowInsulinNoticeAmount
            )
        )
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("info_observer", "[CarelevoPatchRx::updateLowInsulinNoticeAmount] response success")
                    }

                    is ResponseResult.Error   -> {
                        Log.d("info_observer", "[CarelevoPatchRx::updateLowInsulinNoticeAmount] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("info_observer", "[CarelevoPatchRx::updateLowInsulinNoticeAmount] response failed")
                    }
                }
            }
    }

    private fun createUserSettingInfo() {
        val maxBolusDose = preferences.get(DoubleKey.SafetyMaxBolus)
        val lowInsulinNoticeAmount = sp.getInt(R.string.key_carelevo_low_reservoir_reminders, 0)

        infoDisposable += createUserSettingInfoUseCase.execute(
            CarelevoUserSettingInfoRequestModel(
                lowInsulinNoticeAmount = lowInsulinNoticeAmount,
                maxBasalSpeed = 15.0,
                maxBolusDose = maxBolusDose
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("patch_test", "[CarelevoPatch::createUserSettingInfo] response success")
                    }

                    is ResponseResult.Error   -> {
                        Log.d("patch_test", "[CarelevoPatch::createUserSettingInfo] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("patch_test", "[CarelevoPatch::createUserSettingInfo] response failed")
                    }
                }
            }
    }

}