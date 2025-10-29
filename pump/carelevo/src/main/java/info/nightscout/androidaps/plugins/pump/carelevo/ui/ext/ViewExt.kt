package info.nightscout.androidaps.plugins.pump.carelevo.ui.ext

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.plugins.pump.carelevo.R
import java.util.Locale

internal fun setInfusionStateText(infusionState: Int, context: Context): String {
    Log.d("setInfusionStateText", "setInfusionStateText: $infusionState")
    return when (infusionState) {
        0 -> context.getString(R.string.carelevo_overview_infusion_state_pump_stop)
        1 -> context.getString(R.string.carelevo_overview_infusion_state_basal)
        2 -> context.getString(R.string.carelevo_overview_infusion_state_temp_basal)
        3 -> context.getString(R.string.carelevo_overview_infusion_state_imme_bolus)
        4 -> context.getString(R.string.carelevo_overview_infusion_state_imme_bolus)
        5 -> context.getString(R.string.carelevo_overview_infusion_state_extend_bolus)
        else -> ""
    }
}

internal fun convertMinutesToRemainingHours(context: Context, totalMinutes: Int): String {
    if (totalMinutes == 0) {
        return ""
    }

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

fun convertMinutesToDaysComputeHoursMinutes(context: Context, totalMinutes: Int): String {
    // 1일 = 1440분
    val days = totalMinutes / 1440
    val remainingMinutesAfterDays = totalMinutes % 1440

    // 1시간 = 60분
    val hours = remainingMinutesAfterDays / 60
    val minutes = remainingMinutesAfterDays % 60

    // 결과 문자열로 반환
    return if (days > 0) {
        String.format(
            Locale.getDefault(),
            ContextCompat.getString(context, R.string.common_unit_value_day_hour_min),
            days,
            hours,
            minutes
        )
        //"${days}일 ${hours}:${minutes}"
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
    }
}