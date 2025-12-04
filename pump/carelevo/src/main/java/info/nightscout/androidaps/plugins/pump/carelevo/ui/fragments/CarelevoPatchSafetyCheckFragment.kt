package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
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
import info.nightscout.androidaps.plugins.pump.carelevo.ui.binding.setProgressFractionText
import info.nightscout.androidaps.plugins.pump.carelevo.ui.binding.setRemainTimeText
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

            btnSafetyCheck.setOnClickListener {
                viewModel.startSafetyCheck()
            }

            btnNext.setOnClickListener {
                sharedViewModel.setPage(CarelevoPatchStep.PATCH_ATTACH)
            }

            btnRetry.setOnClickListener {
                viewModel.retryAdditionalPriming()
            }
        }

        if (viewModel.isSafetyCheckPassed()) {
            handleSafetyCheckSuccess()
            handleProgress(100)
            handleRemainSec(0)
        } else {
            handleSafetyCheckReady()
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

        repeatOnStartedWithViewOwner {
            viewModel.progress.collect { value ->
                value?.let {
                    handleProgress(it)
                }
            }
        }

        repeatOnStartedWithViewOwner {
            viewModel.remainSec.collect { value ->
                value?.let {
                    handleRemainSec(it)
                }
            }
        }
    }

    private fun handleProgress(value: Int?) {
        binding.progressbarPatchSafetyCheck.progress = value ?: 0
        binding.tvPercent.setProgressFractionText(value)
    }

    private fun handleRemainSec(value: Long?) {
        binding.tvRemainTime.setRemainTimeText(value)
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
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_bluetooth_not_enabled))
            }

            is CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_not_connected_waiting_retry))
            }

            is CarelevoConnectSafetyCheckEvent.SafetyCheckProgress -> {
                handleSafetyCheckProgress()
            }

            is CarelevoConnectSafetyCheckEvent.SafetyCheckComplete -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_safety_check_success))
                handleSafetyCheckSuccess()
            }

            is CarelevoConnectSafetyCheckEvent.SafetyCheckFailed -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_safety_check_failed))
            }

            is CarelevoConnectSafetyCheckEvent.DiscardComplete -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_discard_complete))
                requireActivity().finish()
            }

            is CarelevoConnectSafetyCheckEvent.DiscardFailed -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_discard_failed))
            }
        }
    }

    private fun handleSafetyCheckReady() = with(binding) {
        tvTitle.text = getString(R.string.carelevo_patch_safety_check_start_title)
        tvDesc.text = getString(R.string.carelevo_patch_safety_check_start_desc)
        btnNext.isVisible = false
        btnSafetyCheck.isVisible = true
        layoutRetry.isVisible = false
        btnNext.isEnabled = false
        tvDescWarning.isVisible = false
        ivWarning.isVisible = false
    }

    private fun handleSafetyCheckProgress() = with(binding) {
        tvTitle.text = getString(R.string.carelevo_patch_safety_check_start_title)
        tvDesc.text = getString(R.string.carelevo_patch_safety_check_progress_desc)
        btnNext.isVisible = true
        btnSafetyCheck.isVisible = false
        layoutRetry.isVisible = false
        btnNext.isEnabled = false
    }

    private fun handleSafetyCheckSuccess() = with(binding) {
        tvTitle.text = getString(R.string.carelevo_patch_safety_check_end_title)
        tvDesc.text = getString(R.string.carelevo_patch_safety_check_end_desc)
        btnNext.isVisible = true
        btnSafetyCheck.isVisible = false
        layoutRetry.isVisible = true
        btnNext.isEnabled = true
        tvDescWarning.isVisible = true
        ivWarning.isVisible = true
    }

    private fun showDiscardConfirmDialog() {
        showDialogDiscardConfirm(
            positiveCallback = {
                viewModel.startDiscardProcess()
            }
        )
    }
}