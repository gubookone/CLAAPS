package info.nightscout.androidaps.plugins.pump.carelevo.ui.activities

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.androidaps.plugins.pump.carelevo.di.CarelevoPluginQualifier
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.CarelevoAlarmDialog
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.repeatOnStarted
import info.nightscout.androidaps.plugins.pump.carelevo.ui.ext.transformStringResources
import info.nightscout.androidaps.plugins.pump.carelevo.ui.model.AlarmEvent
import info.nightscout.androidaps.plugins.pump.carelevo.ui.viewModel.CarelevoAlarmViewModel
import javax.inject.Inject

class CarelevoAlarmActivity : TranslatedDaggerAppCompatActivity() {

    @Inject
    @CarelevoPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val viewModel: CarelevoAlarmViewModel by viewModels { viewModelFactory }

    private val requestBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.alarmInfo?.let {
                viewModel.triggerEvent(AlarmEvent.ClearAlarm(it))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 알람 불러오기
        viewModel.loadUnacknowledgedAlarms()
        setupObserver()

    }

    private fun setupObserver() {
        repeatOnStarted {
            viewModel.alarmQueue.collect { alarms ->
                if (alarms.isNotEmpty()) {
                    showAlarmDialog(alarms.first())
                }
            }
        }

        repeatOnStarted {
            viewModel.alarmQueueEmptyEvent.collect {
                finish()
            }
        }

        repeatOnStarted {
            viewModel.event.collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: AlarmEvent) {
        when (event) {
            AlarmEvent.RequestBluetoothEnable -> handleRequestBluetoothEnable()
            else                              -> Unit
        }
    }

    private fun handleRequestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        requestBtLauncher.launch(enableBtIntent)
    }

    private fun showAlarmDialog(alarm: CarelevoAlarmInfo) {
        val (titleRes, descRes, btnRes) = alarm.cause.transformStringResources()

        val descArgs: List<String>? = when (alarm.cause) {
            AlarmCause.ALARM_ALERT_OUT_OF_INSULIN -> {
                //인슐린 부족 주의
                listOf(
                    (alarm.value ?: 0).toString()
                )
            }

            else                                  -> emptyList()
        }

        val desc = descRes?.let {
            if (descArgs.isNullOrEmpty()) {
                getString(descRes)
            } else {
                getString(descRes, *descArgs.toTypedArray())
            }
        } ?: ""

        CarelevoAlarmDialog.Builder().setTitle(
            "${getString(titleRes)}(${alarm.createdAt})"
        ).setContent(
            desc
        ).setPrimaryButton(
            CarelevoAlarmDialog.Button(
                text = getString(btnRes),
                onClickListener = {
                    viewModel.triggerEvent(AlarmEvent.ClearAlarm(info = alarm))
                }
            )).build().show(supportFragmentManager, "")
    }
}