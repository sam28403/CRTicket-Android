package cc.samlab.ticket.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R

/** A user action opens installation; the system owns the permission and confirmation screens. */
class UpdateInstallActivity : AppCompatActivity() {
    companion object {
        private const val DOWNLOAD_ID = "download_id"
        private const val PERMISSION_PENDING = "permission_pending"

        internal fun intent(context: Context, id: Long) =
            Intent(context, UpdateInstallActivity::class.java).putExtra(DOWNLOAD_ID, id)
    }

    private var permissionPending = false
    private var restorePermissionCheck = false

    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionPending = false
        restorePermissionCheck = false
        if (isFinishing) return@registerForActivityResult
        if (packageManager.canRequestPackageInstalls()) openInstaller()
        else fail(R.string.update_install_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.UPDATE_ENABLED) {
            finish()
            return
        }
        permissionPending = savedInstanceState?.getBoolean(PERMISSION_PENDING) == true
        restorePermissionCheck = permissionPending
        if (permissionPending) return
        if (packageManager.canRequestPackageInstalls()) {
            openInstaller()
        } else {
            runCatching {
                permissionPending = true
                permission.launch(Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:$packageName".toUri(),
                ))
            }.onFailure { fail(R.string.update_install_failed) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!restorePermissionCheck || isFinishing) return
        restorePermissionCheck = false
        permissionPending = false
        if (packageManager.canRequestPackageInstalls()) openInstaller()
        else fail(R.string.update_install_permission_denied)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PERMISSION_PENDING, permissionPending)
        super.onSaveInstanceState(outState)
    }

    private fun openInstaller() {
        runCatching {
            val id = intent.getLongExtra(DOWNLOAD_ID, -1)
            val manager = requireNotNull(getSystemService(DownloadManager::class.java))
            val uri = requireNotNull(manager.getUriForDownloadedFile(id))
            val install = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val handlers = packageManager.queryIntentActivities(
                install, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY,
            ).map { it.activityInfo }.filter { it.exported }
            val installer = handlers.firstOrNull { it.packageName.contains("packageinstaller", true) }
                ?: handlers.firstOrNull()
            requireNotNull(installer) { "No system APK installer" }
            install.setClassName(installer.packageName, installer.name)
            startActivity(install)
            finish()
        }.onFailure { fail(R.string.update_install_failed) }
    }

    private fun fail(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
