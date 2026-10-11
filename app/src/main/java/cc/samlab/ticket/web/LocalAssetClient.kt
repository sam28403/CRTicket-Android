package cc.samlab.ticket.web

import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class LocalAssetClient(
    private val assets: AssetManager,
    private val onPageFinished: () -> Unit,
    private val openExternal: (Uri) -> Unit,
    private val toast: (String) -> Unit,
) : WebViewClient() {
    companion object {
        const val ORIGIN = "https://appassets.androidplatform.net"
        private val POLICY = listOf(
            "default-src 'self' data: blob:",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline'",
            "connect-src 'self' blob: data:",
            "frame-src 'none'",
            "object-src 'none'",
            "base-uri 'self'",
        ).joinToString("; ")

        fun isLocal(uri: Uri): Boolean = uri.scheme == "https" &&
            uri.host == "appassets.androidplatform.net" &&
            (uri.port == -1 || uri.port == 443)

        private fun errorResponse(status: Int, reason: String) = WebResourceResponse(
            "text/plain", "UTF-8", status, reason, emptyMap(),
            ByteArrayInputStream(reason.toByteArray()),
        )

        private fun mimeType(path: String) = when (path.substringAfterLast('.').lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json", "geojson" -> "application/json"
            "ttf" -> "font/ttf"
            "woff2" -> "font/woff2"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    override fun onPageFinished(view: WebView, url: String) = onPageFinished()

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse {
        if (!isLocal(request.url)) return errorResponse(403, "Forbidden")
        val path = request.url.path.orEmpty().removePrefix("/").ifEmpty { "index.html" }
        if (path.split('/').any { it == "." || it == ".." } || path.contains('\\')) {
            return errorResponse(403, "Forbidden")
        }
        return try {
            val stream = assets.open("web/$path")
            val cache = if (path.endsWith(".html")) "no-cache"
                else "public, max-age=31536000, immutable"
            WebResourceResponse(
                mimeType(path), "UTF-8", 200, "OK",
                mapOf(
                    "Cache-Control" to cache,
                    "X-Content-Type-Options" to "nosniff",
                    "Content-Security-Policy" to POLICY,
                ),
                stream,
            )
        } catch (_: Exception) {
            errorResponse(404, "Not Found")
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (isLocal(request.url)) return false
        if (request.isForMainFrame && request.url.scheme in setOf("https", "http")) {
            openExternal(request.url)
        }
        return true
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) toast("页面加载失败，可在系统返回后重新打开应用")
    }
}
