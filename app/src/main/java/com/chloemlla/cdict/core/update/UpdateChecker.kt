package com.chloemlla.cdict.core.update

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

class UpdateChecker(
    private val context: Context,
    private val githubReleaseApiUrl: String = CDICT_RELEASE_API,
) {
    suspend fun checkForUpdate(currentBuild: BuildMetadata = BuildMetadata.current()): UpdateCandidate? =
        withContext(Dispatchers.IO) {
            val latest = fetchLatestGitHubRelease() ?: return@withContext null

            val localVersion = parseVersionDescriptor("${currentBuild.versionName}-${currentBuild.shortHash}")
                ?: parseVersionDescriptor(currentBuild.versionName)
                ?: return@withContext null
            if (isExactVersionMatch(latest.tagName, localVersion)) return@withContext null

            val versionComparison = compareReleaseVersion(latest.tagName, localVersion)
            val publishTimeNewer = latest.publishedAtUtcMillis > currentBuild.buildTimeUtcMillis + PUBLISH_TIME_TOLERANCE_MILLIS

            val shouldUpdate = versionComparison > 0 || publishTimeNewer
            if (!shouldUpdate) return@withContext null

            UpdateCandidate(
                currentBuild = currentBuild,
                release = latest,
                matchedAsset = selectBestApkAsset(latest.assets, Build.SUPPORTED_ABIS.toList()),
                matchReason = if (versionComparison > 0) UpdateMatchReason.SEMANTIC_VERSION else UpdateMatchReason.PUBLISHED_AT,
            )
        }

    private fun fetchLatestGitHubRelease(): ReleaseInfo? {
        val connection = openHttpConnection(githubReleaseApiUrl).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub release request failed with HTTP ${connection.responseCode}")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(payload)
            return (0 until releases.length())
                .asSequence()
                .mapNotNull { index -> releases.optJSONObject(index)?.let(::parseGitHubRelease) }
                .firstOrNull()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseGitHubRelease(json: JSONObject): ReleaseInfo? {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val tagName = json.optString("tag_name").orEmpty()
        if (parseVersionDescriptor(tagName) == null) return null

        val releaseAssets = parseReleaseAssets(json)
        val manifest = fetchReleaseManifest(releaseAssets)
        val described = releaseAssets.map { asset ->
            if (!asset.isApk) return@map asset
            val entry = manifest[normalizeChecksumName(asset.name)]
            asset.copy(
                sha256 = entry?.sha256,
                abi = entry?.abi ?: inferAbiFromAssetName(asset.name),
                sizeBytes = asset.sizeBytes ?: entry?.sizeBytes,
            )
        }

        // 旧版本发布只有 checksums.txt / 正文哈希，没有 release-manifest.json，
        // 因此仅在清单没覆盖全部 APK 时才回退去下载校验文件。
        val assets = if (described.any { it.isApk && it.sha256.isNullOrBlank() }) {
            val checksums = parseSha256Checksums(json.optString("body")) +
                fetchSha256ChecksumAssets(releaseAssets)
            described.map { asset ->
                if (asset.isApk && asset.sha256.isNullOrBlank()) {
                    asset.copy(sha256 = checksums[normalizeChecksumName(asset.name)])
                } else {
                    asset
                }
            }
        } else {
            described
        }
        if (assets.none { it.isApk && !it.sha256.isNullOrBlank() }) {
            return null
        }

        return ReleaseInfo(
            tagName = tagName,
            releaseName = json.optString("name").ifBlank { tagName },
            body = json.optString("body"),
            htmlUrl = json.optString("html_url").orEmpty(),
            publishedAtUtcMillis = parseInstant(json.optString("published_at")) ?: return null,
            assets = assets,
        )
    }

    private fun parseReleaseAssets(json: JSONObject): List<ReleaseAsset> {
        return json.optJSONArray("assets")
            ?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val asset = array.optJSONObject(index) ?: continue
                        val name = asset.optString("name").orEmpty()
                        val downloadUrl = asset.optString("browser_download_url").orEmpty()
                        if (name.isBlank() || downloadUrl.isBlank()) continue
                        add(
                            ReleaseAsset(
                                name = name,
                                downloadUrl = downloadUrl,
                                contentType = asset.optString("content_type").takeIf { it.isNotBlank() },
                                sizeBytes = asset.optLong("size", -1L).takeIf { it > 0L },
                            ),
                        )
                    }
                }
            }
            .orEmpty()
    }

    private fun fetchReleaseManifest(assets: List<ReleaseAsset>): Map<String, ManifestAsset> {
        val manifestAsset = assets.firstOrNull { isReleaseManifestAsset(it.name) } ?: return emptyMap()
        return parseReleaseManifest(fetchTextAsset(manifestAsset.downloadUrl))
    }

    private fun fetchSha256ChecksumAssets(assets: List<ReleaseAsset>): Map<String, String> {
        return assets
            .filter { asset ->
                !asset.isApk &&
                    normalizeAssetName(asset.name).let { it.contains("checksum") || it.contains("sha256") }
            }
            .fold(emptyMap()) { checksums, asset ->
                checksums + parseSha256Checksums(fetchTextAsset(asset.downloadUrl))
            }
    }

    /**
     * 校验信息拉取失败必须抛出：把它咽成 null 会让最新那条发布因「没有带校验和的 APK」被跳过，
     * 最终把一次网络故障报告成「已是最新版本」。
     */
    private fun fetchTextAsset(url: String): String {
        val connection = openHttpConnection(url).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "text/plain, application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Update asset request failed with HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttpConnection(url: String): HttpsURLConnection {
        val parsedUrl = URL(url)
        if (parsedUrl.protocol != "https") {
            throw IOException("Update endpoints must use HTTPS.")
        }
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
            ?: return parsedUrl.openConnection() as HttpsURLConnection
        return network.openConnection(parsedUrl) as HttpsURLConnection
    }

    private fun compareReleaseVersion(remoteTagName: String, localVersion: VersionDescriptor): Int {
        val remote = parseVersionDescriptor(remoteTagName) ?: return 0
        return remote.semanticVersion.compareTo(localVersion.semanticVersion)
    }

    private fun isExactVersionMatch(remoteTagName: String, localVersion: VersionDescriptor): Boolean {
        val remote = parseVersionDescriptor(remoteTagName) ?: return false
        return remote.semanticVersion == localVersion.semanticVersion &&
            remote.shortHash.isNotBlank() &&
            localVersion.shortHash.isNotBlank() &&
            remote.shortHash.equals(localVersion.shortHash, ignoreCase = true)
    }

    private fun parseVersionDescriptor(value: String): VersionDescriptor? {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return null

        val versionPart = cleaned
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('(')
            .substringBefore('+')
            .substringBefore('-')

        val shortHash = extractShortHash(cleaned)
        val parts = versionPart.split('.')
            .mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        return VersionDescriptor(
            semanticVersion = SemanticVersion(
                major = parts.getOrNull(0) ?: 0,
                minor = parts.getOrNull(1) ?: 0,
                patch = parts.getOrNull(2) ?: 0,
            ),
            shortHash = shortHash,
        )
    }

    private fun extractShortHash(value: String): String {
        val bracketMatch = SHORT_HASH_IN_PARENS_REGEX.find(value)
        if (bracketMatch != null) return bracketMatch.groupValues[1]

        val suffixMatch = SHORT_HASH_SUFFIX_REGEX.find(value)
        if (suffixMatch != null) return suffixMatch.groupValues[1]

        return ""
    }

    private fun parseInstant(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            return compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
        }
    }

    private data class VersionDescriptor(
        val semanticVersion: SemanticVersion,
        val shortHash: String,
    )

    private companion object {
        private const val REQUEST_TIMEOUT_MILLIS = 6_000
        private const val PUBLISH_TIME_TOLERANCE_MILLIS = 90_000L
        private const val USER_AGENT = "CDict"
        private const val CDICT_RELEASE_API = "https://api.github.com/repos/Chloemlla/CDict/releases?per_page=20"
        private val SHORT_HASH_IN_PARENS_REGEX = Regex("""\(([0-9a-fA-F]{7,40})\)$""")
        private val SHORT_HASH_SUFFIX_REGEX = Regex("""(?:-|_)([0-9a-fA-F]{7,40})$""")
    }
}

