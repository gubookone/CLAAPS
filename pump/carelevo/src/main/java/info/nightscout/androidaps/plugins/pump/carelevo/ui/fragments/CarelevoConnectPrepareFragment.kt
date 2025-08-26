package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.FragmentCarelevoConnectPrepareBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseCircleProgress
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.repeatOnStartedWithViewOwner
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogConnect
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogDiscardConfirm
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogInsulinInput
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectPrepareEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoConnectPrepareViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoConnectViewModel

class CarelevoConnectPrepareFragment : CarelevoBaseFragment<FragmentCarelevoConnectPrepareBinding>(R.layout.fragment_carelevo_connect_prepare) {

    companion object {

        fun getInstance(): CarelevoConnectPrepareFragment = CarelevoConnectPrepareFragment()
    }

    private val viewModel: CarelevoConnectPrepareViewModel by viewModels { viewModelFactory }
    private val sharedViewModel: CarelevoConnectViewModel by activityViewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.isCreated) {
            viewModel.observeScannedDevice()
            viewModel.setIsCreated(true)
        }

        setupView()
        setupObserver()
    }

    override fun setupView() {
        loadingProgress = CarelevoBaseCircleProgress(requireContext())

        with(binding) {
            btnDiscard.setOnClickListener {
                showDialogDiscardConfirm(
                    positiveCallback = {
                        viewModel.startPatchDiscardProcess()
                    })
            }

            btnNext.setOnClickListener {
                showDialogInsulinInput(
                    insulin = viewModel.inputInsulin, positiveCallback = {
                        if (checkPermissions()) {
                            viewModel.setInputInsulin(it)
                            viewModel.startScan()
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 100)
                            } else {
                                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
                            }
                        }
                    })
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (hasPermission(Manifest.permission.BLUETOOTH_SCAN) && hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), permissionType) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupObserver() {
        repeatOnStartedWithViewOwner {
            viewModel.event.collect {
                handleEvent(it)
            }
        }

        repeatOnStartedWithViewOwner {
            viewModel.uiState.collect {
                handelState(it)
            }
        }
    }

    private fun showConnectDialog() {
        viewModel.selectedDevice?.let {
            showDialogConnect(address = it.device.address, negativeCallback = {
                viewModel.startScan()
            }, positiveCallback = {
                Log.d("connect_test", "[CarelevoConnectPrepareFragment::showConnectDialog] click positive")
                viewModel.startConnect()
            })
        }
    }

    private fun handelState(state: State) {
        when (state) {
            is UiState.Idle    -> hideFullScreenProgress()
            is UiState.Loading -> showFullScreenProgress()
            else               -> hideFullScreenProgress()
        }
    }

    private fun handleEvent(event: Event) {
        when (event) {
            is CarelevoConnectPrepareEvent.ShowConnectDialog                 -> {
                showConnectDialog()
            }

            is CarelevoConnectPrepareEvent.ShowMessageScanFailed             -> {
                ToastUtils.infoToast(requireContext(), "스캔 실패했습니다. 다시 시도해 주세요.")
            }

            is CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled    -> {
                ToastUtils.infoToast(requireContext(), "블루투스 연결 상태를 확인해 주세요.")
            }

            is CarelevoConnectPrepareEvent.ShowMessageScanIsWorking          -> {
                ToastUtils.infoToast(requireContext(), "스캔 중 입니다. 잠시만 기다려 주세요.")
            }

            is CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo  -> {
                ToastUtils.infoToast(requireContext(), "설정 정보가 없습니다.")
            }

            is CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty -> {
                ToastUtils.infoToast(requireContext(), "검색된 패치가 없습니다. 다시 시도해 주세요.")
            }

            is CarelevoConnectPrepareEvent.ConnectFailed                     -> {
                ToastUtils.infoToast(requireContext(), "패치 연결 실패 했습니다. 다시 시도해 주세요.")
            }

            is CarelevoConnectPrepareEvent.ConnectComplete                   -> {
                ToastUtils.infoToast(requireContext(), "피채 연결 성공 했습니다.")
                sharedViewModel.setPage(1)
            }
        }
    }

}