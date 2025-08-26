package info.nightscout.androidaps.plugins.pump.carelevo.ui.ext

import android.content.Context
import info.nightscout.androidaps.plugins.pump.carelevo.R

internal fun setInfusionStateText(infusionState: Int, context: Context): String {
    return when (infusionState) {
        0    -> context.getString(R.string.carelevo_overview_infusion_state_pump_stop)
        1    -> context.getString(R.string.carelevo_overview_infusion_state_basal)
        2    -> context.getString(R.string.carelevo_overview_infusion_state_temp_basal)
        3    -> context.getString(R.string.carelevo_overview_infusion_state_imme_bolus)
        5    -> context.getString(R.string.carelevo_overview_infusion_state_extend_bolus)
        else -> ""
    }
}

internal fun convertMinutesToRemainingHours(context: Context, totalMinutes: Int): String {
    val days = totalMinutes / (24 * 60)          // 하루(1440분) 단위
    val remainingAfterDays = totalMinutes % (24 * 60)

    // 올림 처리를 위해 (분 단위가 있으면 +1시간)
    val hours = if (remainingAfterDays % 60 == 0) {
        remainingAfterDays / 60
    } else {
        (remainingAfterDays / 60) + 1
    }

    return if (days > 0) {
        context.getString(R.string.common_unit_day_hour_value, days, hours)
    } else {
        context.getString(R.string.common_unit_hour_value, hours)
    }
}