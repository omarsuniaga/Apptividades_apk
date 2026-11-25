package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.Currency
import com.example.domain.model.SANTO_DOMINGO_ZONE
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object ActivityTitleDeriver {
    private const val MAX_LEN = 200
    private const val FALLBACK = "Servicio musical"

    /**
     * Contract 2.1: supplier name -> else first line of description -> else venue -> else "Servicio musical" (max 200).
     */
    fun derive(
        supplierName: String?,
        description: String?,
        venueName: String?,
    ): String {
        val supplier = supplierName?.trim().orEmpty()
        if (supplier.isNotEmpty()) return supplier.take(MAX_LEN)

        val firstDescLine = description
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
        if (firstDescLine.isNotEmpty()) return firstDescLine.take(MAX_LEN)

        val venue = venueName?.trim().orEmpty()
        if (venue.isNotEmpty()) return venue.take(MAX_LEN)

        return FALLBACK
    }
}

object DominicanFormatter {

    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    private val wholeFormat = DecimalFormat("#,##0", symbols)
    private val decimalFormat = DecimalFormat("#,##0.00", symbols)
    private val oneDecimalFormat = DecimalFormat("0.0", symbols)

    private val fullMonthNames = mapOf(
        Month.JANUARY to "enero",
        Month.FEBRUARY to "febrero",
        Month.MARCH to "marzo",
        Month.APRIL to "abril",
        Month.MAY to "mayo",
        Month.JUNE to "junio",
        Month.JULY to "julio",
        Month.AUGUST to "agosto",
        Month.SEPTEMBER to "septiembre",
        Month.OCTOBER to "octubre",
        Month.NOVEMBER to "noviembre",
        Month.DECEMBER to "diciembre",
    )

    private val shortMonthNames = mapOf(
        Month.JANUARY to "ene",
        Month.FEBRUARY to "feb",
        Month.MARCH to "mar",
        Month.APRIL to "abr",
        Month.MAY to "may",
        Month.JUNE to "jun",
        Month.JULY to "jul",
        Month.AUGUST to "ago",
        Month.SEPTEMBER to "sep",
        Month.OCTOBER to "oct",
        Month.NOVEMBER to "nov",
        Month.DECEMBER to "dic",
    )

    private val monthInitials = mapOf(
        Month.JANUARY to "E",
        Month.FEBRUARY to "F",
        Month.MARCH to "M",
        Month.APRIL to "A",
        Month.MAY to "M",
        Month.JUNE to "J",
        Month.JULY to "J",
        Month.AUGUST to "A",
        Month.SEPTEMBER to "S",
        Month.OCTOBER to "O",
        Month.NOVEMBER to "N",
        Month.DECEMBER to "D",
    )

    private val fullWeekdays = mapOf(
        DayOfWeek.MONDAY to "Lunes",
        DayOfWeek.TUESDAY to "Martes",
        DayOfWeek.WEDNESDAY to "Miércoles",
        DayOfWeek.THURSDAY to "Jueves",
        DayOfWeek.FRIDAY to "Viernes",
        DayOfWeek.SATURDAY to "Sábado",
        DayOfWeek.SUNDAY to "Domingo",
    )

    private val shortWeekdays = mapOf(
        DayOfWeek.MONDAY to "Lun",
        DayOfWeek.TUESDAY to "Mar",
        DayOfWeek.WEDNESDAY to "Mié",
        DayOfWeek.THURSDAY to "Jue",
        DayOfWeek.FRIDAY to "Vie",
        DayOfWeek.SATURDAY to "Sáb",
        DayOfWeek.SUNDAY to "Dom",
    )

    fun todaySantoDomingo(): LocalDate = LocalDate.now(SANTO_DOMINGO_ZONE)

    fun formatMoney(amount: BigDecimal, currency: Currency): String {
        val isWhole = amount.stripTrailingZeros().scale() <= 0
        val num = if (isWhole) wholeFormat.format(amount) else decimalFormat.format(amount)
        return "${currency.symbol} $num"
    }

    fun formatCompactMoney(amount: BigDecimal, currency: Currency? = null): String {
        val prefix = if (currency != null) "${currency.symbol} " else ""
        return if (amount >= BigDecimal("1000")) {
            val thousands = amount.divide(BigDecimal("1000"), 0, RoundingMode.HALF_UP)
            "${prefix}${wholeFormat.format(thousands)}k"
        } else {
            "${prefix}${wholeFormat.format(amount)}"
        }
    }

    fun formatOneDecimal(value: Double): String = oneDecimalFormat.format(value)

    fun formatFullDateHeader(date: LocalDate): String {
        val weekday = fullWeekdays[date.dayOfWeek].orEmpty()
        val month = fullMonthNames[date.month].orEmpty()
        return "$weekday ${date.dayOfMonth} de $month"
    }

    fun formatMonthYear(month: YearMonth): String {
        val m = fullMonthNames[month.month].orEmpty().replaceFirstChar { it.uppercase() }
        return "$m ${month.year}"
    }

    fun formatMonthNameLower(month: Month): String = fullMonthNames[month].orEmpty()

