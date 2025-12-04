package info.nightscout.androidaps.plugins.pump.carelevo.ui.widget

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.annotation.ColorInt
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

    var valueTextColor: Int
        @ColorInt get() = binding.tvValue.currentTextColor
        set(@ColorInt value) {
            binding.tvValue.setTextColor(value)
        }

    var valueTextSizeSp: Float
        get() = binding.tvValue.textSize / resources.displayMetrics.scaledDensity
        set(value) {
            binding.tvValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
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
                valueTextColor = a.getColor(R.styleable.CarelevoKeyValueRowView_valueTextColor, binding.tvValue.currentTextColor)

                val sizePx = a.getDimension(R.styleable.CarelevoKeyValueRowView_valueTextSize, binding.tvValue.textSize)
                binding.tvValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
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

        setImageValue(null)
    }

    fun setImageValue(drawableRes: Int?) = binding.ivImage.apply {
        val res = drawableRes?.takeIf { it != 0 }
        if (res == null) {
            setImageDrawable(null)
            visibility = GONE
        } else {
            setImageResource(res)
            visibility = VISIBLE
        }
    }

    fun getContentWidthPx(): Int {
        val root = binding.root
        val divider = binding.tvDivide
        val dividerWidth = (divider.width + divider.paddingStart + divider.paddingEnd) / 2

        return (root.width - root.paddingStart - root.paddingEnd - dividerWidth).coerceAtLeast(0)
    }

    fun getKeyPaint(): TextPaint = binding.tvKey.paint

    fun setKeyWidthPx(widthPx: Int) {
        binding.tvKey.layoutParams = binding.tvKey.layoutParams.apply {
            width = widthPx
        }
        binding.tvKey.requestLayout()
    }

}