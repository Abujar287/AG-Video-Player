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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ApkInstallResult {
    LAUNCHED,
    PERMISSION_NEEDED,
    FAILED
}

object GitHubUpdateManager {

    private val EXTERNAL_DOWNLOADER_PACKAGES = listOf(
        "idm.internet.download.manager.plus" to "1DM+",
        "idm.internet.download.manager" to "1DM",
        "idm.internet.download.manager.adm.lite" to "1DM Lite",
        "com.dv.adm" to "ADM"
    )

    fun getInstalledVersionName(context: Context): String {
        val pkgVersion = runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return (pkgVersion ?: BuildConfig.VERSION_NAME).removePrefix("v").removePrefix("V")
    }

    fun getInstalledVersionCode(context: Context): Int {
        val pkgCode = runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrNull()
        return pkgCode?.takeIf { it > 0 } ?: BuildConfig.VERSION_CODE
    }

    suspend fun checkForUpdate(
        repoSlug: String,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): UpdateUiState = withContext(Dispatchers.IO) {
        val cleanedSlug = repoSlug.trim()
            .ifBlank { BuildConfig.GITHUB_REPO_SLUG }
            .removePrefix("https://github.com/")
            .trim('/')

        if (!cleanedSlug.contains('/') || cleanedSlug.equals("owner/ag-video-player", ignoreCase = true)) {
            return@withContext UpdateUiState.Error("GitHub repository not linked in this build.")
        }

        val cleanCurrentName = currentVersionName.trim().removePrefix("v").removePrefix("V").ifBlank { BuildConfig.VERSION_NAME }

        try {
            val candidates = mutableListOf<GitHubReleaseInfo>()
            var networkSuccess = false

            // 1. Check /releases/latest (non-cached)
            val (latestCode, latestBody) = httpGet(
                "https://api.github.com/repos/$cleanedSlug/releases/latest",
                accept = "application/vnd.github+json"
            )
            if (latestCode in 200..299 && latestBody != null) {
                networkSuccess = true
                parseReleaseJson(JSONObject(latestBody), cleanedSlug)?.let { candidates += it }
            }

            // 2. Also inspect recent releases list to ensure exact highest version/tag is selected
            val (listCode, listBody) = httpGet(
                "https://api.github.com/repos/$cleanedSlug/releases?per_page=30",
                accept = "application/vnd.github+json"
            )
            if (listCode in 200..299 && listBody != null) {
                networkSuccess = true
                val arr = JSONArray(listBody)
                for (i in 0 until arr.length()) {
                    val candidateObj = arr.optJSONObject(i) ?: continue
                    parseReleaseJson(candidateObj, cleanedSlug)?.let { candidates += it }
                }
            }

            val bestApiRelease = candidates.maxWithOrNull { a, b ->
                compareVersions(a.versionName, a.versionCode, b.versionName, b.versionCode)
            }

            if (bestApiRelease != null) {
                if (!isNewerVersion(cleanCurrentName, currentVersionCode, bestApiRelease.versionName, bestApiRelease.versionCode)) {
                    return@withContext UpdateUiState.UpToDate(cleanCurrentName)
                }
                if (bestApiRelease.apkDownloadUrl.isBlank()) {
                    return@withContext UpdateUiState.Error("No update APK file found in latest release.")
                }
                return@withContext UpdateUiState.Available(bestApiRelease)
            }

            if (networkSuccess) {
                return@withContext UpdateUiState.UpToDate(cleanCurrentName)
            }

            // 3. Fallback without REST API rate limits via GitHub web endpoints
            val webFallback = checkViaGitHubWeb(cleanedSlug, cleanCurrentName, currentVersionCode)
            if (webFallback != null) {
                return@withContext webFallback
            }

            UpdateUiState.Error("Could not reach update server. Please try again.")
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            val webFallback = runCatching {
                checkViaGitHubWeb(cleanedSlug, cleanCurrentName, currentVersionCode)
            }.getOrNull()
            webFallback ?: UpdateUiState.Error("Unable to check for updates right now.")
        }
    }

    private fun parseReleaseJson(releaseObj: JSONObject, cleanedSlug: String): GitHubReleaseInfo? {
        if (releaseObj.optBoolean("draft", false)) return null
        val tagName = releaseObj.optString("tag_name", "").trim()
        if (tagName.isBlank()) return null

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
        if (downloadUrl.isBlank()) return null

        val remoteVersionName = tagName.removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
        val releaseTitle = releaseObj.optString("name", tagName).trim().ifBlank { tagName }
        val releaseNotes = releaseObj.optString("body", "")
        val htmlUrl = releaseObj.optString("html_url", "https://github.com/$cleanedSlug/releases/tag/$tagName")
        val remoteVersionCode = parseOptionalVersionCode(releaseNotes)

        return GitHubReleaseInfo(
            versionName = remoteVersionName,
            versionCode = remoteVersionCode,
            releaseTitle = releaseTitle,
            releaseNotes = releaseNotes,
            apkDownloadUrl = downloadUrl,
            apkFileName = apkAsset?.optString("name")?.takeIf { it.isNotBlank() } ?: "AG Video Player.apk",
            apkSizeBytes = apkAsset?.optLong("size", 0L) ?: 0L,
            htmlUrl = htmlUrl
        )
    }

