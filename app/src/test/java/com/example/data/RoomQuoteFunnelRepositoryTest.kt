package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.quotes.QuotesDatabase
import com.example.data.local.quotes.RoomQuoteFunnelRepository
import com.example.domain.model.Currency
import com.example.domain.rules.EventQuote
import com.example.domain.rules.QuoteLineItem
import com.example.domain.rules.QuoteStatus
import com.example.domain.rules.SavedQuote
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomQuoteFunnelRepositoryTest {

    private lateinit var db: QuotesDatabase
    private lateinit var repo: RoomQuoteFunnelRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, QuotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomQuoteFunnelRepository(
            db.savedQuoteDao(),
            Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun sample(id: String, updated: Long = 10) = SavedQuote(
        id = id,
        quote = EventQuote(
            quoteNumber = "COT-20261125-01",
            clientName = "Hotel Catalonia",
            eventTitle = "Lobby",
            eventDate = LocalDate.of(2026, 12, 5),
            eventTime = LocalTime.of(19, 30),
            venueName = "Catalonia Royal",
            issueDate = LocalDate.of(2026, 11, 25),
            validUntilDate = LocalDate.of(2026, 12, 10),
            currency = Currency.USD,
            depositPercent = 30,
            items = listOf(
                QuoteLineItem("a", "Violín en vivo", "2 horas", 2, BigDecimal("150.50")),
                QuoteLineItem("b", "Montaje", "", 1, BigDecimal("25")),
            ),
            notes = "Nota",
            terms = "Términos",
            issuerName = "Omar",
        ),
        includeBankAccounts = false,
        status = QuoteStatus.SEGUIMIENTO,
        createdAtMillis = 5,
        updatedAtMillis = updated,
        sentAtMillis = 7,
        followUpDate = LocalDate.of(2026, 11, 28),
        lostReason = null,
        convertedToActivity = true,
    )

    @Test
    fun roundTripPreservesEveryField() = runBlocking {
        val original = sample("q1")
        repo.upsert(original)

        val loaded = repo.getQuote("q1")!!
        assertEquals(original.id, loaded.id)
        assertEquals(original.status, loaded.status)
        assertEquals(original.followUpDate, loaded.followUpDate)
        assertEquals(original.sentAtMillis, loaded.sentAtMillis)
        assertEquals(original.convertedToActivity, loaded.convertedToActivity)
        assertEquals(original.includeBankAccounts, loaded.includeBankAccounts)
        assertEquals(original.quote.copy(), loaded.quote)
        assertEquals(0, BigDecimal("326.00").compareTo(loaded.amount))
        assertEquals(Currency.USD, loaded.currency)
    }

    @Test
    fun nullableFieldsStayNull() = runBlocking {
        val base = sample("q2")
        repo.upsert(base.copy(quote = base.quote.copy(eventDate = null, eventTime = null), followUpDate = null, sentAtMillis = null))
        val loaded = repo.getQuote("q2")!!
        assertNull(loaded.quote.eventDate)
        assertNull(loaded.quote.eventTime)
        assertNull(loaded.followUpDate)
        assertNull(loaded.sentAtMillis)
    }

    @Test
    fun upsertReplacesAndObserveOrdersByMostRecentlyUpdated() = runBlocking {
        repo.upsert(sample("old", updated = 1))
        repo.upsert(sample("new", updated = 99))
        repo.upsert(sample("old", updated = 50).copy(status = QuoteStatus.GANADA))

        val list = repo.observeQuotes().first()
        assertEquals(listOf("new", "old"), list.map { it.id })
        assertEquals(2, list.size)
        assertEquals(QuoteStatus.GANADA, list.first { it.id == "old" }.status)
    }

    @Test
    fun deleteAndDeleteAll() = runBlocking {
        repo.upsert(sample("a"))
        repo.upsert(sample("b"))
        repo.delete("a")
        assertEquals(listOf("b"), repo.observeQuotes().first().map { it.id })
        repo.deleteAll()
        assertEquals(0, repo.observeQuotes().first().size)
    }
}
