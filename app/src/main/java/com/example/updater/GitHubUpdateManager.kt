package com.example.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class GitHubReleaseInfo(
    val versionName: String,
    val versionCode: Int?,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: GitHubReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : ApkDownloadState
    data class Completed(val apkFile: File) : ApkDownloadState
    data class Failed(val error: String) : ApkDownloadState
}

class GitHubUpdateManager(private val context: Context) {

    suspend fun checkForUpdate(repoSlug: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val cleanedSlug = repoSlug.trim().removePrefix("https://github.com/").trim('/')
        if (!cleanedSlug.contains('/')) {
            return@withContext UpdateCheckResult.Error(
                "Enter a valid GitHub repository in owner/repo format."
            )
        }
        val apiUrl = "https://api.github.com/repos/$cleanedSlug/releases/latest"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "VeloPlay/${BuildConfig.VERSION_NAME}")
            }

            val code = connection.responseCode
            if (code == 404) {
                return@withContext UpdateCheckResult.Error(
                    "No published GitHub release found for $cleanedSlug."
                )
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("GitHub API returned HTTP $code.")
            }

            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(bodyText)
            val tagName = json.optString("tag_name", "").trim()
            val remoteVersionName = tagName.removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
            val releaseTitle = json.optString("name", tagName).ifBlank { tagName }
            val releaseNotes = json.optString("body", "No release notes provided.")
            val htmlUrl = json.optString("html_url", "https://github.com/$cleanedSlug/releases")
            val parsedVersionCode = parseOptionalVersionCode(releaseNotes)

            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            var apkName = "VeloPlay-$remoteVersionName.apk"
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) {
                return@withContext UpdateCheckResult.Error(
                    "Latest release ($tagName) has no .apk asset attached."
                )
            }

            val hasNewerVersion = isNewerVersion(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                remoteVersionName = remoteVersionName,
                remoteVersionCode = parsedVersionCode
            )

            if (hasNewerVersion) {
                UpdateCheckResult.UpdateAvailable(
                    GitHubReleaseInfo(
                        versionName = remoteVersionName,
                        versionCode = parsedVersionCode,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkName,
                        apkSizeBytes = apkSize,
                        htmlUrl = htmlUrl
                    )
                )
            } else {
                UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to check GitHub updates.")
        } finally {
            connection?.disconnect()
        }
    }

    fun downloadApk(release: GitHubReleaseInfo): Flow<ApkDownloadState> = flow {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Remove stale APK files before saving new update
        updatesDir.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }

        val targetFile = File(updatesDir, release.apkFileName.ifBlank { "veloplay-update.apk" })
        val tempFile = File(updatesDir, "${targetFile.name}.part")
        var connection: HttpURLConnection? = null

        try {
            var currentUrl = release.apkDownloadUrl
            var redirectCount = 0
            while (redirectCount < 5) {
                connection?.disconnect()
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "VeloPlay/${BuildConfig.VERSION_NAME}")
                }
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location") ?: break
                    currentUrl = location
                    redirectCount++
                } else {
                    break
                }
            }

            val activeConn = connection ?: throw IllegalStateException("Connection failed")
            if (activeConn.responseCode !in 200..299) {
                emit(ApkDownloadState.Failed("Download failed (HTTP ${activeConn.responseCode})"))
                return@flow
            }

            val totalBytes = activeConn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
            var downloaded = 0L
            var lastTickTime = System.currentTimeMillis()
            var lastTickBytes = 0L
            var currentSpeed = 0L

            emit(ApkDownloadState.Downloading(0, 0L, totalBytes, 0L))

            activeConn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - lastTickTime
                        if (elapsedMs >= 250L || downloaded == totalBytes) {
                            if (elapsedMs > 0L) {
                                currentSpeed = ((downloaded - lastTickBytes) * 1000L) / elapsedMs
                            }
                            lastTickTime = now
                            lastTickBytes = downloaded
                            val percent = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            emit(
                                ApkDownloadState.Downloading(
                                    progressPercent = percent,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = currentSpeed
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            emit(ApkDownloadState.Completed(targetFile))
        } catch (e: Exception) {
            tempFile.delete()
            emit(ApkDownloadState.Failed(e.localizedMessage ?: "Download interrupted."))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownSourcesIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun launchApkInstaller(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    fun openExternalDownloadUrl(apkUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Download APK with Browser or IDM").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    companion object {
        internal fun parseOptionalVersionCode(releaseNotes: String): Int? {
            val regex = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            return regex.find(releaseNotes)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        internal fun isNewerVersion(
            currentVersionName: String,
            currentVersionCode: Int,
            remoteVersionName: String,
            remoteVersionCode: Int?
        ): Boolean {
            if (remoteVersionCode != null && remoteVersionCode > currentVersionCode) {
                return true
            }
            val currentParts = currentVersionName.split('.').mapNotNull {
                it.filter(Char::isDigit).toIntOrNull()
            }
            val remoteParts = remoteVersionName.split('.').mapNotNull {
                it.filter(Char::isDigit).toIntOrNull()
            }
            val maxLen = maxOf(currentParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val curr = currentParts.getOrElse(i) { 0 }
                val rem = remoteParts.getOrElse(i) { 0 }
                if (rem > curr) return true
                if (rem < curr) return false
            }
            return false
        }
    }
}