    private fun checkViaGitHubWeb(
        cleanedSlug: String,
        currentVersionName: String,
        currentVersionCode: Int
    ): UpdateUiState? {
        val discoveredTags = linkedSetOf<String>()

        // 1. Follow redirects on /releases/latest to get exact latest tag without API rate limit
        val latestPageUrl = "https://github.com/$cleanedSlug/releases/latest"
        val finalRedirectUrl = resolveRedirectUrl(latestPageUrl)
        if (finalRedirectUrl != null && finalRedirectUrl.contains("/releases/tag/")) {
            val tag = Uri.decode(finalRedirectUrl.substringAfterLast("/releases/tag/").substringBefore('?').trim())
            if (tag.isNotBlank()) discoveredTags += tag
        }

        // 2. Also scan releases.atom and pick the strictly highest semver tag
        val (atomCode, atomBody) = httpGet("https://github.com/$cleanedSlug/releases.atom", accept = "application/atom+xml")
        if (atomCode in 200..299 && atomBody != null) {
            Regex("/releases/tag/([^\"<\\s]+)").findAll(atomBody).forEach { match ->
                val rawTag = Uri.decode(match.groupValues.getOrNull(1)?.trim().orEmpty())
                if (rawTag.isNotBlank()) discoveredTags += rawTag
            }
        }

        if (discoveredTags.isEmpty()) return null

        val bestTag = discoveredTags.maxWithOrNull { t1, t2 ->
            val v1 = t1.removePrefix("v").removePrefix("V")
            val v2 = t2.removePrefix("v").removePrefix("V")
            compareVersions(v1, null, v2, null)
        } ?: return null

        val remoteVersionName = bestTag.removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
        if (!isNewerVersion(currentVersionName, currentVersionCode, remoteVersionName, null)) {
            return UpdateUiState.UpToDate(currentVersionName)
        }

        // 3. Parse direct .apk asset URL from GitHub expanded_assets fragment
        val assetsUrl = "https://github.com/$cleanedSlug/releases/expanded_assets/${Uri.encode(bestTag)}"
        val (assetsCode, assetsHtml) = httpGet(assetsUrl, accept = "text/html")
        var apkHref = ""
        var apkName = "AG Video Player.apk"
        if (assetsCode in 200..299 && assetsHtml != null) {
            val apkMatch = Regex("href=\"([^\"]*/releases/download/[^\"]+\\.apk)\"", RegexOption.IGNORE_CASE).find(assetsHtml)
            val matchedPath = apkMatch?.groupValues?.getOrNull(1)?.replace("&amp;", "&").orEmpty()
            if (matchedPath.isNotBlank()) {
                apkHref = if (matchedPath.startsWith("http")) matchedPath else "https://github.com$matchedPath"
                apkName = Uri.decode(apkHref.substringAfterLast('/')).ifBlank { "AG Video Player.apk" }
            }
        }

        if (apkHref.isBlank()) {
            apkHref = "https://github.com/$cleanedSlug/releases/download/$bestTag/AG.Video.Player.apk"
        }

        val resolvedSize = fetchRemoteFileSize(apkHref)

        return UpdateUiState.Available(
            GitHubReleaseInfo(
                versionName = remoteVersionName,
                versionCode = null,
                releaseTitle = "v$remoteVersionName",
                releaseNotes = "",
                apkDownloadUrl = apkHref,
                apkFileName = apkName,
                apkSizeBytes = resolvedSize,
                htmlUrl = "https://github.com/$cleanedSlug/releases/tag/$bestTag"
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
            File(context.cacheDir, fileName)
        }
    }

    fun clearPreviousUpdateApks(context: Context) {
        runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { existing ->
                if (existing.isFile && existing.name.endsWith(".apk", ignoreCase = true)) {
                    existing.delete()
                }
            }
            context.cacheDir.listFiles()?.forEach { existing ->
                if (existing.isFile && existing.name.endsWith(".apk", ignoreCase = true)) {
                    existing.delete()
                }
            }
        }
    }

