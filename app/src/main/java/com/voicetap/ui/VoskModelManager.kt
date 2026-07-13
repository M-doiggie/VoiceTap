package com.voicetap.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

object VoskModelManager {

    // Small English model (~40 MB) — adequate for short phrase matching
    private const val MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    suspend fun downloadModel(context: Context, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val dest = File(context.filesDir, "vosk-model")
                if (dest.exists()) return@withContext true

                val tempZip = File(context.cacheDir, "vosk-model.zip")
                val conn = URL(MODEL_URL).openConnection()
                val total = conn.contentLength.toLong()
                var downloaded = 0L

                BufferedInputStream(conn.getInputStream()).use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                        }
                    }
                }

                // Unzip — the zip contains a single top-level directory
                ZipInputStream(tempZip.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val outFile = File(context.filesDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                // Rename extracted directory to expected path
                val extracted = context.filesDir.listFiles()
                    ?.firstOrNull { it.isDirectory && it.name.startsWith("vosk-model-small") }
                extracted?.renameTo(dest)

                tempZip.delete()
                dest.exists()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    fun isModelReady(context: Context): Boolean {
        if (File(context.filesDir, "vosk-model").exists()) return true

        // Dev convenience: if the model was pushed to Downloads, copy it automatically
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val sideloaded = File(downloads, "vosk-model-small-en-us-0.15")
        if (sideloaded.exists()) {
            sideloaded.copyRecursively(File(context.filesDir, "vosk-model"), overwrite = true)
            return File(context.filesDir, "vosk-model").exists()
        }
        return false
    }
}
