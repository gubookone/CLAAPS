package info.nightscout.androidaps.plugins.pump.carelevo.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.ActivityCarelevoBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseActivity
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoCommunicationCheckFragment
import info.nightscout.androidaps.plugins.pump.carelevo.ui.fragments.CarelevoPatchConnectionFlowFragment

class CarelevoActivity : CarelevoBaseActivity<ActivityCarelevoBinding>(R.layout.activity_carelevo) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupView()
    }

    private fun setupView() {
        val type = intent.getIntExtra("type", 0)
        when (type) {
            0 -> setFragment(CarelevoPatchConnectionFlowFragment.getInstance())
            1 -> setFragment(CarelevoCommunicationCheckFragment.getInstance())
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach { permission ->
            when {
                permission.value -> {
                    Toast.makeText(this, "permission granted", Toast.LENGTH_SHORT).show()
                }

                shouldShowRequestPermissionRationale(permission.key) -> {
                    Toast.makeText(this, "permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setFragment(fragment: Fragment) = supportFragmentManager.beginTransaction()
        .apply {
            replace(R.id.container_fragment, fragment)
                .addToBackStack(null)
                .commit()
        }
}