    fun formatMonthNameCapitalized(month: Month): String =
        fullMonthNames[month].orEmpty().replaceFirstChar { it.uppercase() }

    fun formatShortMonth(month: Month): String = shortMonthNames[month].orEmpty()

    fun formatMonthInitial(month: Month): String = monthInitials[month].orEmpty()

    fun formatMonthRange(start: YearMonth, end: YearMonth): String {
        val s = formatShortMonth(start.month).replaceFirstChar { it.uppercase() }
        val e = formatShortMonth(end.month).replaceFirstChar { it.uppercase() }
        return "$s ${start.year} – $e ${end.year}"
    }

    fun formatShortWeekday(dayOfWeek: DayOfWeek): String = shortWeekdays[dayOfWeek].orEmpty()

    fun formatWeekdayUpper(dayOfWeek: DayOfWeek): String =
        shortWeekdays[dayOfWeek].orEmpty().uppercase(Locale.ROOT)

    fun formatShortDate(date: LocalDate): String =
        "${date.dayOfMonth} ${formatShortMonth(date.month)}"

    fun formatChipDate(date: LocalDate): String =
        "${formatShortWeekday(date.dayOfWeek)} ${date.dayOfMonth}"

    fun formatFormDate(date: LocalDate): String =
        "${formatShortWeekday(date.dayOfWeek)} ${date.dayOfMonth} ${formatShortMonth(date.month)} ${date.year}"

    fun formatTime24(time: LocalTime?): String =
        time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—"

    fun formatTime12h(time: LocalTime?): String {
        if (time == null) return "—"
        val hour12 = when {
            time.hour == 0 -> 12
            time.hour > 12 -> time.hour - 12
            else -> time.hour
        }
        val suffix = if (time.hour < 12) "a. m." else "p. m."
        return String.format(Locale.US, "%d:%02d %s", hour12, time.minute, suffix)
    }

    fun formatGapBetween(first: LocalTime, second: LocalTime): String? {
        val minutes = Duration.between(first, second).toMinutes()
        if (minutes <= 0) return null
        val hours = minutes / 60
        val rem = minutes % 60
        return when {
            hours > 0 && rem > 0 -> "$hours h $rem min"
            hours > 0 -> "$hours h"
            else -> "$rem min"
        }
    }

    fun formatDayListInMonth(dates: List<LocalDate>): String {
        if (dates.isEmpty()) return ""
        val sorted = dates.sorted()
        val sameMonth = sorted.map { YearMonth.from(it) }.distinct().size == 1
        return if (sameMonth) {
            val days = sorted.map { it.dayOfMonth.toString() }
            val joined = joinSpanishList(days)
            "$joined ${formatShortMonth(sorted.first().month)}"
        } else {
            joinSpanishList(sorted.map { formatShortDate(it) })
        }
    }

    fun joinSpanishList(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} y ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
    }
}

object WhatsAppReminderBuilder {

    private val ddMmFormatter = DateTimeFormatter.ofPattern("dd/MM")

    /**
     * Cleans phone digits; 10-digit Dominican numbers get prefix "1".
     * Returns null if no usable phone number is saved.
     */
    fun normalizeDominicanPhone(rawPhone: String?): String? {
        if (rawPhone.isNullOrBlank()) return null
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.length < 7) return null
        return if (digits.length == 10) "1$digits" else digits
    }

    fun buildMessage(group: ReceivableGroup): String {
        val datesText = group.sortedDates.joinToString(", ") { it.format(ddMmFormatter) }
        val totalText = DominicanFormatter.formatMoney(group.totalAmount, group.currency)
        return if (group.count == 1) {
            "Hola ${group.supplierDisplayName}, ¿cómo estás? Te escribo por el toque del $datesText, pendiente de pago: $totalText. ¡Gracias!"
        } else {
            "Hola ${group.supplierDisplayName}, ¿cómo estás? Te escribo por los ${group.count} toques del $datesText, pendientes de pago: $totalText. ¡Gracias!"
        }
    }

    fun buildWaMeUrl(rawPhone: String?, message: String): String? {
        val normalized = normalizeDominicanPhone(rawPhone) ?: return null
        val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "https://wa.me/$normalized?text=$encoded"
    }
}

object CsvExporter {
    private const val UTF8_BOM = "\uFEFF"

    fun buildActivitiesCsv(activities: List<Activity>): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.appendLine("Fecha,Hora,Título,Proveedor,Tipo,Lugar,Descripción,Monto,Moneda,Estado de pago,Fecha de pago")
        val sorted = activities.sortedWith(compareBy<Activity> { it.eventDate }.thenBy { it.eventTime })
        for (a in sorted) {
            val cols = listOf(
                a.eventDate.toString(),
                a.eventTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                a.title,
                a.supplierName,
                a.activityType.dbValue,
                a.venueName,
                a.description.orEmpty(),
                a.amount.stripTrailingZeros().toPlainString(),
                a.currency.code,
                a.paymentStatus.dbValue,
                a.paidAt.orEmpty(),
            )
            sb.appendLine(cols.joinToString(",") { escapeCsv(it) })
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
