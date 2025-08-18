package info.nightscout.androidaps.plugins.pump.carelevo.ui.base

import android.app.Dialog
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import info.nightscout.androidaps.plugins.pump.carelevo.R

open class CarelevoTopRoundBottomSheetDialog : BottomSheetDialogFragment() {

    override fun getTheme(): Int {
        return R.style.TopRoundBottomSheetDialogTheme
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }
}