    fun startApkDownload(context: Context, release: GitHubReleaseInfo): Long {
        val rawUrl = release.apkDownloadUrl.trim().replace(" ", "%20")
        if (!rawUrl.startsWith("https://", ignoreCase = true) && !rawUrl.startsWith("http://", ignoreCase = true)) {
            return -1L
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return -1L
        val fileName = buildUpdateFileName(release)
        val appDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return -1L

        return runCatching {
            clearPreviousUpdateApks(context)
            appDownloadsDir.mkdirs()

            val request = DownloadManager.Request(Uri.parse(rawUrl))
                .addRequestHeader("User-Agent", "Mozilla/5.0 (Android) AG-Video-Player")
                .setTitle("AG Video Player v${release.versionName}")
                .setDescription("Downloading update package")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

            dm.enqueue(request)
        }.getOrDefault(-1L)
    }

    suspend fun downloadApkDirect(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (bytesDownloaded: Long, bytesTotal: Long, percent: Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val rawUrl = release.apkDownloadUrl.trim().replace(" ", "%20")
        if (!rawUrl.startsWith("https://", ignoreCase = true) && !rawUrl.startsWith("http://", ignoreCase = true)) {
            return@withContext null
        }

        clearPreviousUpdateApks(context)
        val targetFile = getExpectedApkFile(context, release)
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        var currentUrl = rawUrl
        var connection: HttpURLConnection? = null
        try {
            for (hop in 0..6) {
                currentCoroutineContext().ensureActive()
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AG-Video-Player")
                    setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")?.trim().orEmpty()
                    conn.disconnect()
                    if (location.isBlank()) return@withContext null
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }
                connection = conn
                break
            }

            val activeConn = connection ?: return@withContext null
            val code = activeConn.responseCode
            if (code !in 200..299) {
                return@withContext null
            }

            val headerLength = activeConn.contentLengthLongCompat()
            val totalBytes = if (headerLength > 0L) headerLength else release.apkSizeBytes.coerceAtLeast(0L)
            var downloaded = 0L
            var lastEmitMs = 0L

            activeConn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 150L) {
                            lastEmitMs = now
                            val pct = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99) else 0
                            onProgress(downloaded, totalBytes, pct)
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                val finalTotal = maxOf(totalBytes, targetFile.length())
                onProgress(targetFile.length(), finalTotal, 100)
                if (isValidApkFile(context, targetFile)) targetFile else null
            } else {
                null
            }
        } catch (ce: CancellationException) {
            tempFile.delete()
            throw ce
        } catch (_: Exception) {
            tempFile.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun copyContentUriToApkFile(context: Context, contentUriString: String, release: GitHubReleaseInfo): File? {
        return runCatching {
            val uri = Uri.parse(contentUriString)
            val target = getExpectedApkFile(context, release)
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.takeIf { isValidApkFile(context, it) }
        }.getOrNull()
    }

    fun findInstalledExternalDownloader(context: Context): Pair<String, String>? {
        val pm = context.packageManager
        for ((pkg, label) in EXTERNAL_DOWNLOADER_PACKAGES) {
            val installed = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.isSuccess
            if (installed) return pkg to label
        }
        return null
    }

    fun openInExternalDownloader(context: Context, release: GitHubReleaseInfo): Boolean {
        val rawUrl = release.apkDownloadUrl.trim().replace(" ", "%20")
        if (rawUrl.isBlank()) return false
        val installed = findInstalledExternalDownloader(context) ?: return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)).apply {
                setPackage(installed.first)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
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

    fun canInstallPackages(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    fun installDownloadedApk(context: Context, apkFile: File): ApkInstallResult {
        if (!isValidApkFile(context, apkFile)) {
            return ApkInstallResult.FAILED
        }
        if (!canInstallPackages(context)) {
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
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
            } else {
                @Suppress("DEPRECATION")
                val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = uri
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            }
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

    private fun compareVersions(
        nameA: String,
        codeA: Int?,
        nameB: String,
        codeB: Int?
    ): Int {
        if (codeA != null && codeB != null && codeA != codeB) {
            return codeA.compareTo(codeB)
        }
        val partsA = nameA.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val partsB = nameB.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val a = partsA.getOrElse(i) { 0 }
            val b = partsB.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return (codeA ?: 0).compareTo(codeB ?: 0)
    }

    private fun parseOptionalVersionCode(notes: String): Int? {
        val match = Regex("versionCode\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE).find(notes)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun httpGet(urlString: String, accept: String): Pair<Int, String?> {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
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

    private fun resolveRedirectUrl(startUrl: String): String? {
        var current = startUrl
        for (hop in 0..6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AG-Video-Player")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")?.trim().orEmpty()
                    if (loc.isBlank()) return current
                    current = URL(URL(current), loc).toString()
                    continue
                }
                return current
            } finally {
                conn.disconnect()
            }
        }
        return current
    }

    private fun fetchRemoteFileSize(startUrl: String): Long {
        return runCatching {
            var current = startUrl
            for (hop in 0..6) {
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AG-Video-Player")
                }
                try {
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location")?.trim().orEmpty()
                        if (loc.isBlank()) return@runCatching 0L
                        current = URL(URL(current), loc).toString()
                        continue
                    }
                    if (code in 200..299) {
                        val len = conn.contentLengthLongCompat()
                        if (len > 0L) return@runCatching len
                    }
                    return@runCatching 0L
                } finally {
                    conn.disconnect()
                }
            }
            0L
        }.getOrDefault(0L)
    }

    private fun HttpURLConnection.contentLengthLongCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            contentLengthLong
        } else {
            getHeaderField("Content-Length")?.toLongOrNull() ?: contentLength.toLong()
        }
    }
}
