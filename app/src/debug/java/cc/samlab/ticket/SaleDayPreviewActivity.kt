package cc.samlab.ticket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import cc.samlab.ticket.ui.sale.SaleDayCompleteState

/** Isolated debug-only host for the evening state. */
class SaleDayPreviewActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        setContent {
            MaterialExpressiveTheme {
                Surface(Modifier.fillMaxSize()) { SaleDayCompleteState(true) }
            }
        }
    }
}
