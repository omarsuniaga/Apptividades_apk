package com.example.data.local.quotes

import com.example.data.port.QuoteFunnelPort
import com.example.domain.model.Currency
import com.example.domain.rules.EventQuote
import com.example.domain.rules.QuoteLineItem
import com.example.domain.rules.QuoteStatus
import com.example.domain.rules.SavedQuote
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

internal data class QuoteItemJson(
    val id: String,
    val description: String,
    val detail: String = "",
    val quantity: Int = 1,
    val unitPrice: String = "0",
)

class RoomQuoteFunnelRepository(
    private val dao: SavedQuoteDao,
    moshi: Moshi,
) : QuoteFunnelPort {

    private val itemsAdapter = moshi.adapter<List<QuoteItemJson>>(
        Types.newParameterizedType(List::class.java, QuoteItemJson::class.java),
    )

    override fun observeQuotes(): Flow<List<SavedQuote>> =
        dao.observeAll().map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    override suspend fun getQuote(id: String): SavedQuote? =
        dao.getById(id)?.let { runCatching { it.toDomain() }.getOrNull() }

    override suspend fun upsert(quote: SavedQuote) = dao.upsert(quote.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()

    private fun SavedQuote.toEntity(): SavedQuoteEntity = SavedQuoteEntity(
        id = id,
        quoteNumber = quote.quoteNumber,
        clientName = quote.clientName,
        eventTitle = quote.eventTitle,
        eventDate = quote.eventDate?.toString(),
        eventTime = quote.eventTime?.toString(),
        venueName = quote.venueName,
        issueDate = quote.issueDate.toString(),
        validUntilDate = quote.validUntilDate.toString(),
        currencyCode = quote.currency.code,
        depositPercent = quote.depositPercent,
        itemsJson = itemsAdapter.toJson(
            quote.items.map {
                QuoteItemJson(it.id, it.description, it.detail, it.quantity, it.unitPrice.toPlainString())
            },
        ),
        notes = quote.notes,
        terms = quote.terms,
        issuerName = quote.issuerName,
        includeBankAccounts = includeBankAccounts,
        status = status.name,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        sentAtMillis = sentAtMillis,
        followUpDate = followUpDate?.toString(),
        lostReason = lostReason,
        convertedToActivity = convertedToActivity,
    )

    private fun SavedQuoteEntity.toDomain(): SavedQuote = SavedQuote(
        id = id,
        quote = EventQuote(
            quoteNumber = quoteNumber,
            clientName = clientName,
            eventTitle = eventTitle,
            eventDate = eventDate?.let(LocalDate::parse),
            eventTime = eventTime?.let(LocalTime::parse),
            venueName = venueName,
            issueDate = LocalDate.parse(issueDate),
            validUntilDate = LocalDate.parse(validUntilDate),
            currency = Currency.fromCode(currencyCode),
            depositPercent = depositPercent,
            items = itemsAdapter.fromJson(itemsJson).orEmpty().map {
                QuoteLineItem(
                    id = it.id,
                    description = it.description,
                    detail = it.detail,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                )
            },
            notes = notes,
            terms = terms,
            issuerName = issuerName,
        ),
        includeBankAccounts = includeBankAccounts,
        status = QuoteStatus.fromName(status),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        sentAtMillis = sentAtMillis,
        followUpDate = followUpDate?.let(LocalDate::parse),
        lostReason = lostReason,
        convertedToActivity = convertedToActivity,
    )
}
