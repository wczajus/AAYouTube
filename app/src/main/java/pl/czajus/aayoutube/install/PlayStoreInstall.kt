package pl.czajus.aayoutube.install

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import pl.czajus.aayoutube.R
import java.io.File

private const val PLAY_STORE = "com.android.vending"

/**
 * Android Auto lists projection screens only for apps whose installer is the Play Store. The
 * system package installer takes that name from [Intent.EXTRA_INSTALLER_PACKAGE_NAME] when an APK
 * is opened with [Intent.ACTION_INSTALL_PACKAGE]; this is how Local Video Player ends up
 * "installed by" the Play Store on the owner's phone.
 */
fun Context.isInstalledFromPlayStore(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        packageManager.getInstallSourceInfo(packageName).installingPackageName == PLAY_STORE

/** Copies [apk] to the cache (the APK is ~100 MB, so off the main thread) and opens the installer. */
fun Activity.installAsFromPlayStore(apk: Uri) {
    Toast.makeText(this, R.string.install_preparing, Toast.LENGTH_SHORT).show()
    Thread {
        val copy = File(File(cacheDir, "apk").apply { mkdirs() }, "install.apk")
        val copied = contentResolver.openInputStream(apk)?.use { input ->
            copy.outputStream().use { input.copyTo(it) }
        } != null
        runOnUiThread {
            if (!copied) {
                Toast.makeText(this, R.string.install_failed, Toast.LENGTH_LONG).show()
                return@runOnUiThread
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", copy)
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, PLAY_STORE)
            startActivity(intent)
        }
    }.start()
}
