package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.model.Currency
import com.example.domain.rules.CrmAccount
import com.example.domain.rules.CrmAccountType
import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmContactChannel
import com.example.domain.rules.CrmContactReason
import com.example.domain.rules.CrmMessageBuilder
import com.example.domain.rules.CrmStage
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.HotelCrmCalculator
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private enum class CrmFilter { ALL, TO_CONTACT, ACTIVO, DORMIDO, PROSPECTO, INACTIVO }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrmScreen(
    accounts: List<CrmAccount>,
    today: LocalDate,
    issuerName: String,
    onBack: () -> Unit,
    onSendWhatsApp: (CrmAccount, String) -> Unit,
    onLogContact: (CrmAccount, CrmContactChannel, String, LocalDate, LocalDate?) -> Unit,
    onSaveDetails: (CrmAccount, String, String, String, String, String) -> Unit,
    onSetNextContact: (CrmAccount, LocalDate?) -> Unit,
    onAddProspect: (CrmAccountType, String) -> Unit,
    onDeleteContact: (CrmContact) -> Unit,
    onDeleteAccountData: (CrmAccount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    var typeName by rememberSaveable { mutableStateOf(CrmAccountType.HOTEL.name) }
    var filterName by rememberSaveable { mutableStateOf(CrmFilter.ALL.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var logTarget by remember { mutableStateOf<CrmAccount?>(null) }
    var showAddProspect by remember { mutableStateOf(false) }

    val type = CrmAccountType.entries.first { it.name == typeName }
    val filter = CrmFilter.entries.first { it.name == filterName }
    val ofType = remember(accounts, type) { accounts.filter { it.type == type } }
    val summary = remember(ofType) { HotelCrmCalculator.summarize(ofType) }
    val visible = remember(ofType, filter, query) {
        val q = query.trim().lowercase()
        ofType.filter { account ->
            val matchesFilter = when (filter) {
                CrmFilter.ALL -> true
                CrmFilter.TO_CONTACT -> account.needsContact
                CrmFilter.ACTIVO -> account.stage == CrmStage.ACTIVO
                CrmFilter.DORMIDO -> account.stage == CrmStage.DORMIDO
                CrmFilter.PROSPECTO -> account.stage == CrmStage.PROSPECTO
                CrmFilter.INACTIVO -> account.stage == CrmStage.INACTIVO
            }
            matchesFilter && (q.isEmpty() || account.displayName.lowercase().contains(q))
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UtilityIconTile(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                onClick = onBack,
                testTag = "crm_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
            Text(
                text = stringResource(R.string.crm_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { showAddProspect = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.surface),
                modifier = Modifier.testTag("crm_add_prospect_button"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.crm_add_prospect), fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("crm_list"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrmAccountType.entries.forEach { t ->
                        val count = accounts.count { it.type == t }
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                typeName = t.name
                                filterName = CrmFilter.ALL.name
                            },
                            label = { Text(stringResource(R.string.crm_tab_count, t.pluralLabel, count)) },
                            modifier = Modifier.testTag("crm_type_${t.name.lowercase()}"),
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.heroCardBg,
                    modifier = Modifier.fillMaxWidth().testTag("crm_summary_card"),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SummaryTile(R.string.crm_tile_to_contact, summary.toContactCount, "crm_tile_to_contact", highlight = summary.toContactCount > 0, modifier = Modifier.weight(1f))
                        SummaryTile(R.string.crm_tile_active, summary.countsByStage.getValue(CrmStage.ACTIVO), "crm_tile_active", modifier = Modifier.weight(1f))
                        SummaryTile(R.string.crm_tile_dormant, summary.countsByStage.getValue(CrmStage.DORMIDO), "crm_tile_dormant", modifier = Modifier.weight(1f))
                        SummaryTile(R.string.crm_tile_prospects, summary.countsByStage.getValue(CrmStage.PROSPECTO), "crm_tile_prospects", modifier = Modifier.weight(1f))
                    }
                }
            }

            if (summary.toContactCount > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.accentSoft,
                        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().testTag("crm_to_contact_banner"),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = colors.accentHover)
                            Text(
                                text = pluralStringResource(R.plurals.crm_banner_to_contact, summary.toContactCount, summary.toContactCount),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.ink,
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.crm_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("crm_search"),
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CrmFilter.entries.toList()) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filterName = f.name },
                            label = { Text(filterLabel(f)) },
                            modifier = Modifier.testTag("crm_filter_${f.name.lowercase()}"),
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.Hotel,
                        title = stringResource(if (ofType.isEmpty()) R.string.crm_empty_title else R.string.crm_empty_filtered_title),
                        body = stringResource(if (ofType.isEmpty()) R.string.crm_empty_body else R.string.crm_empty_filtered_body),
                        actionLabel = if (ofType.isEmpty()) stringResource(R.string.crm_add_prospect) else null,
                        onAction = if (ofType.isEmpty()) ({ showAddProspect = true }) else null,
                    )
                }
            } else {
                items(visible, key = { it.accountKey }) { account ->
                    AccountCard(
                        account = account,
                        today = today,
                        onWhatsApp = { onSendWhatsApp(account, CrmMessageBuilder.build(account, issuerName)) },
                        onLog = { logTarget = account },
                        onDetails = { selectedKey = account.accountKey },
                    )
                }
            }
        }
    }

    val selected = accounts.firstOrNull { it.accountKey == selectedKey }
    if (selected != null) {
        CrmDetailDialog(
            account = selected,
            today = today,
            issuerName = issuerName,
            onClose = { selectedKey = null },
            onSendWhatsApp = { onSendWhatsApp(selected, it) },
            onLog = { logTarget = selected },
            onSaveDetails = { person, role, phone, email, notes ->
                onSaveDetails(selected, person, role, phone, email, notes)
            },
            onSetNextContact = { onSetNextContact(selected, it) },
            onDeleteContact = onDeleteContact,
            onDeleteAccountData = {
                onDeleteAccountData(selected)
                selectedKey = null
            },
        )
    }

    logTarget?.let { target ->
        LogContactDialog(
            today = today,
            onConfirm = { channel, note, next ->
                onLogContact(target, channel, note, today, next)
                logTarget = null
            },
            onDismiss = { logTarget = null },
        )
    }

    if (showAddProspect) {
        AddProspectDialog(
            initialType = type,
            onConfirm = { t, name ->
                onAddProspect(t, name)
                typeName = t.name
                showAddProspect = false
            },
            onDismiss = { showAddProspect = false },
        )
    }
}

