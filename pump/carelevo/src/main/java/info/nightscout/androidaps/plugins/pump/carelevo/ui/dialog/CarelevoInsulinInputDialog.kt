package info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.DialogCarelevoInsulinInputBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseDialog

class CarelevoInsulinInputDialog : CarelevoBaseDialog<DialogCarelevoInsulinInputBinding>(R.layout.dialog_carelevo_insulin_input) {

    companion object {
        const val TAG_DIALOG = "carelevo_insulin_input_dialog"
        private const val INPUT_INSULIN = "input_insulin"

        fun getInstance(insulin : Int) : CarelevoInsulinInputDialog = CarelevoInsulinInputDialog().apply {
            arguments = Bundle().apply {
                putInt(INPUT_INSULIN, insulin)
            }
        }
    }

    private var insulin = 0
    private var positiveClickListener : ((insulin : Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        insulin = arguments?.getInt(INPUT_INSULIN, 0) ?: 300
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindData()
        setupView()
    }

    private fun bindData() {
        with(binding) {
            etInsulin.setText(insulin.toString())
        }
    }

    private fun setupView() {
        with(binding) {
            btnCancel.setOnClickListener {
                dismissAllowingStateLoss()
            }

            btnOk.setOnClickListener {
                runCatching {
                    etInsulin.text.toString().toInt()
                }.fold(
                    onSuccess = {
                        positiveClickListener?.invoke(it)
                        dismissAllowingStateLoss()
                    },
                    onFailure = {
                        ToastUtils.infoToast(requireContext(), "인슐린 양을 확인해 주세요.")
                    }
                )
            }
        }
    }

    fun setPositiveClickListener(listener : ((insulin : Int) -> Unit)?) {
        positiveClickListener = listener
    }

}