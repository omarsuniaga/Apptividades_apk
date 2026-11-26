package com.example.ui.screens

import com.example.ui.components.MyRouteSection
import com.example.domain.rules.withTracked
import com.example.domain.rules.timingOverride
import com.example.domain.rules.effectiveDriveMinutes
import com.example.domain.rules.TrackedRoutes
import com.example.domain.rules.TrackedRoute
import com.example.ui.components.routeSummaryText
import com.example.ui.components.RouteWarnings
import com.example.domain.rules.RouteOriginKind
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.example.R
import com.example.data.port.PlacePrediction
import com.example.data.port.ResolvedPlace
import com.example.domain.model.Activity
import com.example.domain.model.ActivityDraft
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.Currency
import com.example.domain.model.GigCategory
import com.example.domain.model.LabSettings
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Venue
import com.example.domain.rules.CurrencyConverter
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.FrequentSupplier
import com.example.domain.rules.NameNormalizer
import com.example.domain.rules.SavedVenueSuggestion
import com.example.ui.components.ActivityFinanceSection
import com.example.ui.components.ErrorBanner
import com.example.ui.components.EventTimelineCard
import com.example.ui.components.NavigationActionButtons
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActivityFormScreen(
    existingActivity: Activity?,
    initialDate: LocalDate,
    defaultCurrency: Currency,
    frequentSuppliers: List<FrequentSupplier>,
    venueSuggestions: List<SavedVenueSuggestion>,
    isMutating: Boolean,
    usualRateProvider: (String, Currency) -> BigDecimal?,
    conflictChecker: (LocalDate, LocalTime?, String?, String?, Int?) -> Activity?,
    onDismiss: () -> Unit,
    onSaveNew: (ActivityDraft, Int, CachedActivityRoute?, (Result<Unit>) -> Unit) -> Unit,
    onUpdateExisting: (String, String?, Boolean, ActivityDraft, CachedActivityRoute?, (Result<Unit>) -> Unit) -> Unit,
    onDeleteExisting: (String, String?, Boolean, (Result<Unit>) -> Unit) -> Unit,
    savedVenues: List<Venue> = emptyList(),
    initialCachedRoute: CachedActivityRoute? = null,
    venuesFeaturePending: Boolean = false,
    onSearchPlaces: suspend (String) -> Result<List<PlacePrediction>> = { Result.success(emptyList()) },
    onFetchPlaceDetails: suspend (String) -> Result<ResolvedPlace> = {
        Result.failure(IllegalStateException("Map search unavailable"))
    },
    onEstimateRoute: suspend (Double, Double, String?) -> CachedActivityRoute? = { _, _, _ -> null },
    onUpsertVenue: (Venue, (Venue) -> Unit) -> Unit = { v, cb -> cb(v) },
    settings: LabSettings = LabSettings(),
    initialTimingOverride: ActivityTimelineOverride? = null,
    onTimingOverrideChanged: (ActivityTimelineOverride?) -> Unit = {},
    onSaveDefaultSettings: (LabSettings) -> Unit = {},
    onQuickMessageSupplier: ((Activity) -> Unit)? = null,
    onExportToCalendar: ((Activity) -> Unit)? = null,
    defaultUsdToDopRate: BigDecimal = CurrencyConverter.DEFAULT_USD_TO_DOP_RATE,
    initialFinancialRecord: ActivityFinancialRecord = ActivityFinancialRecord(),
    today: LocalDate = DominicanFormatter.todaySantoDomingo(),
    onFinancialRecordChanged: (ActivityFinancialRecord) -> Unit = {},
    trackedRoutes: Map<String, TrackedRoute> = emptyMap(),
    onSaveTrackedRoute: (venueKey: String, route: TrackedRoute?) -> Unit = { _, _ -> },
) {
    val colors = ApptividadesThemeDefaults.colors
    val isEditing = existingActivity != null

    var showMapPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = !showMapPicker, onBack = onDismiss)

    var selectedSupplierName by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.supplierName.orEmpty())
    }
    var customSupplierMode by remember(existingActivity?.id) {
        val existingName = existingActivity?.supplierName.orEmpty()
        mutableStateOf(
            existingName.isNotBlank() &&
                frequentSuppliers.none {
                    NameNormalizer.normalizeKey(it.displayName) == NameNormalizer.normalizeKey(existingName)
                }
        )
    }
    var selectedCategory by remember(existingActivity?.id) {
        mutableStateOf(
            if (existingActivity != null) {
                GigCategory.infer(existingActivity.activityType, existingActivity.description)
            } else {
                GigCategory.ACTIVIDAD_FIJA
            }
        )
    }
    var eventDate by remember(existingActivity?.id, initialDate) {
        mutableStateOf(existingActivity?.eventDate ?: initialDate)
    }
    var eventTime by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.eventTime ?: LocalTime.of(18, 0))
    }
    var venueName by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.venueName.orEmpty())
    }
    var venueId by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.venueId)
    }
    var venueAddressText by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.venueAddress.orEmpty())
    }
    var latitude by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.latitude)
    }
    var longitude by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.longitude)
    }
    var cachedRoute by remember(existingActivity?.id, initialCachedRoute) {
        mutableStateOf(initialCachedRoute)
    }
    var isEstimatingRoute by remember { mutableStateOf(false) }

    // Fetch & store OSRM estimate once per coordinate pair (reusing cached result if unchanged)
    // Saved routes change the departure times, which can change where the trip starts.
    LaunchedEffect(latitude, longitude, trackedRoutes) {
        val lat = latitude
        val lng = longitude
        if (lat == null || lng == null) {
            cachedRoute = null
            isEstimatingRoute = false
            return@LaunchedEffect
        }
        // A saved activity is always asked again: its origin depends on the other events of
        // the day. The resolver answers from the cache when nothing changed.
        val existing = cachedRoute.takeIf { existingActivity == null }
        if (existing != null &&
            kotlin.math.abs(existing.destLat - lat) < 0.0005 &&
            kotlin.math.abs(existing.destLng - lng) < 0.0005
        ) {
            return@LaunchedEffect
        }
        isEstimatingRoute = cachedRoute == null
        val estimated = runCatching { onEstimateRoute(lat, lng, existingActivity?.id) }.getOrNull()
        isEstimatingRoute = false
        if (estimated != null) {
            cachedRoute = estimated
        }
    }
    var description by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.description.orEmpty())
    }
    var timingOverride by remember(existingActivity?.id, initialTimingOverride) {
        mutableStateOf(initialTimingOverride)
    }
    val durationPresets = remember { listOf(45, 60, 90, 120) }
    val effectiveEventMinutes = timingOverride?.eventMinutes ?: settings.eventMinutes
    var customDurationMode by remember(existingActivity?.id, initialTimingOverride?.eventMinutes) {
        mutableStateOf(effectiveEventMinutes !in durationPresets)
    }
    var customDurationText by remember(existingActivity?.id, initialTimingOverride?.eventMinutes) {
        mutableStateOf(effectiveEventMinutes.toString())
    }

    fun applyEventDurationMinutes(minutes: Int) {
        val safe = minutes.coerceIn(15, 720)
        val base = timingOverride ?: ActivityTimelineOverride(activityId = existingActivity?.id.orEmpty())
        val nextEventMin = if (safe == settings.eventMinutes) null else safe
        val updated = base.copy(eventMinutes = nextEventMin).takeIf { it.hasAnyOverride }
        timingOverride = updated
        onTimingOverrideChanged(updated)
    }
    var currency by remember(existingActivity?.id, defaultCurrency) {
        mutableStateOf(existingActivity?.currency ?: defaultCurrency)
    }
    var amountText by remember(existingActivity?.id) {
        mutableStateOf(
            existingActivity?.amount?.stripTrailingZeros()?.toPlainString().orEmpty()
        )
    }
    var isPaid by remember(existingActivity?.id) {
        mutableStateOf(existingActivity?.paymentStatus == PaymentStatus.PAGADO)
    }
    var financialRecord by remember(existingActivity?.id, initialFinancialRecord) {
        mutableStateOf(initialFinancialRecord)
    }
    var exchangeRateText by remember(
        existingActivity?.id,
        initialFinancialRecord.exchangeRateToDop,
        defaultUsdToDopRate,
    ) {
        val initialRate = initialFinancialRecord.exchangeRateToDop ?: defaultUsdToDopRate
        mutableStateOf(initialRate.stripTrailingZeros().toPlainString())
    }
    var repeatWeekly by remember(existingActivity?.id) { mutableStateOf(false) }
    var repeatWeeks by remember(existingActivity?.id) { mutableIntStateOf(4) }
    var updateWholeSeries by remember(existingActivity?.id) { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val usualRate = remember(selectedSupplierName, currency) {
        if (selectedSupplierName.isNotBlank()) {
            usualRateProvider(selectedSupplierName, currency)
        } else null
    }

    val matchedVenueSuggestion = remember(venueName, venueSuggestions) {
        val key = NameNormalizer.normalizeKey(venueName)
        if (key.isEmpty()) null
        else venueSuggestions.firstOrNull {
            it.canonicalKey == key || (key.length >= 5 && it.canonicalKey.startsWith(key))
        }
    }

    val filteredSavedVenues = remember(venueName, savedVenues) {
        val q = venueName.trim().lowercase()
        if (q.isEmpty()) savedVenues.take(4)
        else savedVenues.filter {
            it.name.lowercase().contains(q) && !it.name.equals(venueName.trim(), ignoreCase = true)
        }.take(4)
    }

    val filteredVenueSuggestions = remember(venueName, venueSuggestions, filteredSavedVenues) {
        val savedKeys = filteredSavedVenues.map { NameNormalizer.normalizeKey(it.name) }.toSet()
        val q = venueName.trim().lowercase()
        val base = if (q.isEmpty()) venueSuggestions
        else venueSuggestions.filter {
            it.displayName.lowercase().contains(q) && !it.displayName.equals(venueName.trim(), ignoreCase = true)
        }
        base.filterNot { it.canonicalKey in savedKeys }.take(4)
    }

    val conflict = remember(eventDate, eventTime, existingActivity?.id, updateWholeSeries, effectiveEventMinutes) {
        conflictChecker(
            eventDate,
            eventTime,
            existingActivity?.id,
            if (updateWholeSeries) existingActivity?.seriesId else null,
            effectiveEventMinutes,
        )
    }

    fun applySupplierSelection(name: String) {
        selectedSupplierName = name
        customSupplierMode = false
        val rate = usualRateProvider(name, currency)
        if (rate != null) {
            amountText = rate.stripTrailingZeros().toPlainString()
        }
    }

    val validationVenueMsg = stringResource(R.string.form_validation_venue)
    val validationAmountMsg = stringResource(R.string.form_validation_amount)

    fun submitForm() {
        val cleanVenue = venueName.trim()
        if (cleanVenue.isEmpty()) {
            formError = validationVenueMsg
            return
        }
        val cleanAmount = amountText.replace(",", "").trim()
        val parsedAmount = if (cleanAmount.isEmpty()) BigDecimal.ZERO else cleanAmount.toBigDecimalOrNull()
        if (parsedAmount == null || parsedAmount < BigDecimal.ZERO) {
            formError = validationAmountMsg
            return
        }
        formError = null

        val draft = ActivityDraft(
            supplierName = selectedSupplierName.trim(),
            category = selectedCategory,
            eventDate = eventDate,
            eventTime = eventTime,
            venueId = venueId?.takeIf { it.isNotBlank() },
            venueName = cleanVenue,
            description = description.trim().ifEmpty { null },
            amount = parsedAmount,
            currency = currency,
            isPaid = isPaid,
            venueSubLocation = existingActivity?.venueSubLocation,
            venueAddress = venueAddressText.trim().ifBlank { null },
            city = existingActivity?.city,
            notes = existingActivity?.notes,
            tags = existingActivity?.tags.orEmpty(),
            latitude = latitude,
            longitude = longitude,
        )

        val callback: (Result<Unit>) -> Unit = { result ->
            result.onSuccess { onDismiss() }
                .onFailure { err -> formError = err.message ?: "No se pudo guardar la actividad." }
        }

        val routeToPersist = cachedRoute?.takeIf {
            latitude != null && longitude != null &&
                kotlin.math.abs(it.destLat - latitude!!) < 0.0005 &&
                kotlin.math.abs(it.destLng - longitude!!) < 0.0005
        }

        if (currency == Currency.USD) {
            val parsedRate = exchangeRateText.replace(",", ".").trim().toBigDecimalOrNull()
                ?.takeIf { it > BigDecimal.ZERO }
                ?: defaultUsdToDopRate
            if (financialRecord.exchangeRateToDop?.compareTo(parsedRate) != 0) {
                val updatedFinance = financialRecord.copy(exchangeRateToDop = parsedRate)
                financialRecord = updatedFinance
                onFinancialRecordChanged(updatedFinance)
            }
        }

        if (existingActivity == null) {
            onSaveNew(draft, if (repeatWeekly) repeatWeeks else 1, routeToPersist, callback)
        } else {
            onUpdateExisting(
                existingActivity.id,
                existingActivity.seriesId,
                updateWholeSeries,
                draft,
                routeToPersist,
                callback,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UtilityIconTile(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.nav_close),
                onClick = onDismiss,
                testTag = "form_close_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
            Text(
                text = stringResource(
                    if (isEditing) R.string.form_edit_title else R.string.form_new_title
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isEditing) {
                UtilityIconTile(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.form_delete_button),
                    onClick = { showDeleteDialog = true },
                    testTag = "form_delete_button",
                    borderColor = colors.dangerBorder,
                    backgroundColor = colors.dangerBg,
                    tint = colors.danger,
                )
            }
        }

        // Scrollable form body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!formError.isNullOrBlank()) {
                    ErrorBanner(message = formError!!)
                }

                // Proveedor chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.form_supplier_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.muted,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        frequentSuppliers.forEach { supplier ->
                            val selected = !customSupplierMode &&
                                NameNormalizer.normalizeKey(selectedSupplierName) == supplier.canonicalKey
                            SelectablePillChip(
                                label = supplier.displayName,
                                selected = selected,
                                onClick = { applySupplierSelection(supplier.displayName) },
                                testTag = "supplier_chip_${supplier.canonicalKey}",
                            )
                        }
                        SelectablePillChip(
                            label = stringResource(R.string.form_supplier_other),
                            selected = customSupplierMode,
                            onClick = {
                                customSupplierMode = true
                                if (frequentSuppliers.any {
                                        it.canonicalKey == NameNormalizer.normalizeKey(selectedSupplierName)
                                    }
                                ) {
                                    selectedSupplierName = ""
                                }
                            },
                            testTag = "supplier_chip_other",
                        )
                    }

                    if (customSupplierMode || frequentSuppliers.isEmpty()) {
                        OutlinedTextField(
                            value = selectedSupplierName,
                            onValueChange = { selectedSupplierName = it },
                            placeholder = { Text(stringResource(R.string.form_supplier_custom_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = formFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_supplier_custom_input"),
                        )
                    }
                }

                // Tipo chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.form_type_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.muted,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GigCategory.entries.forEach { category ->
                            SelectablePillChip(
                                label = category.label,
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                testTag = "type_chip_${category.name.lowercase()}",
                            )
                        }
                    }
                }

                // Fecha & Hora row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1.3f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.form_date_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.muted,
                        )
                        Surface(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("form_date_picker_button"),
                        ) {
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) {
                                Text(
                                    text = DominicanFormatter.formatFormDate(eventDate),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.form_time_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.muted,
                        )
                        Surface(
                            onClick = { showTimePicker = true },
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = BorderStroke(
                                width = if (conflict != null) 1.5.dp else 1.dp,
                                color = if (conflict != null) colors.danger else colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("form_time_picker_button"),
                        ) {
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) {
                                Text(
                                    text = eventTime?.let { DominicanFormatter.formatTime24(it) }
                                        ?: stringResource(R.string.form_time_optional),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = if (conflict != null) colors.dangerText else colors.ink,
                                )
                            }
                        }
                    }
                }

                // Event Duration selector (45 / 60 / 90 / 120 min / Otro) synced live with Timeline & Conflicts
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.form_duration_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.muted,
                        )
                        Text(
                            text = stringResource(R.string.form_duration_default_hint, settings.eventMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        durationPresets.forEach { presetMin ->
                            val selected = !customDurationMode && effectiveEventMinutes == presetMin
                            SelectablePillChip(
                                label = stringResource(R.string.form_duration_chip_min, presetMin),
                                selected = selected,
                                onClick = {
                                    customDurationMode = false
                                    customDurationText = presetMin.toString()
                                    applyEventDurationMinutes(presetMin)
                                },
                                testTag = "form_duration_chip_$presetMin",
                            )
                        }
                        SelectablePillChip(
                            label = stringResource(R.string.form_duration_other),
                            selected = customDurationMode,
                            onClick = {
                                customDurationMode = true
                                customDurationText = effectiveEventMinutes.toString()
                            },
                            testTag = "form_duration_chip_other",
                        )
                    }
                    if (customDurationMode) {
                        OutlinedTextField(
                            value = customDurationText,
                            onValueChange = { raw ->
                                if (raw.length <= 3 && raw.all { it.isDigit() }) {
                                    customDurationText = raw
                                    val parsed = raw.toIntOrNull()
                                    if (parsed != null && parsed in 15..720) {
                                        applyEventDurationMinutes(parsed)
                                    }
                                }
                            },
                            placeholder = { Text(stringResource(R.string.form_duration_custom_hint)) },
                            suffix = { Text("min", color = colors.muted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            colors = formFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_duration_custom_input"),
                        )
                    }
                }

                // Inline schedule conflict warning
                if (conflict != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_conflict_warning"),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = colors.danger,
                                modifier = Modifier.size(20.dp),
                            )
                            val conflictDetails = buildList {
                                if (conflict.supplierName.isNotBlank()) add(conflict.supplierName)
                                if (conflict.venueName.isNotBlank()) add(conflict.venueName)
                            }.joinToString(" · ")
                            Text(
                                text = "${stringResource(R.string.form_conflict_prefix)} ya tienes $conflictDetails a las ${DominicanFormatter.formatTime24(conflict.eventTime)} ese día.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.dangerText,
                            )
                        }
                    }
                }

                // Lugar + saved venue suggestions + map picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.form_venue_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.muted,
                        )
                        Surface(
                            onClick = { showMapPicker = true },
                            shape = RoundedCornerShape(10.dp),
                            color = colors.accentSoft,
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
                            modifier = Modifier.testTag("form_pick_on_map_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Map,
                                    contentDescription = null,
                                    tint = colors.accentHover,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource(R.string.form_pick_on_map),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.accentHover,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = venueName,
                        onValueChange = {
                            venueName = it
                            // If user edits venue manually after picking a saved place, clear stale venueId
                            if (venueId != null && savedVenues.none { v -> v.id == venueId && v.name.equals(it.trim(), ignoreCase = true) }) {
                                venueId = null
                            }
                            if (it.isBlank()) {
                                latitude = null
                                longitude = null
                                cachedRoute = null
                            }
                        },
                        placeholder = { Text(stringResource(R.string.form_venue_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = formFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_venue_input"),
                    )

                    OutlinedTextField(
                        value = venueAddressText,
                        onValueChange = { venueAddressText = it },
                        label = { Text(stringResource(R.string.form_venue_address_label)) },
                        placeholder = { Text(stringResource(R.string.form_venue_address_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = formFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_venue_address_input"),
                    )

                    if (latitude != null && longitude != null) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_venue_coords_badge"),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.success,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource(R.string.form_venue_coords_saved),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = colors.successText,
                                )
                            }
                            TextButton(
                                onClick = {
                                    venueId = null
                                    latitude = null
                                    longitude = null
                                    cachedRoute = null
                                },
                                modifier = Modifier.testTag("form_clear_pin_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.form_venue_clear_pin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.muted,
                                )
                            }
                        }
                    }

                    if (isEstimatingRoute) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.form_route_calculating),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                    } else if (cachedRoute != null) {
                        val trackedRoute = TrackedRoutes.find(trackedRoutes, venueName)
                        val route = cachedRoute!!.withTracked(trackedRoute)
                        val routeLabel = routeSummaryText(
                            distanceKm = route.distanceKm,
                            travelMinutes = route.effectiveDriveMinutes,
                            originKind = route.originKind,
                            originVenueName = route.originVenueName,
                            isTrackedDrive = route.trackedDriveMinutes != null,
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.neutralChipBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .testTag("form_route_estimate_badge"),
                        ) {
                            Text(
                                text = routeLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        RouteWarnings(
                            shortfallMinutes = route.shortfallMinutes,
                            unpinnedPreviousVenueName = route.unpinnedPreviousVenueName,
                        )
                        if (venueName.isNotBlank()) {
                            MyRouteSection(
                                venueName = venueName.trim(),
                                route = route,
                                tracked = trackedRoute,
                                eventDate = eventDate,
                                eventTime = eventTime,
                                settings = settings,
                                dayOverride = timingOverride,
                                onSave = onSaveTrackedRoute,
                            )
                        }
                    }

                    val chainedRoute = cachedRoute?.takeIf { it.originKind == RouteOriginKind.PREVIOUS_EVENT }
                    NavigationActionButtons(
                        venueName = listOfNotNull(
                            venueName.trim().ifBlank { null },
                            venueAddressText.trim().ifBlank { null },
                        ).joinToString(", ").ifBlank { null },
                        latitude = latitude,
                        longitude = longitude,
                        idTag = "form_venue",
                        originLatitude = chainedRoute?.originLat,
                        originLongitude = chainedRoute?.originLng,
                    )

                    if (filteredSavedVenues.isNotEmpty() || filteredVenueSuggestions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Saved lab_venues with coordinates shown first
                            filteredSavedVenues.forEach { saved ->
                                Surface(
                                    onClick = {
                                        venueId = saved.id
                                        venueName = saved.name
                                        venueAddressText = saved.address.orEmpty()
                                        latitude = saved.latitude
                                        longitude = saved.longitude
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = colors.accentSoft,
                                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.25f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            tint = colors.accentHover,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = saved.name,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.accentHover,
                                        )
                                    }
                                }
                            }
                            filteredVenueSuggestions.forEach { suggestion ->
                                Surface(
                                    onClick = { venueName = suggestion.displayName },
                                    shape = RoundedCornerShape(999.dp),
                                    color = colors.neutralChipBg,
                                ) {
                                    Text(
                                        text = suggestion.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.neutralChipText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    } else if (matchedVenueSuggestion != null) {
                        val gigsCount = pluralStringResource(
                            R.plurals.gig_count,
                            matchedVenueSuggestion.gigCountLast12Months,
                            matchedVenueSuggestion.gigCountLast12Months,
                        )
                        Text(
                            text = stringResource(R.string.form_venue_saved_hint, "$gigsCount en 12 meses"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }

                // Monto + RD$/US$ segmented control
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.form_amount_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.muted,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Segmented currency switch
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.neutralChipBg,
                            modifier = Modifier.height(54.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Currency.entries.forEach { curr ->
                                    val active = currency == curr
                                    Surface(
                                        onClick = {
                                            currency = curr
                                            if (selectedSupplierName.isNotBlank()) {
                                                val rate = usualRateProvider(selectedSupplierName, curr)
                                                if (rate != null && amountText.isBlank()) {
                                                    amountText = rate.stripTrailingZeros().toPlainString()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (active) colors.surface else colors.neutralChipBg,
                                        modifier = Modifier
                                            .height(46.dp)
                                            .testTag("currency_${curr.code.lowercase()}"),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                        ) {
                                            Text(
                                                text = curr.symbol,
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                color = if (active) colors.ink else colors.muted,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { next ->
                                if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    amountText = next
                                }
                            },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = SpaceGroteskFontFamily,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = formFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("form_amount_input"),
                        )
                    }

                    if (usualRate != null && selectedSupplierName.isNotBlank()) {
                        Text(
                            text = stringResource(
                                R.string.form_usual_rate_hint,
                                selectedSupplierName,
                                DominicanFormatter.formatMoney(usualRate, currency),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            modifier = Modifier.testTag("form_usual_rate_hint"),
                        )
                    }

                    // Historical exchange rate lock when billing in USD (RD$ per 1 US$)
                    if (currency == Currency.USD) {
                        val effectiveRate = exchangeRateText.replace(",", ".").trim().toBigDecimalOrNull()
                            ?.takeIf { it > BigDecimal.ZERO }
                            ?: defaultUsdToDopRate
                        val parsedUsdAmount = amountText.replace(",", "").trim().toBigDecimalOrNull()
                            ?.takeIf { it > BigDecimal.ZERO }
                        val dopEquivalent = parsedUsdAmount?.let {
                            CurrencyConverter.convert(
                                amount = it,
                                from = Currency.USD,
                                to = Currency.DOP,
                                historicalUsdToDopRate = effectiveRate,
                                fallbackUsdToDopRate = defaultUsdToDopRate,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.accentSoft,
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("form_exchange_rate_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.SwapHoriz,
                                            contentDescription = null,
                                            tint = colors.accentHover,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.form_exchange_rate_label),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.form_exchange_rate_default_hint,
                                            defaultUsdToDopRate.stripTrailingZeros().toPlainString(),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.muted,
                                    )
                                }

                                OutlinedTextField(
                                    value = exchangeRateText,
                                    onValueChange = { next ->
                                        if (next.isEmpty() || next.matches(Regex("^\\d{0,3}\\.?\\d{0,2}$"))) {
                                            exchangeRateText = next
                                            val parsed = next.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                                            if (parsed != null) {
                                                val updated = financialRecord.copy(exchangeRateToDop = parsed)
                                                financialRecord = updated
                                                onFinancialRecordChanged(updated)
                                            }
                                        }
                                    },
                                    placeholder = { Text(stringResource(R.string.form_exchange_rate_hint)) },
                                    prefix = {
                                        Text(
                                            text = "RD$ ",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = colors.muted,
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = formFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("form_exchange_rate_input"),
                                )

                                if (dopEquivalent != null) {
                                    Text(
                                        text = stringResource(
                                            R.string.form_exchange_rate_equiv,
                                            DominicanFormatter.formatMoney(dopEquivalent, Currency.DOP),
                                        ),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = colors.accentHover,
                                        modifier = Modifier.testTag("form_exchange_rate_equiv"),
                                    )
                                }
                            }
                        }
                    }
                }

                // Optional description
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.form_description_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.muted,
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text(stringResource(R.string.form_description_hint)) },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = formFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_description_input"),
                    )
                }

                // Artist Event Timeline (from preparation through getting free, with inline timing adjustment)
                EventTimelineCard(
                    eventDate = eventDate,
                    eventTime = eventTime,
                    settings = settings,
                    routeTravelMinutes = cachedRoute
                        ?.withTracked(TrackedRoutes.find(trackedRoutes, venueName))
                        ?.effectiveDriveMinutes,
                    routeDistanceKm = cachedRoute?.distanceKm,
                    displayOverride = cachedRoute
                        ?.withTracked(TrackedRoutes.find(trackedRoutes, venueName))
                        .timingOverride(timingOverride),
                    chainedFromVenue = cachedRoute
                        ?.takeIf { it.originKind == RouteOriginKind.PREVIOUS_EVENT }
                        ?.originVenueName,
                    timingOverride = timingOverride,
                    onApplyActivityOverride = { updatedOverride ->
                        timingOverride = updatedOverride
                        val nextMin = updatedOverride?.eventMinutes ?: settings.eventMinutes
                        customDurationMode = nextMin !in durationPresets
                        customDurationText = nextMin.toString()
                        onTimingOverrideChanged(updatedOverride)
                    },
                    onSaveDefaultSettings = { updatedSettings ->
                        timingOverride = null
                        onSaveDefaultSettings(updatedSettings)
                    },
                )

                // Quick Supplier Communication (Arrival / Finished / Open Situation) when editing
                if (existingActivity != null && onQuickMessageSupplier != null) {
                    OutlinedButton(
                        onClick = { onQuickMessageSupplier(existingActivity) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.success.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.successBg,
                            contentColor = colors.successText,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("form_quick_message_supplier_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = colors.successText,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.supplier_msg_form_cta),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Quick native Calendar export (zero-permission ACTION_INSERT) when editing
                if (existingActivity != null && onExportToCalendar != null) {
                    OutlinedButton(
                        onClick = { onExportToCalendar(existingActivity) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.line),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.surface,
                            contentColor = colors.ink,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("form_export_calendar_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null,
                            tint = colors.ink,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.calendar_export_cta),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Partial payments (anticipos/señas), receipts & per-activity expenses
                ActivityFinanceSection(
                    totalAmount = amountText.replace(",", "").trim().toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    currency = currency,
                    isPaid = isPaid,
                    record = financialRecord,
                    today = today,
                    onRecordChanged = { updated ->
                        financialRecord = updated
                        onFinancialRecordChanged(updated)
                    },
                    onAutoMarkPaid = { isPaid = true },
                )

                // Ya me pagaron toggle
                Surface(
                    onClick = { isPaid = !isPaid },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_already_paid_row"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.form_already_paid),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink,
                        )
                        Checkbox(
                            checked = isPaid,
                            onCheckedChange = { isPaid = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.ink,
                                uncheckedColor = colors.muted,
                                checkmarkColor = colors.surface,
                            ),
                        )
                    }
                }

                // Weekly series creation (only when creating new)
                if (!isEditing) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.form_repeat_weekly),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.ink,
                                )
                                Switch(
                                    checked = repeatWeekly,
                                    onCheckedChange = { repeatWeekly = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surface,
                                        checkedTrackColor = colors.accent,
                                    ),
                                    modifier = Modifier.testTag("form_repeat_weekly_switch"),
                                )
                            }
                            if (repeatWeekly) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.form_repeat_weeks_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedButton(
                                            onClick = { if (repeatWeeks > 2) repeatWeeks-- },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.size(40.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        ) { Text("−", color = colors.ink) }
                                        Text(
                                            text = repeatWeeks.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.ink,
                                            modifier = Modifier.width(28.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        OutlinedButton(
                                            onClick = { if (repeatWeeks < 26) repeatWeeks++ },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.size(40.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        ) { Text("+", color = colors.ink) }
                                    }
                                }
                            }
                        }
                    }
                }

                // Series edit scope (when editing an activity with seriesId)
                if (existingActivity?.seriesId != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.form_series_scope_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.muted,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { updateWholeSeries = false }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = !updateWholeSeries,
                                    onClick = { updateWholeSeries = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.ink),
                                )
                                Text(
                                    text = stringResource(R.string.form_series_only_this),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.ink,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { updateWholeSeries = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = updateWholeSeries,
                                    onClick = { updateWholeSeries = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.ink),
                                )
                                Text(
                                    text = stringResource(R.string.form_series_all),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.ink,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Sticky submit bar
        Surface(
            color = colors.ground,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 1.dp, color = colors.line)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = { submitForm() },
                        enabled = !isMutating,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.surface,
                        ),
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("form_save_button"),
                    ) {
                        if (isMutating) {
                            CircularProgressIndicator(
                                color = colors.surface,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    if (isEditing) R.string.form_update_button else R.string.form_save_button
                                ),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMapPicker) {
        VenueMapPickerScreen(
            title = stringResource(R.string.map_picker_title),
            initialName = venueName,
            initialAddress = venueAddressText.trim().ifBlank { null },
            initialLatitude = latitude,
            initialLongitude = longitude,
            savedVenues = savedVenues,
            venuesFeaturePending = venuesFeaturePending,
            isHomePicker = false,
            onSearchPlaces = onSearchPlaces,
            onFetchPlaceDetails = onFetchPlaceDetails,
            onConfirmVenue = { picked ->
                onUpsertVenue(picked) { saved ->
                    venueId = saved.id.takeIf { it.isNotBlank() }
                    venueName = saved.name
                    venueAddressText = saved.address.orEmpty()
                    latitude = saved.latitude
                    longitude = saved.longitude
                    showMapPicker = false
                }
            },
            onConfirmManualAddress = { manualName, manualAddr ->
                venueId = null
                venueName = manualName
                venueAddressText = manualAddr.orEmpty()
                latitude = null
                longitude = null
                cachedRoute = null
                showMapPicker = false
            },
            onDismiss = { showMapPicker = false },
        )
    }

    if (showDatePicker) {
        val initialMillis = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            eventDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.form_confirm), color = colors.accentHover) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.form_cancel), color = colors.muted)
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val initialHour = eventTime?.hour ?: 18
        val initialMin = eventTime?.minute ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMin,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.form_time_label), style = MaterialTheme.typography.titleMedium) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        eventTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) { Text(stringResource(R.string.form_confirm), color = colors.accentHover) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            eventTime = null
                            showTimePicker = false
                        }
                    ) { Text(stringResource(R.string.form_time_optional), color = colors.muted) }
                    TextButton(onClick = { showTimePicker = false }) {
                        Text(stringResource(R.string.form_cancel), color = colors.muted)
                    }
                }
            },
        )
    }

    if (showDeleteDialog && existingActivity != null) {
        val hasSeries = !existingActivity.seriesId.isNullOrBlank()
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.form_delete_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        if (hasSeries) R.string.form_delete_series_confirm_body
                        else R.string.form_delete_confirm_body
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasSeries) {
                        OutlinedButton(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteExisting(existingActivity.id, existingActivity.seriesId, true) { r ->
                                    r.onSuccess { onDismiss() }.onFailure { formError = it.message }
                                }
                            },
                            border = BorderStroke(1.dp, colors.dangerBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.dangerText),
                        ) {
                            Text(stringResource(R.string.form_delete_all_series))
                        }
                    }
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteExisting(existingActivity.id, existingActivity.seriesId, false) { r ->
                                r.onSuccess { onDismiss() }.onFailure { formError = it.message }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.danger,
                            contentColor = colors.surface,
                        ),
                    ) {
                        Text(
                            stringResource(
                                if (hasSeries) R.string.form_delete_only_this else R.string.action_delete
                            )
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.form_cancel), color = colors.muted)
                }
            },
        )
    }
}

@Composable
private fun SelectablePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) colors.ink else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.ink else colors.line),
        modifier = Modifier
            .height(44.dp)
            .testTag(testTag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.surface else colors.ink,
            )
        }
    }
}

@Composable
private fun formFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = ApptividadesThemeDefaults.colors.surface,
    unfocusedContainerColor = ApptividadesThemeDefaults.colors.surface,
    focusedBorderColor = ApptividadesThemeDefaults.colors.ink,
    unfocusedBorderColor = ApptividadesThemeDefaults.colors.line,
    cursorColor = ApptividadesThemeDefaults.colors.accent,
)
