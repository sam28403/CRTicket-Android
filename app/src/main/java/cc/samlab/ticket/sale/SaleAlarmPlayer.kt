package cc.samlab.ticket.sale

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import cc.samlab.ticket.R

/** The ringing lifecycle belongs to the foreground service, including while the screen is off. */
internal class SaleAlarmPlayer(private val context: Context, private val onStop: () -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS) onStop()
        }.build()
    private var player: MediaPlayer? = null
    @Volatile var playing: Boolean = false
        private set

    fun start() {
        audio.requestAudioFocus(focus)
        play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), fallback = false)
    }

    private fun play(uri: Uri, fallback: Boolean) {
        player?.release()
        playing = false
        val value = MediaPlayer()
        player = value
        value.setAudioAttributes(attributes)
        value.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
        value.isLooping = true
        value.setOnPreparedListener {
            if (player === it) {
                it.start()
                playing = true
            }
        }
        value.setOnErrorListener { _, what, extra ->
            Log.w("SaleAlarmPlayer", "Alarm sound failed: $what/$extra")
            if (!fallback) playFallback() else onStop()
            true
        }
        try {
            value.setDataSource(context, uri)
            value.prepareAsync()
        } catch (error: Exception) {
            Log.w("SaleAlarmPlayer", "Cannot load alarm sound", error)
            if (!fallback) playFallback() else onStop()
        }
    }

    private fun playFallback() {
        play(Uri.parse("android.resource://${context.packageName}/${R.raw.sale_alarm}"), fallback = true)
    }

    fun stop() {
        playing = false
        player?.release()
        player = null
        audio.abandonAudioFocusRequest(focus)
    }
}
