package com.futtv.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String = ""
)

object AppUpdater {

    // ─── Configuración ────────────────────────────────────────────────────────
    // Alojá este JSON en GitHub (raw) o en cualquier servidor.
    // Formato del archivo latest.json:
    // {
    //   "versionCode": 2,
    //   "versionName": "1.1",
    //   "apkUrl": "https://github.com/TU_USUARIO/REPO/releases/download/v1.1/futtv.apk",
    //   "changelog": "Novedades de esta versión"
    // }
    const val UPDATE_CHECK_URL =
        "https://raw.githubusercontent.com/XxZman/futtv/main/latest.json"

    private val json = Json { ignoreUnknownKeys = true }

    // Cliente dedicado para descargas con timeout largo
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    // ─── Chequeo de versión ───────────────────────────────────────────────────
    suspend fun checkForUpdate(context: Context, client: OkHttpClient): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(Request.Builder().url(UPDATE_CHECK_URL).build()).execute()
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val info = json.decodeFromString<UpdateInfo>(body)
                @Suppress("DEPRECATION")
                val current = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                if (info.versionCode > current) info else null
            } catch (_: Exception) {
                null
            }
        }

    // ─── Descarga del APK con progreso ───────────────────────────────────────
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val outDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
        val outFile = File(outDir, "futtv-update.apk")
        val response = downloadClient.newCall(Request.Builder().url(apkUrl).build()).execute()
        val body = response.body ?: throw Exception("Respuesta vacía del servidor")
        val contentLength = body.contentLength()
        outFile.outputStream().use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8_192)
                var downloaded = 0L
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    out.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (contentLength > 0) onProgress(downloaded.toFloat() / contentLength)
                }
            }
        }
        outFile
    }

    // ─── Instalación ─────────────────────────────────────────────────────────
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.updater", apkFile
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        )
    }
}
