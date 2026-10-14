package cc.samlab.ticket.ui.window

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch

/** Observe posture while started. Legacy Web pages retain their full window viewport. */
internal class WindowLayoutObserver(
    activity: ComponentActivity,
    onFolds: (List<FoldingFeature>) -> Unit,
) {
    init {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
                    onFolds(info.displayFeatures.filterIsInstance<FoldingFeature>())
                }
            }
        }
    }
}
