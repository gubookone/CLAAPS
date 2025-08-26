package info.nightscout.androidaps.plugins.pump.carelevo.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.ItemCarelevoRowKeyValueVerticalLineBinding

class CarelevoKeyValueRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ItemCarelevoRowKeyValueVerticalLineBinding =
        ItemCarelevoRowKeyValueVerticalLineBinding.inflate(LayoutInflater.from(context), this, true)

    var keyText: String
        get() = binding.key ?: ""
        set(value) {
            binding.key = value
        }

    var valueText: String?
        get() = binding.value ?: ""
        set(value) {
            binding.value = value ?: ""
        }

    var topLineVisible: Boolean
        get() = binding.topLineVisible ?: false
        set(value) {
            binding.topLineVisible = value
        }

    var bottomLineVisible: Boolean
        get() = binding.bottomLineVisible ?: false
        set(value) {
            binding.bottomLineVisible = value
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.CarelevoKeyValueRowView, defStyleAttr, 0)
            try {
                keyText = a.getString(R.styleable.CarelevoKeyValueRowView_keyText) ?: ""
                valueText = a.getString(R.styleable.CarelevoKeyValueRowView_valueText) ?: ""
                topLineVisible = a.getBoolean(R.styleable.CarelevoKeyValueRowView_topLineVisible, true)
                bottomLineVisible = a.getBoolean(R.styleable.CarelevoKeyValueRowView_bottomLineVisible, true)
            } finally {
                a.recycle()
            }
        } else {
            // 기본값
            keyText = ""
            valueText = ""
            topLineVisible = true
            bottomLineVisible = true
        }
    }
}