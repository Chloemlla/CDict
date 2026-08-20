package com.chloemlla.cdict.ui.about

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GALLERY_ALBUM = "CDict"
private const val MIME_PNG = "image/png"

/**
 * 把位图以 PNG 写进系统相册的 Pictures/CDict。
 *
 * Android 10 起走 MediaStore 的 RELATIVE_PATH，不需要任何权限；Android 9 及以下 MediaStore 只认
 * DATA 列的绝对路径，因此得先自建目录并申请 WRITE_EXTERNAL_STORAGE（清单里已限制 maxSdkVersion=28）。
 */
object GallerySaver {

    val STORAGE_PERMISSION: String = Manifest.permission.WRITE_EXTERNAL_STORAGE

    fun hasStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, STORAGE_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun savePng(context: Context, bitmap: Bitmap, baseName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                insertPng(context, bitmap, galleryFileName(baseName, System.currentTimeMillis()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
}

private fun insertPng(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    val legacyPath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        legacyAlbumFile(fileName)?.absolutePath ?: return false
    } else {
        null
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
        if (legacyPath != null) {
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, legacyPath)
        } else {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$GALLERY_ALBUM",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    val written = try {
        resolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } == true
    } catch (e: Exception) {
        false
    }
    if (!written) {
        resolver.delete(uri, null, null)
        return false
    }
    if (legacyPath == null) {
        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
    }
    return true
}

@Suppress("DEPRECATION")
private fun legacyAlbumFile(fileName: String): File? {
    val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val album = File(pictures, GALLERY_ALBUM)
    if (!album.isDirectory && !album.mkdirs()) return null
    return File(album, fileName)
}

/**
 * 文件名直接落到文件系统路径上，而基础名里含服务端下发的渠道 id，因此在这里再做一次字符收敛，
 * 不依赖调用方的校验。
 */
internal fun galleryFileName(baseName: String, timeMillis: Long): String {
    val safe = baseName
        .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
        .joinToString("")
        .trim('-')
        .ifEmpty { "image" }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timeMillis))
    return "$safe-$stamp.png"
}
