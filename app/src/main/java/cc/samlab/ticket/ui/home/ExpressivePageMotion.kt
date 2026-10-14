package cc.samlab.ticket.ui.home

import android.animation.ValueAnimator
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat

/** Animates the native overlay without changing the WebView's navigation history. */
internal class ExpressivePageMotion {
    private var active: View? = null
    val backing: Boolean
        get() = active != null

    fun enter(view: View) {
        reset(view)
        if (!ValueAnimator.areAnimatorsEnabled()) return
        view.alpha = 0f
        view.translationY = 20f * view.resources.displayMetrics.density
        view.scaleX = 0.98f
        view.scaleY = 0.98f
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(260).setInterpolator(DecelerateInterpolator()).start()
    }

    fun start(view: View) {
        reset(view)
        active = view
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height,
                    28f * view.resources.displayMetrics.density)
            }
        }
        view.clipToOutline = true
    }

    fun progress(event: BackEventCompat) {
        val view = active ?: return
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val progress = event.progress.coerceIn(0f, 1f)
        val direction = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        view.scaleX = 1f - 0.1f * progress
        view.scaleY = 1f - 0.1f * progress
        view.translationX = direction * view.width * 0.05f * progress
    }

    fun cancel(onEnd: () -> Unit) {
        val view = active
        active = null
        if (view == null || !ValueAnimator.areAnimatorsEnabled()) {
            view?.let(::reset)
            onEnd()
            return
        }
        view.animate().scaleX(1f).scaleY(1f).translationX(0f)
            .setDuration(200).setInterpolator(DecelerateInterpolator())
            .withEndAction {
                reset(view)
                onEnd()
            }.start()
    }

    fun finish() {
        active?.let(::reset)
        active = null
    }

    private fun reset(view: View) {
        view.animate().cancel()
        view.animate().withEndAction(null)
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.clipToOutline = false
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
    }
}
