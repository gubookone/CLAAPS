package info.nightscout.androidaps.plugins.pump.carelevo.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.carelevo.di.CarelevoPluginQualifier
import javax.inject.Inject

abstract class CarelevoBaseFragment<T : ViewDataBinding>(@LayoutRes val layoutResID : Int) : DaggerFragment() {

    @Inject
    @CarelevoPluginQualifier
    lateinit var viewModelFactory : ViewModelProvider.Factory

    lateinit var binding : T

    protected lateinit var loadingProgress : CarelevoBaseCircleProgress

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, layoutResID, container, false)
        return binding.root
    }

    abstract fun setupView()

    protected fun showFullScreenProgress() {
        if(::loadingProgress.isInitialized && !loadingProgress.isShowing) {
            loadingProgress.show()
        }
    }

    protected fun hideFullScreenProgress() {
        if(::loadingProgress.isInitialized && loadingProgress.isShowing) {
            loadingProgress.dismiss()
        }
    }
}