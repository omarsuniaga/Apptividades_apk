package com.example.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.example.domain.rules.AccountStatementBuilder
import com.example.domain.rules.GeneratedStatementPdf
import java.io.File

/**
 * Shares generated PDF statements strictly from app-private cache via FileProvider
 * (never writes to public storage) and cleans up temporary cache files automatically.
 */
object PdfStatementSharer {

    private const val STATEMENTS_DIR = "statements"
    private const val CLEANUP_DELAY_MS = 120_000L

    fun writeToCache(context: Context, pdf: GeneratedStatementPdf): File {
        val dir = File(context.cacheDir, STATEMENTS_DIR).apply { mkdirs() }
        cleanupStaleCacheFiles(dir)
        val safeName = pdf.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(dir, safeName)
        // Path confinement check
        val canonicalDir = dir.canonicalPath + File.separator
        require(outFile.canonicalPath.startsWith(canonicalDir)) {
            "Nombre de archivo PDF inválido."
        }
        outFile.writeBytes(pdf.bytes)
        return outFile
    }

    /**
     * Writes PDF to cache, exposes it with FileProvider, and launches ACTION_SEND (application/pdf)
     * with short caption. Prefers WhatsApp (`com.whatsapp`, then `com.whatsapp.w4b`) targeted at the
     * supplier's phone when available; otherwise opens the Android system chooser.
     * Schedules cache cleanup afterwards.
     */
    fun shareStatementPdf(
        context: Context,
        pdf: GeneratedStatementPdf,
        caption: String,
        supplierRawPhone: String?,
    ): Result<Unit> = runCatching {
        val cachedFile = writeToCache(context, pdf)
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cachedFile,
        )

        val normalizedPhone = AccountStatementBuilder.normalizePhoneForWhatsApp(supplierRawPhone)
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newRawUri(pdf.fileName, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pm = context.packageManager
        val whatsAppPackage = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }

        var launched = false
        if (whatsAppPackage != null) {
            val waIntent = Intent(baseIntent).apply {
                setPackage(whatsAppPackage)
                if (!normalizedPhone.isNullOrBlank()) {
                    putExtra("jid", "$normalizedPhone@s.whatsapp.net")
                }
            }
            launched = runCatching {
                context.startActivity(waIntent)
                true
            }.getOrDefault(false)
        }

        if (!launched) {
            val chooser = Intent.createChooser(baseIntent, "Enviar resumen de actividades (PDF)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }

        // Clean cache file after giving the receiving app time to read the content URI
        scheduleCacheCleanup(cachedFile)
    }

    /**
     * Saves PDF bytes to a user-selected SAF Uri (zero storage permissions needed).
     */
    fun saveToDocumentUri(context: Context, uri: Uri, bytes: ByteArray): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(bytes)
            stream.flush()
        } ?: error("No se pudo abrir el archivo destino.")
    }

    fun cleanupAllCachedStatements(context: Context) {
        val dir = File(context.cacheDir, STATEMENTS_DIR)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun cleanupStaleCacheFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - CLEANUP_DELAY_MS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private fun scheduleCacheCleanup(file: File) {
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { if (file.exists()) file.delete() }
        }, CLEANUP_DELAY_MS)
    }
}
