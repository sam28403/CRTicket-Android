package cc.samlab.ticket.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.Base64
import cc.samlab.ticket.R
import java.io.ByteArrayOutputStream

internal fun readSettingsFile(context: Context, uri: Uri, maxBytes: Int): ByteArray {
    return context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { context.getString(R.string.settings_file_unreadable) }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= maxBytes) { context.getString(R.string.settings_file_too_large) }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

internal fun decodeSettingsAvatar(context: Context, bytes: ByteArray): Bitmap {
    if (Build.VERSION.SDK_INT >= 28) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) {
                decoder, info, _ ->
            val scale = 1024f / maxOf(info.size.width, info.size.height).coerceAtLeast(1024)
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1024).coerceAtLeast(1)
    }
    return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
        context.getString(R.string.settings_image_unreadable)
    }
}

internal fun cropSettingsAvatar(source: Bitmap, zoom: Float, x: Float, y: Float): String {
    val size = 256
    val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height) * zoom
    val width = source.width * scale
    val height = source.height * scale
    val left = (size - width) / 2 + x * (width - size) / 2
    val top = (size - height) / 2 + y * (height - size) / 2
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(
        source, null, RectF(left, top, left + width, top + height),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
    bitmap.recycle()
    return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
