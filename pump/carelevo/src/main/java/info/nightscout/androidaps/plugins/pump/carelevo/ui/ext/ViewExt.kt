package info.nightscout.androidaps.plugins.pump.carelevo.ui.ext

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.plugins.pump.carelevo.R
import java.util.Locale

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