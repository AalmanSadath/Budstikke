package dev.aalman.budstikke.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Files handed to Android's MMS service. The app has no internet permission, so it never
 * talks to the carrier itself: the service sends a PDU the app wrote, or writes a downloaded
 * one for the app to read, through a content URI on this app's FileProvider. The URI is
 * granted to the service for that one file and revoked when its result comes back.
 */
internal object MmsFiles {

    /** Holds only these PDUs; nothing else is reachable through the provider's mms/ path. */
    fun dir(context: Context): File = File(context.cacheDir, "mms").apply { mkdirs() }

    fun authority(context: Context): String = "${context.packageName}.files"

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    /** Gives the MMS service access to [uri]: write for a download, read for a send. */
    fun grant(context: Context, uri: Uri, write: Boolean) {
        val flags = if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else Intent.FLAG_GRANT_READ_URI_PERMISSION
        MMS_SERVICE_PACKAGES.forEach { pkg -> runCatching { context.grantUriPermission(pkg, uri, flags) } }
    }

    fun revoke(context: Context, uri: Uri) {
        runCatching {
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    /**
     * Where the MMS service runs. AOSP has its own package for it; some phones run it inside
     * the phone process instead.
     */
    private val MMS_SERVICE_PACKAGES = listOf("com.android.mms.service", "com.android.phone")
}
