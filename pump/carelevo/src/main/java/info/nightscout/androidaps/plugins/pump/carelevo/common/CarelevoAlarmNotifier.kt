package info.nightscout.androidaps.plugins.pump.carelevo.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.ui.activities.CarelevoAlarmActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarelevoAlarmNotifier @Inject constructor(
    private val context: Context,
    private val alarmUseCase: CarelevoAlarmInfoUseCase
) {

    private val disposables = CompositeDisposable()
    private val channelId = "carelevo_alarm_channel"

    private var previousAlarms: List<CarelevoAlarmInfo> = emptyList()

    fun startObserving() {
        createNotificationChannel()
        disposables += alarmUseCase.observeAlarms()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ optionalList ->
                           val alarms = optionalList.orElse(emptyList())
                           Log.d("AlarmObserver", "observeAlarms: ${alarms.size}")
                           alarms.forEach { newAlarm ->
                               Log.d("AlarmObserver", "observeAlarms: $newAlarm")
                               val oldAlarm = previousAlarms.find { it.alarmId == newAlarm.alarmId }

                               if (oldAlarm != null) {
                                   when {
                                       // acknowledged 상태가 false → true 로 바뀐 경우
                                       !oldAlarm.isAcknowledged && newAlarm.isAcknowledged    -> {
                                           cancelNotification(newAlarm.alarmId)
                                       }
                                       // acknowledged 는 그대로인데 occurrenceCount만 증가한 경우
                                       oldAlarm.occurrenceCount != newAlarm.occurrenceCount &&
                                           oldAlarm.isAcknowledged == newAlarm.isAcknowledged -> {
                                           Log.d("AlarmObserver", "occurrenceCount updated: ${newAlarm.occurrenceCount}")
                                           playBeep()
                                       }
                                   }
                               } else {
                                   // 신규 알람 (리스트에 없던 것)
                                   if (!newAlarm.isAcknowledged) {
                                       if (isInForeground) {
                                           showAlarmScreen()
                                       } else {
                                           // 앱이 백그라운드면 알림만 표시
                                           showNotification(newAlarm)
                                       }
                                   }
                               }
                           }

                           previousAlarms = alarms

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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(app.aaps.core.ui.R.drawable.notif_icon) // 적절한 아이콘
            .setContentTitle("케어레보 알람 발생")
            .setContentText("알람 종류: ${alarm.alarmType}, 원인: ${alarm.cause}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // 알람 ID 별로 notificationId 를 다르게 주면 여러 알람이 동시에 표시 가능
        notificationManager.notify(alarm.alarmId.hashCode(), notification)
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