/** release-manifest.json 里的单个条目，由发布流水线随 APK 一起产出。 */
internal data class ManifestAsset(
    val name: String,
    val abi: String?,
    val sha256: String?,
    val sizeBytes: Long?,
)

internal val ReleaseAsset.isApk: Boolean
    get() = name.endsWith(".apk", ignoreCase = true)

internal fun isReleaseManifestAsset(name: String): Boolean =
    normalizeAssetName(name) == "release_manifest_json"

/** 解析发布清单，返回「小写文件名 -> 条目」。文本不是预期结构时返回空表，交由 checksums.txt 兜底。 */
internal fun parseReleaseManifest(text: String): Map<String, ManifestAsset> {
    if (text.isBlank()) return emptyMap()
    val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
    val array = root.optJSONArray("assets") ?: return emptyMap()
    return buildMap {
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val name = entry.optString("name").orEmpty()
            if (name.isBlank()) continue
            put(
                normalizeChecksumName(name),
                ManifestAsset(
                    name = name,
                    abi = entry.optString("abi").takeIf { it.isNotBlank() },
                    sha256 = entry.optString("sha256").lowercase().takeIf { SHA256_REGEX.matches(it) },
                    sizeBytes = entry.optLong("sizeBytes", -1L).takeIf { it > 0L },
                ),
            )
        }
    }
}

