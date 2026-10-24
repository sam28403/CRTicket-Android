package cc.samlab.ticket.ui.maps

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.settings.MapSource
import cc.samlab.ticket.ui.settings.rememberMapSource
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cc.samlab.ticket.web.LocalAssetClient

/** Isolated map surface: no native bridge and no railway queries. */
@Composable
internal fun MapSurface(
    station: String = "",
    segments: String? = null,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onChooseSource: (() -> Unit)? = null,
) {
    val source = rememberMapSource()
    val load = remember(station, segments, source) { MapLoadStatus() }
    key(station, segments, source) {
        AndroidView(
            modifier = modifier.clip(RoundedCornerShape(28.dp)),
            factory = { context ->
                WebView(context).apply {
                    // Keep Chromium's drawing in its own layer beneath the native controls.
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.userAgentString += " CRTicket"
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (message.message() == "CRTicketMap:failed") load.fail()
                            return true
                        }
                    }
                    webViewClient = LocalAssetClient(
                        context.assets,
                        {
                            if (segments != null) evaluateJavascript(
                                "window.crticketTrackSegments=$segments;" +
                                    "window.dispatchEvent(new Event('crticket-track'))",
                                null,
                            )
                        },
                        { uri -> context.startActivity(Intent(Intent.ACTION_VIEW, uri)) },
                        {},
                        allowTransport = source == MapSource.TRANSPORT,
                        onMapError = { post { load.fail() } },
                    )
                }
            },
            update = { view ->
                val url = "${LocalAssetClient.ORIGIN}/index.html#/station-map" +
                    "?station=${Uri.encode(station)}&dark=$dark&source=${source.id}&track=${segments != null}"
                if (view.tag != url) {
                    view.tag = url
                    view.loadUrl(url)
                }
            },
            onRelease = {
                it.stopLoading()
                it.destroy()
            },
        )
    }
    if (load.failed) AlertDialog(
        onDismissRequest = { load.failed = false },
        title = { Text(stringResource(R.string.map_source_failed_title)) },
        text = { Text(stringResource(R.string.map_source_failed_message)) },
        confirmButton = {
            TextButton(onClick = {
                load.failed = false
                onChooseSource?.invoke()
            }) {
                Text(stringResource(if (onChooseSource == null) R.string.map_source_dismiss
                    else R.string.map_source_change))
            }
        },
    )
}

private class MapLoadStatus {
    var failed by mutableStateOf(false)
    private var reported = false

    fun fail() {
        if (reported) return
        reported = true
        failed = true
    }
}
