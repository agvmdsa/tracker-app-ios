package br.org.cesar.blueguide.feature.fingerprint.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens the share sheet with fingerprint recordings — the counterpart of the iOS app's
 * `ShareLink` ("Exportar todas", or one recording). The files stay in the app's private
 * storage; the receiving app only gets read access to these URIs.
 */
object FingerprintSharing {

    fun share(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        val authority = "${context.packageName}.fileprovider"
        val uris = files.map { FileProvider.getUriForFile(context, authority, it) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            // The grant follows the ClipData, so every file is readable by the chosen app.
            clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar gravações").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
