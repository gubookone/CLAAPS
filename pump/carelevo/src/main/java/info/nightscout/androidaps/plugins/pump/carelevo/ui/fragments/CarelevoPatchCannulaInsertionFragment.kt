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
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectCannulaEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchCannulaInsertionViewModel

class CarelevoPatchCannulaInsertionFragment : CarelevoBaseFragment<FragmentCarelevoPatchCannulaInsertionBinding>(R.layout.fragment_carelevo_patch_cannula_insertion) {

    companion object {

        fun getInstance(): CarelevoPatchCannulaInsertionFragment = CarelevoPatchCannulaInsertionFragment()
    }

    private val viewModel: CarelevoPatchCannulaInsertionViewModel by viewModels { viewModelFactory }

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
                    viewModel.startSetBasal()
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
            is CarelevoConnectCannulaEvent.ShowMessageBluetoothNotEnabled -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_bluetooth_not_enabled))
            }

            is CarelevoConnectCannulaEvent.ShowMessageCarelevoIsNotConnected -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_patch_not_connected))
            }

            is CarelevoConnectCannulaEvent.ShowMessageProfileNotSet -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_profile_not_set))
            }

            is CarelevoConnectCannulaEvent.CheckCannulaComplete -> {
                if (event.result) {
                    ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_cannula_inserted))
                } else {
                    ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_cannula_not_inserted))
                }
            }

            is CarelevoConnectCannulaEvent.CheckCannulaFailed -> {
                if (event.failedCount >= 3) {
                    activityFinish()
                }
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_cannula_check_failed))
            }

            is CarelevoConnectCannulaEvent.DiscardComplete -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_discard_complete))
                activityFinish()
            }

            is CarelevoConnectCannulaEvent.DiscardFailed -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_discard_failed))
            }

            is CarelevoConnectCannulaEvent.SetBasalComplete -> {
                ToastUtils.infoToast(requireContext(), getString(R.string.carelevo_toast_msg_set_basal_complete))
                activityFinish()
            }

            is CarelevoConnectCannulaEvent.SetBasalFailed -> {
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

    private fun showCheckNeedleDialog() {
        TextBottomSheetDialog.Builder().setTitle(
            requireContext().getString(R.string.carelevo_dialog_patch_needle_check_title)
        ).setContent(
            requireContext().getString(R.string.carelevo_dialog_patch_needle_check_desc)
        ).setSecondaryButton(
            TextBottomSheetDialog.Button(
                text = requireContext().getString(R.string.carelevo_btn_close),
            )
        ).setPrimaryButton(
            TextBottomSheetDialog.Button(
                text = requireContext().getString(R.string.carelevo_btn_needle_insert_check),
                onClickListener = {
                    viewModel.startCheckCannula()
                }
            )).build().show(childFragmentManager, "")
    }

}