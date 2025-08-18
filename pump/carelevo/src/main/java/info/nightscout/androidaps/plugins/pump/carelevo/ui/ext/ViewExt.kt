package info.nightscout.androidaps.plugins.pump.carelevo.ui.ext

import android.content.Context
import android.view.View
import info.nightscout.androidaps.plugins.pump.carelevo.R

internal fun setInfusionStateText(infusionState : Int, context : Context) : String {
    return when(infusionState) {
        0 -> context.getString(R.string.carelevo_overview_infusion_state_pump_stop)
        1 -> context.getString(R.string.carelevo_overview_infusion_state_basal)
        2 -> context.getString(R.string.carelevo_overview_infusion_state_temp_basal)
        3 -> context.getString(R.string.carelevo_overview_infusion_state_imme_bolus)
        5 -> context.getString(R.string.carelevo_overview_infusion_state_extend_bolus)
        else -> ""
    }
}