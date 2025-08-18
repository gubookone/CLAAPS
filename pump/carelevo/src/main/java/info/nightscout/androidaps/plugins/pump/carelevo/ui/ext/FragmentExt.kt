package info.nightscout.androidaps.plugins.pump.carelevo.ui.ext

import androidx.fragment.app.Fragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.CarelevoConnectDialog
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.CarelevoDiscardConfirmDialog
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.CarelevoInsulinInputDialog
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.CarelevoPumpResumeConfirmDialog
import info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog.CarelevoPumpStopDurationSelectDialog

internal fun Fragment.showDialogInsulinInput(
    insulin : Int,
    positiveCallback : ((value : Int) -> Unit)? = null
) {
    CarelevoInsulinInputDialog
        .getInstance(insulin)
        .apply {
            setPositiveClickListener(positiveCallback)
        }.show(childFragmentManager, CarelevoInsulinInputDialog.TAG_DIALOG)
}

internal fun Fragment.showDialogConnect(
    address : String,
    negativeCallback : (() -> Unit)? = null,
    positiveCallback : (() -> Unit)? = null
) {
    CarelevoConnectDialog
        .getInstance(address)
        .apply {
            setNegativeClickListener(negativeCallback)
            setPositiveClickListener(positiveCallback)
        }.show(childFragmentManager, CarelevoConnectDialog.TAG_DIALOG)
}

internal fun Fragment.showDialogDiscardConfirm(
    positiveCallback: (() -> Unit)?
) {
    CarelevoDiscardConfirmDialog
        .getInstance()
        .apply {
            setPositiveClickListener(positiveCallback)
        }.show(childFragmentManager, CarelevoDiscardConfirmDialog.TAG_DIALOG)
}

internal fun Fragment.showDialogPumpStopDurationSelect(
    positiveCallback: ((duration : Int) -> Unit)?
) {
    CarelevoPumpStopDurationSelectDialog
        .getInstance()
        .apply {
            setPositiveClickListener(positiveCallback)
        }.show(childFragmentManager, CarelevoPumpStopDurationSelectDialog.TAG_DIALOG)
}

internal fun Fragment.showDialogPumpResumeConfirm(
    positiveCallback: (() -> Unit)?
) {
    CarelevoPumpResumeConfirmDialog
        .getInstance()
        .apply {
            setPositiveClickListener(positiveCallback)
        }.show(childFragmentManager, CarelevoPumpResumeConfirmDialog.TAG_DIALOG)
}