package cc.samlab.ticket.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun AboutLogo() {
    val context = LocalContext.current
    val view = LocalView.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var showEgg by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = turns * 360f,
        animationSpec = tween(650),
        label = "about-logo-celebration",
    )
    if (showEgg) AboutEasterEgg { showEgg = false }
    val icon = remember(context) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }
    val interactions = remember { MutableInteractionSource() }
    val motion = rememberLogoMotion(interactions)
    Surface(
        onClick = {
            motion.play()
            val now = SystemClock.elapsedRealtime()
            taps = if (now - lastTap > 1_500L) 1 else taps + 1
            lastTap = now
            val unlocked = taps >= 8
            view.performHapticFeedback(
                if (unlocked) HapticFeedbackConstants.LONG_PRESS
                else HapticFeedbackConstants.KEYBOARD_TAP,
            )
            if (unlocked) {
                taps = 0
                turns += 1
                showEgg = true
            }
        },
        interactionSource = interactions,
        shape = motion.shape,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 12.dp,
        modifier = Modifier.size(224.dp).graphicsLayer { rotationZ = rotation }
            .then(motion.modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap = icon,
                contentDescription = stringResource(R.string.about_icon),
                modifier = Modifier.size(96.dp),
            )
        }
    }
}