@Composable
private fun filterLabel(filter: CrmFilter): String = when (filter) {
    CrmFilter.ALL -> stringResource(R.string.crm_filter_all)
    CrmFilter.TO_CONTACT -> stringResource(R.string.crm_filter_to_contact)
    CrmFilter.ACTIVO -> stringResource(R.string.crm_stage_active)
    CrmFilter.DORMIDO -> stringResource(R.string.crm_stage_dormant)
    CrmFilter.PROSPECTO -> stringResource(R.string.crm_stage_prospect)
    CrmFilter.INACTIVO -> stringResource(R.string.crm_stage_inactive)
}

@Composable
private fun stageLabel(stage: CrmStage): String = when (stage) {
    CrmStage.PROSPECTO -> stringResource(R.string.crm_stage_prospect)
    CrmStage.ACTIVO -> stringResource(R.string.crm_stage_active)
    CrmStage.DORMIDO -> stringResource(R.string.crm_stage_dormant)
    CrmStage.INACTIVO -> stringResource(R.string.crm_stage_inactive)
}

@Composable
private fun stageColors(stage: CrmStage): Pair<Color, Color> {
    val c = ApptividadesThemeDefaults.colors
    return when (stage) {
        CrmStage.PROSPECTO -> c.infoBg to c.info
        CrmStage.ACTIVO -> c.successBg to c.successText
        CrmStage.DORMIDO -> c.accentSoft to c.accentHover
        CrmStage.INACTIVO -> c.neutralChipBg to c.neutralChipText
    }
}

