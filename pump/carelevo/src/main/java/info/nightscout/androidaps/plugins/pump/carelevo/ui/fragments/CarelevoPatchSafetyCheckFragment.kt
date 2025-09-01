package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.FragmentCarelevoPatchSafetyCheckBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseCircleProgress
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.repeatOnStartedWithViewOwner
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogDiscardConfirm
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectSafetyCheckEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.type.CarelevoPatchStep
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchConnectionFlowViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchSafetyCheckViewModel

class CarelevoPatchSafetyCheckFragment : CarelevoBaseFragment<FragmentCarelevoPatchSafetyCheckBinding>(R.layout.fragment_carelevo_patch_safety_check) {

    companion object {

        fun getInstance(): CarelevoPatchSafetyCheckFragment = CarelevoPatchSafetyCheckFragment()
    }

    private val viewModel: CarelevoPatchSafetyCheckViewModel by viewModels { viewModelFactory }
    private val sharedViewModel: CarelevoPatchConnectionFlowViewModel by activityViewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.isCreated) {
            viewModel.setIsCreated(true)
        }
        setupView()
        setupObserver()
    }

    override fun setupView() {
        loadingProgress = CarelevoBaseCircleProgress(requireContext())
        with(binding) {
            btnDiscard.setOnClickListener {
                showDiscardConfirmDialog()
            }

            btnNext.setOnClickListener {
                viewModel.startSafetyCheck()
            }
        }
    }

    private fun setupObserver() {
        repeatOnStartedWithViewOwner {
            viewModel.event.collect {
                handleEvent(it)
            }
        }

        repeatOnStartedWithViewOwner {
            viewModel.uiState.collect {
                handleState(it)
            }
        }
    }

    private fun handleState(state: State) {
        when (state) {
            is UiState.Idle -> hideFullScreenProgress()
            is UiState.Loading -> showFullScreenProgress()
            else -> hideFullScreenProgress()
        }
    }

    private fun handleEvent(event: Event) {
        when (event) {
            is CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled -> {
                ToastUtils.infoToast(requireContext(), "블루투스 연결 상태를 확인해 주세요.")
            }

            is CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected -> {
                ToastUtils.infoToast(requireContext(), "연결된 패치가 없습니다.")
            }

            is CarelevoConnectSafetyCheckEvent.SafetyCheckComplete -> {
                ToastUtils.infoToast(requireContext(), "안전점검 성공 했습니다.")
                sharedViewModel.setPage(CarelevoPatchStep.PATCH_ATTACH)
            }

            is CarelevoConnectSafetyCheckEvent.SafetyCheckFailed -> {
                ToastUtils.infoToast(requireContext(), "인전점검 실패 했습니다.")
            }

            is CarelevoConnectSafetyCheckEvent.DiscardComplete -> {
                ToastUtils.infoToast(requireContext(), "사용종료 되었습니다.")
                requireActivity().finish()
            }

            is CarelevoConnectSafetyCheckEvent.DiscardFailed -> {
                ToastUtils.infoToast(requireContext(), "사용 종료 실패했습니다. 다시 시도해 주세요.")
            }

            else -> Unit
        }
    }

    private fun showDiscardConfirmDialog() {
        showDialogDiscardConfirm(
            positiveCallback = {
                viewModel.startDiscardProcess()
            }
        )
    }
}