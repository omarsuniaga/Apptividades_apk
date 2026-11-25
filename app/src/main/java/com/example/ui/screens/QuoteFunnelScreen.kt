package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Currency
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.QuoteFunnelCalculator
import com.example.domain.rules.QuoteStatus
import com.example.domain.rules.SavedQuote
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuoteFunnelScreen(
    quotes: List<SavedQuote>,
    today: LocalDate,
    onBack: () -> Unit,
    onNewQuote: () -> Unit,
    onSendQuote: (SavedQuote) -> Unit,
    onSetFollowUp: (SavedQuote, LocalDate) -> Unit,
    onMarkWon: (SavedQuote) -> Unit,
    onMarkLost: (SavedQuote, String) -> Unit,
    onReopen: (SavedQuote) -> Unit,
    onCreateActivity: (SavedQuote) -> Unit,
    onDelete: (SavedQuote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }
    var followUpTarget by remember { mutableStateOf<SavedQuote?>(null) }
    var lostTarget by remember { mutableStateOf<SavedQuote?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedQuote?>(null) }

    val summary = remember(quotes, today) { QuoteFunnelCalculator.summarize(quotes, today) }
    val clientStats = remember(quotes) { QuoteFunnelCalculator.statsByClient(quotes) }
    val filter = QuoteStatus.entries.firstOrNull { it.name == filterName }
    val visible = remember(quotes, filter) {
        // Most urgent first: follow-ups due, then the rest by latest activity.
        quotes.filter { filter == null || it.status == filter }
            .sortedWith(
                compareByDescending<SavedQuote> { QuoteFunnelCalculator.isFollowUpDue(it, today) }
                    .thenByDescending { it.updatedAtMillis },
            )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UtilityIconTile(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                onClick = onBack,
                testTag = "funnel_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
            Text(
                text = stringResource(R.string.funnel_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onNewQuote,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.surface),
                modifier = Modifier.testTag("funnel_new_quote_button"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.funnel_new_quote), fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummaryCard(
                    closeRatePercent = summary.closeRatePercent,
                    open = summary.openAmountByCurrency,
                    won = summary.wonAmountByCurrency,
                    lost = summary.lostAmountByCurrency,
                    openCount = QuoteStatus.entries.filter { it.isOpen }.sumOf { summary.countsByStatus.getValue(it) },
                    wonCount = summary.countsByStatus.getValue(QuoteStatus.GANADA),
                    lostCount = summary.countsByStatus.getValue(QuoteStatus.PERDIDA),
                )
            }

            if (summary.followUpsDue > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.accentSoft,
                        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("funnel_followups_due_banner"),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = colors.accentHover)
                            Text(
                                text = pluralStringResource(
                                    R.plurals.funnel_followups_due,
                                    summary.followUpsDue,
                                    summary.followUpsDue,
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.ink,
                            )
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter == null,
                            onClick = { filterName = null },
                            label = { Text(stringResource(R.string.funnel_filter_all, summary.totalCount)) },
                            modifier = Modifier.testTag("funnel_filter_all"),
                        )
                    }
                    items(QuoteStatus.entries.toList()) { status ->
                        FilterChip(
                            selected = filter == status,
                            onClick = { filterName = if (filter == status) null else status.name },
                            label = { Text("${status.label} (${summary.countsByStatus.getValue(status)})") },
                            modifier = Modifier.testTag("funnel_filter_${status.name.lowercase()}"),
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.Campaign,
                        title = stringResource(
                            if (quotes.isEmpty()) R.string.funnel_empty_title else R.string.funnel_empty_filtered_title,
                        ),
                        body = stringResource(
                            if (quotes.isEmpty()) R.string.funnel_empty_body else R.string.funnel_empty_filtered_body,
                        ),
                        actionLabel = if (quotes.isEmpty()) stringResource(R.string.funnel_new_quote) else null,
                        onAction = if (quotes.isEmpty()) onNewQuote else null,
                    )
                }
            } else {
                items(visible, key = { it.id }) { saved ->
                    QuoteCard(
                        saved = saved,
                        today = today,
                        onSend = { onSendQuote(saved) },
                        onFollowUp = { followUpTarget = saved },
                        onWon = { onMarkWon(saved) },
                        onLost = { lostTarget = saved },
                        onReopen = { onReopen(saved) },
                        onCreateActivity = { onCreateActivity(saved) },
                        onDelete = { deleteTarget = saved },
                    )
                }
            }

            if (clientStats.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.funnel_by_client_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("funnel_client_stats"),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            clientStats.take(10).forEach { stat ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stat.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.funnel_client_line,
                                                stat.totalQuotes,
                                                stat.wonCount,
                                                stat.lostCount,
                                                stat.openCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.muted,
                                        )
                                    }
                                    StatusPill(
                                        label = stat.closeRatePercent?.let { "$it%" }
                                            ?: stringResource(R.string.funnel_rate_none),
                                        backgroundColor = if ((stat.closeRatePercent ?: 0) >= 50) colors.successBg else colors.neutralChipBg,
                                        textColor = if ((stat.closeRatePercent ?: 0) >= 50) colors.successText else colors.neutralChipText,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    followUpTarget?.let { target ->
        FollowUpDialog(
            today = today,
            onPick = { date ->
                onSetFollowUp(target, date)
                followUpTarget = null
            },
            onDismiss = { followUpTarget = null },
        )
    }
    lostTarget?.let { target ->
        LostReasonDialog(
            onConfirm = { reason ->
                onMarkLost(target, reason)
                lostTarget = null
            },
            onDismiss = { lostTarget = null },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.funnel_delete_title)) },
            text = { Text(stringResource(R.string.funnel_delete_body, target.quote.quoteNumber)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        deleteTarget = null
                    },
                    modifier = Modifier.testTag("funnel_delete_confirm"),
                ) { Text(stringResource(R.string.funnel_delete_confirm), color = colors.dangerText) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.form_cancel)) }
            },
        )
    }
}

