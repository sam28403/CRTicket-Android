package cc.samlab.ticket.ui.home

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import cc.samlab.ticket.R

/** Native home content; the WebView router owns navigation and restoration. */
class ExpressiveHomeHost(private val activity: ComponentActivity) {
    var dark by mutableStateOf(false)
    var route by mutableStateOf("")
        private set
    val visible: Boolean
        get() = route.isNotEmpty()
    val isHome: Boolean
        get() = route == "/home"
    private lateinit var homeView: ComposeView
    private lateinit var aboutView: ComposeView
    private val motion = ExpressivePageMotion()
    private var previewHome = false

    val backgroundColor: Int
        get() = homeColorScheme(activity, dark).surfaceContainer.toArgb()

    fun attach(
        container: FrameLayout,
        onNavigate: (String) -> Unit,
        onBack: () -> Unit,
        onExternal: (String) -> Unit,
    ) {
        homeView = ComposeView(activity).apply {
            id = R.id.expressive_home_view
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Box(if (isHome) Modifier else Modifier.clearAndSetSemantics {}) {
                    ExpressiveHomeScreen(dark, isHome, onNavigate)
                }
            }
        }
        aboutView = ComposeView(activity).apply {
            id = R.id.expressive_about_view
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Box(if (route == "/about") Modifier else Modifier.clearAndSetSemantics {}) {
                    ExpressiveAboutScreen(dark, onBack, onExternal)
                }
            }
        }
        container.addView(homeView, FrameLayout.LayoutParams(-1, -1))
        container.addView(aboutView, FrameLayout.LayoutParams(-1, -1))
    }

    fun setPage(page: String) {
        val previous = route
        val completedGesture = motion.backing
        motion.finish()
        route = if (page in setOf("/home", "/about")) page else ""
        if (route == "/about" && previous != route) previewHome = previous == "/home"
        updateVisibility()
        if (previous != route && visible && !completedGesture) {
            motion.enter(if (isHome) homeView else aboutView)
        }
    }

    fun startBack(event: BackEventCompat) {
        if (route != "/about") return
        if (previewHome) homeView.visibility = View.VISIBLE
        motion.start(aboutView)
        motion.progress(event)
    }

    fun progressBack(event: BackEventCompat) = motion.progress(event)

    fun cancelBack() = motion.cancel(::updateVisibility)

    private fun updateVisibility() {
        homeView.visibility = if (isHome) View.VISIBLE else View.GONE
        aboutView.visibility = if (route == "/about") View.VISIBLE else View.GONE
    }
}
