package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.BankAccount
import com.example.domain.model.Currency
import com.example.domain.model.PaymentStatus
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

data class StatementLineItem(
    val activityId: String,
    val eventDate: LocalDate,
    val eventTime: LocalTime?,
    val serviceDescription: String,
    val venueName: String,
    val status: ReceivableStatus,
    val overdueDays: Long,
    val statusLabel: String,
    val amount: BigDecimal,
)

data class AccountStatement(
    val supplierKey: String,
    val supplierDisplayName: String,
    val issuerName: String,
    val issueDate: LocalDate,
    val currency: Currency,
    val items: List<StatementLineItem>,
    val totalPending: BigDecimal,
    val overdueTotal: BigDecimal,
    val bankAccounts: List<BankAccount>,
    val fileName: String,
    val shareCaption: String,
    val documentTitle: String = AccountStatementBuilder.DEFAULT_DOCUMENT_TITLE,
    val friendlyNotice: String = AccountStatementBuilder.FRIENDLY_NOTICE,
    val closingQuote: String = AccountStatementBuilder.CICERO_QUOTE,
    val closingQuoteAttribution: String = AccountStatementBuilder.CICERO_ATTRIBUTION,
    val gratitudeMessage: String = AccountStatementBuilder.GRATITUDE_MESSAGE,
)

/**
 * Pure domain builder for Phase 3 supplier account statements (PDF).
 * Reuses Phase 1 rules: month-in-arrears status classification and supplier/venue name normalization.
 */
object AccountStatementBuilder {

    const val DEFAULT_DOCUMENT_TITLE = "Resumen de actividades"
    const val DEFAULT_SERVICE_LABEL = "Servicio musical"
    const val FRIENDLY_NOTICE =
        "Si ya realizaste algún pago, por favor avísame para actualizar este resumen. ¡Gracias!"
    const val CICERO_QUOTE =
        "«La gratitud no solo es la mayor de las virtudes, sino la madre de todas las demás.»"
    const val CICERO_ATTRIBUTION = "Cicerón, Pro Plancio, 54 a. C."
    const val GRATITUDE_MESSAGE =
        "Gracias por confiar en mi música. Cada presentación es una colaboración y un gusto para mí. Valoro mucho nuestra relación y espero que sigamos compartiendo muchos escenarios juntos."

    fun build(
        supplierName: String,
        currency: Currency,
        activities: List<Activity>,
        bankAccounts: List<BankAccount>,
        today: LocalDate,
        issuerFallback: String = "Omar",
        financialRecords: Map<String, ActivityFinancialRecord> = emptyMap(),
    ): AccountStatement {
        val trimmedSupplier = supplierName.trim()
        require(trimmedSupplier.isNotEmpty()) {
            "El nombre del proveedor no puede estar vacío."
        }

        val supplierResolution = NameNormalizer.buildSupplierResolution(
            activities.map { it.supplierName } + trimmedSupplier
        )
        val venueResolution = NameNormalizer.buildVenueResolution(
            activities.map { it.venueName }
        )

        val targetKey = supplierResolution.canonicalKey(trimmedSupplier)
        require(targetKey.isNotEmpty()) {
            "El nombre del proveedor no es válido."
        }

        val displaySupplier = supplierResolution.canonicalToDisplay[targetKey] ?: trimmedSupplier

        // Suppliers arrange payment for the whole month, so confirmed gigs that are
        // still to be played this month belong in the statement too.
        val currentMonth = YearMonth.from(today)
        val billable = activities
            .filter { act ->
                if (act.paymentStatus != PaymentStatus.PENDIENTE ||
                    !(act.eventDate.isBefore(today) || YearMonth.from(act.eventDate) == currentMonth) ||
                    act.currency != currency ||
                    supplierResolution.canonicalKey(act.supplierName) != targetKey
                ) {
                    false
                } else {
                    val breakdown = ActivityFinanceCalculator.breakdown(act, financialRecords[act.id])
                    act.amount.compareTo(BigDecimal.ZERO) == 0 || breakdown.pendingBalance > BigDecimal.ZERO
                }
            }
            .sortedWith(compareBy<Activity> { it.eventDate }.thenBy { it.eventTime })

        require(billable.isNotEmpty()) {
            "No hay servicios pendientes de pago hasta este mes para $displaySupplier en ${currency.code}."
        }

        val items = billable.map { act ->
            val breakdown = ActivityFinanceCalculator.breakdown(act, financialRecords[act.id])
            val status = ReceivablesCalculator.classify(act.eventDate, today)
            val overdueDays = if (status == ReceivableStatus.VENCIDO) {
                ChronoUnit.DAYS.between(act.eventDate, today).coerceAtLeast(0L)
            } else {
                0L
            }
            val statusLabel = when (status) {
                ReceivableStatus.VENCIDO ->
                    if (overdueDays == 1L) "Vencido 1 día" else "Vencido $overdueDays días"
                ReceivableStatus.ESTE_MES -> "Por cobrar este mes"
                ReceivableStatus.PROXIMO_MES -> "En plazo"
            }

            val rawDesc = act.description?.trim().orEmpty()
            val descNormalizedKey = NameNormalizer.normalizeKey(rawDesc)
            val baseServiceDesc = if (
                rawDesc.isEmpty() ||
                descNormalizedKey == targetKey ||
                rawDesc.equals(displaySupplier, ignoreCase = true) ||
                rawDesc.equals(act.supplierName.trim(), ignoreCase = true)
            ) {
                DEFAULT_SERVICE_LABEL
            } else {
                rawDesc
            }
            val serviceDesc = if (breakdown.paidAmount > BigDecimal.ZERO) {
                val paidStr = DominicanFormatter.formatMoney(breakdown.paidAmount, currency)
                "$baseServiceDesc (abono: $paidStr)"
            } else {
                baseServiceDesc
            }

            val venueDisplay = venueResolution.displayName(act.venueName)
                .ifBlank { act.venueName.trim() }

            StatementLineItem(
                activityId = act.id,
                eventDate = act.eventDate,
                eventTime = act.eventTime,
                serviceDescription = serviceDesc,
                venueName = venueDisplay,
                status = status,
                overdueDays = overdueDays,
                statusLabel = statusLabel,
                amount = breakdown.pendingBalance,
            )
        }

        val totalPending = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
        val overdueTotal = items
            .filter { it.status == ReceivableStatus.VENCIDO }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }

