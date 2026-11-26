package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.domain.model.Activity
import com.example.domain.model.Currency
import com.example.domain.rules.HotelSeasonCalculator
import com.example.domain.rules.HotelSeasonReport
import com.example.ui.screens.HotelSeasonScreen
import com.example.ui.theme.ApptividadesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HotelSeasonScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25)
    private var seq = 0

    private fun gig(venue: String, ym: YearMonth, amount: Int) = Activity(
        id = "a${++seq}",
        ownerId = "o",
        supplierName = "FJ",
        title = "FJ",
        eventDate = ym.atDay(10),
        venueName = venue,
        amount = BigDecimal(amount),
        currency = Currency.DOP,
    )

    private fun report(activities: List<Activity>): HotelSeasonReport =
        HotelSeasonCalculator.build(activities, today, Currency.DOP)

    /** Jan 2025..Oct 2026 with a winter-high / summer-low shape, plus a dropped hotel. */
    private fun fullHistory(): List<Activity> {
        val list = mutableListOf<Activity>()
        var cursor = YearMonth.of(2025, 1)
        while (!cursor.isAfter(YearMonth.of(2026, 10))) {
            val amount = when (cursor.monthValue) {
                12, 1, 2, 3 -> 60_000
                11, 4 -> 30_000
                else -> 10_000
            }
            list += gig("Lopesan", cursor, amount)
            cursor = cursor.plusMonths(1)
        }
        list += gig("Catalonia", YearMonth.of(2025, 3), 100_000)
        list += gig("Catalonia", YearMonth.of(2026, 3), 20_000)
        return list
    }

    private class Calls {
        var exported = false
        var crm = false
        var back = false
    }

    private fun show(report: HotelSeasonReport): Calls {
        val calls = Calls()
        composeTestRule.setContent {
            ApptividadesTheme {
                HotelSeasonScreen(
                    report = report,
                    onBack = { calls.back = true },
                    onExportCsv = { calls.exported = true },
                    onOpenCrm = { calls.crm = true },
                )
            }
        }
        return calls
    }

    private fun scrollTo(tag: String) {
        composeTestRule.onNodeWithTag("hotelseason_list").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `summary shows the twelve month total and the change against the previous year`() {
        val r = report(fullHistory())
        show(r)
        composeTestRule.onNodeWithTag("hotelseason_total").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotelseason_change").assertIsDisplayed()
    }

    @Test
    fun `empty history shows the no data messages and no crm button`() {
        show(report(emptyList()))
        composeTestRule.onNodeWithTag("hotelseason_insights").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotelseason_open_crm").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hotelseason_change").assertTextContains("12 meses anteriores", substring = true)
    }

    @Test
    fun `insights list the dropped hotel and the quiet months and the crm shortcut works`() {
        val calls = show(report(fullHistory()))
        composeTestRule.onNodeWithTag("hotelseason_insight_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hotelseason_open_crm").performScrollTo().performClick()
        assertEquals(true, calls.crm)
    }

    @Test
    fun `export and back buttons call their callbacks`() {
        val calls = show(report(fullHistory()))
        composeTestRule.onNodeWithTag("hotelseason_export").performClick()
        composeTestRule.onNodeWithTag("hotelseason_back").performClick()
        assertEquals(true, calls.exported)
        assertEquals(true, calls.back)
    }

    @Test
    fun `season chart shows twelve bars when there is enough history`() {
        val r = report(fullHistory())
        show(r)
        scrollTo("hotelseason_seasons")
        for (month in 1..12) {
            composeTestRule.onNodeWithTag("season_bar_$month").assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("hotelseason_little_history").assertDoesNotExist()
    }

    @Test
    fun `little history shows the note instead of inventing seasons`() {
        show(report(listOf(gig("Lopesan", YearMonth.of(2026, 9), 5_000))))
        scrollTo("hotelseason_seasons")
        composeTestRule.onNodeWithTag("hotelseason_little_history").assertIsDisplayed()
    }

    @Test
    fun `upcoming month shows how much is still missing`() {
        val r = report(fullHistory())
        show(r)
        val dec = r.upcoming.first { it.month == YearMonth.of(2026, 12) }
        scrollTo("upcoming_${dec.month}")
        composeTestRule.onNodeWithTag("upcoming_gap_${dec.month}").assertIsDisplayed()
    }

    @Test
    fun `hotel cards show their change against the previous year`() {
        val r = report(fullHistory())
        show(r)
        val catalonia = r.hotels.first { it.displayName == "Catalonia" }
        scrollTo("hotel_${catalonia.key}")
        composeTestRule.onNode(
            hasText("80", substring = true) and hasAnyAncestor(hasTestTag("hotel_change_${catalonia.key}")),
            useUnmergedTree = true,
        ).assertExists()
        composeTestRule.onNodeWithTag("heat_${catalonia.key}_0").assertExists()
    }
}
