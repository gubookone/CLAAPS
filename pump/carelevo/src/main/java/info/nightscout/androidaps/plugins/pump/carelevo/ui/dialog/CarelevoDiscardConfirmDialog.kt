package info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.DialogCarelevoDiscardConfirmBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseDialog

class CarelevoDiscardConfirmDialog : CarelevoBaseDialog<DialogCarelevoDiscardConfirmBinding>(R.layout.dialog_carelevo_discard_confirm) {

    companion object {
        const val TAG_DIALOG = "dialog_carelevo_discard_confirm"
        fun getInstance() : CarelevoDiscardConfirmDialog = CarelevoDiscardConfirmDialog()
    }

    private var positiveClickListener : (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupView()
    }

    private fun setupView() {
        with(binding) {
            btnCancel.setOnClickListener {
                dismissAllowingStateLoss()
            }

            btnOk.setOnClickListener {
                positiveClickListener?.invoke()
                dismissAllowingStateLoss()
            }
        }
    }

    fun setPositiveClickListener(listener : (() -> Unit)?) {
        positiveClickListener = listener
    }

}