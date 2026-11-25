package com.example.domain.rules

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.model.BankAccount
import com.example.domain.model.Currency
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * Optional personal-brand assets rendered into both Account Statements and Event Quotes.
 */
data class PdfBrandAssets(
    val logoBitmap: Bitmap? = null,
    val signatureBitmap: Bitmap? = null,
    val artistSubtitle: String? = null,
    val contactLine: String? = null,
)

data class QuoteLineItem(
    val id: String,
    val description: String,
    val detail: String = "",
    val quantity: Int = 1,
    val unitPrice: BigDecimal = BigDecimal.ZERO,
) {
    val lineTotal: BigDecimal
        get() = unitPrice.multiply(BigDecimal(quantity.coerceAtLeast(1)))
            .setScale(2, RoundingMode.HALF_UP)
}

data class EventQuote(
    val quoteNumber: String,
    val clientName: String,
    val eventTitle: String,
    val eventDate: LocalDate?,
    val eventTime: LocalTime?,
    val venueName: String,
    val issueDate: LocalDate,
    val validUntilDate: LocalDate,
    val currency: Currency,
    val depositPercent: Int = 50,
    val items: List<QuoteLineItem>,
    val notes: String = "",
    val terms: String = QuoteCalculator.DEFAULT_TERMS,
    val issuerName: String,
    val bankAccounts: List<BankAccount> = emptyList(),
) {
    val subtotal: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.lineTotal) }
            .setScale(2, RoundingMode.HALF_UP)

    val depositAmount: BigDecimal
        get() = if (depositPercent <= 0) {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        } else {
            subtotal.multiply(BigDecimal(depositPercent.coerceIn(0, 100)))
                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        }

    val balanceDue: BigDecimal
        get() = subtotal.subtract(depositAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
}

object QuoteCalculator {

    const val DEFAULT_TERMS =
        "Para reservar formalmente la fecha se requiere el anticipo indicado. El saldo restante se liquida el día del evento antes de iniciar la presentación."

    fun buildQuoteNumber(issueDate: LocalDate, sequenceHint: Int = 1): String {
        val seq = sequenceHint.coerceIn(1, 999).toString().padStart(2, '0')
        return "COT-${issueDate.year}${issueDate.monthValue.toString().padStart(2, '0')}${issueDate.dayOfMonth.toString().padStart(2, '0')}-$seq"
    }

    fun buildFileName(clientName: String, issueDate: LocalDate): String {
        val slug = AccountStatementBuilder.slugifySupplier(clientName.ifBlank { "cliente" })
        return "cotizacion-$slug-$issueDate.pdf"
    }

    fun buildShareCaption(quote: EventQuote): String {
        val totalStr = DominicanFormatter.formatMoney(quote.subtotal, quote.currency)
        val eventPart = quote.eventTitle.trim().takeIf { it.isNotEmpty() }?.let { " para $it" }.orEmpty()
        val depositPart = if (quote.depositPercent > 0) {
            val depStr = DominicanFormatter.formatMoney(quote.depositAmount, quote.currency)
            " Reserva de fecha con ${quote.depositPercent}% ($depStr)."
        } else {
            ""
        }
        return "Hola ${quote.clientName.trim().ifBlank { "cliente" }}, te comparto la cotización ${quote.quoteNumber}$eventPart por un total de $totalStr.$depositPart ¡Quedo a tu orden!"
    }
}

/**
 * Generates an editorial A4 portrait (595x842 pt) PDF Event Quote using Android PdfDocument,
 * supporting personal branding (logo, subtitle, contact line, and handwritten/uploaded signature).
 */
