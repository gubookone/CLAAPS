package info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.Connect
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.DiscoveryService
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.EnableNotifications
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.StartScan
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.StopScan
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralScanResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.ScannedDevice
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isAbnormalBondingFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isAbnormalFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isDiscoverCleared
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isPairingFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isReInitialized
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeConnected
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeDiscovered
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeNotificationEnabled
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoConnectNewPatchUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectPrepareEvent
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import kotlin.jvm.optionals.getOrNull

class CarelevoConnectPrepareViewModel @Inject constructor(
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch : CarelevoPatch,
    private val bleController : CarelevoBleController,
    private val connectNewPatchUseCase: CarelevoConnectNewPatchUseCase,
    private val patchDiscardUseCase : CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase : CarelevoPatchForceDiscardUseCase
) : ViewModel() {

    @Inject @Named("characterTx") lateinit var txUuid : UUID

    private var _isCreated = false
    val isCreated get() = _isCreated

    private var _selectedDevice : ScannedDevice? = null
    val selectedDevice get() = _selectedDevice

    private var _isScanWorking = false
    val isScanWorking get() = _isScanWorking

    private val commandDelay = 300L

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState : MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var _inputInsulin = 300
    val inputInsulin get() = _inputInsulin

    private val compositeDisposable = CompositeDisposable()

    private val connectDisposable = CompositeDisposable()

    fun setIsCreated(isCreated : Boolean) {
        _isCreated = isCreated
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when(event) {
                is CarelevoConnectPrepareEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event : Event) : Event {
        return when(event) {
            is CarelevoConnectPrepareEvent.ShowConnectDialog -> event
            is CarelevoConnectPrepareEvent.ShowMessageScanFailed -> event
            is CarelevoConnectPrepareEvent.ShowMessageScanIsWorking -> event
            is CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled -> event
            is CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty -> event
            is CarelevoConnectPrepareEvent.ConnectComplete -> event
            is CarelevoConnectPrepareEvent.ConnectFailed -> event
            is CarelevoConnectPrepareEvent.DiscardComplete -> event
            is CarelevoConnectPrepareEvent.DiscardFailed -> event
            is CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo -> event
            else -> CarelevoConnectPrepareEvent.NoAction
        }
    }

    private fun setUiState(state : State) {
        _uiState.tryEmit(state)
    }

    fun observeScannedDevice() {
        compositeDisposable += CarelevoBleSource.scanDevices
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe {
                Log.d("connect_test", "[CarelevoConnectPrepareViewModel::observeScannedDeviceTest] device : $it")
                if(it is PeripheralScanResult.Success) {
                    val result = it.value
                    if(result.isNotEmpty()) {
                        _selectedDevice = result[0]
                    }
                }
            }
    }

    fun startScan() {
        if(!bleController.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if(isScanWorking) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageScanIsWorking)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += bleController.execute(StartScan())
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe { result ->
                _isScanWorking = true
                Thread.sleep(10000)
                setUiState(UiState.Idle)
                stopScan()
            }
    }

    private fun stopScan() {
        compositeDisposable += bleController.execute(StopScan())
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe { result ->
                _isScanWorking = false
                if(selectedDevice != null) {
                    triggerEvent(CarelevoConnectPrepareEvent.ShowConnectDialog)
                } else {
                    triggerEvent(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
                }
            }
    }

    fun setInputInsulin(insulin : Int) {
        _inputInsulin = insulin
    }

    fun startPatchDiscardProcess() {
        when(carelevoPatch.patchState.value?.getOrNull()) {
            is PatchState.ConnectedBooted -> {
                startPatchDiscard()
            }
            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
            }
            else -> {
                startPatchForceDiscard()
            }
        }
    }

    private fun startPatchDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startPatchDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
            }.subscribe { response ->
                when(response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startPatchDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
                    }
                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startPatchDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }
                    else -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startPatchDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
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
                Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startPatchForceDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
            }.subscribe { response ->
                when(response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startPatchForceDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
                    }
                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startPatchForceDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }
                    else -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewMode;::startPatchForceDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }
                }
            }
    }

    fun startConnect() {
        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnectTest] startConnectTest called")
        if(!bleController.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if(selectedDevice == null) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty)
            return
        }

        val address = selectedDevice?.device?.address ?: ""
        bleController.clearBond(address)

        connectDisposable += bleController.execute(Connect(address))
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { result ->
                when(result) {
                    is CommandResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] connect result success")
                    }
                    else -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] connect result failed")
                        stopConnect()
                    }
                }
            }

        connectDisposable += carelevoPatch.btState
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { btState ->
                setUiState(UiState.Loading)

                Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] bt state : $btState")
                btState?.getOrNull()?.let { state ->
                    if(state.shouldBeConnected()) {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] should be connected called")
                        Thread.sleep(commandDelay)
                        bleController.execute(DiscoveryService(address))
                            .blockingGet()
                            .takeIf { it !is CommandResult.Success }
                            ?.let { stopConnect() }
                    }

                    if(state.shouldBeDiscovered()) {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] should be discovered called")
                        Thread.sleep(commandDelay)
                        bleController.execute(EnableNotifications(address, txUuid))
                            .blockingGet()
                            .takeIf { it !is CommandResult.Success }
                            ?.let { stopConnect() }
                    }

                    if(state.shouldBeNotificationEnabled()) {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] should be notification enabled called")
                        Thread.sleep(commandDelay)
                        connectNewPatch()
                    }
                    if(state.isDiscoverCleared()) {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] is discover cleared called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                    if(state.isAbnormalFailed()) {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] is abnormal failed called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                    if(state.isAbnormalBondingFailed()) {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] is abnormal bonding failed called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                    if(state.isReInitialized()) {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] is reinitialized called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                    if(state.isPairingFailed()) {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::startConnect] is pairing failed called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                }
            }
    }

    private fun stopConnect() {
        connectDisposable.clear()
        triggerEvent(CarelevoConnectPrepareEvent.ConnectFailed)
        setUiState(UiState.Idle)
    }

    private fun connectNewPatch() {
        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::connectNewPatch] connectNewPatch called")

        if(!bleController.isBluetoothEnabled()) {
            Log.d("connect_test", "[CarelevoConnectPrepareViewModel::connectNewPatch] bluetooth is not enabled")
            setUiState(UiState.Idle)
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        val userSettingInfo = carelevoPatch.userSettingInfo.value?.getOrNull()
        if(userSettingInfo == null) {
            Log.d("connect_test", "[CarelevoConnectPrepareViewModel::connectNewPatch] userSettingInfo is null")
            setUiState(UiState.Idle)
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo)
            return
        }

        compositeDisposable += connectNewPatchUseCase.execute(
            CarelevoConnectNewPatchRequestModel(
                volume = inputInsulin,
                expiry = 72,
                remains = userSettingInfo.lowInsulinNoticeAmount!!,
                maxBasalSpeed = userSettingInfo.maxBasalSpeed!!,
                maxVolume = userSettingInfo.maxBolusDose!!
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when(response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::connectNewPatch] response success")
                        triggerEvent(CarelevoConnectPrepareEvent.ConnectComplete)
                        setUiState(UiState.Idle)
                    }
                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::connectNewPatch] response error : ${response.e}")
                        triggerEvent(CarelevoConnectPrepareEvent.ConnectFailed)
                        setUiState(UiState.Idle)
                    }
                    else -> {
                        Log.d("connect_test", "[CarelevoConnectPrepareViewModel::connectNewPatch] response failed")
                        triggerEvent(CarelevoConnectPrepareEvent.ConnectFailed)
                        setUiState(UiState.Idle)
                    }
                }
            }
    }

    override fun onCleared() {
        connectDisposable.clear()
        compositeDisposable.clear()
        super.onCleared()
    }
}