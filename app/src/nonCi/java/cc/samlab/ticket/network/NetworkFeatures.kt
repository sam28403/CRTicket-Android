package cc.samlab.ticket.network

import android.content.Context
import okhttp3.EventListener
import okhttp3.Interceptor

internal object NetworkFeatures {
    const val enabled = true
    fun initialize(context: Context) = Unit
    fun isAllowed(): Boolean = true
    fun requireEnabled() = Unit
    fun blocksRoute(route: String): Boolean = false
    val interceptor = Interceptor { it.proceed(it.request()) }
    val events = EventListener.Factory { EventListener.NONE }
}
