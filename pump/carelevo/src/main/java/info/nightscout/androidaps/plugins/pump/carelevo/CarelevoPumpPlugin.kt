package info.nightscout.androidaps.plugins.pump.carelevo

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.BS
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.Connect
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.DiscoveryService
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.EnableNotifications
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BondingState.Companion.codeToBondingResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isAbnormalBondingFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isDiscoverCleared
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isReInitialized
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeConnected
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeDiscovered
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoObserveReceiver
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParserRegister
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoUpdateBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model.SetBasalProgramRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model.StartTempBasalInfusionRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoCancelExtendBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoCancelImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoFinishImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoStartExtendBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoStartImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.CancelBolusInfusionResponseModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartExtendBolusInfusionRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartImmeBolusInfusionRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.model.StartImmeBolusInfusionResponseModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoRequestPatchInfusionInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoOverviewFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

@Singleton
class CarelevoPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val sp: SP,
    private val uiInteraction: UiInteraction,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val instantiator: Instantiator,
    private val carelevoProtocolParserRegister: CarelevoProtocolParserRegister,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,

    private val updateBasalProgramUseCase: CarelevoUpdateBasalProgramUseCase,
    private val startTempBasalInfusionUseCase: CarelevoStartTempBasalInfusionUseCase,
    private val cancelTempBasalInfusionUseCase: CarelevoCancelTempBasalInfusionUseCase,
    private val startImmeBolusInfusionUseCase: CarelevoStartImmeBolusInfusionUseCase,
    private val startExtendBolusInfusionUseCase: CarelevoStartExtendBolusInfusionUseCase,
    private val cancelImmeBolusInfusionUseCase: CarelevoCancelImmeBolusInfusionUseCase,
    private val cancelExtendBolusInfusionUseCase: CarelevoCancelExtendBolusInfusionUseCase,
    private val finishImmeBolusInfusionUseCase: CarelevoFinishImmeBolusInfusionUseCase,

    private val updateMaxBolusDoseUseCase: CarelevoUpdateMaxBolusDoseUseCase,
    private val updateLowInsulinNoticeAmountUseCase: CarelevoUpdateLowInsulinNoticeAmountUseCase,
    private val deleteUserSettingInfoUseCase: CarelevoDeleteUserSettingInfoUseCase,

    private val requestPatchInfusionInfoUseCase: CarelevoRequestPatchInfusionInfoUseCase
) : PumpPluginBase(
    PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(CarelevoOverviewFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_eopatch2_128)
        .pluginName(R.string.carelevo)
        .shortName(R.string.carelevo_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.carelevo_description),
    ownPreferences = listOf(CarelevoBooleanPreferenceKey::class.java, CarelevoIntPreferenceKey::class.java),
    aapsLogger, rh, preferences, commandQueue
), Pump {

    private val pluginDisposable = CompositeDisposable()

    private var _lastDateTime: Long = 0

    private var _pumpType: PumpType = PumpType.CAREMEDI_CARELEVO
    private val _pumpDescription = PumpDescription().fillFor(_pumpType)
    private var isImmeBolusStop = false

    @Inject @Named("characterTx") lateinit var txUuid: UUID
    private val reconnectDisposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()

        Log.i("plugin_test", "[CarelevoPumpPlugin::onStart] onStart called")

        pluginDisposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { event ->
                if (event.isChanged(DoubleKey.SafetyMaxBolus.key)) {
                    Log.d("plugin_test", "[CarelevoPumpPlugin::get Pref change event] max bolus change")
                    updateMaxBolusDose()
                }
                if (event.isChanged(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key)) {
                    Log.d("plugin_test", "[CarelevoPumpPlugin::get pref change event] patch_expiration")
                    //updateLowInsulinNoticeAmount()
                }
                if (event.isChanged(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key)) {
                    Log.d("plugin_test", "[CarelevoPumpPlugin::get pref change event] low insulin amount change")
                    updateLowInsulinNoticeAmount()
                }
            }

        pluginDisposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                carelevoProtocolParserRegister.registerParser()
                carelevoPatch.initPatch()
                val profile = profileFunction.getProfile()
                carelevoPatch.setProfile(profile)

                val patchState = carelevoPatch.patchState.value?.getOrNull()
                Log.d("plugin_test", "[CarelevoPumpPlugin::onStart] patchState : $patchState")
                if (patchState != PatchState.NotConnectedNotBooting && patchState != PatchState.ConnectedBooted) {
                    startReconnect()
                }
            }

        pluginDisposable.add(
            CarelevoObserveReceiver(
                context = context,
                filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            ).subscribe {
                it.takeIf {
                    it.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED
                }?.run {
                    val bondState = getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val current = CarelevoBleSource.bluetoothState.value?.copy(isBonded = bondState.codeToBondingResult())
                    current?.let { t -> CarelevoBleSource._bluetoothState.onNext(t) }
                }
            }
        )
    }

    override fun onStop() {
        super.onStop()
        Log.d("plugin_test", "[CarelevoPumpPlugin::onStop] onStop called")
        deleteUserSettingInfo()
        pluginDisposable.clear()
        reconnectDisposable.clear()
    }

    private fun updateMaxBolusDose() {
        val maxBolusDose = preferences.get(DoubleKey.SafetyMaxBolus)
        val patchState = carelevoPatch.patchState.value?.getOrNull()
        pluginDisposable += updateMaxBolusDoseUseCase.execute(
            CarelevoUserSettingInfoRequestModel(
                patchState = patchState,
                maxBolusDose = maxBolusDose
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::updateMaxBolusDose] response success")
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::updateMaxBolusDose] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::updateMaxBolusDose] response failed")
                    }
                }
            }
    }

    private fun updateLowInsulinNoticeAmount() {
        val lowInsulinNoticeAmount = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key, 0)
        val patchState = carelevoPatch.patchState.value?.getOrNull()

        Log.d("CarelevoPumpPlugin", "lowInsulinNoticeAmount($lowInsulinNoticeAmount)")
        if (lowInsulinNoticeAmount == 0) {
            return
        }

        pluginDisposable += updateLowInsulinNoticeAmountUseCase.execute(
            CarelevoUserSettingInfoRequestModel(
                patchState = patchState,
                lowInsulinNoticeAmount = lowInsulinNoticeAmount
            )
        )
            .observeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::updateLowInsulinNoticeAmount] response success")
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::updateLowInsulinNoticeAmount] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::updateLowInsulinNoticeAmount] response failed")
                    }
                }
            }
    }

    private fun deleteUserSettingInfo() {
        pluginDisposable += deleteUserSettingInfoUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::deleteUserSettingInfo] response success")
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::deleteUserSettingInfo] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::deleteUserSettingInfo] response failed")
                    }
                }
            }
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val lowReservoirEntries = arrayOf<CharSequence>("20 U", "25 U", "30 U", "35 U", "40 U", "45 U", "50 U")
        val lowReservoirValues = arrayOf<CharSequence>("20", "25", "30", "35", "40", "45", "50")
        val expirationRemindersEntries =
            arrayOf<CharSequence>("1 hr", "2 hr", "3 hr", "4 hr", "5 hr", "6 hr", "7 hr", "8 hr", "9 hr", "10 hr", "11 hr", "12 hr", "13 hr", "14 hr", "15 hr", "16 hr", "17 hr", "18 hr", "19 hr", "20 hr", "21 hr", "22 hr", "23 hr", "24 hr")
        val expirationRemindersValues = arrayOf<CharSequence>("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24")

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "carelevo_beeps"
            title = rh.gs(R.string.carelevo_preferences_category_confirmation_beeps)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveListIntPreference(
                    ctx = context,
                    intKey = CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS,
                    title = R.string.carelevo_low_reservoir_reminders_title,
                    entries = lowReservoirEntries,
                    entryValues = lowReservoirValues
                )
            )
            addPreference(
                AdaptiveIntPreference(
                    ctx = context,
                    intKey = CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS,
                    title = R.string.carelevo_patch_expiration_reminders_title_value,
                    dialogMessage = R.string.carelevo_patch_expiration_reminders_message
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER,
                    title = R.string.carelevo_patch_buzzer_alarm_title
                )
            )
        }

        /*
                val alertsCategory = PreferenceCategory(context)
                parent.addPreference(alertsCategory)
                alertsCategory.apply {
                    key = "omnipod_dash_alerts"
                    title = rh.gs(R.string.carelevo_preferences_category_alerts)
                    initialExpandedChildrenCount = 0

                    addPreference(
                        AdaptiveSwitchPreference(
                            ctx = context,
                            booleanKey = OmnipodBooleanPreferenceKey.ExpirationReminder,
                            title = R.string.carelevo_preferences_expiration_reminder_enabled,
                            summary = R.string.carelevo_preferences_expiration_reminder_enabled_summary
                        )
                    )
                    addPreference(
                        AdaptiveIntPreference(
                            ctx = context,
                            intKey = OmnipodIntPreferenceKey.ExpirationReminderHours,
                            title = R.string.carelevo_preferences_expiration_reminder_hours_before_expiry
                        )
                    )
                    addPreference(
                        AdaptiveSwitchPreference(
                            ctx = context,
                            booleanKey = OmnipodBooleanPreferenceKey.ExpirationAlarm,
                            title = R.string.carelevo_preferences_expiration_alarm_enabled,
                            summary = R.string.carelevo_preferences_expiration_alarm_enabled_summary
                        )
                    )
                    addPreference(
                        AdaptiveIntPreference(
                            ctx = context,
                            intKey = OmnipodIntPreferenceKey.ExpirationAlarmHours,
                            title = R.string.carelevo_preferences_expiration_alarm_hours_before_shutdown
                        )
                    )
                    addPreference(
                        AdaptiveSwitchPreference(
                            ctx = context,
                            booleanKey = OmnipodBooleanPreferenceKey.LowReservoirAlert,
                            title = R.string.carelevo_preferences_low_reservoir_alert_enabled
                        )
                    )
                    addPreference(
                        AdaptiveIntPreference(
                            ctx = context,
                            intKey = OmnipodIntPreferenceKey.LowReservoirAlertUnits,
                            title = R.string.carelevo_preferences_low_reservoir_alert_units
                        )
                    )

                }
                val notificationsCategory = PreferenceCategory(context)
                parent.addPreference(notificationsCategory)
                notificationsCategory.apply {
                    key = "omnipod_dash_notifications"
                    title = rh.gs(R.string.carelevo_preferences_category_notifications)
                    initialExpandedChildrenCount = 0

                    addPreference(
                        AdaptiveSwitchPreference(
                            ctx = context,
                            booleanKey = OmnipodBooleanPreferenceKey.SoundUncertainTbrNotification,
                            title = R.string.carelevo_preferences_notification_uncertain_tbr_sound_enabled
                        )
                    )
                    addPreference(
                        AdaptiveSwitchPreference(
                            ctx = context,
                            booleanKey = OmnipodBooleanPreferenceKey.SoundUncertainSmbNotification,
                            title = R.string.carelevo_preferences_notification_uncertain_smb_sound_enabled
                        )
                    )
                    addPreference(
                        AdaptiveSwitchPreference(
                            ctx = context,
                            booleanKey = OmnipodBooleanPreferenceKey.SoundUncertainBolusNotification,
                            title = R.string.carelevo_preferences_notification_uncertain_bolus_sound_enabled
                        )
                    )
                    addPreference(
                        AdaptiveSwitchPreference(
                            ctx = context,
                            booleanKey = DashBooleanPreferenceKey.SoundDeliverySuspendedNotification,
                            title = app.aaps.pump.omnipod.dash.R.string.carelevo_preferences_notification_delivery_suspended_sound_enabled
                        )
                    )
                }*/
    }

    // 패치가 실제 연결 중 인지 확인
    override fun isInitialized(): Boolean {
        return carelevoPatch.isCarelevoConnected()
    }

    override fun isSuspended(): Boolean {
        val result = carelevoPatch.infusionInfo.value?.getOrNull()?.basalInfusionInfo?.isStop ?: false
        return result
    }

    override fun isBusy(): Boolean {
        return false
    }

    override fun isConnected(): Boolean {
        return if (!carelevoPatch.isCarelevoConnected()) {
            carelevoPatch.isWorking
        } else {
            true
        }
    }

    override fun isConnecting(): Boolean {
        return false
    }

    override fun isHandshakeInProgress(): Boolean {
        return false
    }

    override fun connect(reason: String) {
        Log.d("plugin_test", "[CarelevoPumpPlugin::connect] connect called : $reason")
        _lastDateTime = System.currentTimeMillis()
    }

    override fun disconnect(reason: String) {
        Log.d("plugin_test", "[CarelevoPumpPlugin::disconnect] disconnect called : $reason")
    }

    override fun stopConnecting() {
        Log.d("plugin_test", "[CarelevoPumpPlugin::stopConnecting] stopConnecting called")
    }

    override fun getPumpStatus(reason: String) {
        if (!carelevoPatch.isBluetoothEnabled()) {
            return
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            return
        }

        pluginDisposable += requestPatchInfusionInfoUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::getPumpState] response success")
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::getPumpState] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::getPumpState] response failed")
                    }
                }
            }
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        _lastDateTime = System.currentTimeMillis()
        return when (carelevoPatch.getPatchState()) {
            is PatchState.ConnectedBooted -> {
                startUpdateBasalProgram(profile)
            }

            is PatchState.NotConnectedNotBooting -> {
                carelevoPatch.setProfile(profile)
                uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
                instantiator.providePumpEnactResult().success(true).enacted(true)
            }

            else -> {
                instantiator.providePumpEnactResult()
            }
        }
    }

    private fun startUpdateBasalProgram(profile: Profile): PumpEnactResult {
        val result = instantiator.providePumpEnactResult()
        carelevoPatch.infusionInfo.value?.getOrNull()?.let {
            if (it.extendBolusInfusionInfo != null) {
                val cancelExtendBolusResult = cancelExtendedBolus()
                if (!cancelExtendBolusResult.success) {
                    return result
                }
            }

            if (it.tempBasalInfusionInfo != null) {
                val cancelTempBasalResult = cancelTempBasal(true)
                if (!cancelTempBasalResult.success) {
                    return result
                }
            }
        }

        return updateBasalProgramUseCase.execute(SetBasalProgramRequestModel(profile))
            .observeOn(aapsSchedulers.main)
            .subscribeOn(aapsSchedulers.io)
            .timeout(10000L, TimeUnit.MILLISECONDS)
            .doOnSuccess { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::startUpdateBasal] response success")
                        carelevoPatch.setProfile(profile)
                        uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
                        result.success = true
                        result.enacted = true
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::startUpdateBasal] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::startUpdateBasal] response failed")
                    }
                }
            }.doOnError {
                result.success = false
                result.enacted = false
            }.map {
                result
            }
            .blockingGet()
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        val checkResult = carelevoPatch.checkIsSameProfile(profile)
        Log.d("plugin_test", "[CarelevoPumpPlugin::isThisProfileSet] checkResult : $checkResult")
        return checkResult
    }

    override fun lastDataTime(): Long {
        return _lastDateTime
    }

    override val baseBasalRate: Double
        get() {
            return carelevoPatch.profile.value?.getOrNull()?.let {
                Log.d("plugin_test", "[CarelevoPumpPlugin::baseBasalRate] baseBasalRate : ${it.getBasal()}")
                it.getBasal()
            } ?: 0.0
        }
    override val reservoirLevel: Double
        get() {
            return carelevoPatch.patchInfo.value?.getOrNull()?.insulinRemain ?: 0.0
        }
    override val batteryLevel: Int
        get() = 0

    // start imme bolus infusion
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        val result = instantiator.providePumpEnactResult()
        if (!carelevoPatch.isBluetoothEnabled()) {
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            return result
        }
        isImmeBolusStop = false
        val actionId = (carelevoPatch.patchInfo.value?.getOrNull()?.bolusActionSeq ?: 0) + 1
        return startImmeBolusInfusionUseCase.execute(
            StartImmeBolusInfusionRequestModel(
                actionSeq = actionId,
                volume = detailedBolusInfo.insulin
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnSuccess { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val responseResult = response.data as StartImmeBolusInfusionResponseModel
                        Log.d("plugin_test", "[CarelevoPumpPlugin::deliverTreatment] response success result : $responseResult")
                        _lastDateTime = System.currentTimeMillis()
                        // startImmeBolusTimer(detailedBolusInfo, responseResult.expectSec)
                        val totalSec = responseResult.expectSec
                        val tr = EventOverviewBolusProgress.Treatment(0.0, 0, detailedBolusInfo.bolusType == BS.Type.SMB, detailedBolusInfo.id)
                        (0..totalSec).forEach {
                            if (!isImmeBolusStop) {
                                if (it == totalSec) {
                                    rxBus.send(EventOverviewBolusProgress.apply {
                                        status = rh.gs(app.aaps.core.ui.R.string.bolus_delivered_successfully, detailedBolusInfo.insulin)
                                        percent = 100
                                    })
                                    handleFinishImmeBolus()
                                } else {
                                    // delay(1000)
                                    SystemClock.sleep(1000)
                                    val delivering = (detailedBolusInfo.insulin / totalSec) * it
                                    rxBus.send(EventOverviewBolusProgress.apply {
                                        status = rh.gs(app.aaps.core.ui.R.string.bolus_delivering, delivering)
                                        percent = kotlin.math.min((delivering / detailedBolusInfo.insulin * 100).toInt(), 100)
                                        t = tr
                                    })
                                }
                            } else {
                                return@forEach
                            }
                        }
                        result.success = true
                        result.enacted = true
                        result.bolusDelivered = detailedBolusInfo.insulin
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::deliverTreatment] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarlevoPumpPlugin::deliverTreatment] response failed")
                    }
                }
            }.doOnError {
                result.success = false
                result.enacted = false
                result.bolusDelivered = 0.0
            }.map {
                result
            }.blockingGet()

    }

    private fun handleFinishImmeBolus() {
        pluginDisposable += finishImmeBolusInfusionUseCase.execute()
            .observeOn(aapsSchedulers.main)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::handleFinishImmeBolus] response success")
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::handleFinishImmeBolus] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::handleFinishImmeBolus] response failed")
                    }
                }
            }
    }

    // cancel imme bolus
    override fun stopBolusDelivering() {
        pluginDisposable += cancelImmeBolusInfusionUseCase.execute()
            .observeOn(aapsSchedulers.main)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CancelBolusInfusionResponseModel
                        Log.d("plugin_test", "[CarelevoPumpPlugin::stopBolusDelivering] response success result : $result")
                        rxBus.send(EventOverviewBolusProgress.apply {
                            status = rh.gs(app.aaps.core.ui.R.string.bolus_delivered_successfully, result.infusedAmount.toFloat())
                        })
                        isImmeBolusStop = true
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::stopBolusDelivering] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::stopBolusDelivering] response failed")
                    }
                }
            }
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalAbsolute] setTempBasalAbsolute called")
        val result = instantiator.providePumpEnactResult()
        if (!carelevoPatch.isBluetoothEnabled()) {
            Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalAbsolute] bluetooth is not enabled")
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalAbsolute] carelevo is not connected")
            return result
        }

        return startTempBasalInfusionUseCase.execute(
            StartTempBasalInfusionRequestModel(
                isUnit = true,
                speed = absoluteRate,
                minutes = durationInMinutes
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnSuccess { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalAbsolute] response success")
                        _lastDateTime = System.currentTimeMillis()
                        pumpSync.syncTemporaryBasalWithPumpId(
                            timestamp = dateUtil.now(),
                            rate = absoluteRate,
                            duration = T.mins(durationInMinutes.toLong()).msecs(),
                            isAbsolute = true,
                            type = tbrType,
                            pumpId = dateUtil.now(),
                            pumpType = PumpType.CAREMEDI_CARELEVO,
                            pumpSerial = serialNumber()
                        )
                        result.success = true
                        result.enacted = true
                        result.duration = durationInMinutes
                        result.absolute = absoluteRate
                        result.isPercent = false
                        result.isTempCancel = false
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalAbsolute] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalAbsolute] response failed")
                    }
                }
            }.doOnError {
                result.success = false
                result.enacted = false
            }.map {
                result
            }
            .blockingGet()
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = instantiator.providePumpEnactResult()
        Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalPercent] setTempBasalPercent called")
        if (!carelevoPatch.isBluetoothEnabled()) {
            Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalPercent] bluetooth is not enabled")
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalPercent] carelevo is not connected")
            return result
        }

        return startTempBasalInfusionUseCase.execute(
            StartTempBasalInfusionRequestModel(
                isUnit = false,
                percent = percent,
                minutes = durationInMinutes
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .doOnSuccess { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalPercent] response success")
                        _lastDateTime = System.currentTimeMillis()
                        pumpSync.syncTemporaryBasalWithPumpId(
                            timestamp = dateUtil.now(),
                            rate = percent.toDouble(),
                            duration = T.mins(durationInMinutes.toLong()).msecs(),
                            isAbsolute = false,
                            type = tbrType,
                            pumpId = dateUtil.now(),
                            pumpType = PumpType.CAREMEDI_CARELEVO,
                            pumpSerial = serialNumber()
                        )
                        result.success = true
                        result.enacted = true
                        result.duration = durationInMinutes
                        result.percent = percent
                        result.isPercent = true
                        result.isTempCancel = false
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalPercent] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setTempBasalPercent] response failed")
                    }
                }
            }.doOnError {
                result.success = false
                result.enacted = false
            }.map {
                result
            }.blockingGet()
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val result = instantiator.providePumpEnactResult()
        if (!carelevoPatch.isBluetoothEnabled()) {
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            return result
        }

        return cancelTempBasalInfusionUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .doOnSuccess { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::cancelTempBasal] response success")
                        _lastDateTime = System.currentTimeMillis()
                        pumpSync.syncStopTemporaryBasalWithPumpId(
                            timestamp = dateUtil.now(),
                            endPumpId = dateUtil.now(),
                            pumpType = PumpType.CAREMEDI_CARELEVO,
                            pumpSerial = serialNumber()
                        )
                        result.success = true
                        result.enacted = true
                        result.isTempCancel = true
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::cancelTempBasal] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::cancelTempBasal] response failed")
                    }
                }
            }.doOnError {
                result.success = false
                result.enacted = false
            }.map {
                result
            }
            .blockingGet()
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        val result = instantiator.providePumpEnactResult()
        if (!carelevoPatch.isBluetoothEnabled()) {
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            return result
        }

        return startExtendBolusInfusionUseCase.execute(
            StartExtendBolusInfusionRequestModel(
                volume = insulin,
                minutes = durationInMinutes
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .doOnSuccess { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setExtendedBolus] response success")
                        _lastDateTime = System.currentTimeMillis()
                        pumpSync.syncExtendedBolusWithPumpId(
                            timestamp = dateUtil.now(),
                            amount = insulin,
                            duration = T.mins(durationInMinutes.toLong()).msecs(),
                            isEmulatingTB = false,
                            pumpId = dateUtil.now(),
                            pumpType = PumpType.CAREMEDI_CARELEVO,
                            pumpSerial = serialNumber()
                        )
                        result.success = true
                        result.enacted = true
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setExtendedBolus] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::setExtendedBolus] response failed")
                    }
                }
            }.doOnError {
                result.success = false
                result.enacted = false
            }.map {
                result
            }.blockingGet()
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        val result = instantiator.providePumpEnactResult()
        if (!carelevoPatch.isBluetoothEnabled()) {
            return result
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            return result
        }

        return cancelExtendBolusInfusionUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .doOnSuccess { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::cancelExtendedBolus] response success")
                        _lastDateTime = System.currentTimeMillis()
                        pumpSync.syncStopExtendedBolusWithPumpId(
                            timestamp = dateUtil.now(),
                            endPumpId = dateUtil.now(),
                            pumpType = PumpType.CAREMEDI_CARELEVO,
                            pumpSerial = serialNumber()
                        )
                        result.success = true
                        result.enacted = true
                        result.isTempCancel = true
                    }

                    is ResponseResult.Error   -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::cancelExtendedBolus] response error : ${response.e}")
                    }

                    else                      -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::cancelExtendedBolus] response failed")
                    }
                }
            }.doOnError {
                result.success = false
                result.enacted = false
            }.map {
                result
            }.blockingGet()
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        val now = System.currentTimeMillis()
        val pumpJson = JSONObject()
        val battery = JSONObject()
        val status = JSONObject()
        val extended = JSONObject()
        try {
            battery.put("percent", 100)
            val isPumpStop = carelevoPatch.patchInfo.value?.getOrNull()?.isStopped ?: false
            status.put("status", isPumpStop)
            status.put("timestamp", dateUtil.toISOString(lastDataTime()))
            extended.put("Version", version)
            val tb = pumpSync.expectedPumpState().temporaryBasal
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.convertedToAbsolute(now, profile))
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.timestamp))
                extended.put("TempBasalRemaining", tb.plannedRemainingMinutes)
            }
            val eb = pumpSync.expectedPumpState().extendedBolus
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.rate)
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.timestamp))
                extended.put("ExtendedBolusRemaining", eb.plannedRemainingMinutes)
            }
            extended.put("BaseBasalRate", baseBasalRate)
            try {
                extended.put("ActiveProfile", profileFunction.getProfile())
            } catch (e: Exception) {
                // TODO: log
            }
            pumpJson.put("battery", battery)
            pumpJson.put("status", status)
            pumpJson.put("extended", extended)
            pumpJson.put("reservoir", carelevoPatch.patchInfo.value?.getOrNull()?.insulinRemain ?: 0)
            pumpJson.put("clock", dateUtil.toISOString(now))
        } catch (e: JSONException) {

        }
        return pumpJson
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.Carelevo
    }

    override fun model(): PumpType {
        return PumpType.CAREMEDI_CARELEVO
    }

    override fun serialNumber(): String {
        return carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
    }

    override val pumpDescription: PumpDescription
        get() = _pumpDescription

    override fun shortStatus(veryShort: Boolean): String {
        return ""
    }

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = false

    override fun loadTDDs(): PumpEnactResult {
        return instantiator.providePumpEnactResult()
    }

    override fun canHandleDST(): Boolean {
        return false
    }

    private fun startReconnect() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            return
        }

        val address = carelevoPatch.patchInfo.value?.getOrNull()?.address?.uppercase() ?: return


        reconnectDisposable += bleController.execute(Connect(address))
            .observeOn(aapsSchedulers.io)
            .subscribe { result ->
                when (result) {
                    is CommandResult.Success -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::startReconnect] connect result is success")
                    }

                    else                     -> {
                        Log.d("plugin_test", "[CarelevoPumpPlugin::startReconnect] connect result is failed")
                        stopReconnect()
                    }
                }
            }

        reconnectDisposable += carelevoPatch.btState
            .observeOn(aapsSchedulers.io)
            .timeout(10000L, TimeUnit.MILLISECONDS)
            .doOnError {
                Log.d("plugin_test", "[CarelevoPumpPlugin::startReconnect] reconnect time out : $it")
                stopReconnect()
            }
            .subscribe { btState ->
                btState.getOrNull()?.let { state ->
                    if (state.shouldBeConnected()) {
                        bleController.execute(DiscoveryService(address))
                            .blockingGet()
                            .takeIf { it !is CommandResult.Success }
                            ?.let { stopReconnect() }
                    }

                    if (state.shouldBeDiscovered()) {
                        bleController.execute(EnableNotifications(address, txUuid))
                            .blockingGet()
                            .takeIf { it !is CommandResult.Success }
                            ?.let { stopReconnect() }
                        Thread.sleep(2000)
                        reconnectDisposable.clear()
                        reconnectDisposable.dispose()
                    }
                    if (state.isDiscoverCleared()) {
                        stopReconnect()
                    }
                    if (state.isAbnormalBondingFailed()) {
                        stopReconnect()
                    }
                    if (state.isReInitialized()) {
                        stopReconnect()
                    }

                }
            }
    }

    private fun stopReconnect() {
        reconnectDisposable.clear()
    }
}