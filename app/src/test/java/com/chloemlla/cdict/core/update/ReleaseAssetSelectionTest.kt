package com.chloemlla.cdict.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAssetSelectionTest {

    private val universalName = "CDict_android_1.1.0-8cb00553.apk"
    private val arm64Name = "CDict_android_1.1.0-8cb00553_arm64-v8a.apk"
    private val armV7aName = "CDict_android_1.1.0-8cb00553_armeabi-v7a.apk"
    private val x86Name = "CDict_android_1.1.0-8cb00553_x86.apk"
    private val x8664Name = "CDict_android_1.1.0-8cb00553_x86_64.apk"

    private fun asset(name: String, abi: String? = null) = ReleaseAsset(
        name = name,
        downloadUrl = "https://example.invalid/$name",
        sha256 = "a".repeat(64),
        abi = abi,
    )

    private fun releaseAssets(abiFromManifest: Boolean) = listOf(
        asset(universalName, if (abiFromManifest) "universal" else null),
        asset(arm64Name, if (abiFromManifest) "arm64-v8a" else null),
        asset(armV7aName, if (abiFromManifest) "armeabi-v7a" else null),
        asset(x86Name, if (abiFromManifest) "x86" else null),
        asset(x8664Name, if (abiFromManifest) "x86_64" else null),
    )

    @Test
    fun `release manifest supplies abi sha256 and size for versioned asset names`() {
        val manifest = parseReleaseManifest(
            """
            {
              "application": "CDict",
              "tag": "v1.1.0-8cb00553",
              "assets": [
                {"name": "$universalName", "abi": "universal", "sizeBytes": 104529920, "sha256": "${"b".repeat(64)}"},
                {"name": "$arm64Name", "abi": "arm64-v8a", "sizeBytes": 40894464, "sha256": "${"C".repeat(64)}"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, manifest.size)
        val universal = manifest[universalName.lowercase()]
        assertEquals("universal", universal?.abi)
        assertEquals("b".repeat(64), universal?.sha256)
        assertEquals(104529920L, universal?.sizeBytes)
        assertEquals("c".repeat(64), manifest[arm64Name.lowercase()]?.sha256)
        assertEquals("arm64-v8a", manifest[arm64Name.lowercase()]?.abi)
    }

    @Test
    fun `manifest entries with unusable fields degrade to null instead of throwing`() {
        val manifest = parseReleaseManifest(
            """{"assets":[{"name":"$x86Name","abi":"","sizeBytes":0,"sha256":"nope"}]}""",
        )
        val entry = manifest[x86Name.lowercase()]
        assertEquals(1, manifest.size)
        assertNull(entry?.abi)
        assertNull(entry?.sha256)
        assertNull(entry?.sizeBytes)
    }

    @Test
    fun `malformed or unrelated manifest text yields empty map`() {
        assertTrue(parseReleaseManifest("").isEmpty())
        assertTrue(parseReleaseManifest("not json").isEmpty())
        assertTrue(parseReleaseManifest("""{"tag":"v1.1.0-8cb00553"}""").isEmpty())
    }

    @Test
    fun `release manifest asset is recognized by name only`() {
        assertTrue(isReleaseManifestAsset("release-manifest.json"))
        assertTrue(isReleaseManifestAsset("release_manifest.json"))
        assertTrue(isReleaseManifestAsset("RELEASE-MANIFEST.JSON"))
        assertFalse(isReleaseManifestAsset("checksums.txt"))
        assertFalse(isReleaseManifestAsset(universalName))
    }

    @Test
    fun `checksums file with underscored versioned names still resolves every apk`() {
        val text = listOf(universalName, arm64Name, armV7aName, x86Name, x8664Name)
            .joinToString("\n") { "${"d".repeat(64)}  $it" }
        val checksums = parseSha256Checksums(text)
        assertEquals(5, checksums.size)
        assertEquals("d".repeat(64), checksums[arm64Name.lowercase()])
        assertEquals("d".repeat(64), checksums[x8664Name.lowercase()])
    }

    @Test
    fun `abi is inferred from asset name when no manifest is published`() {
        assertEquals("arm64-v8a", inferAbiFromAssetName(arm64Name))
        assertEquals("armeabi-v7a", inferAbiFromAssetName(armV7aName))
        assertEquals("x86", inferAbiFromAssetName(x86Name))
        assertEquals("x86_64", inferAbiFromAssetName(x8664Name))
        assertNull(inferAbiFromAssetName(universalName))
        assertEquals("universal", inferAbiFromAssetName("app-universal-release.apk"))
    }

    @Test
    fun `device abi picks the matching split for versioned names`() {
        listOf(true, false).forEach { fromManifest ->
            val assets = releaseAssets(fromManifest)
            assertEquals(
                arm64Name,
                selectBestApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))?.name,
            )
            assertEquals(
                armV7aName,
                selectBestApkAsset(assets, listOf("armeabi-v7a", "armeabi"))?.name,
            )
            assertEquals(
                x8664Name,
                selectBestApkAsset(assets, listOf("x86_64", "x86", "armeabi-v7a"))?.name,
            )
            assertEquals(
                x86Name,
                selectBestApkAsset(assets, listOf("x86", "armeabi-v7a"))?.name,
            )
        }
    }

    @Test
    fun `unknown device abi falls back to the universal apk`() {
        assertEquals(universalName, selectBestApkAsset(releaseAssets(true), listOf("riscv64"))?.name)
        assertEquals(universalName, selectBestApkAsset(releaseAssets(false), listOf("riscv64"))?.name)
    }

    @Test
    fun `apk without a checksum is never selected`() {
        val assets = listOf(
            ReleaseAsset(name = arm64Name, downloadUrl = "https://example.invalid/a", sha256 = null),
            asset(universalName, "universal"),
        )
        assertEquals(universalName, selectBestApkAsset(assets, listOf("arm64-v8a"))?.name)
        assertNull(selectBestApkAsset(emptyList(), listOf("arm64-v8a")))
    }

    @Test
    fun `legacy unversioned asset names keep working`() {
        val legacy = listOf(
            asset("CDict.apk"),
            asset("CDict-arm64-v8a.apk"),
            asset("CDict-armeabi-v7a.apk"),
        )
        assertEquals("CDict-arm64-v8a.apk", selectBestApkAsset(legacy, listOf("arm64-v8a"))?.name)
        assertEquals("CDict.apk", selectBestApkAsset(legacy, listOf("riscv64"))?.name)
    }
}
