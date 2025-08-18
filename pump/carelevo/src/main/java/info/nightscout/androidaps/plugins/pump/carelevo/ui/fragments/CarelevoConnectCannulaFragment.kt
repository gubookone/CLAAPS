package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.FragmentCarelevoConnectCannulaBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseCircleProgress
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.repeatOnStartedWithViewOwner
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogDiscardConfirm
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectCannulaEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoConnectCannulaViewModel

class CarelevoConnectCannulaFragment : CarelevoBaseFragment<FragmentCarelevoConnectCannulaBinding>(R.layout.fragment_carelevo_connect_cannula) {

    companion object {
        fun getInstance() : CarelevoConnectCannulaFragment = CarelevoConnectCannulaFragment()
    }

    private val viewModel : CarelevoConnectCannulaViewModel by viewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(!viewModel.isCreated) {
            viewModel.observePatchInfo()
            viewModel.setIsCreated(true)
        }
        setupView()
        setupObserver()
    }

    override fun setupView() {
        loadingProgress = CarelevoBaseCircleProgress(requireContext())
        with(binding) {
            btnNeedleCheck.setOnClickListener {
                viewModel.startCheckCannula()
            }

            btnOk.setOnClickListener {
                if(viewModel.isNeedleInsert.value) {
                    viewModel.startSetBasal()
                } else {
                    ToastUtils.infoToast(requireContext(), "비늘삽입이 완료되지 않았습니다.")
                }
            }

            btnDiscard.setOnClickListener {
                // viewModel.startDiscardProcess()
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

    private fun handleState(state : State) {
        when(state) {
            is UiState.Idle -> hideFullScreenProgress()
            is UiState.Loading -> showFullScreenProgress()
            else -> hideFullScreenProgress()
        }
    }

    private fun handleEvent(event : Event) {
        when(event) {
            is CarelevoConnectCannulaEvent.ShowMessageBluetoothNotEnabled -> {
                ToastUtils.infoToast(requireContext(), "블루투스 연결 상태를 확인해 주세요.")
            }
            is CarelevoConnectCannulaEvent.ShowMessageCarelevoIsNotConnected -> {
                ToastUtils.infoToast(requireContext(), "연결된 패치가 없습니다.")
            }
            is CarelevoConnectCannulaEvent.ShowMessageProfileNotSet -> {
                ToastUtils.infoToast(requireContext(), "프로파일이 설정되어 있지 않습니다.")
            }
            is CarelevoConnectCannulaEvent.CheckCannulaComplete -> {
                // todo dialog
                if(event.result) {
                    ToastUtils.infoToast(requireContext(), "바늘 삽입이 완료 되었습니다.")
                } else {
                    ToastUtils.infoToast(requireContext(), "바늘 삽입이 완료 되지 않았습니다.")
                }
            }
            is CarelevoConnectCannulaEvent.CheckCannulaFailed -> {
                ToastUtils.infoToast(requireContext(), "바늘 삽입 점검 실패했습니다.")
            }
            is CarelevoConnectCannulaEvent.DiscardComplete -> {
                ToastUtils.infoToast(requireContext(), "사용종료 되었습니다.")
                requireActivity().finish()
            }
            is CarelevoConnectCannulaEvent.DiscardFailed -> {
                ToastUtils.infoToast(requireContext(), "사용종료 실패했습니다.")
            }
            is CarelevoConnectCannulaEvent.SetBasalComplete -> {
                ToastUtils.infoToast(requireContext(), "패치 연결이 완료되었습니다.")
                requireActivity().finish()
            }
            is CarelevoConnectCannulaEvent.SetBasalFailed -> {
                ToastUtils.infoToast(requireContext(), "기저 주입 요청 실패했습니다. 다시 시도해 주세요.")
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

}