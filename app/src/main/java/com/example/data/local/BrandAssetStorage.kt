package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages app-private logo and handwritten/uploaded signature bitmaps under filesDir/branding.
 * Uses Android Photo Picker URIs or in-app Canvas bitmaps with strict path confinement and zero
 * external storage permissions.
 */
object BrandAssetStorage {

    private const val BRANDING_DIR = "branding"
    private const val LOGO_FILE_NAME = "artist_logo.png"
    private const val SIGNATURE_FILE_NAME = "artist_signature.png"
    private const val MAX_DIMENSION_PX = 640

    private fun brandingDir(context: Context): File =
        File(context.filesDir, BRANDING_DIR).apply { mkdirs() }

    private fun resolveSafeFile(context: Context, fileName: String): File {
        val dir = brandingDir(context)
        val cleanName = File(fileName).name
        val target = File(dir, cleanName)
        require(target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            "Nombre de archivo inválido."
        }
        return target
    }

    suspend fun importLogoFromUri(context: Context, uri: Uri): Result<String> =
        importImageFromUri(context, uri, LOGO_FILE_NAME)

    suspend fun importSignatureFromUri(context: Context, uri: Uri): Result<String> =
        importImageFromUri(context, uri, SIGNATURE_FILE_NAME)

    suspend fun saveDrawnSignature(context: Context, bitmap: Bitmap): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val scaled = scaleDownIfNeeded(bitmap, MAX_DIMENSION_PX)
                val outFile = resolveSafeFile(context, SIGNATURE_FILE_NAME)
                FileOutputStream(outFile).use { stream ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.flush()
                }
                if (scaled !== bitmap) scaled.recycle()
                SIGNATURE_FILE_NAME
            }
        }

    fun loadBitmap(context: Context, fileName: String?): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        return runCatching {
            val file = resolveSafeFile(context, fileName)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    fun deleteAsset(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        runCatching {
            val file = resolveSafeFile(context, fileName)
            if (file.exists()) file.delete()
        }
    }

    fun clearAllBrandAssets(context: Context) {
        runCatching {
            val dir = File(context.filesDir, BRANDING_DIR)
            if (dir.exists()) {
                dir.listFiles()?.forEach { runCatching { it.delete() } }
            }
        }
    }

    private suspend fun importImageFromUri(
        context: Context,
        uri: Uri,
        targetFileName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: error("No se pudo leer la imagen seleccionada.")

            val maxRaw = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (maxRaw / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: error("No se pudo decodificar la imagen.")

            val scaled = scaleDownIfNeeded(decoded, MAX_DIMENSION_PX)
            val outFile = resolveSafeFile(context, targetFileName)
            FileOutputStream(outFile).use { stream ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            targetFileName
        }
    }

    private fun scaleDownIfNeeded(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val scale = maxDim.toFloat() / maxOf(w, h).toFloat()
        val dstW = (w * scale).toInt().coerceAtLeast(1)
        val dstH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, dstW, dstH, true)
    }
}
