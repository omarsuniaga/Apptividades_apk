package com.example.domain.rules

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.model.BankAccount
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate

data class GeneratedStatementPdf(
    val bytes: ByteArray,
    val pageCount: Int,
    val fileName: String,
)

/**
 * Generates an on-device A4 portrait (595x842 pt) PDF account statement using Android PdfDocument.
 * Guarantees rows are never split across page breaks and draws accurate "Página X de Y" footers
 * via a lightweight two-pass layout calculation.
 */
object AccountStatementPdfGenerator {

    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    private const val MARGIN_X = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2f) // 523f
    private const val FOOTER_LINE_Y = 804f
    private const val CONTENT_BOTTOM = 792f

    // Table column layout: Fecha/Hora | Servicio/Ubicación | Importe (total = 523f)
    private const val COL_DATE_X = MARGIN_X
    private const val COL_DATE_W = 84f
    private const val COL_SERVICE_X = COL_DATE_X + COL_DATE_W
    private const val COL_SERVICE_W = 349f
    private const val COL_AMOUNT_RIGHT_X = MARGIN_X + CONTENT_WIDTH

    private val COLOR_HEADER_BG = Color.parseColor("#15171C")
    private val COLOR_AMBER = Color.parseColor("#F59E0B")
    private val COLOR_AMBER_BROWN = Color.parseColor("#92400E")
    private val COLOR_INK = Color.parseColor("#15171C")
    private val COLOR_TEXT_SECONDARY = Color.parseColor("#475569")
    private val COLOR_TEXT_MUTED = Color.parseColor("#64748B")
    private val COLOR_LINE = Color.parseColor("#E2E8F0")
    private val COLOR_SURFACE_SUBTLE = Color.parseColor("#F8FAFC")
    private val COLOR_TABLE_HEADER_BG = Color.parseColor("#F1F5F9")
    private val COLOR_DANGER_DARK_RED = Color.parseColor("#991B1B")
    private val COLOR_QUOTE_BG = Color.parseColor("#FFFBEB")
    private val COLOR_QUOTE_BORDER = Color.parseColor("#FDE68A")
    private val COLOR_QUOTE_TEXT = Color.parseColor("#451A03")

    private class Paints {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val headerTitle = textPaint(17f, Color.WHITE, Typeface.BOLD)
        val headerSubtitle = textPaint(9.5f, Color.parseColor("#CBD5E1"), Typeface.NORMAL)
        val headerDate = textPaint(9f, Color.parseColor("#E2E8F0"), Typeface.NORMAL, Paint.Align.RIGHT)
        val headerCurrency = textPaint(9f, COLOR_AMBER, Typeface.BOLD, Paint.Align.RIGHT)

        val eyebrow = textPaint(8f, COLOR_TEXT_MUTED, Typeface.BOLD)
        val supplierTitle = textPaint(13.5f, COLOR_INK, Typeface.BOLD)
        val totalAmount = textPaint(15f, COLOR_INK, Typeface.BOLD)
        val overdueSub = textPaint(8.5f, COLOR_DANGER_DARK_RED, Typeface.BOLD)

        val tableHeader = textPaint(8.2f, COLOR_TEXT_SECONDARY, Typeface.BOLD)
        val tableHeaderRight = textPaint(8.2f, COLOR_TEXT_SECONDARY, Typeface.BOLD, Paint.Align.RIGHT)
        val cellPrimary = textPaint(9f, COLOR_INK, Typeface.NORMAL)
        val cellPrimaryBold = textPaint(9f, COLOR_INK, Typeface.BOLD)
        val cellMuted = textPaint(8f, COLOR_TEXT_MUTED, Typeface.NORMAL)
        val cellAmountRight = textPaint(9f, COLOR_INK, Typeface.BOLD, Paint.Align.RIGHT)

        val sectionHeading = textPaint(9.2f, COLOR_INK, Typeface.BOLD)
        val bankTitle = textPaint(9f, COLOR_INK, Typeface.BOLD)
        val bankHolder = textPaint(8.2f, COLOR_TEXT_SECONDARY, Typeface.NORMAL)
        val bankAccountNum = textPaint(10f, COLOR_AMBER_BROWN, Typeface.BOLD)

        val friendlyLine = textPaint(8.8f, COLOR_TEXT_SECONDARY, Typeface.NORMAL)
        val quoteItalic = textPaint(8.8f, COLOR_QUOTE_TEXT, Typeface.ITALIC)
        val quoteAuthor = textPaint(8f, COLOR_AMBER_BROWN, Typeface.BOLD)
        val quoteBody = textPaint(8.6f, COLOR_INK, Typeface.NORMAL)
        val signature = textPaint(9.2f, COLOR_INK, Typeface.BOLD)

        val footerLeft = textPaint(8f, COLOR_TEXT_MUTED, Typeface.NORMAL)
        val footerRight = textPaint(8f, COLOR_TEXT_MUTED, Typeface.NORMAL, Paint.Align.RIGHT)

        private fun textPaint(
            size: Float,
            color: Int,
            style: Int,
            align: Paint.Align = Paint.Align.LEFT,
        ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
            textAlign = align
        }
    }

    fun generate(
        statement: AccountStatement,
        brandAssets: PdfBrandAssets? = null,
    ): GeneratedStatementPdf {
        val paints = Paints()
        // Pass 1: count exact pages so "Página X de Y" is exact on every page
        val totalPages = renderInternal(statement, brandAssets, paints, document = null, totalPagesHint = 1)
        // Pass 2: render real PdfDocument
        val document = PdfDocument()
        try {
            val actualPages = renderInternal(statement, brandAssets, paints, document = document, totalPagesHint = totalPages)
            val out = ByteArrayOutputStream()
            document.writeTo(out)
            return GeneratedStatementPdf(
                bytes = out.toByteArray(),
                pageCount = actualPages,
                fileName = statement.fileName,
            )
        } finally {
            document.close()
        }
    }

    private fun renderInternal(
        statement: AccountStatement,
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
            val activeCanvas = canvas
            if (activeCanvas != null) {
                drawFooter(
                    canvas = activeCanvas,
                    paints = paints,
                    documentTitle = statement.documentTitle.ifBlank { AccountStatementBuilder.DEFAULT_DOCUMENT_TITLE },
                    supplierName = statement.supplierDisplayName,
                    contactLine = brandAssets?.contactLine,
                    pageNumber = pageNumber,
                    totalPages = totalPagesHint,
                )
            }
            if (document != null && currentPage != null) {
                document.finishPage(currentPage)
            }
            currentPage = null
            canvas = null
        }

        fun startPage(isFirstPage: Boolean) {
            if (pageNumber > 0) finishCurrentPage()
            pageNumber += 1
            if (document != null) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                currentPage = document.startPage(pageInfo)
                canvas = currentPage!!.canvas
            }
            y = if (isFirstPage) {
                canvas?.let { drawFullHeader(it, paints, statement, brandAssets) }
                88f
            } else {
                canvas?.let { drawContinuationHeader(it, paints, statement) }
                52f
            }
        }

        fun ensureSpace(neededHeight: Float, repeatTableHeader: Boolean = false) {
            if (y + neededHeight > CONTENT_BOTTOM) {
                startPage(isFirstPage = false)
                if (repeatTableHeader) {
                    canvas?.let { drawTableHeader(it, paints, y) }
                    y += 24f
                }
            }
        }

        // Start Page 1
        startPage(isFirstPage = true)

        // 1. "PARA" + Total box
        val hasOverdue = statement.overdueTotal > BigDecimal.ZERO
        val summaryBoxHeight = if (hasOverdue) 58f else 46f
        canvas?.let { drawParaAndTotalBox(it, paints, statement, y, summaryBoxHeight) }
        y += summaryBoxHeight + 14f

        // 2. Services Table
        ensureSpace(24f + 34f, repeatTableHeader = false)
        canvas?.let { drawTableHeader(it, paints, y) }
        y += 24f

        for (item in statement.items) {
            val serviceLines = wrapText(item.serviceDescription, paints.cellPrimary, COL_SERVICE_W - 10f).take(3)
            val venueLines = if (item.venueName.isNotBlank()) {
                wrapText(item.venueName, paints.cellMuted, COL_SERVICE_W - 10f).take(2)
            } else {
                emptyList()
            }
            val serviceBlockHeight = (serviceLines.size * 11.5f) + (venueLines.size * 10f)
            val dateBlockHeight = if (item.eventTime != null) 22f else 12f
            val rowHeight = maxOf(serviceBlockHeight, dateBlockHeight, 22f) + 10f

            // Never split a row across page breaks
            ensureSpace(rowHeight, repeatTableHeader = true)
            canvas?.let {
                drawTableRow(
                    canvas = it,
                    paints = paints,
                    item = item,
                    serviceLines = serviceLines,
                    venueLines = venueLines,
                    currency = statement.currency,
                    topY = y,
                    rowHeight = rowHeight,
                )
            }
            y += rowHeight
        }

        // Total row at end of table
        val totalRowHeight = 26f
        ensureSpace(totalRowHeight, repeatTableHeader = false)
        canvas?.let { drawTableTotalRow(it, paints, statement, y, totalRowHeight) }
        y += totalRowHeight + 16f

        // 3. "DATOS PARA EL PAGO"
        val bankHeadingHeight = 16f
        if (statement.bankAccounts.isEmpty()) {
            val fallbackCardHeight = 34f
            ensureSpace(bankHeadingHeight + fallbackCardHeight + 4f)
            canvas?.let { drawSectionHeading(it, paints, "DATOS PARA EL PAGO", y) }
            y += bankHeadingHeight
            canvas?.let {
                drawNoBankAccountCard(it, paints, statement.issuerName, y, fallbackCardHeight)
            }
            y += fallbackCardHeight + 12f
        } else {
            val rows = statement.bankAccounts.chunked(2)
            val cardHeight = 56f
            val rowGap = 8f
            // Keep heading with at least first card row
            ensureSpace(bankHeadingHeight + cardHeight + 4f)
            canvas?.let { drawSectionHeading(it, paints, "DATOS PARA EL PAGO", y) }
            y += bankHeadingHeight

            rows.forEachIndexed { idx, pair ->
                if (idx > 0) {
                    ensureSpace(cardHeight + 4f)
                }
                canvas?.let { drawBankCardRow(it, paints, pair, y, cardHeight) }
                y += cardHeight + if (idx < rows.lastIndex) rowGap else 12f
            }
        }

        // 4. One friendly line only (warm, never threatening) — omitted if cleared in editor
        val friendlyLines = wrapText(statement.friendlyNotice, paints.friendlyLine, CONTENT_WIDTH)
        if (friendlyLines.isNotEmpty()) {
            val friendlyHeight = (friendlyLines.size * 12f) + 8f
            ensureSpace(friendlyHeight)
            canvas?.let { c ->
                var lineY = y + 10f
                friendlyLines.forEach { line ->
                    c.drawText(line, MARGIN_X, lineY, paints.friendlyLine)
                    lineY += 12f
                }
            }
            y += friendlyHeight + 6f
        }

        // 5. Closing gratitude box (quote + gratitude message + issuer) — omitted if cleared in editor
        val innerBoxWidth = CONTENT_WIDTH - 28f
        val quoteLines = wrapText(statement.closingQuote, paints.quoteItalic, innerBoxWidth)
        val cleanAttribution = statement.closingQuoteAttribution.trim()
        val bodyLines = wrapText(statement.gratitudeMessage, paints.quoteBody, innerBoxWidth)
        val hasSigBitmap = brandAssets?.signatureBitmap != null && !brandAssets.signatureBitmap.isRecycled
        if (quoteLines.isNotEmpty() || bodyLines.isNotEmpty() || hasSigBitmap) {
            val quoteSectionHeight = if (quoteLines.isNotEmpty()) {
                (quoteLines.size * 11.5f) + (if (cleanAttribution.isNotEmpty()) 13f else 0f) + (if (bodyLines.isNotEmpty()) 7f else 0f)
            } else {
                0f
            }
            val bodySectionHeight = if (bodyLines.isNotEmpty()) (bodyLines.size * 11.5f) else 0f
            val signatureImageExtra = if (hasSigBitmap) 44f else 0f
            val closingHeight = 12f + quoteSectionHeight + bodySectionHeight + signatureImageExtra + 18f + 10f

            ensureSpace(closingHeight)
            canvas?.let {
                drawClosingBox(
                    canvas = it,
                    paints = paints,
                    quoteLines = quoteLines,
                    quoteAttribution = cleanAttribution,
                    bodyLines = bodyLines,
                    issuerName = statement.issuerName,
                    brandAssets = brandAssets,
                    topY = y,
                    boxHeight = closingHeight,
                )
            }
            y += closingHeight
        }

        finishCurrentPage()
        return pageNumber
    }

    private fun drawFullHeader(
        canvas: Canvas,
        paints: Paints,
        statement: AccountStatement,
        brandAssets: PdfBrandAssets?,
    ) {
        paints.fill.color = COLOR_HEADER_BG
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 72f, paints.fill)

        paints.fill.color = COLOR_AMBER
        canvas.drawRect(0f, 72f, PAGE_WIDTH.toFloat(), 75f, paints.fill)

        var textStartX = MARGIN_X
        val logo = brandAssets?.logoBitmap
        if (logo != null && !logo.isRecycled) {
            val boxSize = 42f
            val boxTop = 15f
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

        val maxLeftW = 330f - (textStartX - MARGIN_X)
        val docTitle = statement.documentTitle.ifBlank { AccountStatementBuilder.DEFAULT_DOCUMENT_TITLE }
        canvas.drawText(truncateText(docTitle, paints.headerTitle, maxLeftW), textStartX, 34f, paints.headerTitle)
        val issuerLine = listOfNotNull(
            statement.issuerName.takeIf { it.isNotBlank() },
            brandAssets?.artistSubtitle?.trim()?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        canvas.drawText(truncateText(issuerLine, paints.headerSubtitle, maxLeftW), textStartX, 53f, paints.headerSubtitle)

        val dateLabel = formatIssueDate(statement.issueDate)
        canvas.drawText("Fecha de emisión: $dateLabel", COL_AMOUNT_RIGHT_X, 33f, paints.headerDate)
        canvas.drawText(
            "Moneda: ${statement.currency.code} (${statement.currency.symbol})",
            COL_AMOUNT_RIGHT_X,
            52f,
            paints.headerCurrency,
        )
    }

    private fun drawContinuationHeader(canvas: Canvas, paints: Paints, statement: AccountStatement) {
        paints.fill.color = COLOR_HEADER_BG
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 36f, paints.fill)
        paints.fill.color = COLOR_AMBER
        canvas.drawRect(0f, 36f, PAGE_WIDTH.toFloat(), 38f, paints.fill)

        val docTitle = statement.documentTitle.ifBlank { AccountStatementBuilder.DEFAULT_DOCUMENT_TITLE }
        canvas.drawText(
            truncateText("$docTitle · ${statement.supplierDisplayName}", paints.headerSubtitle, 420f),
            MARGIN_X,
            23f,
            paints.headerSubtitle,
        )
        canvas.drawText(
            "${statement.currency.code} (${statement.currency.symbol})",
            COL_AMOUNT_RIGHT_X,
            23f,
            paints.headerCurrency,
        )
    }

    private fun drawParaAndTotalBox(
        canvas: Canvas,
        paints: Paints,
        statement: AccountStatement,
        topY: Float,
        boxHeight: Float,
    ) {
        // Left: PARA + supplier
        canvas.drawText("PARA", MARGIN_X, topY + 12f, paints.eyebrow)
        val supplierLines = wrapText(statement.supplierDisplayName, paints.supplierTitle, 275f).take(2)
        var sy = topY + 29f
        supplierLines.forEach { line ->
            canvas.drawText(line, MARGIN_X, sy, paints.supplierTitle)
            sy += 16f
        }

        // Right: TOTAL PENDIENTE box
        val boxW = 225f
        val boxLeft = COL_AMOUNT_RIGHT_X - boxW
        val rect = RectF(boxLeft, topY, COL_AMOUNT_RIGHT_X, topY + boxHeight)

        paints.fill.color = COLOR_SURFACE_SUBTLE
        canvas.drawRoundRect(rect, 6f, 6f, paints.fill)
        paints.stroke.color = COLOR_LINE
        canvas.drawRoundRect(rect, 6f, 6f, paints.stroke)

        val padX = boxLeft + 12f
        canvas.drawText("TOTAL PENDIENTE", padX, topY + 15f, paints.eyebrow)
        canvas.drawText(
            DominicanFormatter.formatMoney(statement.totalPending, statement.currency),
            padX,
            topY + 34f,
            paints.totalAmount,
        )
        if (statement.overdueTotal > BigDecimal.ZERO) {
            canvas.drawText(
                "De los cuales vencido: ${DominicanFormatter.formatMoney(statement.overdueTotal, statement.currency)}",
                padX,
                topY + 49f,
                paints.overdueSub,
            )
        }
    }

    private fun drawTableHeader(canvas: Canvas, paints: Paints, topY: Float) {
        paints.fill.color = COLOR_TABLE_HEADER_BG
        canvas.drawRect(MARGIN_X, topY, COL_AMOUNT_RIGHT_X, topY + 22f, paints.fill)

        val baseline = topY + 14.5f
        canvas.drawText("FECHA", COL_DATE_X + 4f, baseline, paints.tableHeader)
        canvas.drawText("SERVICIO", COL_SERVICE_X + 4f, baseline, paints.tableHeader)
        canvas.drawText("IMPORTE", COL_AMOUNT_RIGHT_X - 4f, baseline, paints.tableHeaderRight)

        paints.stroke.color = COLOR_LINE
        canvas.drawLine(MARGIN_X, topY + 22f, COL_AMOUNT_RIGHT_X, topY + 22f, paints.stroke)
    }

    private fun drawTableRow(
        canvas: Canvas,
        paints: Paints,
        item: StatementLineItem,
        serviceLines: List<String>,
        venueLines: List<String>,
        currency: com.example.domain.model.Currency,
        topY: Float,
        rowHeight: Float,
    ) {
        val textTop = topY + 13f

        // Date + am/pm time
        val dateText = "${item.eventDate.dayOfMonth} ${DominicanFormatter.formatShortMonth(item.eventDate.month)} ${item.eventDate.year}"
        canvas.drawText(dateText, COL_DATE_X + 4f, textTop, paints.cellPrimary)
        if (item.eventTime != null) {
            canvas.drawText(
                DominicanFormatter.formatTime12h(item.eventTime),
                COL_DATE_X + 4f,
                textTop + 11.5f,
                paints.cellMuted,
            )
        }

        // Service description + venue below
        var sy = textTop
        serviceLines.forEach { line ->
            canvas.drawText(line, COL_SERVICE_X + 4f, sy, paints.cellPrimary)
            sy += 11.5f
        }
        venueLines.forEach { line ->
            canvas.drawText(line, COL_SERVICE_X + 4f, sy, paints.cellMuted)
            sy += 10f
        }

        // Amount
        canvas.drawText(
            DominicanFormatter.formatMoney(item.amount, currency),
            COL_AMOUNT_RIGHT_X - 4f,
            textTop,
            paints.cellAmountRight,
        )

        // Row bottom divider
        paints.stroke.color = COLOR_LINE
        canvas.drawLine(MARGIN_X, topY + rowHeight, COL_AMOUNT_RIGHT_X, topY + rowHeight, paints.stroke)
    }

    private fun drawTableTotalRow(
        canvas: Canvas,
        paints: Paints,
        statement: AccountStatement,
        topY: Float,
        rowHeight: Float,
    ) {
        paints.fill.color = COLOR_SURFACE_SUBTLE
        canvas.drawRect(MARGIN_X, topY, COL_AMOUNT_RIGHT_X, topY + rowHeight, paints.fill)

        paints.stroke.color = COLOR_INK
        canvas.drawLine(MARGIN_X, topY, COL_AMOUNT_RIGHT_X, topY, paints.stroke)

        val baseline = topY + 17f
        val countLabel = if (statement.items.size == 1) "1 servicio" else "${statement.items.size} servicios"
        canvas.drawText("TOTAL ($countLabel)", COL_DATE_X + 4f, baseline, paints.cellPrimaryBold)
        canvas.drawText(
            DominicanFormatter.formatMoney(statement.totalPending, statement.currency),
            COL_AMOUNT_RIGHT_X - 4f,
            baseline,
            paints.cellAmountRight,
        )
    }

    private fun drawSectionHeading(canvas: Canvas, paints: Paints, title: String, topY: Float) {
        canvas.drawText(title, MARGIN_X, topY + 10f, paints.sectionHeading)
    }

    private fun drawNoBankAccountCard(
        canvas: Canvas,
        paints: Paints,
        issuerName: String,
        topY: Float,
        height: Float,
    ) {
        val rect = RectF(MARGIN_X, topY, COL_AMOUNT_RIGHT_X, topY + height)
        paints.fill.color = COLOR_SURFACE_SUBTLE
        canvas.drawRoundRect(rect, 6f, 6f, paints.fill)
        paints.stroke.color = COLOR_LINE
        canvas.drawRoundRect(rect, 6f, 6f, paints.stroke)

        canvas.drawText(
            "Coordina el pago directamente con $issuerName.",
            MARGIN_X + 12f,
            topY + 21f,
            paints.cellPrimary,
        )
    }

    private fun drawBankCardRow(
        canvas: Canvas,
        paints: Paints,
        pair: List<BankAccount>,
        topY: Float,
        cardHeight: Float,
    ) {
        val gap = 10f
        val singleFullWidth = pair.size == 1
        val cardWidth = if (singleFullWidth) CONTENT_WIDTH else (CONTENT_WIDTH - gap) / 2f

        pair.forEachIndexed { idx, account ->
            val left = MARGIN_X + idx * (cardWidth + gap)
            val rect = RectF(left, topY, left + cardWidth, topY + cardHeight)

            paints.fill.color = COLOR_SURFACE_SUBTLE
            canvas.drawRoundRect(rect, 6f, 6f, paints.fill)
            paints.stroke.color = if (account.isDefault) COLOR_AMBER else COLOR_LINE
            canvas.drawRoundRect(rect, 6f, 6f, paints.stroke)

            val padX = left + 11f
            val maxTextWidth = cardWidth - 22f

            val typeSuffix = account.accountType?.let { " · ${it.label}" }.orEmpty()
            val bankLine = truncateText("${account.bankName}$typeSuffix", paints.bankTitle, maxTextWidth)
            canvas.drawText(bankLine, padX, topY + 16f, paints.bankTitle)

            val holderLine = truncateText("Titular: ${account.holderName}", paints.bankHolder, maxTextWidth)
            canvas.drawText(holderLine, padX, topY + 30f, paints.bankHolder)

            val numLine = truncateText(account.accountNumber, paints.bankAccountNum, maxTextWidth)
            canvas.drawText(numLine, padX, topY + 46f, paints.bankAccountNum)
        }
    }

    private fun drawClosingBox(
        canvas: Canvas,
        paints: Paints,
        quoteLines: List<String>,
        quoteAttribution: String,
        bodyLines: List<String>,
        issuerName: String,
        brandAssets: PdfBrandAssets?,
        topY: Float,
        boxHeight: Float,
    ) {
        val rect = RectF(MARGIN_X, topY, COL_AMOUNT_RIGHT_X, topY + boxHeight)
        paints.fill.color = COLOR_QUOTE_BG
        canvas.drawRoundRect(rect, 6f, 6f, paints.fill)
        paints.stroke.color = COLOR_QUOTE_BORDER
        canvas.drawRoundRect(rect, 6f, 6f, paints.stroke)

        // Left amber accent strip
        paints.fill.color = COLOR_AMBER
        canvas.drawRect(MARGIN_X, topY + 2f, MARGIN_X + 3.5f, topY + boxHeight - 2f, paints.fill)

        val textX = MARGIN_X + 14f
        var cy = topY + 18f

        if (quoteLines.isNotEmpty()) {
            quoteLines.forEach { line ->
                canvas.drawText(line, textX, cy, paints.quoteItalic)
                cy += 11.5f
            }
            if (quoteAttribution.isNotEmpty()) {
                canvas.drawText(quoteAttribution, textX, cy + 1f, paints.quoteAuthor)
                cy += 17f
            } else if (bodyLines.isNotEmpty()) {
                cy += 6f
            }
        }

        bodyLines.forEach { line ->
            canvas.drawText(line, textX, cy, paints.quoteBody)
            cy += 11.5f
        }

        val sigBmp = brandAssets?.signatureBitmap
        if (sigBmp != null && !sigBmp.isRecycled) {
            val maxW = 135f
            val maxH = 38f
            val scale = minOf(maxW / sigBmp.width.coerceAtLeast(1), maxH / sigBmp.height.coerceAtLeast(1))
            val dstW = sigBmp.width * scale
            val dstH = sigBmp.height * scale
            val dstTop = cy - 4f
            canvas.drawBitmap(sigBmp, null, RectF(textX, dstTop, textX + dstW, dstTop + dstH), paints.bitmapPaint)
            cy = dstTop + maxH + 6f
            paints.stroke.color = COLOR_QUOTE_BORDER
            canvas.drawLine(textX, cy - 2f, textX + 160f, cy - 2f, paints.stroke)
            canvas.drawText(issuerName, textX, cy + 10f, paints.signature)
        } else {
            canvas.drawText(issuerName, textX, cy + 5f, paints.signature)
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        paints: Paints,
        documentTitle: String,
        supplierName: String,
        contactLine: String?,
        pageNumber: Int,
        totalPages: Int,
    ) {
        paints.stroke.color = COLOR_LINE
        canvas.drawLine(MARGIN_X, FOOTER_LINE_Y, COL_AMOUNT_RIGHT_X, FOOTER_LINE_Y, paints.stroke)

        val baseline = FOOTER_LINE_Y + 14f
        val contactSuffix = contactLine?.trim()?.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
        val leftLabel = truncateText("$documentTitle - $supplierName$contactSuffix", paints.footerLeft, 390f)
        canvas.drawText(leftLabel, MARGIN_X, baseline, paints.footerLeft)
        canvas.drawText("Página $pageNumber de $totalPages", COL_AMOUNT_RIGHT_X, baseline, paints.footerRight)
    }

    private fun formatIssueDate(date: LocalDate): String =
        "${date.dayOfMonth} de ${DominicanFormatter.formatMonthNameLower(date.month)} de ${date.year}"

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
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
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