internal fun parseSha256Checksums(text: String): Map<String, String> {
    if (text.isBlank()) return emptyMap()
    return buildMap {
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val match = SHA256_REGEX.find(line) ?: return@forEach
            val hash = match.value.lowercase()
            val beforeHash = line.substring(0, match.range.first)
            val afterHash = line.substring(match.range.last + 1)
            val assetName = (apkFileNames(afterHash) + apkFileNames(beforeHash)).firstOrNull()
                ?: return@forEach
            put(normalizeChecksumName(assetName), hash)
        }
    }
}

/**
 * 从资产名推断架构，供没有 release-manifest.json 的旧版本发布使用。
 * x86_64 必须排在 x86 之前，否则 64 位包会被误判成 32 位。
 */
internal fun inferAbiFromAssetName(name: String): String? {
    val normalized = normalizeAssetName(name)
    KNOWN_ABIS.firstOrNull { normalized.contains(normalizeAbiToken(it)) }?.let { return it }
    return if (normalized.contains("universal")) "universal" else null
}

/** 按设备 ABI 优先级挑选 APK；没有匹配架构时退回 universal，仍无则取名字最短的包。 */
internal fun selectBestApkAsset(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
    val apkAssets = assets.filter { it.isApk && !it.sha256.isNullOrBlank() }
    if (apkAssets.isEmpty()) return null

    val preferredAbis = supportedAbis.map { normalizeAbiToken(it) }
    val scored = apkAssets.map { asset ->
        val normalizedName = normalizeAssetName(asset.name)
        val normalizedAssetAbi = asset.abi?.let(::normalizeAbiToken).orEmpty()
        val abiScore = preferredAbis.indexOfFirst { abi ->
            abi.isNotBlank() && (normalizedAssetAbi == abi || normalizedName.contains(abi))
        }
        val fallbackScore = when {
            normalizedAssetAbi == "universal" -> 10_000
            normalizedAssetAbi == "all" -> 10_001
            normalizedName.contains("universal") -> 10_000
            normalizedName.contains("all") -> 10_001
            else -> 20_000
        }
        if (abiScore >= 0) asset to abiScore else asset to fallbackScore
    }
    return scored.minWithOrNull(compareBy<Pair<ReleaseAsset, Int>> { it.second }.thenBy { it.first.name.length })?.first
}

internal fun normalizeAssetName(value: String): String =
    value.lowercase()
        .replace('-', '_')
        .replace('.', '_')
        .replace(' ', '_')

internal fun normalizeAbiToken(value: String): String =
    value.lowercase()
        .replace('-', '_')
        .replace('.', '_')

internal fun normalizeChecksumName(value: String): String =
    value.substringAfterLast('/')
        .lowercase()
        .trim()

private fun apkFileNames(value: String): List<String> =
    APK_FILE_NAME_REGEX.findAll(value)
        .map { it.value.substringAfterLast('/') }
        .toList()

private val KNOWN_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi")
private val SHA256_REGEX = Regex("""\b[0-9a-fA-F]{64}\b""")
private val APK_FILE_NAME_REGEX = Regex("""[A-Za-z0-9._+-]+\.apk""", RegexOption.IGNORE_CASE)
