package com.example.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class ApkInstallResult {
    LAUNCHED,
    PERMISSION_NEEDED,
    FAILED
}

object GitHubUpdateManager {

    suspend fun checkForUpdate(repoSlug: String): UpdateUiState = withContext(Dispatchers.IO) {
        val cleanedSlug = repoSlug.trim()
            .ifBlank { BuildConfig.GITHUB_REPO_SLUG }
            .removePrefix("https://github.com/")
            .trim('/')

        if (!cleanedSlug.contains('/') || cleanedSlug.equals("owner/ag-video-player", ignoreCase = true)) {
            return@withContext UpdateUiState.Error("GitHub repository not linked in this build.")
        }

        try {
            var releaseObj: JSONObject? = null
            var apiForbiddenOrRateLimited = false

            val (latestCode, latestBody) = httpGet(
                "https://api.github.com/repos/$cleanedSlug/releases/latest",
                accept = "application/vnd.github+json"
            )
            if (latestCode in 200..299 && latestBody != null) {
                releaseObj = JSONObject(latestBody)
            } else if (latestCode == 404) {
                val (listCode, listBody) = httpGet(
                    "https://api.github.com/repos/$cleanedSlug/releases?per_page=10",
                    accept = "application/vnd.github+json"
                )
                if (listCode in 200..299 && listBody != null) {
                    val arr = JSONArray(listBody)
                    for (i in 0 until arr.length()) {
                        val candidate = arr.optJSONObject(i) ?: continue
                        if (!candidate.optBoolean("draft", false) && hasApkAsset(candidate)) {
                            releaseObj = candidate
                            break
                        }
                    }
                } else if (listCode == 403 || listCode == 429) {
                    apiForbiddenOrRateLimited = true
                }
            } else if (latestCode == 403 || latestCode == 429) {
                apiForbiddenOrRateLimited = true
            }

            if (releaseObj != null) {
                val tagName = releaseObj.optString("tag_name", "").trim()
                val remoteVersionName = tagName.removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
                val releaseTitle = releaseObj.optString("name", tagName).trim().ifBlank { tagName }
                val releaseNotes = releaseObj.optString("body", "")
                val htmlUrl = releaseObj.optString("html_url", "https://github.com/$cleanedSlug/releases")
                val remoteVersionCode = parseOptionalVersionCode(releaseNotes)

                if (!isNewerVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, remoteVersionName, remoteVersionCode)) {
                    return@withContext UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                }

                val assets = releaseObj.optJSONArray("assets") ?: JSONArray()
                var apkAsset: JSONObject? = null
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                        apkAsset = a
                        break
                    }
                }

                val downloadUrl = apkAsset?.optString("browser_download_url", "").orEmpty()
                if (downloadUrl.isBlank()) {
                    return@withContext UpdateUiState.Error("No update APK file found in latest release.")
                }

