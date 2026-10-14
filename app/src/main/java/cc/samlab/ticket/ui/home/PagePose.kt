package cc.samlab.ticket.ui.home

import android.view.View
import android.view.ViewOutlineProvider

/** Snapshot the finger's last position so committing and cancelling never jump. */
internal data class PagePose(
    val scaleX: Float,
    val scaleY: Float,
    val x: Float,
    val y: Float,
    val alpha: Float,
) {
    fun applyTo(
        view: View,
        fraction: Float,
        scale: Float = 1f,
        x: Float = 0f,
        y: Float = 0f,
        alpha: Float = 1f,
    ) {
        view.scaleX = scaleX + (scale - scaleX) * fraction
        view.scaleY = scaleY + (scale - scaleY) * fraction
        view.translationX = this.x + (x - this.x) * fraction
        view.translationY = this.y + (y - this.y) * fraction
        view.alpha = this.alpha + (alpha - this.alpha) * fraction
    }

    companion object {
        fun capture(view: View) = PagePose(
            view.scaleX, view.scaleY, view.translationX, view.translationY, view.alpha,
        )
    }
}

internal fun resetPagePose(view: View) {
    view.alpha = 1f
    view.translationX = 0f
    view.translationY = 0f
    view.scaleX = 1f
    view.scaleY = 1f
    view.clipToOutline = false
    view.outlineProvider = ViewOutlineProvider.BACKGROUND
}
