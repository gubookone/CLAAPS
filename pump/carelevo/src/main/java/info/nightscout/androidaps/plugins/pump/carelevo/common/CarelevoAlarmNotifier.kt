package info.nightscout.androidaps.plugins.pump.carelevo.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.ui.activities.CarelevoAlarmActivity
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.transformNotificationStringResources
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarelevoAlarmNotifier @Inject constructor(
    private val context: Context,
    private val aapsSchedulers: AapsSchedulers,
    private val alarmUseCase: CarelevoAlarmInfoUseCase
) {

    private val disposables = CompositeDisposable()
    private val channelId = "carelevo_alarm_channel"

    fun startObserving() {
        createNotificationChannel()
        disposables += alarmUseCase.observeAlarms()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ optionalList ->
                           val alarms = optionalList.orElse(emptyList())
                           Log.d("AlarmObserver", "observeAlarms: $alarms")
                           if (alarms.isNotEmpty()) {
                               if (isInForeground) {
                                   showAlarmScreen()
                               } else {
                                   alarms.forEach { newAlarm ->
                                       showNotification(newAlarm)
                                   }
                               }
                           }
                       }, { e ->
                           Log.e("AlarmObserver", "observeAlarms error", e)
                       })
    }

    fun stopObserving() {
        disposables.clear()
    }

    private fun showNotification(alarm: CarelevoAlarmInfo) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val (titleRes, descRes, _) = alarm.cause.transformNotificationStringResources()
        val description = buildNotificationDescription(alarm, descRes)

        val contentPendingIntent = createAlarmActivityPendingIntent(alarm)

        val notification = buildNotification(
            title = context.getString(titleRes),
            description = description,
            contentIntent = contentPendingIntent
        )

        notificationManager.notify(alarm.alarmId.hashCode(), notification)
    }

    /** descRes와 alarm 정보로 최종 본문 문자열 생성 */
    private fun buildNotificationDescription(
        alarm: CarelevoAlarmInfo,
        @StringRes descRes: Int?
    ): String {
        if (descRes == null) return ""
        return when (alarm.cause) {
            AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            AlarmCause.ALARM_NOTICE_LOW_INSULIN -> {
                // 인슐린 부족(주의/알림)
                val remain = (alarm.value ?: 0).toString()
                context.getString(descRes, remain)
            }

            AlarmCause.ALARM_NOTICE_PATCH_EXPIRED -> {
                // 패치 사용 기간 알림
                formatPatchExpired(descRes, alarm.value ?: 0)
            }

            AlarmCause.ALARM_NOTICE_BG_CHECK -> {
                // 혈당 체크 알림
                val span = formatBgCheckSpan(alarm.value ?: 0)
                context.getString(descRes, span)
            }

            else -> context.getString(descRes)
        }
    }

    private fun formatPatchExpired(@StringRes descRes: Int, totalHours: Int): String {
        val days = totalHours / 24
        val remainHours = totalHours % 24
        return context.getString(descRes, days, remainHours)
    }

    private fun formatBgCheckSpan(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            context.getString(
                R.string.common_label_unit_value_duration_hour_and_minute,
                hours, minutes
            )
        } else {
            context.getString(
                R.string.common_label_unit_value_minute,
                minutes
            )
        }
    }

    private fun buildNotification(
        title: String,
        description: String,
        contentIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(app.aaps.core.ui.R.drawable.notif_icon)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun createAlarmActivityPendingIntent(alarm: CarelevoAlarmInfo): PendingIntent {
        val requestCode = alarm.alarmId.hashCode()

        val intent = Intent(context, CarelevoAlarmActivity::class.java).apply {
            /*          action = "OPEN_ALARM_${alarm.alarmId}"
                      putExtra("alarm_id", alarm.alarmId)
                      putExtra("alarm_cause", alarm.cause.name)
                      putExtra("alarm_value", alarm.value ?: 0)*/
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val stackBuilder = TaskStackBuilder.create(context).apply {
            addNextIntentWithParentStack(intent)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return stackBuilder.getPendingIntent(requestCode, flags)
            ?: PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    private fun createNotificationChannel() {
        val name = "Carelevo Alarm Channel"
        val descriptionText = "케어레보 패치 알람 알림 채널"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun cancelNotification(alarmId: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId.hashCode())
    }

    private fun playBeep() {
        val player = MediaPlayer.create(context, app.aaps.core.ui.R.raw.error) // res/raw/alarm_sound.mp3
        player.setOnCompletionListener { it.release() }
        player.start()
    }

    fun showAlarmScreen() {
        val intent = Intent(context, CarelevoAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    val isInForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}