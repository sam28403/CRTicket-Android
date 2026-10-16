package cc.samlab.ticket.ui.station

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cc.samlab.ticket.web.LocalAssetClient

/** Isolated map surface: no native bridge and no railway queries. */
@Composable
internal fun StationBoardMap(station: String, dark: Boolean, modifier: Modifier = Modifier) {
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
                webViewClient = LocalAssetClient(
                    context.assets,
                    {},
                    { uri -> context.startActivity(Intent(Intent.ACTION_VIEW, uri)) },
                    {},
                )
            }
        },
        update = { view ->
            val url = "${LocalAssetClient.ORIGIN}/index.html#/station-map?station=${Uri.encode(station)}&dark=$dark"
            if (view.tag != url) {
                view.tag = url
                view.loadUrl(url)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
