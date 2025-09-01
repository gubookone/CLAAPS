package info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.FragmentCarelevoPatchAttachBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.type.CarelevoPatchStep
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoPatchConnectionFlowViewModel

class CarelevoPatchAttachFragment : CarelevoBaseFragment<FragmentCarelevoPatchAttachBinding>(R.layout.fragment_carelevo_patch_attach) {

    companion object {

        fun getInstance(): CarelevoPatchAttachFragment = CarelevoPatchAttachFragment()
    }

    private val sharedViewModel: CarelevoPatchConnectionFlowViewModel by activityViewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupView()
    }

    override fun setupView() {
        binding.btnNext.setOnClickListener {
            sharedViewModel.setPage(CarelevoPatchStep.NEEDLE_INSERTION)
        }
    }
}