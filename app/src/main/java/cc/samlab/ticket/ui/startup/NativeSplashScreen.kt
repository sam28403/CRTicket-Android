package cc.samlab.ticket.ui.startup

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.os.Build
import android.view.View
import android.view.animation.PathInterpolator
import cc.samlab.ticket.R

/** Hand the system splash directly to the native page using one shared timeline. */
internal object NativeSplashScreen {
    fun install(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        activity.splashScreen.setOnExitAnimationListener { splash ->
            if (!ValueAnimator.areAnimatorsEnabled()) {
                splash.remove()
                return@setOnExitAnimationListener
            }
            // Animate only the page, keeping its already-resolved window background still.
            val content = activity.findViewById<View>(R.id.expressive_navigation_view)
                ?: activity.findViewById(android.R.id.content)
            val icon = splash.iconView
            content.alpha = 0f
            content.scaleX = 0.975f
            content.scaleY = 0.975f
            val motions = mutableListOf<Animator>(
                ObjectAnimator.ofFloat(content, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, 0.975f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, 0.975f, 1f),
                ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f),
            )
            if (icon != null) {
                motions += ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 0.8f)
                motions += ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 0.8f)
                motions += ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, -12f)
            }
            AnimatorSet().apply {
                playTogether(motions)
                duration = 480L
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        content.alpha = 1f
                        content.scaleX = 1f
                        content.scaleY = 1f
                        splash.remove()
                    }
                })
                start()
            }
        }
    }
}
