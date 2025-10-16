package info.nightscout.androidaps.plugins.pump.carelevo.ui.dialog

import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.databinding.DialogCarelevoAlarmBinding
import info.nightscout.androidaps.plugins.pump.carelevo.ui.base.BaseFullScreenDialog

class CarelevoAlarmDialog : BaseFullScreenDialog<DialogCarelevoAlarmBinding>() {

    override val layoutResId: Int = R.layout.dialog_carelevo_alarm

    private var title = ""
    private var content = ""
    private var primaryButton: Button? = null

    override fun init() {

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

            initButton(tvPrimaryButton, primaryButton)
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
        private var primaryButton: Button? = null

        fun setTitle(title: String) = apply { this.title = title }
        fun setContent(content: String) = apply { this.content = content }
        fun setPrimaryButton(button: Button) = apply { this.primaryButton = button }

        fun build(): CarelevoAlarmDialog {
            return CarelevoAlarmDialog().apply {
                this.title = this@Builder.title
                this.content = this@Builder.content
                this.primaryButton = this@Builder.primaryButton
            }
        }
    }
}