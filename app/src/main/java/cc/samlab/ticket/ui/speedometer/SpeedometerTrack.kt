package cc.samlab.ticket.ui.speedometer

import android.content.Intent
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cc.samlab.ticket.R
import cc.samlab.ticket.speedometer.SpeedRecording
import cc.samlab.ticket.speedometer.recordingTime
import cc.samlab.ticket.ui.trainlist.TrainListItem
import cc.samlab.ticket.ui.trainlist.TrainListRow
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun SpeedometerTrack(record: SpeedRecording?) {
    val context = LocalContext.current
    if (record == null) {
        Text(stringResource(R.string.speed_history_empty), Modifier.padding(24.dp))
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(16.dp)) {
            TrainListItem(
                row = TrainListRow(
                    record.train, record.from, record.to, recordingTime(record.elapsed), "",
                    decimal(record.meters / 1000), decimal(record.average),
                    0, record.meters / 1000, record.average, null,
                ),
                vehicle = record.vehicle.takeIf { it.isNotBlank() },
                showStationCount = false,
                enabled = false,
                onClick = {},
            )
        }
        if (record.points.isEmpty()) {
            Text(stringResource(R.string.speed_no_points), Modifier.padding(20.dp))
        } else {
            val error = stringResource(R.string.speed_map_error)
            val html = remember(record.id, error) {
                val segments = record.points.groupBy { it.segment }.values.map { points ->
                    JSONArray(points.map { JSONArray(listOf(it.latitude, it.longitude)) })
                }
                context.assets.open("speedometer/track.html").bufferedReader().use { it.readText() }
                    .replace("__SEGMENTS__", JSONArray(segments).toString())
                    .replace("__MAP_ERROR__", JSONObject.quote(error))
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { WebView(it).apply {
                    @Suppress("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.userAgentString += " CRTicket-Speedometer/${cc.samlab.ticket.BuildConfig.VERSION_NAME}"
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            android.util.Log.w("SpeedometerMap", message.message())
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url ?: return null
                            if (url.host != "appassets.androidplatform.net") return null
                            val resource = when (url.lastPathSegment) {
                                "leaflet.js" -> "application/javascript" to "leaflet.js"
                                "leaflet.css" -> "text/css" to "leaflet.css"
                                else -> return WebResourceResponse("text/plain", "utf-8", null)
                            }
                            return WebResourceResponse(resource.first, "utf-8",
                                context.assets.open("speedometer-lib/${resource.second}"))
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url ?: return true
                            if (url.scheme == "https" && url.host == "www.openstreetmap.org") {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url))
                            }
                            return true
                        }
                    }
                    loadDataWithBaseURL("https://appassets.androidplatform.net/speedometer/",
                        html, "text/html", "utf-8", null)
                } },
                onRelease = { it.stopLoading(); it.destroy() },
            )
        }
    }
}
