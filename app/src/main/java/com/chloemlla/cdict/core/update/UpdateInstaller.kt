package com.chloemlla.cdict.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class UpdateInstaller(private val context: Context) {
    suspend fun downloadApk(
        asset: ReleaseAsset,
        onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val expectedSha256 = asset.sha256?.trim()?.lowercase()
        if (expectedSha256.isNullOrBlank()) {
            throw IOException("APK SHA256 checksum is missing for ${asset.name}.")
        }
        val targetFile = File(context.cacheDir, buildCacheFileName(asset.name))
        // 上一次取消/失败留下的半包和装过的完整包都不再需要，先清掉再下载。
        deleteCachedApks()
        val connection = openHttpConnection(asset.downloadUrl).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("APK download failed with HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.buffered().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var reportedBytes = 0L
                    var reportedAtNanos = 0L
                    val progressStepBytes = totalBytes?.div(100) ?: PROGRESS_FALLBACK_STEP_BYTES
                    while (true) {
                        // 阻塞的 read 本身不响应取消，逐块检查才能让「取消下载」真的停下传输。
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val now = System.nanoTime()
                        if (downloadedBytes - reportedBytes >= progressStepBytes ||
                            now - reportedAtNanos >= PROGRESS_MIN_INTERVAL_NANOS
                        ) {
                            reportedBytes = downloadedBytes
                            reportedAtNanos = now
                            onProgress?.invoke(downloadedBytes, totalBytes)
                        }
                    }
                    onProgress?.invoke(downloadedBytes, totalBytes)
                }
            }
            val actualSha256 = targetFile.sha256()
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("APK SHA256 mismatch for ${asset.name}. Expected $expectedSha256 but got $actualSha256.")
            }
            verifyDownloadedPackage(targetFile)
            targetFile
        } catch (throwable: Throwable) {
            targetFile.delete()
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    private fun deleteCachedApks() {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                file.delete()
            }
        }
    }

    fun canInstallPackages(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * sha256 与 APK 同源于一条发布，只能证明字节没被改动，不能证明这个包就是本应用：
     * 发布页误传了别的包（或 ABI 全部落空走到「名字最短」兜底）时，包名/签名校验是最后一道闸。
     * 解析签名要整包读一遍，因此放在下载线程上做，别拖到拉起安装器的主线程。
     */
    private fun verifyDownloadedPackage(file: File) {
        val packageManager = context.packageManager
        val archive = packageManager.getPackageArchiveInfo(file.path, SIGNING_FLAGS)
            ?: throw IOException("无法解析下载的安装包，已取消安装。")
        if (archive.packageName != context.packageName) {
            throw IOException("下载的安装包属于其他应用（${archive.packageName}），已取消安装。")
        }
        val downloaded = signingCertificateDigests(archive)
        val installed = signingCertificateDigests(packageManager.getPackageInfo(context.packageName, SIGNING_FLAGS))
        if (downloaded.isEmpty() || installed.none { it in downloaded }) {
            throw IOException("下载的安装包签名与当前应用不一致，已取消安装。")
        }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateDigests(info: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            return signatures?.map { it.toByteArray().sha256() }?.toSet().orEmpty()
        }
        return info.signatures?.map { it.toByteArray().sha256() }?.toSet().orEmpty()
    }

    private fun buildCacheFileName(assetName: String): String {
        val baseName = assetName.substringAfterLast('/').ifBlank { "cdict_update.apk" }
        return baseName.replace(UNSAFE_FILE_CHARS, "_")
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).toHexString()

    private fun ByteArray.toHexString(): String {
        return buildString(size * 2) {
            for (byte in this@toHexString) {
                val value = byte.toInt() and 0xff
                append(HEX_CHARS[value ushr 4])
                append(HEX_CHARS[value and 0x0f])
            }
        }
    }

    private fun openHttpConnection(url: String): HttpsURLConnection {
        val parsedUrl = URL(url)
        if (parsedUrl.protocol != "https") {
            throw IOException("Update downloads must use HTTPS.")
        }
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
            ?: return parsedUrl.openConnection() as HttpsURLConnection
        return network.openConnection(parsedUrl) as HttpsURLConnection
    }

    private companion object {
        private const val REQUEST_TIMEOUT_MILLIS = 30_000
        private const val USER_AGENT = "CDict"
        private const val PROGRESS_MIN_INTERVAL_NANOS = 200_000_000L
        private const val PROGRESS_FALLBACK_STEP_BYTES = 1L * 1024 * 1024
        private val UNSAFE_FILE_CHARS = Regex("""[^A-Za-z0-9._-]""")
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
        private val SIGNING_FLAGS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
    }
}