@Composable
private fun SummaryCard(
    closeRatePercent: Int?,
    open: Map<Currency, BigDecimal>,
    won: Map<Currency, BigDecimal>,
    lost: Map<Currency, BigDecimal>,
    openCount: Int,
    wonCount: Int,
    lostCount: Int,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.heroCardBg,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("funnel_summary_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = closeRatePercent?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.heroCardText,
                    modifier = Modifier.testTag("funnel_close_rate"),
                )
                Text(
                    text = stringResource(R.string.funnel_close_rate_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onDarkMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (closeRatePercent == null) {
                Text(
                    text = stringResource(R.string.funnel_close_rate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onDarkMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.funnel_stat_open, openCount), open, Modifier.weight(1f))
                StatTile(stringResource(R.string.funnel_stat_won, wonCount), won, Modifier.weight(1f))
                StatTile(stringResource(R.string.funnel_stat_lost, lostCount), lost, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, amounts: Map<Currency, BigDecimal>, modifier: Modifier = Modifier) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(shape = RoundedCornerShape(12.dp), color = colors.heroCardSurface, modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onDarkMuted)
            val lines = amounts.entries.map { DominicanFormatter.formatMoney(it.value, it.key) }
            if (lines.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = colors.heroCardText)
            } else {
                lines.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.heroCardText,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuoteCard(
    saved: SavedQuote,
    today: LocalDate,
    onSend: () -> Unit,
    onFollowUp: () -> Unit,
    onWon: () -> Unit,
    onLost: () -> Unit,
    onReopen: () -> Unit,
    onCreateActivity: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val due = QuoteFunnelCalculator.isFollowUpDue(saved, today)
    val expired = QuoteFunnelCalculator.isExpired(saved, today)
    val q = saved.quote

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, if (due) colors.accent else colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("funnel_quote_${saved.id}"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = q.clientName.ifBlank { stringResource(R.string.funnel_no_client) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val (bg, fg) = statusColors(saved.status)
                StatusPill(label = saved.status.label, backgroundColor = bg, textColor = fg)
            }

            val eventLine = listOfNotNull(
                q.eventTitle.takeIf { it.isNotBlank() },
                q.eventDate?.let { formatDate(it) },
                q.venueName.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (eventLine.isNotEmpty()) {
                Text(eventLine, style = MaterialTheme.typography.bodyMedium, color = colors.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = "${q.quoteNumber} · ${DominicanFormatter.formatMoney(saved.amount, saved.currency)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )

            saved.followUpDate?.takeIf { saved.status.isAwaitingAnswer }?.let { date ->
                Text(
                    text = if (due) stringResource(R.string.funnel_followup_due, formatDate(date))
                    else stringResource(R.string.funnel_followup_on, formatDate(date)),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (due) colors.dangerText else colors.muted,
                )
            }
            if (expired) {
                Text(
                    stringResource(R.string.funnel_expired, formatDate(q.validUntilDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dangerText,
                )
            }
            if (saved.status == QuoteStatus.PERDIDA) {
                Text(
                    stringResource(R.string.funnel_lost_reason, saved.lostReason ?: QuoteFunnelCalculator.CLOSED_QUOTE_DEFAULT_REASON),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            if (saved.status == QuoteStatus.GANADA) {
                val note = when {
                    saved.convertedToActivity -> R.string.funnel_activity_created
                    q.eventDate == null -> R.string.funnel_no_event_date
                    else -> null
                }
                note?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.successText)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (saved.status) {
                    QuoteStatus.BORRADOR -> {
                        FunnelButton(R.string.funnel_action_send, onSend, "funnel_send_${saved.id}", primary = true)
                    }
                    QuoteStatus.ENVIADA, QuoteStatus.SEGUIMIENTO -> {
                        FunnelButton(R.string.funnel_action_followup, onFollowUp, "funnel_followup_${saved.id}")
                        FunnelButton(R.string.funnel_action_won, onWon, "funnel_won_${saved.id}", primary = true)
                        FunnelButton(R.string.funnel_action_lost, onLost, "funnel_lost_${saved.id}")
                        FunnelButton(R.string.funnel_action_resend, onSend, "funnel_resend_${saved.id}")
                    }
                    QuoteStatus.GANADA -> {
                        if (QuoteFunnelCalculator.canConvertToActivity(saved)) {
                            FunnelButton(R.string.funnel_action_create_activity, onCreateActivity, "funnel_activity_${saved.id}", primary = true)
                        }
                    }
                    QuoteStatus.PERDIDA -> {
                        FunnelButton(R.string.funnel_action_reopen, onReopen, "funnel_reopen_${saved.id}")
                    }
                }
                TextButton(onClick = onDelete, modifier = Modifier.testTag("funnel_delete_${saved.id}")) {
                    Text(stringResource(R.string.funnel_action_delete), color = colors.dangerText)
                }
            }
        }
    }
}

@Composable
private fun FunnelButton(labelRes: Int, onClick: () -> Unit, tag: String, primary: Boolean = false) {
    val colors = ApptividadesThemeDefaults.colors
    if (primary) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.surface),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier
                .height(38.dp)
                .testTag(tag),
        ) { Text(stringResource(labelRes), fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, colors.line),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier
                .height(38.dp)
                .testTag(tag),
        ) { Text(stringResource(labelRes), color = colors.ink, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun statusColors(status: QuoteStatus): Pair<Color, Color> {
    val c = ApptividadesThemeDefaults.colors
    return when (status) {
        QuoteStatus.BORRADOR -> c.neutralChipBg to c.neutralChipText
        QuoteStatus.ENVIADA -> c.infoBg to c.info
        QuoteStatus.SEGUIMIENTO -> c.accentSoft to c.accentHover
        QuoteStatus.GANADA -> c.successBg to c.successText
        QuoteStatus.PERDIDA -> c.dangerBg to c.dangerText
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowUpDialog(
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.plusDays(QuoteFunnelCalculator.DEFAULT_FOLLOW_UP_DAYS)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                    },
                ) { Text(stringResource(R.string.form_confirm), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
        ) { DatePicker(state = state) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.funnel_followup_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onPick(today.plusDays(1)) }, modifier = Modifier.testTag("funnel_followup_tomorrow")) {
                    Text(stringResource(R.string.funnel_followup_tomorrow))
                }
                TextButton(onClick = { onPick(today.plusDays(3)) }, modifier = Modifier.testTag("funnel_followup_3days")) {
                    Text(stringResource(R.string.funnel_followup_3days))
                }
                TextButton(onClick = { onPick(today.plusDays(7)) }, modifier = Modifier.testTag("funnel_followup_week")) {
                    Text(stringResource(R.string.funnel_followup_week))
                }
                TextButton(onClick = { showPicker = true }, modifier = Modifier.testTag("funnel_followup_pick")) {
                    Text(stringResource(R.string.funnel_followup_pick))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LostReasonDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.funnel_lost_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteFunnelCalculator.LOST_REASON_PRESETS.forEach { reason ->
                        FilterChip(
                            selected = selected == reason,
                            onClick = { selected = if (selected == reason) null else reason },
                            label = { Text(reason) },
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(120) },
                    label = { Text(stringResource(R.string.funnel_lost_other)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("funnel_lost_custom"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(custom.trim().ifBlank { selected.orEmpty() }) },
                modifier = Modifier.testTag("funnel_lost_confirm"),
            ) { Text(stringResource(R.string.funnel_lost_confirm), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
    )
}

private fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${DominicanFormatter.formatShortMonth(date.month)} ${date.year}"
