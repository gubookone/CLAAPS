package info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchAdditionalPrimingUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchSafetyCheckUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectSafetyCheckEvent
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoPatchSafetyCheckViewModel @Inject constructor(
    private val aapsSchedulers: AapsSchedulers,
    private val bleController: CarelevoBleController,
    private val carelevoPatch: CarelevoPatch,
    private val patchSafetyCheckUseCase: CarelevoPatchSafetyCheckUseCase,
    private val patchDiscardUseCase: CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    private val patchAdditionalPrimingUseCase: CarelevoPatchAdditionalPrimingUseCase
) : ViewModel() {

    private var _isCreated = false
    val isCreated get() = _isCreated

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState = MutableStateFlow<State>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val compositeDisposable = CompositeDisposable()

    fun setIsCreated(isCreated: Boolean) {
        _isCreated = isCreated
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is CarelevoConnectSafetyCheckEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event): Event {
        return when (event) {
            is CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled -> event
            is CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected -> event
            is CarelevoConnectSafetyCheckEvent.SafetyCheckComplete -> event
            is CarelevoConnectSafetyCheckEvent.SafetyCheckFailed -> event
            is CarelevoConnectSafetyCheckEvent.DiscardComplete -> event
            is CarelevoConnectSafetyCheckEvent.DiscardFailed -> event
            else -> CarelevoConnectSafetyCheckEvent.NoAction
        }
    }

    private fun setUiState(state: State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun startSafetyCheck() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += patchSafetyCheckUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .doOnError {
                triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
            }
            .doFinally {
                setUiState(UiState.Idle)
            }
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::startSafetyCheck] response success")
                        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
                    }

                    is ResponseResult.Failure -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::startSafetyCheck] response failed")
                        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
                    }

                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::startSafetyCheck] response error : ${response.e}")
                        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
                    }
                }
            }
    }

    fun startDiscardProcess() {
        when (carelevoPatch.patchState.value?.getOrNull()) {
            is PatchState.ConnectedBooted -> {
                startDiscard()
            }

            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardComplete)
            }

            else -> {
                startForceDiscard()
            }
        }
    }

    private fun startDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .doOnError {
                Log.d("connect_test", "[CarelevoSafetyCheckViewModel::startDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoSafetyCheckViewModel::startDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoSafetyCheckViewModel::startDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
                    }

                    else -> {
                        Log.d("connect_test", "[CarelevoSafetyCheckViewModel::startDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
                    }
                }
            }
    }

    private fun startForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .doOnError {
                Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::startForceDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::startForceDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::startForceDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
                    }

                    else -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::startFoeceDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
                    }
                }
            }
    }

    fun retryAdditionalPriming() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += patchAdditionalPrimingUseCase.execute()
            .timeout(60, TimeUnit.SECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .doOnError {
                Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::retryAdditionalPriming] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::retryAdditionalPriming] response success")
                    }

                    is ResponseResult.Error -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::retryAdditionalPriming] response error : ${response.e}")
                    }

                    else -> {
                        Log.d("connect_test", "[CarelevoConnectSafetyCheckViewModel::retryAdditionalPriming] response failed")
                    }
                }
                setUiState(UiState.Idle)
            }
    }

    fun isSafetyCheckPassed() = carelevoPatch.patchInfo.value?.getOrNull()?.checkSafety == true

    fun isConnected() = carelevoPatch.isCarelevoConnected()

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }
}