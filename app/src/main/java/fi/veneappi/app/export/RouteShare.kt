package fi.veneappi.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun Context.routeExportCacheDir(): File {
    val dir = File(cacheDir, "exports")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun Context.exportFileUri(file: File): Uri =
    FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

fun Context.shareStream(uri: Uri, mimeType: String, chooserTitle: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(send, chooserTitle))
}