object QuotePdfGenerator {

    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2f)
    private const val CONTENT_BOTTOM = 792f
    private const val FOOTER_LINE_Y = 804f

    private const val COL_DESC_X = MARGIN_X
    private const val COL_DESC_W = 295f
    private const val COL_QTY_RIGHT_X = COL_DESC_X + COL_DESC_W + 44f
    private const val COL_UNIT_RIGHT_X = COL_QTY_RIGHT_X + 92f
    private const val COL_TOTAL_RIGHT_X = MARGIN_X + CONTENT_WIDTH

    private val COLOR_HEADER_BG = Color.parseColor("#15171C")
    private val COLOR_AMBER = Color.parseColor("#F59E0B")
    private val COLOR_AMBER_BROWN = Color.parseColor("#92400E")
    private val COLOR_INK = Color.parseColor("#15171C")
    private val COLOR_SECONDARY = Color.parseColor("#475569")
    private val COLOR_MUTED = Color.parseColor("#64748B")
    private val COLOR_LINE = Color.parseColor("#E2E8F0")
    private val COLOR_SUBTLE = Color.parseColor("#F8FAFC")
    private val COLOR_TABLE_BG = Color.parseColor("#F1F5F9")
    private val COLOR_WARM_BG = Color.parseColor("#FFFBEB")
    private val COLOR_WARM_BORDER = Color.parseColor("#FDE68A")

    private class Paints {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val headerTitle = textPaint(16.5f, Color.WHITE, Typeface.BOLD)
        val headerSub = textPaint(9.2f, Color.parseColor("#CBD5E1"), Typeface.NORMAL)
        val headerMeta = textPaint(8.5f, Color.parseColor("#E2E8F0"), Typeface.NORMAL, Paint.Align.RIGHT)
        val headerAccent = textPaint(9f, COLOR_AMBER, Typeface.BOLD, Paint.Align.RIGHT)

        val eyebrow = textPaint(7.8f, COLOR_MUTED, Typeface.BOLD)
        val titleMedium = textPaint(12.5f, COLOR_INK, Typeface.BOLD)
        val body = textPaint(9f, COLOR_INK, Typeface.NORMAL)
        val bodyBold = textPaint(9f, COLOR_INK, Typeface.BOLD)
        val bodyMuted = textPaint(8.2f, COLOR_MUTED, Typeface.NORMAL)
        val bodyRight = textPaint(9f, COLOR_INK, Typeface.NORMAL, Paint.Align.RIGHT)
        val bodyBoldRight = textPaint(9f, COLOR_INK, Typeface.BOLD, Paint.Align.RIGHT)
        val totalBigRight = textPaint(13.5f, COLOR_INK, Typeface.BOLD, Paint.Align.RIGHT)
        val accentBoldRight = textPaint(9.5f, COLOR_AMBER_BROWN, Typeface.BOLD, Paint.Align.RIGHT)

        val tableHeader = textPaint(8f, COLOR_SECONDARY, Typeface.BOLD)
        val tableHeaderRight = textPaint(8f, COLOR_SECONDARY, Typeface.BOLD, Paint.Align.RIGHT)
        val sectionHeading = textPaint(9f, COLOR_INK, Typeface.BOLD)
        val bankNum = textPaint(9.6f, COLOR_AMBER_BROWN, Typeface.BOLD)
        val footerLeft = textPaint(8f, COLOR_MUTED, Typeface.NORMAL)
        val footerRight = textPaint(8f, COLOR_MUTED, Typeface.NORMAL, Paint.Align.RIGHT)

        private fun textPaint(
            size: Float,
            color: Int,
            style: Int,
            align: Paint.Align = Paint.Align.LEFT,
        ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
            textAlign = align
        }
    }

    fun generate(
        quote: EventQuote,
        brandAssets: PdfBrandAssets? = null,
    ): GeneratedStatementPdf {
        val paints = Paints()
        val totalPages = renderInternal(quote, brandAssets, paints, document = null, totalPagesHint = 1)
        val document = PdfDocument()
        try {
            val actualPages = renderInternal(quote, brandAssets, paints, document = document, totalPagesHint = totalPages)
            val out = ByteArrayOutputStream()
            document.writeTo(out)
            return GeneratedStatementPdf(
                bytes = out.toByteArray(),
                pageCount = actualPages,
                fileName = QuoteCalculator.buildFileName(quote.clientName, quote.issueDate),
            )
        } finally {
            document.close()
        }
    }

    private fun renderInternal(
        quote: EventQuote,
        brandAssets: PdfBrandAssets?,
        paints: Paints,
        document: PdfDocument?,
        totalPagesHint: Int,
    ): Int {
        var pageNumber = 0
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun finishCurrentPage() {
            canvas?.let { c ->
                paints.stroke.color = COLOR_LINE
                c.drawLine(MARGIN_X, FOOTER_LINE_Y, COL_TOTAL_RIGHT_X, FOOTER_LINE_Y, paints.stroke)
                val contactSuffix = brandAssets?.contactLine?.trim()?.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
                val left = truncateText("${quote.quoteNumber} · ${quote.issuerName}$contactSuffix", paints.footerLeft, 390f)
                c.drawText(left, MARGIN_X, FOOTER_LINE_Y + 14f, paints.footerLeft)
                c.drawText("Página $pageNumber de $totalPagesHint", COL_TOTAL_RIGHT_X, FOOTER_LINE_Y + 14f, paints.footerRight)
            }
            if (document != null && currentPage != null) {
                document.finishPage(currentPage)
            }
            currentPage = null
            canvas = null
        }

        fun startPage(isFirst: Boolean) {
            if (pageNumber > 0) finishCurrentPage()
            pageNumber += 1
            if (document != null) {
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                currentPage = document.startPage(info)
                canvas = currentPage!!.canvas
            }
            y = if (isFirst) {
                canvas?.let { drawHeader(it, paints, quote, brandAssets) }
                94f
            } else {
                canvas?.let { drawContinuationHeader(it, paints, quote) }
                52f
            }
        }

        fun ensureSpace(needed: Float, repeatTableHeader: Boolean = false) {
            if (y + needed > CONTENT_BOTTOM) {
                startPage(isFirst = false)
                if (repeatTableHeader) {
                    canvas?.let { drawTableHeader(it, paints, y) }
                    y += 24f
                }
            }
        }

        startPage(isFirst = true)

        // 1. Client & Event summary card
        val infoCardHeight = 68f
        canvas?.let { drawClientAndEventCard(it, paints, quote, y, infoCardHeight) }
        y += infoCardHeight + 16f

        // 2. Services Table
        ensureSpace(58f)
        canvas?.let { drawTableHeader(it, paints, y) }
        y += 24f

        for (item in quote.items) {
            val descLines = wrapText(item.description, paints.bodyBold, COL_DESC_W - 8f).take(3)
            val detailLines = if (item.detail.isNotBlank()) {
                wrapText(item.detail, paints.bodyMuted, COL_DESC_W - 8f).take(2)
            } else emptyList()
            val textHeight = (descLines.size * 11.5f) + (detailLines.size * 10f)
            val rowHeight = maxOf(textHeight, 16f) + 12f
            ensureSpace(rowHeight, repeatTableHeader = true)
            canvas?.let { c ->
                val textTop = y + 14f
                var ly = textTop
                descLines.forEach { line ->
                    c.drawText(line, COL_DESC_X + 4f, ly, paints.bodyBold)
                    ly += 11.5f
                }
                detailLines.forEach { line ->
                    c.drawText(line, COL_DESC_X + 4f, ly, paints.bodyMuted)
                    ly += 10f
                }
                c.drawText(item.quantity.toString(), COL_QTY_RIGHT_X, textTop, paints.bodyRight)
                c.drawText(
                    DominicanFormatter.formatMoney(item.unitPrice, quote.currency),
                    COL_UNIT_RIGHT_X,
                    textTop,
                    paints.bodyRight,
                )
                c.drawText(
                    DominicanFormatter.formatMoney(item.lineTotal, quote.currency),
                    COL_TOTAL_RIGHT_X - 4f,
                    textTop,
                    paints.bodyBoldRight,
                )
                paints.stroke.color = COLOR_LINE
                c.drawLine(MARGIN_X, y + rowHeight, COL_TOTAL_RIGHT_X, y + rowHeight, paints.stroke)
            }
            y += rowHeight
        }

        // 3. Totals & Deposit box (right aligned)
        val hasDeposit = quote.depositPercent in 1..99
        val totalsHeight = if (hasDeposit) 72f else 36f
        ensureSpace(totalsHeight + 14f)
        canvas?.let { c ->
            val boxW = 248f
            val left = COL_TOTAL_RIGHT_X - boxW
            val rect = RectF(left, y + 6f, COL_TOTAL_RIGHT_X, y + 6f + totalsHeight)
            paints.fill.color = COLOR_SUBTLE
            c.drawRoundRect(rect, 6f, 6f, paints.fill)
            paints.stroke.color = COLOR_LINE
            c.drawRoundRect(rect, 6f, 6f, paints.stroke)

            val labelX = left + 12f
            val valX = COL_TOTAL_RIGHT_X - 12f
            var ty = y + 27f
            c.drawText("TOTAL COTIZADO", labelX, ty, paints.bodyBold)
            c.drawText(DominicanFormatter.formatMoney(quote.subtotal, quote.currency), valX, ty, paints.totalBigRight)
            if (hasDeposit) {
                ty += 20f
                c.drawText("Anticipo reserva (${quote.depositPercent}%)", labelX, ty, paints.bodyMuted)
                c.drawText(
                    DominicanFormatter.formatMoney(quote.depositAmount, quote.currency),
                    valX,
                    ty,
                    paints.accentBoldRight,
                )
                ty += 17f
                c.drawText("Saldo restante", labelX, ty, paints.bodyMuted)
                c.drawText(
                    DominicanFormatter.formatMoney(quote.balanceDue, quote.currency),
                    valX,
                    ty,
                    paints.bodyBoldRight,
                )
            }
        }
        y += totalsHeight + 20f

        // 4. Optional Notes
        val noteLines = wrapText(quote.notes, paints.body, CONTENT_WIDTH - 20f)
        if (noteLines.isNotEmpty()) {
            val boxH = (noteLines.size * 12f) + 26f
            ensureSpace(boxH + 10f)
            canvas?.let { c ->
                val rect = RectF(MARGIN_X, y, COL_TOTAL_RIGHT_X, y + boxH)
                paints.fill.color = COLOR_WARM_BG
                c.drawRoundRect(rect, 6f, 6f, paints.fill)
                paints.stroke.color = COLOR_WARM_BORDER
                c.drawRoundRect(rect, 6f, 6f, paints.stroke)
                c.drawText("NOTAS DEL SERVICIO", MARGIN_X + 10f, y + 14f, paints.eyebrow)
                var ny = y + 27f
                noteLines.forEach { line ->
                    c.drawText(line, MARGIN_X + 10f, ny, paints.body)
                    ny += 12f
                }
            }
            y += boxH + 12f
        }

        // 5. Bank accounts for deposit transfer
        if (quote.bankAccounts.isNotEmpty()) {
            val rows = quote.bankAccounts.chunked(2)
            val cardH = 54f
            ensureSpace(16f + cardH + 8f)
            canvas?.drawText("CUENTAS PARA RESERVA / TRANSFERENCIA", MARGIN_X, y + 10f, paints.sectionHeading)
            y += 16f
            rows.forEachIndexed { idx, pair ->
                if (idx > 0) ensureSpace(cardH + 6f)
                canvas?.let { c -> drawBankCards(c, paints, pair, y, cardH) }
                y += cardH + if (idx < rows.lastIndex) 8f else 14f
            }
        }

        // 6. Terms + Signature block
        val termLines = wrapText(quote.terms, paints.bodyMuted, CONTENT_WIDTH)
        val sigHeight = if (brandAssets?.signatureBitmap != null) 78f else 42f
        val termsTotalH = (if (termLines.isNotEmpty()) 14f + (termLines.size * 11f) + 12f else 0f) + sigHeight
        ensureSpace(termsTotalH)

        if (termLines.isNotEmpty()) {
            canvas?.drawText("CONDICIONES", MARGIN_X, y + 10f, paints.eyebrow)
            y += 15f
            termLines.forEach { line ->
                canvas?.drawText(line, MARGIN_X, y + 8f, paints.bodyMuted)
                y += 11f
            }
            y += 12f
        }

        canvas?.let { c ->
            val sigBmp = brandAssets?.signatureBitmap
            if (sigBmp != null && !sigBmp.isRecycled) {
                val maxW = 140f
                val maxH = 44f
                val scale = minOf(maxW / sigBmp.width.coerceAtLeast(1), maxH / sigBmp.height.coerceAtLeast(1))
                val dstW = sigBmp.width * scale
                val dstH = sigBmp.height * scale
                val dst = RectF(MARGIN_X, y, MARGIN_X + dstW, y + dstH)
                c.drawBitmap(sigBmp, null, dst, paints.bitmapPaint)
                y += maxH + 4f
            } else {
                y += 12f
            }
            paints.stroke.color = COLOR_LINE
            c.drawLine(MARGIN_X, y, MARGIN_X + 185f, y, paints.stroke)
            c.drawText(quote.issuerName, MARGIN_X, y + 13f, paints.bodyBold)
            val sub = brandAssets?.artistSubtitle?.trim().orEmpty()
            if (sub.isNotEmpty()) {
                c.drawText(sub, MARGIN_X, y + 24f, paints.bodyMuted)
            }
        }

        finishCurrentPage()
        return pageNumber
    }

    private fun drawHeader(
        canvas: Canvas,
        paints: Paints,
        quote: EventQuote,
        brandAssets: PdfBrandAssets?,
    ) {
        paints.fill.color = COLOR_HEADER_BG
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 78f, paints.fill)
        paints.fill.color = COLOR_AMBER
        canvas.drawRect(0f, 78f, PAGE_WIDTH.toFloat(), 81f, paints.fill)

        var textStartX = MARGIN_X
        val logo = brandAssets?.logoBitmap
        if (logo != null && !logo.isRecycled) {
            val boxSize = 44f
            val boxTop = 17f
            val bgRect = RectF(MARGIN_X, boxTop, MARGIN_X + boxSize, boxTop + boxSize)
            paints.fill.color = Color.WHITE
            canvas.drawRoundRect(bgRect, 8f, 8f, paints.fill)
            val pad = 4f
            val avail = boxSize - pad * 2f
            val scale = minOf(avail / logo.width.coerceAtLeast(1), avail / logo.height.coerceAtLeast(1))
            val dw = logo.width * scale
            val dh = logo.height * scale
            val dx = MARGIN_X + (boxSize - dw) / 2f
            val dy = boxTop + (boxSize - dh) / 2f
            canvas.drawBitmap(logo, null, RectF(dx, dy, dx + dw, dy + dh), paints.bitmapPaint)
            textStartX = MARGIN_X + boxSize + 12f
        }

        val maxLeftW = 315f - (textStartX - MARGIN_X)
        canvas.drawText(truncateText("Cotización de servicios", paints.headerTitle, maxLeftW), textStartX, 34f, paints.headerTitle)
        val subLine = listOfNotNull(
            quote.issuerName.takeIf { it.isNotBlank() },
            brandAssets?.artistSubtitle?.trim()?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        canvas.drawText(truncateText(subLine, paints.headerSub, maxLeftW), textStartX, 53f, paints.headerSub)

        canvas.drawText(quote.quoteNumber, COL_TOTAL_RIGHT_X, 30f, paints.headerAccent)
        canvas.drawText(
            "Emitida: ${formatLongDate(quote.issueDate)}",
            COL_TOTAL_RIGHT_X,
            46f,
            paints.headerMeta,
        )
        canvas.drawText(
            "Válida hasta: ${formatLongDate(quote.validUntilDate)}",
            COL_TOTAL_RIGHT_X,
            60f,
            paints.headerMeta,
        )
    }

    private fun drawContinuationHeader(canvas: Canvas, paints: Paints, quote: EventQuote) {
        paints.fill.color = COLOR_HEADER_BG
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 36f, paints.fill)
        paints.fill.color = COLOR_AMBER
        canvas.drawRect(0f, 36f, PAGE_WIDTH.toFloat(), 38f, paints.fill)
        canvas.drawText(
            truncateText("Cotización ${quote.quoteNumber} · ${quote.clientName}", paints.headerSub, 400f),
            MARGIN_X,
            23f,
            paints.headerSub,
        )
        canvas.drawText("${quote.currency.code} (${quote.currency.symbol})", COL_TOTAL_RIGHT_X, 23f, paints.headerAccent)
    }

    private fun drawClientAndEventCard(
        canvas: Canvas,
        paints: Paints,
        quote: EventQuote,
        topY: Float,
        height: Float,
    ) {
        val rect = RectF(MARGIN_X, topY, COL_TOTAL_RIGHT_X, topY + height)
        paints.fill.color = COLOR_SUBTLE
        canvas.drawRoundRect(rect, 6f, 6f, paints.fill)
        paints.stroke.color = COLOR_LINE
        canvas.drawRoundRect(rect, 6f, 6f, paints.stroke)

        val leftX = MARGIN_X + 12f
        val midX = MARGIN_X + CONTENT_WIDTH * 0.52f
        canvas.drawText("PREPARADA PARA", leftX, topY + 15f, paints.eyebrow)
        canvas.drawText(
            truncateText(quote.clientName.ifBlank { "Cliente" }, paints.titleMedium, (midX - leftX) - 12f),
            leftX,
            topY + 33f,
            paints.titleMedium,
        )
        val eventLabel = quote.eventTitle.ifBlank { "Evento musical" }
        canvas.drawText(
            truncateText(eventLabel, paints.bodyMuted, (midX - leftX) - 12f),
            leftX,
            topY + 49f,
            paints.bodyMuted,
        )

        canvas.drawText("DETALLES DEL EVENTO", midX, topY + 15f, paints.eyebrow)
        val dateStr = buildString {
            quote.eventDate?.let { append(formatLongDate(it)) } ?: append("Fecha por definir")
            quote.eventTime?.let { append(" · ${DominicanFormatter.formatTime12h(it)}") }
        }
        val rightMaxW = COL_TOTAL_RIGHT_X - midX - 12f
        canvas.drawText(truncateText(dateStr, paints.bodyBold, rightMaxW), midX, topY + 32f, paints.bodyBold)
        val venueStr = quote.venueName.ifBlank { "Lugar por confirmar" }
        canvas.drawText(truncateText(venueStr, paints.bodyMuted, rightMaxW), midX, topY + 47f, paints.bodyMuted)
        canvas.drawText(
            "Moneda: ${quote.currency.code} (${quote.currency.symbol})",
            midX,
            topY + 60f,
            paints.bodyMuted,
        )
    }

    private fun drawTableHeader(canvas: Canvas, paints: Paints, topY: Float) {
        paints.fill.color = COLOR_TABLE_BG
        canvas.drawRect(MARGIN_X, topY, COL_TOTAL_RIGHT_X, topY + 22f, paints.fill)
        val baseline = topY + 14.5f
        canvas.drawText("CONCEPTO", COL_DESC_X + 4f, baseline, paints.tableHeader)
        canvas.drawText("CANT.", COL_QTY_RIGHT_X, baseline, paints.tableHeaderRight)
        canvas.drawText("PRECIO", COL_UNIT_RIGHT_X, baseline, paints.tableHeaderRight)
        canvas.drawText("SUBTOTAL", COL_TOTAL_RIGHT_X - 4f, baseline, paints.tableHeaderRight)
        paints.stroke.color = COLOR_LINE
        canvas.drawLine(MARGIN_X, topY + 22f, COL_TOTAL_RIGHT_X, topY + 22f, paints.stroke)
    }

    private fun drawBankCards(
        canvas: Canvas,
        paints: Paints,
        pair: List<BankAccount>,
        topY: Float,
        cardHeight: Float,
    ) {
        val gap = 10f
        val cardW = if (pair.size == 1) CONTENT_WIDTH else (CONTENT_WIDTH - gap) / 2f
        pair.forEachIndexed { idx, acc ->
            val left = MARGIN_X + idx * (cardW + gap)
            val rect = RectF(left, topY, left + cardW, topY + cardHeight)
            paints.fill.color = COLOR_SUBTLE
            canvas.drawRoundRect(rect, 6f, 6f, paints.fill)
            paints.stroke.color = if (acc.isDefault) COLOR_AMBER else COLOR_LINE
            canvas.drawRoundRect(rect, 6f, 6f, paints.stroke)
            val padX = left + 10f
            val maxW = cardW - 20f
            val typeLabel = acc.accountType?.let { " · ${it.label}" }.orEmpty()
            canvas.drawText(truncateText("${acc.bankName}$typeLabel", paints.bodyBold, maxW), padX, topY + 16f, paints.bodyBold)
            canvas.drawText(truncateText("Titular: ${acc.holderName}", paints.bodyMuted, maxW), padX, topY + 29f, paints.bodyMuted)
            canvas.drawText(truncateText(acc.accountNumber, paints.bankNum, maxW), padX, topY + 44f, paints.bankNum)
        }
    }

    private fun formatLongDate(date: LocalDate): String =
        "${date.dayOfMonth} ${DominicanFormatter.formatShortMonth(date.month)} ${date.year}"

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        val words = clean.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                } else {
                    lines.add(truncateText(word, paint, maxWidth))
                    current = StringBuilder()
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }
}
