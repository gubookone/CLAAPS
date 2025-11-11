package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.FragmentCarelevoConnectBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseCircleProgress
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.repeatOnStartedWithViewOwner
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.showDialogDiscardConfirm
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.CarelevoConnectEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.type.CarelevoPatchStep
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchConnectionFlowViewModel

class CarelevoPatchConnectionFlowFragment : CarelevoBaseFragment<FragmentCarelevoConnectBinding>(R.layout.fragment_carelevo_connect) {

    companion object {

        fun getInstance(): CarelevoPatchConnectionFlowFragment = CarelevoPatchConnectionFlowFragment()
    }

    private val viewModel: CarelevoPatchConnectionFlowViewModel by activityViewModels { viewModelFactory }

    private var lastStep: CarelevoPatchStep? = null

    private lateinit var onBackPressCallback: OnBackPressedCallback

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onBackPressCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.isCreated) {
            viewModel.observePatchEvent()
            viewModel.setIsCreated(true)
        }
        setupView()
        setupObserver()
    }

    override fun setupView() {
        loadingProgress = CarelevoBaseCircleProgress(requireContext())
    }

    private fun setupObserver() {
        repeatOnStartedWithViewOwner {
            viewModel.page.collect { step ->
                if (step == lastStep) return@collect
                lastStep = step
                val (currentFragment, title) = when (step) {
                    CarelevoPatchStep.PATCH_START -> CarelevoPatchStartFragment.getInstance() to requireContext().getString(R.string.carelevo_connect_prepare_title)
                    CarelevoPatchStep.PATCH_CONNECT -> CarelevoPatchConnectFragment.getInstance() to requireContext().getString(R.string.carelevo_connect_patch_title)
                    CarelevoPatchStep.SAFETY_CHECK -> CarelevoPatchSafetyCheckFragment.getInstance() to requireContext().getString(R.string.carelevo_connect_safety_check_title)
                    CarelevoPatchStep.PATCH_ATTACH -> CarelevoPatchAttachFragment.getInstance() to requireContext().getString(R.string.carelevo_connect_patch_attach_title)
                    CarelevoPatchStep.NEEDLE_INSERTION -> CarelevoPatchCannulaInsertionFragment.getInstance() to requireContext().getString(R.string.carelevo_connect_cannula_check_title)
                }
                setFragmentIfChanged(currentFragment)
                binding.tvTitle.text = title
                binding.tvStep.text = requireContext().getString(R.string.common_unit_step_format, step.ordinal + 1, CarelevoPatchStep.entries.size)
            }
        }

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

    private fun handleEvent(event: Event) {
        when (event) {
            is CarelevoConnectEvent.DiscardComplete -> {
                ToastUtils.infoToast(requireContext(), "사용 종료 되었습니다.")
                requireActivity().finish()
            }

            is CarelevoConnectEvent.DiscardFailed -> {
                ToastUtils.infoToast(requireContext(), "사용 종료 실패 했습니다. 다시 시도해 주세요.")
            }

            else -> Unit
        }
    }

    private fun handleState(state: State) {
        when (state) {
            is UiState.Idle -> hideFullScreenProgress()
            is UiState.Loading -> showFullScreenProgress()
            else -> hideFullScreenProgress()
        }
    }

    private fun setFragmentIfChanged(f: Fragment, tag: String = f::class.java.name) {
        val fm = childFragmentManager
        val current = fm.findFragmentById(R.id.container)
        if (current?.javaClass == f.javaClass) return  // 이미 같은 화면이면 교체 X

        fm.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container_fragment, f, tag)
            .commit()
    }

    private fun onBackPressed() {
        showDialogDiscardConfirm(
            positiveCallback = {
                viewModel.startPatchDiscardProcess()
            }
        )
    }

}