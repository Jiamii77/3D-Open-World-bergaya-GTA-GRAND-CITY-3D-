package com.example.data

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

data class InstalledApkInfo(
  val versionName: String,
  val versionCode: Int,
  val applicationId: String,
  val buildType: String,
  val sizeBytes: Long,
  val isSourceAvailable: Boolean,
) {
  val formattedSizeMb: String
    get() = if (sizeBytes > 0L) {
      String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
    } else {
      "N/A"
    }
}

object ApkExportManager {
  fun getInstalledApkInfo(context: Context): InstalledApkInfo {
    val sourcePath = context.applicationInfo.publicSourceDir
    val sourceFile = sourcePath?.let { File(it) }
    val exists = sourceFile != null && sourceFile.exists() && sourceFile.canRead()
    val sizeBytes = if (exists) sourceFile!!.length() else 0L
    return InstalledApkInfo(
      versionName = BuildConfig.VERSION_NAME,
      versionCode = BuildConfig.VERSION_CODE,
      applicationId = BuildConfig.APPLICATION_ID,
      buildType = BuildConfig.BUILD_TYPE,
      sizeBytes = sizeBytes,
      isSourceAvailable = exists,
    )
  }

  suspend fun exportInstalledApkToUri(
    context: Context,
    destinationUri: Uri,
  ): Result<Long> = withContext(Dispatchers.IO) {
    try {
      val sourcePath = context.applicationInfo.publicSourceDir
        ?: return@withContext Result.failure(IllegalStateException("Sumber APK tidak ditemukan di perangkat."))
      val sourceFile = File(sourcePath)
      if (!sourceFile.exists() || !sourceFile.canRead()) {
        return@withContext Result.failure(IllegalStateException("File APK terpasang tidak dapat dibaca."))
      }
      var copiedBytes = 0L
      FileInputStream(sourceFile).use { input ->
        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
          copiedBytes = input.copyTo(output, bufferSize = 64 * 1024)
          output.flush()
        } ?: return@withContext Result.failure(IllegalStateException("Gagal membuka tujuan penyimpanan file."))
      }
      Result.success(copiedBytes)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
