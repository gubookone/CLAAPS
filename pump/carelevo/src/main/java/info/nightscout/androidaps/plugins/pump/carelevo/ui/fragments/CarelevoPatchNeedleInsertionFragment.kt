package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.FragmentCarelevoPatchCannulaInsertionBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseCircleProgress
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.TextBottomSheetDialog
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.repeatOnStartedWithViewOwner
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogDiscardConfirm
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectNeedleEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchNeedleInsertionViewModel

class CarelevoPatchNeedleInsertionFragment : CarelevoBaseFragment<FragmentCarelevoPatchCannulaInsertionBinding>(R.layout.fragment_carelevo_patch_cannula_insertion) {

    companion object {

        const val MAX_NEEDLE_CHECK_COUNT = 3

        fun getInstance(): CarelevoPatchNeedleInsertionFragment = CarelevoPatchNeedleInsertionFragment()
    }

    private val viewModel: CarelevoPatchNeedleInsertionViewModel by viewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.isCreated) {
            viewModel.observePatchInfo()
            viewModel.setIsCreated(true)
        }
        setupView()
        setupObserver()
    }

    override fun setupView() {
        loadingProgress = CarelevoBaseCircleProgress(requireContext())
        with(binding) {
            btnOk.setOnClickListener {
                if (viewModel.isNeedleInsert.value) {
                    showNeedleInsertionCompleteDialog()
                } else {
                    showCheckNeedleDialog()
                }
            }

            btnDiscard.setOnClickListener {
                showDiscardDialog()
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

        repeatOnStartedWithViewOwner {
            viewModel.isNeedleInsert.collect { isNeedleInsert ->
                //
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
            is CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_bluetooth_not_enabled))
            }

            is CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_not_connected_waiting_retry))
            }

            is CarelevoConnectNeedleEvent.ShowMessageProfileNotSet -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_profile_not_set))
            }

            is CarelevoConnectNeedleEvent.CheckNeedleComplete -> {
                if (event.result) {
                    ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_needle_inserted))
                } else {
                    ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_needle_not_inserted))
                }
            }

            is CarelevoConnectNeedleEvent.CheckNeedleFailed -> {
                if (event.failedCount >= MAX_NEEDLE_CHECK_COUNT) {
                    activityFinish()
                }
            }

            is CarelevoConnectNeedleEvent.CheckNeedleError -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_needle_check_failed))
            }

            is CarelevoConnectNeedleEvent.DiscardComplete -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_discard_complete))
                activityFinish()
            }

            is CarelevoConnectNeedleEvent.DiscardFailed -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_discard_failed))
            }

            is CarelevoConnectNeedleEvent.SetBasalComplete -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_set_basal_complete))
                activityFinish()
            }

            is CarelevoConnectNeedleEvent.SetBasalFailed -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_set_basal_failed))
            }

            else -> Unit
        }
    }

    private fun activityFinish() {
        requireActivity().apply {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun showDiscardDialog() {
        showDialogDiscardConfirm(
            positiveCallback = {
                viewModel.startDiscardProcess()
            }
        )
    }

    private fun showNeedleInsertionCompleteDialog() {
        TextBottomSheetDialog.Builder().setTitle(
            requireContext().getString(R.string.carelevo_dialog_patch_connect_needle_injected)
        ).setContent(
            requireContext().getString(R.string.carelevo_dialog_connect_detach_applicator_guide)
        ).setPrimaryButton(
            TextBottomSheetDialog.Button(
                text = requireContext().getString(R.string.carelevo_dialog_connect_detached),
                onClickListener = {
                    viewModel.startSetBasal()
                }
            )).build().show(childFragmentManager, "")
    }

    private fun showCheckNeedleDialog() {
        val needleFailCount = viewModel.needleFailCount() ?: 0
        val remainRetryCount = MAX_NEEDLE_CHECK_COUNT - needleFailCount

        val (btnStr, subContent, desc) = if (needleFailCount > 0) {
            Triple(
                getString(R.string.carelevo_btn_retry),
                getString(R.string.carelevo_dialog_patch_needle_retry_count, remainRetryCount),
                getString(R.string.carelevo_dialog_patch_needle_check_retry_desc)
            )
        } else {
            Triple(
                getString(R.string.carelevo_btn_needle_insert_check),
                "",
                getString(R.string.carelevo_dialog_patch_needle_check_desc)
            )
        }

        TextBottomSheetDialog.Builder()
            .setTitle(requireContext().getString(R.string.carelevo_dialog_patch_needle_check_title))
            .setContent(desc)
            .setSubContent(subContent)
            .setSecondaryButton(
                TextBottomSheetDialog.Button(
                    text = requireContext().getString(R.string.carelevo_btn_close),
                )
            ).setPrimaryButton(
                TextBottomSheetDialog.Button(
                    text = btnStr,
                    onClickListener = {
                        viewModel.startCheckNeedle()
                    }
                )
            ).build().show(childFragmentManager, "CheckNeedleDialog")
    }

}