@Composable
private fun SummaryTile(labelRes: Int, value: Int, tag: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) colors.accent else colors.heroCardSurface,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (highlight) colors.ink else colors.heroCardText,
                modifier = Modifier.testTag(tag),
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (highlight) colors.ink else colors.onDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun daysAgoText(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today).coerceAtLeast(0)
    return when {
        days == 0L -> stringResource(R.string.crm_today)
        days == 1L -> stringResource(R.string.crm_yesterday)
        days < 60 -> stringResource(R.string.crm_days_ago, days.toInt())
        else -> stringResource(R.string.crm_months_ago, (days / 30).toInt())
    }
}

@Composable
private fun reasonText(reason: CrmContactReason): String = when (reason) {
    CrmContactReason.SCHEDULED_DUE -> stringResource(R.string.crm_reason_scheduled)
    CrmContactReason.DORMANT_NOT_CONTACTED -> stringResource(R.string.crm_reason_dormant)
    CrmContactReason.PROSPECT_NEVER_CONTACTED -> stringResource(R.string.crm_reason_prospect)
}

private fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${DominicanFormatter.formatShortMonth(date.month)} ${date.year}"

private fun formatAmounts(amounts: Map<Currency, BigDecimal>): String =
    amounts.entries.joinToString(" · ") { DominicanFormatter.formatMoney(it.value, it.key) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(
    account: CrmAccount,
    today: LocalDate,
    onWhatsApp: () -> Unit,
    onLog: () -> Unit,
    onDetails: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, if (account.needsContact) colors.accent else colors.line),
        modifier = Modifier.fillMaxWidth().testTag("crm_account_${account.accountKey}"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val (bg, fg) = stageColors(account.stage)
                StatusPill(label = stageLabel(account.stage), backgroundColor = bg, textColor = fg)
            }

            account.contactReason?.let {
                Text(
                    text = reasonText(it),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.dangerText,
                )
            }

            val lastGig = account.lastGigDate
            Text(
                text = if (lastGig != null) {
                    stringResource(
                        R.string.crm_last_gig,
                        "${daysAgoText(lastGig, today)} (${formatDate(lastGig)})",
                        account.gigCount,
                    )
                } else {
                    stringResource(R.string.crm_never_worked)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            account.nextGigDate?.let {
                Text(stringResource(R.string.crm_next_gig, formatDate(it)), style = MaterialTheme.typography.bodySmall, color = colors.successText)
            }
            if (account.revenueLast12Months.isNotEmpty()) {
                Text(
                    stringResource(R.string.crm_revenue, formatAmounts(account.revenueLast12Months)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            if (account.openQuotes + account.wonQuotes + account.lostQuotes > 0) {
                Text(
                    stringResource(R.string.crm_quotes_line, account.openQuotes, account.wonQuotes, account.lostQuotes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            Text(
                text = account.lastContactDate?.let { stringResource(R.string.crm_last_contact, daysAgoText(it, today)) }
                    ?: stringResource(R.string.crm_no_contacts),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            account.nextContactDate?.let {
                Text(
                    stringResource(R.string.crm_next_contact, formatDate(it)),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.muted,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onWhatsApp,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.surface),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp).testTag("crm_whatsapp_${account.accountKey}"),
                ) { Text(stringResource(R.string.crm_action_whatsapp), fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onLog,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.line),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp).testTag("crm_log_${account.accountKey}"),
                ) { Text(stringResource(R.string.crm_action_log), color = colors.ink) }
                TextButton(onClick = onDetails, modifier = Modifier.testTag("crm_details_${account.accountKey}")) {
                    Text(stringResource(R.string.crm_action_details), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrmDetailDialog(
    account: CrmAccount,
    today: LocalDate,
    issuerName: String,
    onClose: () -> Unit,
    onSendWhatsApp: (String) -> Unit,
    onLog: () -> Unit,
    onSaveDetails: (String, String, String, String, String) -> Unit,
    onSetNextContact: (LocalDate?) -> Unit,
    onDeleteContact: (CrmContact) -> Unit,
    onDeleteAccountData: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val profile = account.profile
    var person by remember(account.accountKey) { mutableStateOf(profile?.contactPerson.orEmpty()) }
    var role by remember(account.accountKey) { mutableStateOf(profile?.contactRole.orEmpty()) }
    var phone by remember(account.accountKey) { mutableStateOf(account.phone.orEmpty()) }
    var email by remember(account.accountKey) { mutableStateOf(account.email.orEmpty()) }
    var notes by remember(account.accountKey) { mutableStateOf(profile?.notes.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val message = remember(account, issuerName) { CrmMessageBuilder.build(account, issuerName) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.ground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("crm_detail_dialog"),
        ) {
            Surface(color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UtilityIconTile(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.nav_close),
                        onClick = onClose,
                        testTag = "crm_detail_close",
                        borderColor = colors.line,
                        backgroundColor = colors.surface,
                        tint = colors.ink,
                    )
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val (bg, fg) = stageColors(account.stage)
                    StatusPill(label = stageLabel(account.stage), backgroundColor = bg, textColor = fg)
                }
            }
            HorizontalDivider(thickness = 1.dp, color = colors.line)

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionTitle(R.string.crm_detail_section_stats)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val lastGig = account.lastGigDate
                    Text(
                        text = if (lastGig != null) {
                            stringResource(R.string.crm_last_gig, "${daysAgoText(lastGig, today)} (${formatDate(lastGig)})", account.gigCount)
                        } else {
                            stringResource(R.string.crm_never_worked)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink2,
                    )
                    account.nextGigDate?.let {
                        Text(stringResource(R.string.crm_next_gig, formatDate(it)), style = MaterialTheme.typography.bodySmall, color = colors.successText)
                    }
                    if (account.revenueLast12Months.isNotEmpty()) {
                        Text(stringResource(R.string.crm_revenue, formatAmounts(account.revenueLast12Months)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    if (account.openQuotes + account.wonQuotes + account.lostQuotes > 0) {
                        Text(
                            stringResource(R.string.crm_quotes_line, account.openQuotes, account.wonQuotes, account.lostQuotes),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }

                SectionTitle(R.string.crm_detail_section_contact)
                OutlinedTextField(person, { person = it }, label = { Text(stringResource(R.string.crm_detail_person)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("crm_detail_person"))
                OutlinedTextField(role, { role = it }, label = { Text(stringResource(R.string.crm_detail_role)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("crm_detail_role"))
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.crm_detail_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("crm_detail_phone"))
                OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.crm_detail_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("crm_detail_email"))
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.crm_detail_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth().testTag("crm_detail_notes"))
                Button(
                    onClick = { onSaveDetails(person, role, phone, email, notes) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.surface),
                    modifier = Modifier.testTag("crm_detail_save"),
                ) { Text(stringResource(R.string.crm_detail_save), fontWeight = FontWeight.SemiBold) }

                SectionTitle(R.string.crm_detail_section_next)
                Text(
                    text = account.nextContactDate?.let { formatDate(it) } ?: stringResource(R.string.crm_next_none),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HotelCrmCalculator.NEXT_CONTACT_PRESETS_DAYS.forEach { days ->
                        OutlinedButton(
                            onClick = { onSetNextContact(today.plusDays(days)) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier.testTag("crm_next_${days}d"),
                        ) { Text(stringResource(R.string.crm_next_in_days, days.toInt()), color = colors.ink) }
                    }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier.testTag("crm_next_pick"),
                    ) { Text(stringResource(R.string.crm_next_pick), color = colors.ink) }
                    if (account.nextContactDate != null) {
                        TextButton(onClick = { onSetNextContact(null) }, modifier = Modifier.testTag("crm_next_clear")) {
                            Text(stringResource(R.string.crm_next_clear), color = colors.dangerText)
                        }
                    }
                }

                SectionTitle(R.string.crm_detail_section_message)
                Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, border = BorderStroke(1.dp, colors.line)) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                        modifier = Modifier.padding(12.dp).testTag("crm_detail_message"),
                    )
                }
                Button(
                    onClick = { onSendWhatsApp(message) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.surface),
                    modifier = Modifier.testTag("crm_detail_whatsapp"),
                ) { Text(stringResource(R.string.crm_send_whatsapp), fontWeight = FontWeight.SemiBold) }
                if (account.phone.isNullOrBlank()) {
                    Text(stringResource(R.string.crm_whatsapp_no_phone), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }

                SectionTitle(R.string.crm_detail_section_log)
                OutlinedButton(
                    onClick = onLog,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.testTag("crm_detail_log"),
                ) { Text(stringResource(R.string.crm_action_log), color = colors.ink) }
                if (account.contacts.isEmpty()) {
                    Text(stringResource(R.string.crm_log_empty), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                } else {
                    account.contacts.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("crm_contact_${c.id}"),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${formatDate(c.date)} · ${c.channel.label}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                )
                                if (c.note.isNotBlank()) {
                                    Text(c.note, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                                }
                            }
                            IconButton(onClick = { onDeleteContact(c) }, modifier = Modifier.testTag("crm_contact_delete_${c.id}")) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.crm_delete_contact), tint = colors.dangerText)
                            }
                        }
                    }
                }

                if (profile != null) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("crm_delete_account_data")) {
                        Text(stringResource(R.string.crm_delete_account_data), color = colors.dangerText)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        PickDateDialog(
            initial = account.nextContactDate ?: today.plusDays(14),
            onPick = {
                onSetNextContact(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.crm_delete_account_title)) },
            text = { Text(stringResource(R.string.crm_delete_account_body, account.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteAccountData()
                    },
                    modifier = Modifier.testTag("crm_delete_account_confirm"),
                ) { Text(stringResource(R.string.crm_delete_account_data), color = colors.dangerText) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.form_cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = ApptividadesThemeDefaults.colors.ink,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            ) { Text(stringResource(R.string.form_confirm), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogContactDialog(
    today: LocalDate,
    onConfirm: (CrmContactChannel, String, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var channel by remember { mutableStateOf(CrmContactChannel.WHATSAPP) }
    var note by remember { mutableStateOf("") }
    // null = no reminder; default is a 14-day follow-up.
    var nextDays by remember { mutableStateOf<Long?>(14L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crm_log_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrmContactChannel.entries.forEach { c ->
                        FilterChip(
                            selected = channel == c,
                            onClick = { channel = c },
                            label = { Text(c.label) },
                            modifier = Modifier.testTag("crm_log_channel_${c.name.lowercase()}"),
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text(stringResource(R.string.crm_log_note)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("crm_log_note"),
                )
                Text(
                    stringResource(R.string.crm_log_next_label),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HotelCrmCalculator.NEXT_CONTACT_PRESETS_DAYS.forEach { days ->
                        FilterChip(
                            selected = nextDays == days,
                            onClick = { nextDays = days },
                            label = { Text(stringResource(R.string.crm_next_in_days, days.toInt())) },
                            modifier = Modifier.testTag("crm_log_next_${days}d"),
                        )
                    }
                    FilterChip(
                        selected = nextDays == null,
                        onClick = { nextDays = null },
                        label = { Text(stringResource(R.string.crm_next_none)) },
                        modifier = Modifier.testTag("crm_log_next_none"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(channel, note, nextDays?.let { today.plusDays(it) }) },
                modifier = Modifier.testTag("crm_log_confirm"),
            ) { Text(stringResource(R.string.crm_log_confirm), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddProspectDialog(
    initialType: CrmAccountType,
    onConfirm: (CrmAccountType, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crm_prospect_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrmAccountType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag("crm_prospect_type_${t.name.lowercase()}"),
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.crm_prospect_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("crm_prospect_name"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(type, name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("crm_prospect_confirm"),
            ) { Text(stringResource(R.string.crm_prospect_confirm), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
    )
}
