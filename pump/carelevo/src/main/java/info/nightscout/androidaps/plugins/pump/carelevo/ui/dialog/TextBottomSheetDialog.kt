package info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.DialogTextBottomSheetBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.CarelevoBaseDialog

class TextBottomSheetDialog : CarelevoBaseDialog<DialogTextBottomSheetBinding>(R.layout.dialog_text_bottom_sheet) {

    private var title = ""
    private var content = ""
    private var subContent = ""
    private var primaryButton: Button? = null
    private var secondaryButton: Button? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
    }

    fun setupViews() {
        with(binding) {
            tvTitle.text = title
            tvContent.text = if ((content.contains("<b>") && content.contains("</b>"))
                || content.contains("<font")
                || content.contains("<br>")
            ) {
                HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
            } else {
                content
            }
            tvContent.isVisible = content.isNotBlank()

            tvSubContent.text = subContent
            tvSubContent.isVisible = subContent.isNotBlank()

            initButton(tvPrimaryButton, primaryButton)
            initButton(tvSecondaryButton, secondaryButton)

            space.isVisible = secondaryButton != null
        }
    }

    private fun initButton(view: TextView, button: Button?) {
        if (button == null) {
            view.visibility = View.GONE
            return
        }

        view.apply {
            text = button.text
            button.textColor?.let { setTextColor(it) }
            button.background?.let {
                AppCompatResources.getDrawable(context, it)?.let { bg -> background = bg }
            }
            setOnClickListener {
                button.onClickListener?.invoke()
                dismiss()
            }
            visibility = View.VISIBLE
        }
    }

    data class Button(
        val text: String,
        @ColorInt val textColor: Int? = null,
        @DrawableRes val background: Int? = null,
        val onClickListener: (() -> Unit)? = null
    )

    class Builder {

        private var title = ""
        private var content = ""
        private var subContent = ""
        private var primaryButton: Button? = null
        private var secondaryButton: Button? = null

        fun setTitle(title: String) = apply { this.title = title }
        fun setContent(content: String) = apply { this.content = content }
        fun setSubContent(subContent: String) = apply { this.subContent = subContent }
        fun setPrimaryButton(button: Button) = apply { this.primaryButton = button }
        fun setSecondaryButton(button: Button) = apply { this.secondaryButton = button }

        fun build(): TextBottomSheetDialog {
            return TextBottomSheetDialog().apply {
                this.title = this@Builder.title
                this.content = this@Builder.content
                this.subContent = this@Builder.subContent
                this.primaryButton = this@Builder.primaryButton
                this.secondaryButton = this@Builder.secondaryButton
            }
        }
    }
}