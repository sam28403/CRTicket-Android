package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun VehicleNumberBadge(number: String, onClick: () -> Unit) {
    val tone = vehicleSeriesTone(number)
    val background = when (tone) {
        VehicleSeriesTone.RED -> Color(0xFFC62828)
        VehicleSeriesTone.ORANGE -> Color(0xFFB84D00)
        VehicleSeriesTone.OLIVE -> Color(0xFF6B732C)
        VehicleSeriesTone.GREEN -> Color(0xFF2E7D32)
        VehicleSeriesTone.BLACK -> Color(0xFF212121)
        VehicleSeriesTone.BLUE -> Color(0xFF1565C0)
        VehicleSeriesTone.PURPLE -> Color(0xFF6A1B9A)
        VehicleSeriesTone.SKY_BLUE -> Color(0xFF81D4FA)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = if (tone == VehicleSeriesTone.SKY_BLUE) Color(0xFF00344A) else Color.White,
        modifier = Modifier.testTag("vehicle-number:$number"),
    ) {
        Text(
            number,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
