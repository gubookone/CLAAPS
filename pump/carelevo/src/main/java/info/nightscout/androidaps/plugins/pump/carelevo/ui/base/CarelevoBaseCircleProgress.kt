package info.nightscout.androidaps.plugins.pump.carelevo.ui.base

import android.app.Dialog
import android.content.Context
import android.view.Window
import android.view.WindowManager
import info.nightscout.androidaps.plugins.pump.carelevo.R
import org.jetbrains.annotations.NotNull

class CarelevoBaseCircleProgress(
    @NotNull context: Context
) : Dialog(context) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setContentView(R.layout.progress_base_circle)
        }
    }

    fun clearDim() {
        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    fun addDim() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
}