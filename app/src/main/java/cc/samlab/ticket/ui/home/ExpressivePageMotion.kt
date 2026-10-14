package cc.samlab.ticket.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import androidx.activity.BackEventCompat

/** Settings-style transitions for the native pages, with a live destination underneath. */
internal class ExpressivePageMotion {
    private var animator: ValueAnimator? = null
    private var front: View? = null
    private var behind: View? = null
    private var previousForeground: Drawable? = null
    private var scrim: ColorDrawable? = null
    private var cornerRadius = 0f
    private var startY = 0f
    private var direction = 1f
    var returning = false
        private set

    fun enter(view: View, destination: View? = null, reverse: Boolean = false, onEnd: () -> Unit) {
        prepare(view, destination)
        val offset = (if (reverse) -1f else 1f) * 48f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationX = offset
        animate(300, { fraction ->
            view.alpha = fraction
            view.translationX = offset * (1f - fraction)
            setDestination(1f - 0.04f * fraction, 0.14f * fraction)
        }) {
            finish()
            onEnd()
        }
    }

    fun start(view: View, destination: View?, event: BackEventCompat) {
        prepare(view, destination)
        returning = true
        startY = event.touchY
        direction = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        roundFront(view)
        setDestination(0.96f, 0.14f)
        progress(event)
    }

    fun progress(event: BackEventCompat) {
        val view = front ?: return
        if (!returning || !ValueAnimator.areAnimatorsEnabled()) return
        val fraction = event.progress.coerceIn(0f, 1f)
        view.scaleX = 1f - 0.1f * fraction
        view.scaleY = view.scaleX
        view.translationX = direction * view.width * 0.02f * fraction
        view.translationY = ((event.touchY - startY) * 0.08f)
            .coerceIn(-view.height * 0.025f, view.height * 0.025f) * fraction
        cornerRadius = 28f * view.resources.displayMetrics.density * fraction
        view.invalidateOutline()
        setDestination(0.96f + 0.02f * fraction, 0.14f - 0.04f * fraction)
    }

    fun commit(view: View, destination: View?, onEnd: () -> Unit) {
        if (front !== view || !returning) {
            prepare(view, destination)
            returning = true
            direction = 1f
            roundFront(view)
            setDestination(0.96f, 0.14f)
        }
        val pose = PagePose.capture(view)
        val destinationScale = behind?.scaleX ?: 1f
        val shade = scrim?.alpha ?: 0
        animate(240, { fraction ->
            pose.applyTo(
                view, fraction, scale = 0.9f,
                x = pose.x + direction * 32f * view.resources.displayMetrics.density,
                y = 0f, alpha = 0f,
            )
            cornerRadius = 28f * view.resources.displayMetrics.density
            view.invalidateOutline()
            setDestination(
                destinationScale + (1f - destinationScale) * fraction,
                shade / 255f * (1f - fraction),
            )
        }, onEnd)
        // Keep the destination visible until the WebView confirms the route change.
    }

    fun cancel(onEnd: () -> Unit) {
        val view = front
        if (view == null || !returning) {
            finish()
            onEnd()
            return
        }
        val pose = PagePose.capture(view)
        val radius = cornerRadius
        animate(260, { fraction ->
            pose.applyTo(view, fraction)
            cornerRadius = radius * (1f - fraction)
            view.invalidateOutline()
        }) {
            finish()
            onEnd()
        }
    }

    fun finish() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        front?.let(::resetPagePose)
        behind?.let {
            resetPagePose(it)
            it.foreground = previousForeground
        }
        front = null
        behind = null
        scrim = null
        previousForeground = null
        returning = false
    }

    private fun prepare(view: View, destination: View?) {
        finish()
        front = view
        behind = destination
        view.visibility = View.VISIBLE
        destination?.let {
            it.visibility = View.VISIBLE
            previousForeground = it.foreground
            scrim = ColorDrawable(Color.BLACK).apply { alpha = 0 }
            it.foreground = previousForeground?.let { original ->
                LayerDrawable(arrayOf(original, checkNotNull(scrim)))
            } ?: scrim
        }
    }

    private fun setDestination(scale: Float, shade: Float) {
        behind?.scaleX = scale
        behind?.scaleY = scale
        scrim?.alpha = (shade * 255).toInt().coerceIn(0, 255)
    }

    private fun roundFront(view: View) {
        cornerRadius = 0f
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        view.clipToOutline = true
    }

    private fun animate(duration: Long, update: (Float) -> Unit, onEnd: () -> Unit) {
        animator?.removeAllListeners()
        animator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            update(1f)
            onEnd()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { update(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }
}