        val currencyAccounts = bankAccounts
            .filter { it.currency == currency }
            .sortedWith(
                compareByDescending<BankAccount> { it.isDefault }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
                    .thenBy { it.bankName.lowercase(Locale.ROOT) }
            )

        val issuerName = currencyAccounts.firstOrNull { it.isDefault }?.holderName?.trim()?.takeIf { it.isNotEmpty() }
            ?: bankAccounts.firstOrNull { it.isDefault }?.holderName?.trim()?.takeIf { it.isNotEmpty() }
            ?: currencyAccounts.firstOrNull()?.holderName?.trim()?.takeIf { it.isNotEmpty() }
            ?: issuerFallback.trim().ifEmpty { "Omar" }

        val fileName = buildFileName(displaySupplier, today)
        val shareCaption = buildShareCaption(
            supplierDisplayName = displaySupplier,
            serviceCount = items.size,
            totalPending = totalPending,
            currency = currency,
        )

        return AccountStatement(
            supplierKey = targetKey,
            supplierDisplayName = displaySupplier,
            issuerName = issuerName,
            issueDate = today,
            currency = currency,
            items = items,
            totalPending = totalPending,
            overdueTotal = overdueTotal,
            bankAccounts = currencyAccounts,
            fileName = fileName,
            shareCaption = shareCaption,
        )
    }

    fun slugifySupplier(supplierName: String): String {
        val noAccents = Normalizer.normalize(supplierName.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val slug = noAccents
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return slug.ifEmpty { "proveedor" }
    }

    fun buildFileName(supplierName: String, issueDate: LocalDate): String =
        "resumen-de-actividades-${slugifySupplier(supplierName)}-$issueDate.pdf"

    fun normalizePhoneForWhatsApp(rawPhone: String?): String? =
        WhatsAppReminderBuilder.normalizeDominicanPhone(rawPhone)

    fun buildShareCaption(
        supplierDisplayName: String,
        serviceCount: Int,
        totalPending: BigDecimal,
        currency: Currency,
    ): String {
        val formattedTotal = DominicanFormatter.formatMoney(totalPending, currency)
        val servicesWord = if (serviceCount == 1) "servicio" else "servicios"
        return "Hola $supplierDisplayName, te comparto el resumen de actividades con los servicios pendientes de pago ($serviceCount $servicesWord, $formattedTotal). Los datos para el pago están en el documento. ¡Gracias!"
    }

    /**
     * Recomputes totals, overdue subtotal, fileName, and optionally the WhatsApp share caption
     * after the artist edits the statement in the PDF preview editor (without mutating DB records).
     */
    fun recalculate(
        statement: AccountStatement,
        customShareCaptionEdited: Boolean = false,
    ): AccountStatement {
        val cleanTitle = statement.documentTitle.trim().ifEmpty { DEFAULT_DOCUMENT_TITLE }
        val cleanSupplier = statement.supplierDisplayName.trim().ifEmpty { "Proveedor" }
        val cleanIssuer = statement.issuerName.trim().ifEmpty { "Omar" }
        val totalPending = statement.items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
        val overdueTotal = statement.items
            .filter { it.status == ReceivableStatus.VENCIDO }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
        val fileName = buildFileName(cleanSupplier, statement.issueDate)
        val caption = if (customShareCaptionEdited && statement.shareCaption.isNotBlank()) {
            statement.shareCaption.trim()
        } else {
            buildShareCaption(
                supplierDisplayName = cleanSupplier,
                serviceCount = statement.items.size,
                totalPending = totalPending,
                currency = statement.currency,
            )
        }
        return statement.copy(
            documentTitle = cleanTitle,
            supplierDisplayName = cleanSupplier,
            issuerName = cleanIssuer,
            totalPending = totalPending,
            overdueTotal = overdueTotal,
            fileName = fileName,
            shareCaption = caption,
        )
    }
}
