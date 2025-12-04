package info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.ui.extensions.setSelection
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.DialogCarelevoPumpStopDurationSelectBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseDialog

class CarelevoPumpStopDurationSelectDialog : CarelevoBaseDialog<DialogCarelevoPumpStopDurationSelectBinding>(R.layout.dialog_carelevo_pump_stop_duration_select) {

    companion object {
        const val TAG_DIALOG = "dialog_carelevo_pump_stop_duration_select"
        fun getInstance() : CarelevoPumpStopDurationSelectDialog = CarelevoPumpStopDurationSelectDialog()
    }

    private var selectedDurationMin = 30

    private var positiveClickListener : ((duration : Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindRadioButton()
        setupView()
    }

    private fun setupView() {
        with(binding) {
            rgSelect.setOnCheckedChangeListener { group, checkedId ->
                when(checkedId) {
                    R.id.rb_30 -> selectedDurationMin = 30
                    R.id.rb_60 -> selectedDurationMin = 60
                    R.id.rb_90 -> selectedDurationMin = 90
                    R.id.rb_120 -> selectedDurationMin = 120
                    R.id.rb_150 -> selectedDurationMin = 150
                    R.id.rb_180 -> selectedDurationMin = 180
                    R.id.rb_210 -> selectedDurationMin = 210
                    R.id.rb_240 -> selectedDurationMin = 240
                }
            }

            btnCancel.setOnClickListener {
                dismissAllowingStateLoss()
            }

            btnOk.setOnClickListener {
                positiveClickListener?.invoke(selectedDurationMin)
                dismissAllowingStateLoss()
            }
        }
    }

    fun setPositiveClickListener(listener : ((duration : Int) -> Unit)?) {
        positiveClickListener = listener
    }

    private fun bindRadioButton() {
        with(binding) {
            when(selectedDurationMin) {
                30 -> {
                    rgSelect.setSelection(0)
                }
                60 -> {
                    rgSelect.setSelection(1)
                }
                90 -> {
                    rgSelect.setSelection(2)
                }
                120 -> {
                    rgSelect.setSelection(3)
                }
                150 -> {
                    rgSelect.setSelection(4)
                }
                180 -> {
                    rgSelect.setSelection(5)
                }
                210 -> {
                    rgSelect.setSelection(6)
                }
                240 -> {
                    rgSelect.setSelection(7)
                }
            }
        }
    }

}