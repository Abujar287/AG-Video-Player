package com.example.updater

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.util.zip.ZipFile

enum class ApkInstallResult {
    LAUNCHED,
    PERMISSION_NEEDED,
    FAILED
}

data class DownloadedApkTarget(
    val file: File?,
    val contentUri: Uri?
) {
    fun primaryIdentifier(): String = file?.absolutePath ?: contentUri?.toString().orEmpty()
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

    fun getPublicDownloadsDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun getExpectedApkFile(release: GitHubReleaseInfo): File {
        return File(getPublicDownloadsDir(), buildUpdateFileName(release))
    }

    private fun hasMediaStoreDownloadEntry(context: Context, fileName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    private fun resolveWritablePublicFileName(context: Context, baseFileName: String): String {
        val publicDir = getPublicDownloadsDir()
        val stem = baseFileName.removeSuffix(".apk")
        for (index in 1..20) {
            val candidate = if (index == 1) baseFileName else "$stem-$index.apk"
            tryRemoveOwnedPublicDownload(context, candidate)
            val existingFile = runCatching { File(publicDir, candidate).exists() }.getOrDefault(false)
            val existingMediaStore = hasMediaStoreDownloadEntry(context, candidate)
            if (!existingFile && !existingMediaStore) {
                return candidate
            }
        }
        return "$stem-${System.currentTimeMillis() % 100000}.apk"
    }

    private fun tryRemoveOwnedPublicDownload(context: Context, fileName: String) {
        runCatching {
            val publicFile = File(getPublicDownloadsDir(), fileName)
            if (publicFile.exists()) {
                publicFile.delete()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(fileName)
                )
            }
        }
    }

    fun clearLegacyPrivateUpdateApks(context: Context) {
        runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { existing ->
                if (existing.isFile && (existing.name.endsWith(".apk", ignoreCase = true) || existing.name.endsWith(".part", ignoreCase = true))) {
                    existing.delete()
                }
            }
            context.cacheDir.listFiles()?.forEach { existing ->
                if (existing.isFile && (existing.name.startsWith("AG-Video-Player-") || existing.name.startsWith(".ag_update"))) {
                    existing.delete()
                }
            }
        }
    }

    fun scanDownloadedFile(context: Context, file: File?) {
        if (file == null || !file.exists()) return
        runCatching {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                arrayOf("application/vnd.android.package-archive"),
                null
            )
        }
    }

    fun queryPublicDownloadByName(context: Context, fileName: String): DownloadedApkTarget? {
        if (fileName.isBlank()) return null
        val directFile = runCatching {
            File(getPublicDownloadsDir(), fileName).takeIf { it.exists() && it.isFile && it.length() > 0L }
        }.getOrNull()

        val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    } else null
                }
            }.getOrNull()
        } else null

        if (directFile == null && mediaUri == null) return null
        return DownloadedApkTarget(file = directFile, contentUri = mediaUri)
    }

    private fun resolveFinalDownloadUrlBlocking(startUrl: String): String {
        return runCatching {
            var current = startUrl
            for (hop in 0..6) {
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AG-Video-Player")
                }
                try {
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location")?.trim().orEmpty()
                        if (loc.isBlank()) break
                        val nextUrl = URL(URL(current), loc).toString()
                        if (nextUrl.startsWith("https://", ignoreCase = true) || nextUrl.startsWith("http://", ignoreCase = true)) {
                            current = nextUrl
                            continue
                        }
                    }
                    break
                } finally {
                    conn.disconnect()
                }
            }
            current
        }.getOrDefault(startUrl)
    }

    suspend fun startApkDownloadAsync(context: Context, release: GitHubReleaseInfo): Pair<Long, String> =
        withContext(Dispatchers.IO) {
            startApkDownloadInternal(context, release, resolveRedirects = true)
        }

    fun startApkDownload(context: Context, release: GitHubReleaseInfo): Pair<Long, String> {
        return startApkDownloadInternal(context, release, resolveRedirects = false)
    }

    private fun startApkDownloadInternal(
        context: Context,
        release: GitHubReleaseInfo,
        resolveRedirects: Boolean
    ): Pair<Long, String> {
        val rawUrl = release.apkDownloadUrl.trim().replace(" ", "%20")
        if (!rawUrl.startsWith("https://", ignoreCase = true) && !rawUrl.startsWith("http://", ignoreCase = true)) {
            return -1L to ""
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return -1L to ""
        clearLegacyPrivateUpdateApks(context)
        val primaryFileName = resolveWritablePublicFileName(context, buildUpdateFileName(release))
        val targetUrl = if (resolveRedirects) resolveFinalDownloadUrlBlocking(rawUrl) else rawUrl

        val urlsToTry = buildList {
            add(targetUrl)
            if (targetUrl != rawUrl) add(rawUrl)
        }
        val namesToTry = listOf(
            primaryFileName,
            "${buildUpdateFileName(release).removeSuffix(".apk")}-${System.currentTimeMillis() % 100000}.apk"
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            runCatching {
                val publicDir = getPublicDownloadsDir()
                if (!publicDir.exists()) publicDir.mkdirs()
            }
        }

        for (candidateName in namesToTry) {
            for (candidateUrl in urlsToTry) {
                val id = enqueueSystemDownload(dm, candidateUrl, candidateName)
                if (id > 0L) {
                    return id to candidateName
                }
            }
        }

        return -1L to primaryFileName
    }

    private fun enqueueSystemDownload(
        dm: DownloadManager,
        downloadUrl: String,
        fileName: String
    ): Long {
        return runCatching {
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle(fileName)
                .setDescription("Downloading to Download/$fileName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                request.setVisibleInDownloadsUi(true)
            }

            dm.enqueue(request)
        }.getOrDefault(-1L)
    }

    suspend fun downloadApkDirect(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (bytesDownloaded: Long, bytesTotal: Long, percent: Int) -> Unit
    ): DownloadedApkTarget? = withContext(Dispatchers.IO) {
        val rawUrl = release.apkDownloadUrl.trim().replace(" ", "%20")
        if (!rawUrl.startsWith("https://", ignoreCase = true) && !rawUrl.startsWith("http://", ignoreCase = true)) {
            return@withContext null
        }

        clearLegacyPrivateUpdateApks(context)
        val fileName = resolveWritablePublicFileName(context, buildUpdateFileName(release))

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
            if (activeConn.responseCode !in 200..299) {
                return@withContext null
            }

            val headerLength = activeConn.contentLengthLongCompat()
            val totalBytes = if (headerLength > 0L) headerLength else release.apkSizeBytes.coerceAtLeast(0L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadViaMediaStoreDownloads(context, activeConn, fileName, totalBytes, onProgress)
            } else {
                downloadViaLegacyPublicDownloads(context, activeConn, fileName, totalBytes, onProgress)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun downloadViaMediaStoreDownloads(
        context: Context,
        conn: HttpURLConnection,
        fileName: String,
        totalBytes: Long,
        onProgress: (Long, Long, Int) -> Unit
    ): DownloadedApkTarget? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val itemUri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null

        try {
            var downloaded = 0L
            var lastEmitMs = 0L
            val outputStream = resolver.openOutputStream(itemUri, "w") ?: run {
                resolver.delete(itemUri, null, null)
                return null
            }

            conn.inputStream.use { input ->
                outputStream.use { output ->
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

            if (downloaded < 500_000L) {
                resolver.delete(itemUri, null, null)
                return null
            }

            val publishValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(itemUri, publishValues, null, null)

            val resolvedFile = queryMediaStoreFile(context, itemUri, fileName)
            scanDownloadedFile(context, resolvedFile)

            val target = DownloadedApkTarget(file = resolvedFile, contentUri = itemUri)
            val finalTotal = maxOf(totalBytes, downloaded)
            onProgress(downloaded, finalTotal, 100)

            return if (isValidApkTarget(context, target)) {
                target
            } else {
                resolver.delete(itemUri, null, null)
                null
            }
        } catch (ce: CancellationException) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw ce
        } catch (_: Exception) {
            runCatching { resolver.delete(itemUri, null, null) }
            return null
        }
    }

    private suspend fun downloadViaLegacyPublicDownloads(
        context: Context,
        conn: HttpURLConnection,
        fileName: String,
        totalBytes: Long,
        onProgress: (Long, Long, Int) -> Unit
    ): DownloadedApkTarget? {
        // Stage in hidden cache file so zero .tmp/.part files ever appear in public Download folder
        val stagingFile = File(context.cacheDir, ".ag_update_staging.part")
        if (stagingFile.exists()) stagingFile.delete()

        try {
            var downloaded = 0L
            var lastEmitMs = 0L
            conn.inputStream.use { input ->
                FileOutputStream(stagingFile).use { output ->
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

            if (!stagingFile.exists() || stagingFile.length() < 500_000L || !isValidApkFile(context, stagingFile)) {
                stagingFile.delete()
                return null
            }

            val publicDir = getPublicDownloadsDir()
            val finalPublicFile = File(publicDir, fileName)
            val writtenToPublic = runCatching {
                if (!publicDir.exists()) publicDir.mkdirs()
                if (finalPublicFile.exists()) finalPublicFile.delete()
                stagingFile.copyTo(finalPublicFile, overwrite = true)
                stagingFile.delete()
                finalPublicFile.exists() && finalPublicFile.length() >= 500_000L
            }.getOrDefault(false)

            val finalFile = if (writtenToPublic) {
                scanDownloadedFile(context, finalPublicFile)
                runCatching {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    @Suppress("DEPRECATION")
                    dm?.addCompletedDownload(
                        fileName,
                        "AG Video Player update",
                        true,
                        "application/vnd.android.package-archive",
                        finalPublicFile.absolutePath,
                        finalPublicFile.length(),
                        true
                    )
                }
                finalPublicFile
            } else {
                val fallbackCacheApk = File(context.cacheDir, fileName)
                if (fallbackCacheApk.exists()) fallbackCacheApk.delete()
                if (!stagingFile.renameTo(fallbackCacheApk)) {
                    stagingFile.copyTo(fallbackCacheApk, overwrite = true)
                    stagingFile.delete()
                }
                fallbackCacheApk
            }

            val finalTotal = maxOf(totalBytes, finalFile.length())
            onProgress(finalFile.length(), finalTotal, 100)
            val target = DownloadedApkTarget(file = finalFile, contentUri = null)
            return if (isValidApkTarget(context, target)) target else null
        } catch (ce: CancellationException) {
            stagingFile.delete()
            throw ce
        } catch (_: Exception) {
            stagingFile.delete()
            return null
        }
    }

    private fun queryMediaStoreFile(context: Context, uri: Uri, fallbackName: String): File? {
        val path = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Downloads.DATA, MediaStore.Downloads.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.Downloads.DATA)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                    val rawPath = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val actualName = if (nameIdx >= 0) cursor.getString(nameIdx) else fallbackName
                    rawPath?.takeIf { it.isNotBlank() } ?: File(getPublicDownloadsDir(), actualName).absolutePath
                } else null
            }
        }.getOrNull() ?: File(getPublicDownloadsDir(), fallbackName).absolutePath

        return File(path).takeIf { it.exists() }
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
        val fileName = buildUpdateFileName(release)
        val publicDownloadPath = getPublicDownloadsDir().absolutePath

        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)).apply {
                setPackage(installed.first)
                putExtra(Intent.EXTRA_TITLE, fileName)
                putExtra("extra_filename", fileName)
                putExtra("extra_download_folder", publicDownloadPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun isValidApkTarget(context: Context, target: DownloadedApkTarget): Boolean {
        val f = target.file
        if (f != null && f.exists() && f.isFile && f.canRead()) {
            return isValidApkFile(context, f)
        }
        val uri = target.contentUri ?: return false
        val hasZipHeader = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            } ?: false
        }.getOrDefault(false)
        if (!hasZipHeader) return false

        // Verify package name using a fleeting cache copy if direct File path isn't readable, deleting immediately
        val tempInspect = File(context.cacheDir, ".ag_verify_inspect.apk")
        return try {
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempInspect).use { output ->
                        input.copyTo(output)
                    }
                }
                tempInspect.exists() && tempInspect.length() >= 500_000L
            }.getOrDefault(false)
            copied && isValidApkFile(context, tempInspect)
        } finally {
            runCatching { tempInspect.delete() }
        }
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
        if (pkgInfo != null) {
            return pkgInfo.packageName == context.packageName
        }
        return runCatching {
            ZipFile(apkFile).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null
            }
        }.getOrDefault(false)
    }

    fun canInstallPackages(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    fun installDownloadedApk(context: Context, target: DownloadedApkTarget): ApkInstallResult {
        if (!isValidApkTarget(context, target)) {
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

        val installUri = runCatching {
            val f = target.file
            if (f != null && f.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            } else {
                null
            }
        }.getOrNull() ?: target.contentUri ?: return ApkInstallResult.FAILED

        return runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(installUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
            } else {
                @Suppress("DEPRECATION")
                val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = installUri
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
