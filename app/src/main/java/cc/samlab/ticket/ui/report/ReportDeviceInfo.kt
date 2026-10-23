package cc.samlab.ticket.ui.report

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.webkit.WebView
import cc.samlab.ticket.R

internal fun reportDeviceInfo(context: Context): String {
    val unavailable = context.getString(R.string.status_unavailable)
    val gms = runCatching {
        context.packageManager.getPackageInfo("com.google.android.gms", 0)
    }.getOrNull()
    val webView = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
    return buildString {
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("GMS: ${gms?.displayVersion() ?: unavailable}")
        append("WebView: ${webView?.let { "${it.packageName} ${it.displayVersion()}" } ?: unavailable}")
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.displayVersion(): String {
    val code = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
    return "${versionName.orEmpty()} ($code)".trim()
}
