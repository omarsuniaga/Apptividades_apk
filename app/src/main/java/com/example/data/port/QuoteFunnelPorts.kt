package com.example.data.port

import com.example.domain.rules.SavedQuote
import kotlinx.coroutines.flow.Flow

/** Local-only persistence of quotes and their funnel state. */
interface QuoteFunnelPort {
    fun observeQuotes(): Flow<List<SavedQuote>>
    suspend fun getQuote(id: String): SavedQuote?
    suspend fun upsert(quote: SavedQuote)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

/** Schedules / cancels the "time to follow up" reminder of a quote. */
interface QuoteFollowUpPort {
    fun schedule(quote: SavedQuote)
    fun cancel(quoteId: String)
}
