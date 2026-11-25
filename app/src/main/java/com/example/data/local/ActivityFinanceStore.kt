package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.port.ActivityFinancePort
import com.example.domain.model.ActivityExpenseEntry
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.ActivityPaymentEntry
import com.example.domain.model.ExpenseCategory
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

private val Context.activityFinanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "apptividades_activity_finance"
)

@JsonClass(generateAdapter = true)
internal data class PaymentEntryDto(
    val id: String,
    val amount: String,
    val date: String,
    val note: String = "",
    val receiptFileName: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class ExpenseEntryDto(
    val id: String,
    val category: String,
    val amount: String,
    val note: String = "",
    val receiptFileName: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class FinancialRecordDto(
    val activityId: String,
    val payments: List<PaymentEntryDto> = emptyList(),
    val expenses: List<ExpenseEntryDto> = emptyList(),
    val exchangeRateToDop: String? = null,
)

/**
 * Local persistent store for per-activity partial payments (anticipos/señas),
 * expenses, and app-private receipt image copies imported via the zero-permission
 * Android Photo Picker (PickVisualMedia).
 */
class ActivityFinanceStore(
    private val context: Context,
    moshi: Moshi,
) : ActivityFinancePort {

    private val appContext = context.applicationContext
    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        FinancialRecordDto::class.java,
    )
    private val adapter = moshi.adapter<Map<String, FinancialRecordDto>>(mapType)

    override fun observeFinancialRecords(): Flow<Map<String, ActivityFinancialRecord>> =
        appContext.activityFinanceDataStore.data.map { prefs ->
            decodeRecords(prefs[KEY_RECORDS_JSON])
        }

    override suspend fun saveFinancialRecord(record: ActivityFinancialRecord) {
        val cleanId = record.activityId.trim()
        if (cleanId.isEmpty()) return
        appContext.activityFinanceDataStore.edit { prefs ->
            val current = decodeRecords(prefs[KEY_RECORDS_JSON]).toMutableMap()
            val previous = current[cleanId]
            if (record.isEmpty) {
                current.remove(cleanId)
            } else {
                current[cleanId] = record.copy(activityId = cleanId)
            }
            prefs[KEY_RECORDS_JSON] = encodeRecords(current)

            // Clean up any receipt files that were removed from this activity
            val removedFiles = collectReceiptNames(previous) - collectReceiptNames(current[cleanId])
            removedFiles.forEach { ReceiptImageStorage.deleteReceiptFile(appContext, it) }
        }
    }

    override suspend fun deleteFinancialRecord(activityId: String) {
        val cleanId = activityId.trim()
        if (cleanId.isEmpty()) return
        appContext.activityFinanceDataStore.edit { prefs ->
            val current = decodeRecords(prefs[KEY_RECORDS_JSON]).toMutableMap()
            val removed = current.remove(cleanId)
            prefs[KEY_RECORDS_JSON] = encodeRecords(current)
            collectReceiptNames(removed).forEach { ReceiptImageStorage.deleteReceiptFile(appContext, it) }
        }
    }

    override suspend fun clearAll() {
        appContext.activityFinanceDataStore.edit { prefs ->
            prefs.remove(KEY_RECORDS_JSON)
        }
        ReceiptImageStorage.clearAllReceipts(appContext)
    }

    private fun collectReceiptNames(record: ActivityFinancialRecord?): Set<String> {
        if (record == null) return emptySet()
        return buildSet {
            record.payments.mapNotNullTo(this) { it.receiptFileName?.takeIf { n -> n.isNotBlank() } }
            record.expenses.mapNotNullTo(this) { it.receiptFileName?.takeIf { n -> n.isNotBlank() } }
        }
    }

    private fun decodeRecords(json: String?): Map<String, ActivityFinancialRecord> {
        if (json.isNullOrBlank()) return emptyMap()
        val raw = runCatching { adapter.fromJson(json) }.getOrNull() ?: return emptyMap()
        return raw.mapValues { (id, dto) ->
            ActivityFinancialRecord(
                activityId = id,
                payments = dto.payments.mapNotNull { p ->
                    val amount = p.amount.toBigDecimalOrNull() ?: return@mapNotNull null
                    val date = runCatching { LocalDate.parse(p.date) }.getOrElse { LocalDate.now() }
                    ActivityPaymentEntry(
                        id = p.id,
                        amount = amount,
                        date = date,
                        note = p.note,
                        receiptFileName = p.receiptFileName?.takeIf { it.isNotBlank() },
                    )
                },
                expenses = dto.expenses.mapNotNull { e ->
                    val amount = e.amount.toBigDecimalOrNull() ?: return@mapNotNull null
                    ActivityExpenseEntry(
                        id = e.id,
                        category = ExpenseCategory.fromCode(e.category),
                        amount = amount,
                        note = e.note,
                        receiptFileName = e.receiptFileName?.takeIf { it.isNotBlank() },
                    )
                },
                exchangeRateToDop = dto.exchangeRateToDop
                    ?.toBigDecimalOrNull()
                    ?.takeIf { it > BigDecimal.ZERO },
            )
        }
    }

    private fun encodeRecords(records: Map<String, ActivityFinancialRecord>): String {
        val dtos = records.mapValues { (id, rec) ->
            FinancialRecordDto(
                activityId = id,
                payments = rec.payments.map { p ->
                    PaymentEntryDto(
                        id = p.id,
                        amount = p.amount.stripTrailingZeros().toPlainString(),
                        date = p.date.toString(),
                        note = p.note,
                        receiptFileName = p.receiptFileName,
                    )
                },
                expenses = rec.expenses.map { e ->
                    ExpenseEntryDto(
                        id = e.id,
                        category = e.category.code,
                        amount = e.amount.stripTrailingZeros().toPlainString(),
                        note = e.note,
                        receiptFileName = e.receiptFileName,
                    )
                },
                exchangeRateToDop = rec.exchangeRateToDop
                    ?.takeIf { it > BigDecimal.ZERO }
                    ?.stripTrailingZeros()
                    ?.toPlainString(),
            )
        }
        return adapter.toJson(dtos)
    }

    companion object {
        private val KEY_RECORDS_JSON = stringPreferencesKey("financial_records_json_v1")
    }
}

/**
 * Manages compressed app-private copies of payment/expense receipt photos selected
 * via Android's zero-permission Photo Picker (PickVisualMedia).
 * Strictly enforces safe filenames with no path traversal.
 */
object ReceiptImageStorage {

    private const val RECEIPTS_DIR = "receipts"
    private const val MAX_IMAGE_DIMENSION = 1600
    private val SAFE_FILENAME_REGEX = Regex("^[A-Za-z0-9_.-]+$")

    private fun receiptsDirectory(context: Context): File =
        File(context.applicationContext.filesDir, RECEIPTS_DIR).apply { mkdirs() }

    fun resolveReceiptFile(context: Context, fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        if (!SAFE_FILENAME_REGEX.matches(fileName) || fileName.contains("..")) return null
        val dir = receiptsDirectory(context).canonicalFile
        val target = File(dir, fileName).canonicalFile
        return if (target.parentFile == dir && target.exists() && target.isFile) target else null
    }

    fun deleteReceiptFile(context: Context, fileName: String?) {
        resolveReceiptFile(context, fileName)?.let { runCatching { it.delete() } }
    }

    fun clearAllReceipts(context: Context) {
        runCatching {
            val dir = File(context.applicationContext.filesDir, RECEIPTS_DIR)
            if (dir.exists()) {
                dir.listFiles()?.forEach { runCatching { it.delete() } }
            }
        }
    }

    suspend fun importReceiptFromUri(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.applicationContext.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                val rawWidth = bounds.outWidth
                val rawHeight = bounds.outHeight
                if (rawWidth <= 0 || rawHeight <= 0) {
                    error("No se pudo leer la imagen del comprobante.")
                }

                var sampleSize = 1
                while ((rawWidth / sampleSize) > MAX_IMAGE_DIMENSION || (rawHeight / sampleSize) > MAX_IMAGE_DIMENSION) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: error("No se pudo decodificar la imagen del comprobante.")

                val fileName = "rcpt_${UUID.randomUUID().toString().replace("-", "").take(16)}.jpg"
                val destFile = File(receiptsDirectory(context), fileName)
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                bitmap.recycle()
                fileName
            }
        }

    suspend fun loadBitmapForDisplay(context: Context, fileName: String?, maxDimension: Int = 900): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = resolveReceiptFile(context, fileName) ?: return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while ((bounds.outWidth / sample) > maxDimension || (bounds.outHeight / sample) > maxDimension) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            }.getOrNull()
        }
}
