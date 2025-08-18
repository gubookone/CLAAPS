package info.nightscout.androidaps.plugins.pump.carelevo.ui.base

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.androidaps.plugins.pump.carelevo.di.CarelevoPluginQualifier
import javax.inject.Inject

abstract class CarelevoBaseActivity<T : ViewDataBinding>(@LayoutRes val layoutID : Int) : TranslatedDaggerAppCompatActivity() {

    @Inject
    @CarelevoPluginQualifier
    lateinit var viewModelFactory : ViewModelProvider.Factory

    lateinit var binding : T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, layoutID)
        binding.lifecycleOwner = this
    }
}