package com.example.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.NotificationCompat
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
        if (!cleanedSlug.contains('/') || cleanedSlug.equals("owner/ag-video-player", ignoreCase = true)) {
            return@withContext UpdateCheckResult.Error(
                "GitHub repository not linked in this build. Please install the APK from your GitHub Releases once."
            )
        }

        try {
            val apiResult = runCatching {
                fetchReleaseJson("https://api.github.com/repos/$cleanedSlug/releases/latest")
                    ?: fetchLatestFromReleasesList("https://api.github.com/repos/$cleanedSlug/releases?per_page=10")
            }

            val latestJson = apiResult.getOrNull()
            if (latestJson != null) {
                val tagName = latestJson.optString("tag_name", "").trim()
                val remoteVersionName = tagName.removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
                val releaseTitle = latestJson.optString("name", tagName).ifBlank { tagName }
                val releaseNotes = latestJson.optString("body", "No release notes provided.")
                val htmlUrl = latestJson.optString("html_url", "https://github.com/$cleanedSlug/releases")
                val parsedVersionCode = parseOptionalVersionCode(releaseNotes)

                val hasNewerVersion = isNewerVersion(
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    remoteVersionName = remoteVersionName,
                    remoteVersionCode = parsedVersionCode
                )

                if (!hasNewerVersion) {
                    return@withContext UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
                }

                val assets = latestJson.optJSONArray("assets")
                var apkUrl = ""
                var apkName = "Video-Player-$remoteVersionName.apk"
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
                        "Latest release ($tagName) has no .apk file attached yet."
                    )
                }

                return@withContext UpdateCheckResult.UpdateAvailable(
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
            }

            // Fallback to GitHub Web Releases when API returns 403/429 rate-limit or is unreachable
            val apiError = apiResult.exceptionOrNull()
            val webRelease = fetchLatestViaWebFallback(cleanedSlug)
            if (webRelease != null) {
                return@withContext webRelease
            }

            if (apiError != null) {
                return@withContext UpdateCheckResult.Error(
                    apiError.localizedMessage ?: "Failed to check GitHub updates."
                )
            }
            UpdateCheckResult.Error("No published APK release found on GitHub ($cleanedSlug) yet.")
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to check GitHub updates.")
        }
    }

    private fun fetchLatestViaWebFallback(cleanedSlug: String): UpdateCheckResult? {
        return runCatching {
            var currentUrl = "https://github.com/$cleanedSlug/releases/latest"
            var resolvedTag = ""
            for (hop in 0..4) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    useCaches = false
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) VideoPlayer/${BuildConfig.VERSION_NAME}")
                }
                val status = conn.responseCode
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (status in 300..399 && !location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http")) {
                        location
                    } else {
                        "https://github.com$location"
                    }
                    val marker = "/releases/tag/"
                    val idx = currentUrl.indexOf(marker)
                    if (idx >= 0) {
                        resolvedTag = Uri.decode(
                            currentUrl.substring(idx + marker.length)
                                .substringBefore('?')
                                .substringBefore('#')
                                .trim('/')
                        )
                    }
                } else {
                    break
                }
            }

            if (resolvedTag.isBlank()) return@runCatching null

            val remoteVersionName = resolvedTag.removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
            val hasNewerVersion = isNewerVersion(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                remoteVersionName = remoteVersionName,
                remoteVersionCode = null
            )

            if (!hasNewerVersion) {
                return@runCatching UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }

            val expandedAssetsUrl = "https://github.com/$cleanedSlug/releases/expanded_assets/$resolvedTag"
            val assetsHtml = fetchText(expandedAssetsUrl).orEmpty()
            val apkHrefRegex = Regex("""href="([^"]*?/releases/download/[^"]+?\.apk)"""", RegexOption.IGNORE_CASE)
            val relativeHref = apkHrefRegex.find(assetsHtml)?.groupValues?.getOrNull(1)
                ?: return@runCatching UpdateCheckResult.Error(
                    "Latest release ($resolvedTag) has no .apk file attached yet."
                )
            val fullApkUrl = if (relativeHref.startsWith("http")) {
                relativeHref
            } else {
                "https://github.com$relativeHref"
            }
            val apkFileName = fullApkUrl.substringAfterLast('/').substringBefore('?')
                .ifBlank { "Video-Player-$remoteVersionName.apk" }

            UpdateCheckResult.UpdateAvailable(
                GitHubReleaseInfo(
                    versionName = remoteVersionName,
                    versionCode = null,
                    releaseTitle = "Video Player v$remoteVersionName",
                    releaseNotes = "New update available on GitHub Releases.",
                    apkDownloadUrl = fullApkUrl,
                    apkFileName = apkFileName,
                    apkSizeBytes = 0L,
                    htmlUrl = "https://github.com/$cleanedSlug/releases/tag/$resolvedTag"
                )
            )
        }.getOrNull()
    }

    private fun fetchText(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                useCaches = false
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) VideoPlayer/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchReleaseJson(apiUrl: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                useCaches = false
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "VideoPlayer/${BuildConfig.VERSION_NAME}")
            }
            val code = connection.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                throw IllegalStateException("GitHub API returned HTTP $code.")
            }
            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(bodyText)
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchLatestFromReleasesList(apiUrl: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                useCaches = false
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "VideoPlayer/${BuildConfig.VERSION_NAME}")
            }
            val code = connection.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                throw IllegalStateException("GitHub API returned HTTP $code.")
            }
            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            val array = org.json.JSONArray(bodyText)
            for (i in 0 until array.length()) {
                val release = array.optJSONObject(i) ?: continue
                if (release.optBoolean("draft", false)) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    if (asset.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                        return release
                    }
                }
            }
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun downloadApk(release: GitHubReleaseInfo): Flow<ApkDownloadState> = flow {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Remove stale APK files before saving new update
        updatesDir.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }

        val fileName = release.apkFileName.ifBlank { "Video-Player-v${release.versionName}.apk" }
        val targetFile = File(updatesDir, fileName)
        val tempFile = File(updatesDir, "${targetFile.name}.part")
        var connection: HttpURLConnection? = null
        ensureUpdateNotificationChannel()

        try {
            var currentUrl = release.apkDownloadUrl
            var redirectCount = 0
            while (redirectCount < 5) {
                connection?.disconnect()
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "AGVideoPlayer/${BuildConfig.VERSION_NAME}")
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
                cancelProgressNotification()
                emit(ApkDownloadState.Failed("Download failed (HTTP ${activeConn.responseCode})"))
                return@flow
            }

            val totalBytes = activeConn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
            var downloaded = 0L
            var lastTickTime = System.currentTimeMillis()
            var lastTickBytes = 0L
            var currentSpeed = 0L
            var lastNotifiedPercent = -1

            emit(ApkDownloadState.Downloading(0, 0L, totalBytes, 0L))
            postProgressNotification(release.versionName, 0, totalBytes <= 0L)

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
                        if (elapsedMs >= 200L || downloaded == totalBytes) {
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
                            if (percent != lastNotifiedPercent && (percent - lastNotifiedPercent >= 2 || percent == 100)) {
                                lastNotifiedPercent = percent
                                postProgressNotification(release.versionName, percent, totalBytes <= 0L)
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

            exportApkToPublicDownloads(targetFile, fileName)
            postCompletedNotification(release.versionName, targetFile)
            emit(ApkDownloadState.Completed(targetFile))
        } catch (e: Exception) {
            tempFile.delete()
            cancelProgressNotification()
            emit(ApkDownloadState.Failed(e.localizedMessage ?: "Download interrupted."))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun exportApkToPublicDownloads(sourceApk: File, fileName: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceApk.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null) {
                    downloadsDir.mkdirs()
                    val destFile = File(downloadsDir, fileName)
                    sourceApk.copyTo(destFile, overwrite = true)
                }
            }
        }
    }

    private fun ensureUpdateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(UPDATE_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress when downloading Video Player updates"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun postProgressNotification(versionName: String, percent: Int, indeterminate: Boolean) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading Video Player v$versionName")
                .setContentText(if (indeterminate) "Downloading update…" else "$percent% completed")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, percent, indeterminate)
                .build()
            nm.notify(UPDATE_NOTIFICATION_ID, notification)
        }
    }

    private fun postCompletedNotification(versionName: String, apkFile: File) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
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
            val pendingIntent = PendingIntent.getActivity(
                context,
                202,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Video Player v$versionName Ready")
                .setContentText("Saved to Downloads. Tap to install update.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            nm.notify(UPDATE_NOTIFICATION_ID, notification)
        }
    }

    private fun cancelProgressNotification() {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(UPDATE_NOTIFICATION_ID)
        }
    }

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
        private const val UPDATE_CHANNEL_ID = "ag_apk_updates"
        private const val UPDATE_NOTIFICATION_ID = 7001

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
