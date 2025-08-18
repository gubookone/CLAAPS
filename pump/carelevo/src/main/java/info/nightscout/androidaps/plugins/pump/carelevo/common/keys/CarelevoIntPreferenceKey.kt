package info.nightscout.androidaps.plugins.pump.carelevo.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey

enum class CarelevoIntPreferenceKey(
    override val key: String,
    override val min: Int,
    override val max: Int,
    override val defaultValue: Int,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : IntPreferenceKey {

    CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS("CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS", min = 24, max = 167, defaultValue = 116, dependency = CarelevoBooleanPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_ENABLED),
    CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS("CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS", min = 20, max = 50, defaultValue = 30, dependency = CarelevoBooleanPreferenceKey.CARELEVO_LOW_RESERVOIR_REMINDER_ENABLED),
}