package com.example.domain.rules

import com.example.domain.model.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure domain converter between DOP and USD using each activity's locked historical rate
 * (RD$ per 1 US$) with fallback to the user's configured default exchange rate.
 */
object CurrencyConverter {

    val DEFAULT_USD_TO_DOP_RATE: BigDecimal = BigDecimal("60.00")

    fun effectiveRateToDop(
        historicalUsdToDopRate: BigDecimal?,
        fallbackUsdToDopRate: BigDecimal = DEFAULT_USD_TO_DOP_RATE,
    ): BigDecimal {
        val candidate = historicalUsdToDopRate?.takeIf { it > BigDecimal.ZERO }
            ?: fallbackUsdToDopRate.takeIf { it > BigDecimal.ZERO }
            ?: DEFAULT_USD_TO_DOP_RATE
        return candidate
    }

    fun convert(
        amount: BigDecimal,
        from: Currency,
        to: Currency,
        historicalUsdToDopRate: BigDecimal? = null,
        fallbackUsdToDopRate: BigDecimal = DEFAULT_USD_TO_DOP_RATE,
    ): BigDecimal {
        if (from == to || amount.compareTo(BigDecimal.ZERO) == 0) return amount
        val rate = effectiveRateToDop(historicalUsdToDopRate, fallbackUsdToDopRate)
        return when {
            from == Currency.USD && to == Currency.DOP ->
                amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
            from == Currency.DOP && to == Currency.USD ->
                amount.divide(rate, 2, RoundingMode.HALF_UP)
            else -> amount
        }
    }
}
