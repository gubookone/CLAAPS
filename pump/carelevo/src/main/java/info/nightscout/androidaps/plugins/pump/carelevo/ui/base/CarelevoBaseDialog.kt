package info.nightscout.androidaps.plugins.pump.carelevo.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

abstract class CarelevoBaseDialog<B : ViewDataBinding>(@LayoutRes val layoutID : Int) : CarelevoTopRoundBottomSheetDialog() {

    private var _binding : B? = null
    val binding : B get() = _binding ?: throw NullPointerException("binding instance must be not null")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DataBindingUtil.inflate(inflater, layoutID, container, false)
        return binding.root
    }
}