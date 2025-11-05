package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
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
                Log.d("connect_test", "[CarelevoSafetyCheckFragment::setupView] btnNext clicked")
                //sharedViewModel.setPage(CarelevoPatchStep.PATCH_ATTACH)
                setFragment(CarelevoPatchAttachFragment.getInstance())
            }

            btnRetry.setOnClickListener {
                viewModel.retryAdditionalPriming()
            }
        }

        if (viewModel.isSafetyCheckPassed()) {
            handleSafetyCheckSuccess()
        }
    }

    private fun setFragment(fragment: Fragment) = parentFragmentManager.beginTransaction()
        .apply {
            replace(R.id.container_fragment, fragment)
                .addToBackStack(null)
                .commit()
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
                ToastUtils.infoToast(
                    requireContext(),
                    getString(R.string.carelevo_toast_msg_bluetooth_not_enabled)
                )
            }

            is CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected -> {
                ToastUtils.infoToast(
                    requireContext(),
                    getString(R.string.carelevo_toast_msg_not_connected)
                )
            }

            is CarelevoConnectSafetyCheckEvent.SafetyCheckComplete -> {
                ToastUtils.infoToast(
                    requireContext(),
                    getString(R.string.carelevo_toast_msg_safety_check_success)
                )
                handleSafetyCheckSuccess()
            }

            is CarelevoConnectSafetyCheckEvent.SafetyCheckFailed -> {
                ToastUtils.infoToast(
                    requireContext(),
                    getString(R.string.carelevo_toast_msg_safety_check_failed)
                )
            }

            is CarelevoConnectSafetyCheckEvent.DiscardComplete -> {
                ToastUtils.infoToast(
                    requireContext(),
                    getString(R.string.carelevo_toast_msg_discard_complete)
                )
                requireActivity().finish()
            }

            is CarelevoConnectSafetyCheckEvent.DiscardFailed -> {
                ToastUtils.infoToast(
                    requireContext(),
                    getString(R.string.carelevo_toast_msg_discard_failed)
                )
            }
        }
    }

    private fun handleSafetyCheckSuccess() {
        binding.btnNext.isVisible = true
        binding.layoutRetry.isVisible = true
    }

    private fun showDiscardConfirmDialog() {
        showDialogDiscardConfirm(
            positiveCallback = {
                viewModel.startDiscardProcess()
            }
        )
    }
}