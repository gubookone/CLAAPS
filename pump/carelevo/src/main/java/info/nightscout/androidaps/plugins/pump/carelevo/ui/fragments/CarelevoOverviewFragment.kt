package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.content.Intent
import android.os.Build.VERSION_CODES.P
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.viewModels
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.FragmentCarelevoOverviewBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.activities.CarelevoActivity
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseCircleProgress
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.repeatOnStartedWithViewOwner
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogDiscardConfirm
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogPumpResumeConfirm
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogPumpStopDurationSelect
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoOverviewEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoOverviewViewModel

class CarelevoOverviewFragment : CarelevoBaseFragment<FragmentCarelevoOverviewBinding>(R.layout.fragment_carelevo_overview) {

    private val viewModel : CarelevoOverviewViewModel by viewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this.viewLifecycleOwner
        if(!viewModel.isCreated) {
            viewModel.observePatchInfo()
            viewModel.observePatchState()
            viewModel.observeInfusionInfo()
            viewModel.observeProfile()
            viewModel.setIsCreated(true)
        }
        setupView()
        setupObserver()
    }

    override fun setupView() {
        loadingProgress = CarelevoBaseCircleProgress(requireContext())
        with(binding) {
            btnConnect.setOnClickListener {
                if(this@CarelevoOverviewFragment.viewModel.bleState.value == false) {
                    Intent(requireContext(), CarelevoActivity::class.java).apply {
                        putExtra("type", 1)
                        startActivity(this)
                    }
                } else if(this@CarelevoOverviewFragment.viewModel.bleState.value == null) {
                    Intent(requireContext(), CarelevoActivity::class.java).apply {
                        putExtra("type", 0)
                        startActivity(this)
                    }
                } else {
                    ToastUtils.infoToast(requireContext(), "패치가 연결중 입니다.")
                }
            }

            btnDiscard.setOnClickListener {
                showDiscardDialog()
            }

            btnPump.setOnClickListener {
                this@CarelevoOverviewFragment.viewModel.triggerEvent(CarelevoOverviewEvent.ClickPumpStopResumeBtn)
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

    private fun handleState(state : State) {
        when(state) {
            is UiState.Idle -> hideFullScreenProgress()
            is UiState.Loading -> showFullScreenProgress()
            else -> hideFullScreenProgress()
        }
    }

    private fun handleEvent(event : Event) {
        when(event) {
            is CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled -> {
                ToastUtils.infoToast(requireContext(), "블루투스 연결 상태를 확인해 주세요.")
            }
            is CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected -> {
                ToastUtils.infoToast(requireContext(), "연결된 패치가 없습니다.")
            }
            is CarelevoOverviewEvent.DiscardComplete -> {
                ToastUtils.infoToast(requireContext(), "사용종료 완료되었습니다.")
            }
            is CarelevoOverviewEvent.DiscardFailed -> {
                ToastUtils.infoToast(requireContext(), "사용종료 실패 했습니다. 다시 시도해 주세요.")
            }
            is CarelevoOverviewEvent.ResumePumpComplete -> {
                ToastUtils.infoToast(requireContext(), "주입 재개 성공했습니다.")
            }
            is CarelevoOverviewEvent.ResumePumpFailed -> {
                ToastUtils.infoToast(requireContext(), "주입 재개 실패 했습니다. 다시 시도해 주세요.")
            }
            is CarelevoOverviewEvent.StopPumpComplete -> {
                ToastUtils.infoToast(requireContext(), "주입 정지 성공했습니다.")
            }
            is CarelevoOverviewEvent.StopPumpFailed -> {
                ToastUtils.infoToast(requireContext(), "주입 정지 실패했습니다. 다시 시도해 주세요.")
            }
            is CarelevoOverviewEvent.ShowPumpResumeDialog -> {
                showPumpResumeConfirmDialog()
            }
            is CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog -> {
                showPumpStopDurationSelectDialog()
            }
            else -> Unit
        }
    }

    private fun showDiscardDialog() {
        showDialogDiscardConfirm(
            positiveCallback = {
                viewModel.startDiscardProcess()
            }
        )
    }

    private fun showPumpStopDurationSelectDialog() {
        showDialogPumpStopDurationSelect(
            positiveCallback = {
                viewModel.startPumpStopProcess(it)
            }
        )
    }

    private fun showPumpResumeConfirmDialog() {
        showDialogPumpResumeConfirm(
            positiveCallback = {
                viewModel.startPumpResume()
            }
        )
    }
}