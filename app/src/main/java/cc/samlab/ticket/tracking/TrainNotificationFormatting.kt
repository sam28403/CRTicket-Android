package cc.samlab.ticket.tracking

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan

object TrainNotificationFormatting {
    // System notification templates retain emphasis but control the actual font sizes.
    fun prominent(text: String): CharSequence = SpannableString(text).apply {
        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