                return@withContext UpdateUiState.Available(
                    GitHubReleaseInfo(
                        versionName = remoteVersionName,
                        versionCode = remoteVersionCode,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = downloadUrl,
                        apkFileName = apkAsset?.optString("name")?.takeIf { it.isNotBlank() } ?: "AG Video Player.apk",
                        apkSizeBytes = apkAsset?.optLong("size", 0L) ?: 0L,
                        htmlUrl = htmlUrl
                    )
                )
            }

            // Fallback without REST API rate limits (handles HTTP 403/429 transparently via GitHub web endpoints)
            val webFallback = checkViaGitHubWeb(cleanedSlug)
            if (webFallback != null) {
                return@withContext webFallback
            }

            if (apiForbiddenOrRateLimited) {
                UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
            } else {
                UpdateUiState.Error("Could not reach update server. Please try again later.")
            }
        } catch (_: Exception) {
            val webFallback = runCatching { checkViaGitHubWeb(cleanedSlug) }.getOrNull()
            webFallback ?: UpdateUiState.Error("Unable to check for updates right now.")
        }
    }

    private fun checkViaGitHubWeb(cleanedSlug: String): UpdateUiState? {
        // 1. Follow redirect on /releases/latest to get exact latest tag without API rate limit
        val latestPageUrl = "https://github.com/$cleanedSlug/releases/latest"
        val (redirectCode, finalUrl, _) = httpGetWithFinalUrl(latestPageUrl)
        var tagName = ""
        if (redirectCode in 200..299 && finalUrl.contains("/releases/tag/")) {
            tagName = finalUrl.substringAfterLast("/releases/tag/").substringBefore('?').trim()
        }

        // 2. Fallback to releases.atom feed if needed
        if (tagName.isBlank()) {
            val (atomCode, atomBody) = httpGet("https://github.com/$cleanedSlug/releases.atom", accept = "application/atom+xml")
            if (atomCode in 200..299 && atomBody != null) {
                val tagMatch = Regex("/releases/tag/([^\"<\\s]+)").find(atomBody)
                tagName = tagMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            }
        }

        if (tagName.isBlank()) return null

        val remoteVersionName = tagName.removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
        if (!isNewerVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, remoteVersionName, null)) {
            return UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
        }

        // 3. Parse direct .apk asset URL from GitHub expanded_assets fragment
        val assetsUrl = "https://github.com/$cleanedSlug/releases/expanded_assets/$tagName"
        val (assetsCode, assetsHtml) = httpGet(assetsUrl, accept = "text/html")
        var apkHref = ""
        var apkName = "AG Video Player.apk"
        if (assetsCode in 200..299 && assetsHtml != null) {
            val apkMatch = Regex("href=\"([^\"]*/releases/download/[^\"]+\\.apk)\"", RegexOption.IGNORE_CASE).find(assetsHtml)
            val matchedPath = apkMatch?.groupValues?.getOrNull(1).orEmpty()
            if (matchedPath.isNotBlank()) {
                apkHref = if (matchedPath.startsWith("http")) matchedPath else "https://github.com$matchedPath"
                apkName = Uri.decode(apkHref.substringAfterLast('/')).ifBlank { "AG Video Player.apk" }
            }
        }

        if (apkHref.isBlank()) {
            apkHref = "https://github.com/$cleanedSlug/releases/download/$tagName/AG.Video.Player.apk"
        }

        return UpdateUiState.Available(
            GitHubReleaseInfo(
                versionName = remoteVersionName,
                versionCode = null,
                releaseTitle = "v$remoteVersionName",
                releaseNotes = "",
                apkDownloadUrl = apkHref,
                apkFileName = apkName,
                apkSizeBytes = 0L,
                htmlUrl = "https://github.com/$cleanedSlug/releases/tag/$tagName"
            )
        )
    }

    fun buildUpdateFileName(release: GitHubReleaseInfo): String {
        val safeVersion = release.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "latest" }
        return "AG-Video-Player-v$safeVersion.apk"
    }

    fun getExpectedApkFile(context: Context, release: GitHubReleaseInfo): File {
        val fileName = buildUpdateFileName(release)
        val appDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return if (appDownloadsDir != null) {
            File(appDownloadsDir, fileName)
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        }
    }

    fun startApkDownload(context: Context, release: GitHubReleaseInfo): Long {
        val rawUrl = release.apkDownloadUrl.trim().replace(" ", "%20")
        if (!rawUrl.startsWith("https://", ignoreCase = true) && !rawUrl.startsWith("http://", ignoreCase = true)) {
            return -1L
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return -1L
        val fileName = buildUpdateFileName(release)
        val appDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        return runCatching {
            val request = DownloadManager.Request(Uri.parse(rawUrl))
                .setTitle("AG Video Player v${release.versionName}")
                .setDescription("Downloading $fileName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            if (appDownloadsDir != null) {
                appDownloadsDir.mkdirs()
                appDownloadsDir.listFiles()?.forEach { existing ->
                    if (existing.isFile && existing.name.endsWith(".apk", ignoreCase = true)) {
                        existing.delete()
                    }
                }
                request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            } else {
                val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                if (publicFile.exists()) publicFile.delete()
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            dm.enqueue(request)
        }.getOrDefault(-1L)
    }

    fun isValidApkFile(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile || apkFile.length() < 500_000L) {
            return false
        }
        val hasZipHeader = runCatching {
            apkFile.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            }
        }.getOrDefault(false)
        if (!hasZipHeader) return false

        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        }.getOrNull()
        return pkgInfo != null && pkgInfo.packageName == context.packageName
    }

    fun installDownloadedApk(context: Context, apkFile: File): ApkInstallResult {
        if (!isValidApkFile(context, apkFile)) {
            return ApkInstallResult.FAILED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return runCatching {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
                ApkInstallResult.PERMISSION_NEEDED
            }.getOrDefault(ApkInstallResult.FAILED)
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ApkInstallResult.LAUNCHED
        }.getOrDefault(ApkInstallResult.FAILED)
    }

    fun isNewerVersion(
        currentVersionName: String,
        currentVersionCode: Int,
        remoteVersionName: String,
        remoteVersionCode: Int?
    ): Boolean {
        if (remoteVersionCode != null) {
            if (remoteVersionCode > currentVersionCode) return true
            if (remoteVersionCode < currentVersionCode) return false
        }
        val currParts = currentVersionName.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val remParts = remoteVersionName.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(currParts.size, remParts.size)
        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val r = remParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseOptionalVersionCode(notes: String): Int? {
        val match = Regex("versionCode\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE).find(notes)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun hasApkAsset(release: JSONObject): Boolean {
        val assets = release.optJSONArray("assets") ?: return false
        for (i in 0 until assets.length()) {
            val name = assets.optJSONObject(i)?.optString("name", "").orEmpty()
            if (name.endsWith(".apk", ignoreCase = true)) return true
        }
        return false
    }

    private fun httpGet(urlString: String, accept: String): Pair<Int, String?> {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AG-Video-Player")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            code to body
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGetWithFinalUrl(urlString: String): Triple<Int, String, String?> {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/html")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AG-Video-Player")
        }
        return try {
            val code = conn.responseCode
            val finalUrl = conn.url.toString()
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            Triple(code, finalUrl, body)
        } finally {
            conn.disconnect()
        }
    }
}
