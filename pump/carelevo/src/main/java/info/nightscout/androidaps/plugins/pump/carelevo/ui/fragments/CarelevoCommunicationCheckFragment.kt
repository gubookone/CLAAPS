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
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.FragmentCarelevoCommunicationCheckBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseCircleProgress
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.repeatOnStartedWithViewOwner
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoCommunicationCheckEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoCommunicationCheckViewModel

class CarelevoCommunicationCheckFragment : CarelevoBaseFragment<FragmentCarelevoCommunicationCheckBinding>(R.layout.fragment_carelevo_communication_check) {

    companion object {
        fun getInstance() : CarelevoCommunicationCheckFragment = CarelevoCommunicationCheckFragment()
    }

    private val viewModel : CarelevoCommunicationCheckViewModel by viewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupView()
        setupObserver()
    }

    override fun setupView() {
        loadingProgress = CarelevoBaseCircleProgress(requireContext())

        with(binding) {
            btnDiscard.setOnClickListener {
                viewModel.startForceDiscard()
            }

            btnCommunication.setOnClickListener {
                viewModel.startReconnect()
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

    private fun handleEvent(event: Event) {
        when(event) {
            is CarelevoCommunicationCheckEvent.ShowMessageBluetoothNotEnabled -> {
                ToastUtils.infoToast(requireContext(), "블루투스 연결 상태를 확인해 주세요.")
            }
            is CarelevoCommunicationCheckEvent.ShowMessagePatchAddressInvalid -> {
                ToastUtils.infoToast(requireContext(), "연결되어 있는 패치가 없습니다. 사용종료 버튼을 눌러주세요.")
            }
            is CarelevoCommunicationCheckEvent.CommunicationCheckComplete -> {
                ToastUtils.infoToast(requireContext(), "통신 점검 성공했습니다.")
                requireActivity().finish()
            }
            is CarelevoCommunicationCheckEvent.CommunicationCheckFailed -> {
                ToastUtils.infoToast(requireContext(), "통신 점검 실패했습니다.")
            }
            is CarelevoCommunicationCheckEvent.DiscardComplete -> {
                ToastUtils.infoToast(requireContext(), "사용종료 성공했습니다.")
                requireActivity().finish()
            }
            is CarelevoCommunicationCheckEvent.DiscardFailed -> {
                ToastUtils.infoToast(requireContext(),  "사용종료 실패했습니다.")
            }
            else -> Unit
        }
    }

}