package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.domain.model.Activity
import com.example.domain.model.ActivityDraft
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.example.domain.model.Venue
import com.example.domain.rules.FrequentSupplier
import com.example.domain.rules.SavedVenueSuggestion
import com.example.domain.rules.ScheduleConflictChecker
import com.example.ui.screens.ActivityFormScreen
import com.example.ui.theme.ApptividadesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivityFormFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `new activity flow autofills usual rate shows conflict warning accepts manual address and submits draft`() {
        val targetDate = LocalDate.of(2026, 11, 27)
        val existing = listOf(
            Activity(
                id = "existing-1",
                ownerId = "owner-1",
                supplierName = "Crisbel",
                title = "Crisbel",
                eventDate = targetDate,
                eventTime = LocalTime.of(18, 0),
                venueName = "Club Med",
                amount = BigDecimal("6000"),
                currency = Currency.DOP,
            )
        )
        val settings = LabSettings(setupMinutes = 45, eventMinutes = 120, teardownMinutes = 45)
        var capturedDraft: ActivityDraft? = null
        var capturedWeeks = 0

        composeTestRule.setContent {
            ApptividadesTheme {
                ActivityFormScreen(
                    existingActivity = null,
                    initialDate = targetDate,
                    defaultCurrency = Currency.DOP,
                    frequentSuppliers = listOf(
                        FrequentSupplier("crisbel", "Crisbel", 58),
                        FrequentSupplier("fj", "FJ", 40),
                    ),
                    venueSuggestions = listOf(
                        SavedVenueSuggestion("clubmedpuntacana", "Hotel Club Med Punta Cana", 58),
                    ),
                    isMutating = false,
                    usualRateProvider = { supplier, _ ->
                        if (supplier == "Crisbel") BigDecimal("6000") else null
                    },
                    conflictChecker = { d, t, exId, exSeries, candDur ->
                        ScheduleConflictChecker.findConflict(d, t, existing, settings, exId, exSeries, candDur)
                    },
                    onDismiss = {},
                    onSaveNew = { draft, weeks, _, onResult ->
                        capturedDraft = draft
                        capturedWeeks = weeks
                        onResult(Result.success(Unit))
                    },
                    onUpdateExisting = { _, _, _, _, _, _ -> },
                    onDeleteExisting = { _, _, _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()

        // Default time is 18:00 on 2026-11-27 -> should display inline conflict warning
        composeTestRule.onNodeWithTag("form_conflict_warning").performScrollTo().assertIsDisplayed()

        // Tap Crisbel chip -> usual rate RD$ 6,000 should auto-fill
        composeTestRule.onNodeWithTag("supplier_chip_crisbel").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("form_amount_input").performScrollTo().assertTextContains("6000")
        composeTestRule.onNodeWithTag("form_usual_rate_hint").performScrollTo().assertIsDisplayed()

        // Enter venue and manual address directly without using map services
        composeTestRule.onNodeWithTag("form_venue_input").performScrollTo().performTextInput("Hotel Club Med Punta Cana")
        composeTestRule.onNodeWithTag("form_venue_address_input").performScrollTo().performTextInput("Playa Blanca, Punta Cana")
        composeTestRule.onNodeWithTag("form_save_button").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(capturedDraft)
        assertEquals("Crisbel", capturedDraft?.supplierName)
        assertEquals("Hotel Club Med Punta Cana", capturedDraft?.venueName)
        assertEquals("Playa Blanca, Punta Cana", capturedDraft?.venueAddress)
        assertEquals(BigDecimal("6000"), capturedDraft?.amount)
        assertEquals(Currency.DOP, capturedDraft?.currency)
        assertEquals(1, capturedWeeks)
    }

    @Test
    fun `picking saved venue via osm map picker shows attribution caches osrm route and exposes maps and waze buttons`() {
        val targetDate = LocalDate.of(2026, 11, 28)
        var capturedDraft: ActivityDraft? = null
        var capturedRoute: CachedActivityRoute? = null
        val savedClubMed = Venue(
            id = "venue-clubmed-1",
            name = "Club Med Punta Cana",
            address = "Playa Blanca, Punta Cana",
            latitude = 18.5540,
            longitude = -68.3490,
        )

        composeTestRule.setContent {
            ApptividadesTheme {
                ActivityFormScreen(
                    existingActivity = null,
                    initialDate = targetDate,
                    defaultCurrency = Currency.DOP,
                    frequentSuppliers = emptyList(),
                    venueSuggestions = emptyList(),
                    isMutating = false,
                    usualRateProvider = { _, _ -> null },
                    conflictChecker = { _, _, _, _, _ -> null },
                    onDismiss = {},
                    onSaveNew = { draft, _, route, onResult ->
                        capturedDraft = draft
                        capturedRoute = route
                        onResult(Result.success(Unit))
                    },
                    onUpdateExisting = { _, _, _, _, _, _ -> },
                    onDeleteExisting = { _, _, _, _ -> },
                    savedVenues = listOf(savedClubMed),
                    onEstimateRoute = { destLat, destLng, actId ->
                        CachedActivityRoute(
                            activityId = actId.orEmpty(),
                            originLat = 18.6813,
                            originLng = -68.4547,
                            destLat = destLat,
                            destLng = destLng,
                            travelMinutes = 26,
                            distanceKm = 18.4,
                            fromOsrm = true,
                        )
                    },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("form_pick_on_map_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Required OpenStreetMap attribution must be visible
        composeTestRule.onNodeWithTag("osm_attribution_label").assertIsDisplayed()
        composeTestRule.onNodeWithTag("osm_attribution_label").assertTextContains("© OpenStreetMap contributors")

        // Select saved venue and confirm
        composeTestRule.onNodeWithTag("map_picker_list")
            .performScrollToNode(hasTestTag("saved_venue_club_med_punta_cana"))
        composeTestRule.onNodeWithTag("saved_venue_club_med_punta_cana").performClick()
        composeTestRule.onNodeWithTag("map_picker_confirm_button").performClick()
        composeTestRule.waitForIdle()

        // Back on form: coords badge, OSRM estimated time (sin tráfico), and Google Maps / Waze buttons visible
        composeTestRule.onNodeWithTag("form_venue_coords_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("form_route_estimate_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("form_route_estimate_badge").assertTextContains("sin tráfico", substring = true)
        composeTestRule.onNodeWithTag("open_google_maps_form_venue").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_waze_form_venue").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("form_save_button").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(capturedDraft)
        assertEquals("venue-clubmed-1", capturedDraft?.venueId)
        assertEquals("Club Med Punta Cana", capturedDraft?.venueName)
        assertEquals("Playa Blanca, Punta Cana", capturedDraft?.venueAddress)
        assertEquals(18.5540, capturedDraft?.latitude ?: 0.0, 0.0001)
        assertEquals(-68.3490, capturedDraft?.longitude ?: 0.0, 0.0001)

        assertNotNull(capturedRoute)
        assertEquals(26, capturedRoute?.travelMinutes)
        assertEquals(18.4, capturedRoute?.distanceKm ?: 0.0, 0.01)
    }

    @Test
    fun `map picker handles photon failure gracefully and allows manual address entry`() {
        val targetDate = LocalDate.of(2026, 11, 29)
        var capturedDraft: ActivityDraft? = null

        composeTestRule.setContent {
            ApptividadesTheme {
                ActivityFormScreen(
                    existingActivity = null,
                    initialDate = targetDate,
                    defaultCurrency = Currency.DOP,
                    frequentSuppliers = emptyList(),
                    venueSuggestions = emptyList(),
                    isMutating = false,
                    usualRateProvider = { _, _ -> null },
                    conflictChecker = { _, _, _, _, _ -> null },
                    onDismiss = {},
                    onSaveNew = { draft, _, _, onResult ->
                        capturedDraft = draft
                        onResult(Result.success(Unit))
                    },
                    onUpdateExisting = { _, _, _, _, _, _ -> },
                    onDeleteExisting = { _, _, _, _ -> },
                    onSearchPlaces = { Result.failure(IllegalStateException("Photon unavailable")) },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("form_pick_on_map_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Type >= 3 letters to trigger debounced Photon search (400 ms)
        composeTestRule.onNodeWithTag("map_picker_search_input").performTextInput("Sanctuary Cap Cana")
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        // Graceful non-blocking notice appears while manual address entry remains usable
        composeTestRule.onNodeWithTag("map_picker_search_fallback_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_list")
            .performScrollToNode(hasTestTag("map_picker_manual_address_input"))
        composeTestRule.onNodeWithTag("map_picker_manual_address_input")
            .performTextInput("Blvd. Zona Hotelera, Cap Cana")
        composeTestRule.onNodeWithTag("map_picker_confirm_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("form_save_button").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(capturedDraft)
        assertEquals("Sanctuary Cap Cana", capturedDraft?.venueName)
        assertEquals("Blvd. Zona Hotelera, Cap Cana", capturedDraft?.venueAddress)
        assertNull(capturedDraft?.latitude)
        assertNull(capturedDraft?.longitude)
    }
}
