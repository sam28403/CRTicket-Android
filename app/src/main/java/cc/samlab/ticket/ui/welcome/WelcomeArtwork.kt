package cc.samlab.ticket.ui.welcome

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun WelcomeArtwork() {
    val context = LocalContext.current
    val icon = remember(context) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 384, 384)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }
    val transition = rememberInfiniteTransition(label = "welcome")
    val turn by transition.animateFloat(
        initialValue = -12f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "shape rotation",
    )
    val scale by transition.animateFloat(
        initialValue = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "shape breathing",
    )
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(210.dp).graphicsLayer {
                rotationZ = turn
                scaleX = scale
                scaleY = scale
            }.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(64.dp)),
        )
        Box(
            Modifier.size(174.dp).graphicsLayer { rotationZ = -turn * 2 }
                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(54.dp)),
        )
        Image(icon, null, Modifier.size(112.dp).clip(RoundedCornerShape(30.dp)))
        Image(
            painterResource(R.drawable.picture1), null,
            Modifier.align(Alignment.BottomEnd).padding(12.dp).size(56.dp).clip(CircleShape),
        )
    }
}
