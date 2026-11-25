package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BankAccount
import com.example.domain.model.Currency
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.EventQuote
import com.example.domain.rules.QuoteCalculator
import com.example.domain.rules.QuoteLineItem
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

private data class EditableQuoteItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val description: String = "",
    val detail: String = "",
    val quantityText: String = "1",
    val priceText: String = "",
)

private val QUICK_SERVICE_PRESETS = listOf(
    "Ceremonia" to "Música en vivo para entrada y ceremonia",
    "Cóctel" to "Ambientación musical durante cóctel (1 hora)",
    "Recepción" to "Presentación musical en cena / recepción",
    "Sonido / Equipos" to "Amplificación compacta y micrófono",
    "Transporte" to "Traslado y logística hacia el lugar",
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuoteCreatorScreen(
    today: LocalDate,
    defaultCurrency: Currency,
    issuerDefaultName: String,
    frequentClientNames: List<String>,
    bankAccounts: List<BankAccount>,
    onBack: () -> Unit,
    onDownloadQuote: (EventQuote) -> Unit,
    onShareQuote: (EventQuote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current

    var clientName by remember { mutableStateOf("") }
    var eventTitle by remember { mutableStateOf("") }
    var venueName by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf<LocalDate?>(null) }
    var eventTime by remember { mutableStateOf<LocalTime?>(null) }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var validityDays by remember { mutableIntStateOf(15) }
    var depositPercent by remember { mutableIntStateOf(50) }
    var notes by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(QuoteCalculator.DEFAULT_TERMS) }
    var includeBankAccounts by remember { mutableStateOf(bankAccounts.isNotEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val items = remember {
        mutableStateListOf(
            EditableQuoteItem(
                description = "Presentación musical en vivo",
                quantityText = "1",
                priceText = "",
            )
        )
    }

    val matchingBankAccounts = remember(bankAccounts, currency) {
        val byCurrency = bankAccounts.filter { it.currency == currency }
        if (byCurrency.isNotEmpty()) byCurrency else bankAccounts
    }

    val previewQuote = remember(
        clientName,
        eventTitle,
        venueName,
        eventDate,
        eventTime,
        currency,
        validityDays,
        depositPercent,
        notes,
        terms,
        includeBankAccounts,
        matchingBankAccounts,
        items.toList(),
    ) {
        val parsedItems = items.mapNotNull { row ->
            val price = row.priceText.replace(",", ".").trim().toBigDecimalOrNull()
                ?.takeIf { it >= BigDecimal.ZERO }
                ?: BigDecimal.ZERO
            val qty = (row.quantityText.toIntOrNull() ?: 1).coerceIn(1, 99)
            if (row.description.isBlank() && price == BigDecimal.ZERO) return@mapNotNull null
            QuoteLineItem(
                id = row.id,
                description = row.description.trim().ifBlank { "Servicio musical" },
                detail = row.detail.trim(),
                quantity = qty,
                unitPrice = price,
            )
        }
        EventQuote(
            quoteNumber = QuoteCalculator.buildQuoteNumber(today),
            clientName = clientName.trim(),
            eventTitle = eventTitle.trim(),
            eventDate = eventDate,
            eventTime = eventTime,
            venueName = venueName.trim(),
            issueDate = today,
            validUntilDate = today.plusDays(validityDays.toLong()),
            currency = currency,
            depositPercent = depositPercent,
            items = parsedItems,
            notes = notes.trim(),
            terms = terms.trim(),
            issuerName = issuerDefaultName.trim().ifBlank { "Apptividades" },
            bankAccounts = if (includeBankAccounts) matchingBankAccounts else emptyList(),
        )
    }

    fun validateAndRun(action: (EventQuote) -> Unit) {
        if (clientName.trim().isEmpty()) {
            validationError = context.getString(R.string.quote_validation_client)
            return
        }
        val validItems = previewQuote.items.filter { it.description.isNotBlank() && it.unitPrice > BigDecimal.ZERO }
        if (validItems.isEmpty()) {
            validationError = context.getString(R.string.quote_validation_items)
            return
        }
        validationError = null
        action(previewQuote.copy(items = validItems))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UtilityIconTile(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                onClick = onBack,
                testTag = "quote_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.quote_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = previewQuote.quoteNumber,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFontFamily),
                    color = colors.accentHover,
                )
            }
        }

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
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!validationError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.dangerText,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // 1. Client & event details
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quote_section_client),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        OutlinedTextField(
                            value = clientName,
                            onValueChange = {
                                clientName = it
                                validationError = null
                            },
                            label = { Text(stringResource(R.string.quote_client_label)) },
                            placeholder = { Text(stringResource(R.string.quote_client_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quote_client_input"),
                        )
                        if (frequentClientNames.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                frequentClientNames.take(5).forEach { name ->
                                    val selected = clientName.equals(name, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            clientName = name
                                            validationError = null
                                        },
                                        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.ink,
                                            selectedLabelColor = colors.surface,
                                        ),
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = eventTitle,
                            onValueChange = { eventTitle = it },
                            label = { Text(stringResource(R.string.quote_event_title_label)) },
                            placeholder = { Text(stringResource(R.string.quote_event_title_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quote_event_title_input"),
                        )

                        OutlinedTextField(
                            value = venueName,
                            onValueChange = { venueName = it },
                            label = { Text(stringResource(R.string.quote_venue_label)) },
                            placeholder = { Text(stringResource(R.string.quote_venue_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quote_venue_input"),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.line),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("quote_date_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = colors.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = eventDate?.let { DominicanFormatter.formatChipDate(it) }
                                        ?: stringResource(R.string.quote_event_date_tbd),
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.line),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("quote_time_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = colors.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = eventTime?.let { DominicanFormatter.formatTime12h(it) }
                                        ?: stringResource(R.string.form_time_optional),
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // 2. Currency, validity & deposit
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quote_section_terms_config),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Currency.entries.forEach { curr ->
                                val active = currency == curr
                                Surface(
                                    onClick = { currency = curr },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) colors.ink else colors.neutralChipBg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("quote_currency_${curr.code.lowercase()}"),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${curr.symbol} (${curr.code})",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = SpaceGroteskFontFamily,
                                            ),
                                            color = if (active) colors.surface else colors.ink,
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.quote_validity_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.muted,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(7, 15, 30).forEach { days ->
                                    val selected = validityDays == days
                                    Surface(
                                        onClick = { validityDays = days },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) colors.ink else colors.neutralChipBg,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = stringResource(R.string.quote_validity_days, days),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                color = if (selected) colors.surface else colors.ink,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.quote_deposit_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.muted,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(0, 30, 50).forEach { pct ->
                                    val selected = depositPercent == pct
                                    Surface(
                                        onClick = { depositPercent = pct },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) colors.accent else colors.neutralChipBg,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("quote_deposit_$pct"),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (pct == 0) {
                                                    stringResource(R.string.quote_deposit_none)
                                                } else {
                                                    "$pct%"
                                                },
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                color = if (selected) colors.ink else colors.ink,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Itemized services
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quote_section_items),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.quote_quick_presets_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                QUICK_SERVICE_PRESETS.forEach { (label, defaultDetail) ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            items.add(
                                                EditableQuoteItem(
                                                    description = label,
                                                    detail = defaultDetail,
                                                    quantityText = "1",
                                                    priceText = "",
                                                )
                                            )
                                        },
                                        label = { Text("+ $label") },
                                    )
                                }
                            }
                        }

                        items.forEachIndexed { index, row ->
                            if (index > 0) {
                                HorizontalDivider(color = colors.line)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    OutlinedTextField(
                                        value = row.description,
                                        onValueChange = { next ->
                                            items[index] = row.copy(description = next)
                                            validationError = null
                                        },
                                        label = { Text(stringResource(R.string.quote_item_desc_label)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.ink,
                                            unfocusedBorderColor = colors.line,
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quote_item_desc_$index"),
                                    )
                                    if (items.size > 1) {
                                        IconButton(
                                            onClick = { items.removeAt(index) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("quote_item_remove_$index"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = stringResource(R.string.quote_remove_item_cd),
                                                tint = colors.dangerText,
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = row.detail,
                                    onValueChange = { next -> items[index] = row.copy(detail = next) },
                                    label = { Text(stringResource(R.string.quote_item_detail_label)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.ink,
                                        unfocusedBorderColor = colors.line,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedTextField(
                                        value = row.quantityText,
                                        onValueChange = { next ->
                                            if (next.all { it.isDigit() } && next.length <= 2) {
                                                items[index] = row.copy(quantityText = next)
                                            }
                                        },
                                        label = { Text(stringResource(R.string.quote_item_qty_label)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.ink,
                                            unfocusedBorderColor = colors.line,
                                        ),
                                        modifier = Modifier.width(88.dp),
                                    )
                                    OutlinedTextField(
                                        value = row.priceText,
                                        onValueChange = { next ->
                                            if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                items[index] = row.copy(priceText = next)
                                                validationError = null
                                            }
                                        },
                                        label = { Text("${stringResource(R.string.quote_item_price_label)} (${currency.symbol})") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.ink,
                                            unfocusedBorderColor = colors.line,
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quote_item_price_$index"),
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { items.add(EditableQuoteItem()) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("quote_add_item_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.quote_add_item_cta),
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // 4. Notes, terms & bank accounts toggle
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quote_section_notes),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(R.string.quote_notes_label)) },
                            placeholder = { Text(stringResource(R.string.quote_notes_hint)) },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = terms,
                            onValueChange = { terms = it },
                            label = { Text(stringResource(R.string.quote_terms_label)) },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (matchingBankAccounts.isNotEmpty()) {
                            HorizontalDivider(color = colors.line)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.quote_include_banks_toggle,
                                        matchingBankAccounts.size.toString(),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = includeBankAccounts,
                                    onCheckedChange = { includeBankAccounts = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surface,
                                        checkedTrackColor = colors.accent,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Live totals summary card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.quote_total_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                            )
                            Text(
                                text = DominicanFormatter.formatMoney(previewQuote.subtotal, currency),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = colors.ink,
                            )
                        }
                        if (depositPercent in 1..99) {
                            HorizontalDivider(color = colors.line)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.quote_deposit_row_label, depositPercent),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.accentHover,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = DominicanFormatter.formatMoney(previewQuote.depositAmount, currency),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = colors.accentHover,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.quote_balance_row_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted,
                                )
                                Text(
                                    text = DominicanFormatter.formatMoney(previewQuote.balanceDue, currency),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                    ),
                                    color = colors.ink,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Sticky bottom action bar
        HorizontalDivider(color = colors.line)
        Surface(
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { validateAndRun(onDownloadQuote) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("quote_download_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.quote_download_cta),
                        color = colors.ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Button(
                    onClick = { validateAndRun(onShareQuote) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.surface,
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("quote_share_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.quote_share_cta),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = (eventDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
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
                ) {
                    Text(stringResource(R.string.form_confirm), color = colors.accentHover, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (eventDate != null) {
                        TextButton(onClick = {
                            eventDate = null
                            showDatePicker = false
                        }) {
                            Text(stringResource(R.string.quote_event_date_tbd), color = colors.muted)
                        }
                    }
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.form_cancel), color = colors.muted)
                    }
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val initial = eventTime ?: LocalTime.of(19, 0)
        val timePickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = colors.surface,
            title = {
                Text(stringResource(R.string.quote_event_time_label), color = colors.ink)
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    eventTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.form_confirm), color = colors.accentHover, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (eventTime != null) {
                        TextButton(onClick = {
                            eventTime = null
                            showTimePicker = false
                        }) {
                            Text(stringResource(R.string.form_time_optional), color = colors.muted)
                        }
                    }
                    TextButton(onClick = { showTimePicker = false }) {
                        Text(stringResource(R.string.form_cancel), color = colors.muted)
                    }
                }
            },
        )
    }
}
