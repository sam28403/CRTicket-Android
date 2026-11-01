package cc.samlab.ticket.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cc.samlab.ticket.MainActivity

/** Android Settings delegates its manage-space action to the app's storage page. */
class ManageStorageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(OPEN_STORAGE, true)
        })
        finish()
    }

    companion object {
        internal const val OPEN_STORAGE = "open_storage_management"
    }
}
