package info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.DialogCarelevoConnectBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseDialog

class CarelevoConnectDialog : CarelevoBaseDialog<DialogCarelevoConnectBinding>(R.layout.dialog_carelevo_connect) {

    companion object {
        const val TAG_DIALOG = "dialog_carelevo_connect"
        private const val ADDRESS = "address"

        fun getInstance(address : String) : CarelevoConnectDialog = CarelevoConnectDialog().apply {
            arguments = Bundle().apply {
                putString(ADDRESS, address)
            }
        }
    }

    private var address : String? = null
    private var negativeClickListener : (() -> Unit)? = null
    private var positiveClickListener : (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        address = arguments?.getString(ADDRESS) ?: ""
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupView()
        bindData()
    }

    private fun setupView() {
        with(binding) {
            btnRescan.setOnClickListener {
                negativeClickListener?.invoke()
                dismissAllowingStateLoss()
            }

            btnOk.setOnClickListener {
                positiveClickListener?.invoke()
                dismissAllowingStateLoss()
            }
        }
    }

    fun setNegativeClickListener(listener : (() -> Unit)?) {
        negativeClickListener = listener
    }

    fun setPositiveClickListener(listener : (() -> Unit)?) {
        positiveClickListener = listener
    }

    private fun bindData() {
        with(binding) {
            tvAddress.text = address
        }
    }
}