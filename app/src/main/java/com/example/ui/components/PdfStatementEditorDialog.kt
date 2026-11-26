package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.domain.model.BankAccount
import com.example.domain.rules.AccountStatement
import com.example.domain.rules.AccountStatementBuilder
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.ReceivableStatus
import com.example.domain.rules.StatementLineItem
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private data class EditableLineDraft(
    val key: String,
    val activityId: String,
    val eventDate: LocalDate,
    val eventTime: java.time.LocalTime?,
    val serviceDescription: String,
    val venueName: String,
    val status: ReceivableStatus,
    val overdueDays: Long,
    val statusLabel: String,
    val amountText: String,
)

private sealed interface PdfEditorDateTarget {
    data object IssueDate : PdfEditorDateTarget
    data class ItemDate(val index: Int) : PdfEditorDateTarget
}

/**
 * Full-screen editor that lets the artist correct irregularities in a generated PDF
 * (header metadata, line items, manual adjustments/discounts, bank accounts, closing texts,
 * and WhatsApp caption) without mutating saved Supabase records.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfStatementEditorDialog(
    statement: AccountStatement,
    originalStatement: AccountStatement,
    availableBankAccounts: List<BankAccount>,
    onApply: (AccountStatement) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors

    var documentTitle by remember(statement) { mutableStateOf(statement.documentTitle) }
    var issuerName by remember(statement) { mutableStateOf(statement.issuerName) }
    var supplierName by remember(statement) { mutableStateOf(statement.supplierDisplayName) }
    var issueDate by remember(statement) { mutableStateOf(statement.issueDate) }

    var nextManualKey by remember { mutableIntStateOf(1) }
    var lineDrafts by remember(statement) {
        mutableStateOf(
            statement.items.mapIndexed { idx, item ->
                EditableLineDraft(
                    key = "${item.activityId}_$idx",
                    activityId = item.activityId,
                    eventDate = item.eventDate,
                    eventTime = item.eventTime,
                    serviceDescription = item.serviceDescription,
                    venueName = item.venueName,
                    status = item.status,
                    overdueDays = item.overdueDays,
                    statusLabel = item.statusLabel,
                    amountText = item.amount.stripTrailingZeros().toPlainString(),
                )
            }
        )
    }

    val allCandidateBanks = remember(availableBankAccounts, statement.bankAccounts) {
        val seen = LinkedHashMap<String, BankAccount>()
        (availableBankAccounts + statement.bankAccounts).forEach { acc ->
            seen.putIfAbsent(acc.id, acc)
        }
        seen.values.toList()
    }
    var selectedBankIds by remember(statement) {
        mutableStateOf(statement.bankAccounts.map { it.id }.toSet())
    }

    var friendlyNotice by remember(statement) { mutableStateOf(statement.friendlyNotice) }
    var closingQuote by remember(statement) { mutableStateOf(statement.closingQuote) }
    var closingQuoteAttribution by remember(statement) { mutableStateOf(statement.closingQuoteAttribution) }
    var gratitudeMessage by remember(statement) { mutableStateOf(statement.gratitudeMessage) }
    var shareCaption by remember(statement) { mutableStateOf(statement.shareCaption) }
    var shareCaptionTouched by remember(statement) {
        mutableStateOf(statement.shareCaption != originalStatement.shareCaption)
    }

    var activeDateTarget by remember { mutableStateOf<PdfEditorDateTarget?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val errorEmptyItemsMsg = stringResource(R.string.pdf_editor_error_empty_items)
    val errorInvalidAmountMsg = stringResource(R.string.pdf_editor_error_invalid_amount)

    val liveTotal = remember(lineDrafts) {
        lineDrafts.fold(BigDecimal.ZERO) { acc, item ->
            val parsed = item.amountText.replace(",", "").trim().toBigDecimalOrNull()
            if (parsed != null) acc.add(parsed) else acc
        }
    }

    fun resetToOriginal() {
        documentTitle = originalStatement.documentTitle
        issuerName = originalStatement.issuerName
        supplierName = originalStatement.supplierDisplayName
        issueDate = originalStatement.issueDate
        lineDrafts = originalStatement.items.mapIndexed { idx, item ->
            EditableLineDraft(
                key = "${item.activityId}_orig_$idx",
                activityId = item.activityId,
                eventDate = item.eventDate,
                eventTime = item.eventTime,
                serviceDescription = item.serviceDescription,
                venueName = item.venueName,
                status = item.status,
                overdueDays = item.overdueDays,
                statusLabel = item.statusLabel,
                amountText = item.amount.stripTrailingZeros().toPlainString(),
            )
        }
        selectedBankIds = originalStatement.bankAccounts.map { it.id }.toSet()
        friendlyNotice = originalStatement.friendlyNotice
        closingQuote = originalStatement.closingQuote
        closingQuoteAttribution = originalStatement.closingQuoteAttribution
        gratitudeMessage = originalStatement.gratitudeMessage
        shareCaption = originalStatement.shareCaption
        shareCaptionTouched = false
        validationError = null
    }

    fun applyChanges() {
        if (lineDrafts.isEmpty()) {
            validationError = errorEmptyItemsMsg
            return
        }
        val parsedItems = mutableListOf<StatementLineItem>()
        for (draft in lineDrafts) {
            val cleanAmount = draft.amountText.replace(",", "").trim()
            val amount = if (cleanAmount.isEmpty()) BigDecimal.ZERO else cleanAmount.toBigDecimalOrNull()
            if (amount == null) {
                validationError = errorInvalidAmountMsg
                return
            }
            parsedItems.add(
                StatementLineItem(
                    activityId = draft.activityId,
                    eventDate = draft.eventDate,
                    eventTime = draft.eventTime,
                    serviceDescription = draft.serviceDescription.trim()
                        .ifEmpty { AccountStatementBuilder.DEFAULT_SERVICE_LABEL },
                    venueName = draft.venueName.trim().ifEmpty { "—" },
                    status = draft.status,
                    overdueDays = draft.overdueDays,
                    statusLabel = draft.statusLabel.trim().ifEmpty {
                        when (draft.status) {
                            ReceivableStatus.VENCIDO -> "Vencido"
                            ReceivableStatus.ESTE_MES -> "Por cobrar este mes"
                            ReceivableStatus.PROXIMO_MES -> "En plazo"
                        }
                    },
                    amount = amount,
                )
            )
        }

        validationError = null
        val chosenBanks = allCandidateBanks.filter { it.id in selectedBankIds }
        val updated = AccountStatementBuilder.recalculate(
            statement = statement.copy(
                documentTitle = documentTitle,
                issuerName = issuerName,
                supplierDisplayName = supplierName,
                issueDate = issueDate,
                items = parsedItems,
                bankAccounts = chosenBanks,
                friendlyNotice = friendlyNotice.trim(),
                closingQuote = closingQuote.trim(),
                closingQuoteAttribution = closingQuoteAttribution.trim(),
                gratitudeMessage = gratitudeMessage.trim(),
                shareCaption = shareCaption,
            ),
            customShareCaptionEdited = shareCaptionTouched,
        )
        onApply(updated)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
        focusedBorderColor = colors.ink,
        unfocusedBorderColor = colors.line,
        focusedTextColor = colors.ink,
        unfocusedTextColor = colors.ink,
        focusedLabelColor = colors.ink,
        unfocusedLabelColor = colors.muted,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = FullScreenDialogProperties,
    ) {
        FullScreenDialogWindow()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.ground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("pdf_statement_editor_dialog"),
        ) {
            // Top bar
            Surface(
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UtilityIconTile(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.nav_close),
                        onClick = onDismiss,
                        testTag = "pdf_editor_close_button",
                        borderColor = colors.line,
                        backgroundColor = colors.surface,
                        tint = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.pdf_editor_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = ::resetToOriginal,
                        modifier = Modifier.testTag("pdf_editor_reset_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.pdf_editor_reset),
                            color = colors.accentHover,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Info banner: edits don't alter database
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.infoBg,
                            border = BorderStroke(1.dp, colors.info.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = colors.info,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.pdf_editor_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.ink,
                                )
                            }
                        }
                    }

                    if (!validationError.isNullOrBlank()) {
                        item {
                            ErrorBanner(message = validationError!!)
                        }
                    }

                    // Section 1: Header metadata
                    item {
                        EditorSectionCard(title = stringResource(R.string.pdf_editor_section_header)) {
                            OutlinedTextField(
                                value = documentTitle,
                                onValueChange = { documentTitle = it },
                                label = { Text(stringResource(R.string.pdf_editor_doc_title)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_editor_title_input"),
                            )
                            OutlinedTextField(
                                value = issuerName,
                                onValueChange = { issuerName = it },
                                label = { Text(stringResource(R.string.pdf_editor_issuer)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_editor_issuer_input"),
                            )
                            OutlinedTextField(
                                value = supplierName,
                                onValueChange = { supplierName = it },
                                label = { Text(stringResource(R.string.pdf_editor_supplier)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_editor_supplier_input"),
                            )
                            Surface(
                                onClick = { activeDateTarget = PdfEditorDateTarget.IssueDate },
                                shape = RoundedCornerShape(12.dp),
                                color = colors.ground,
                                border = BorderStroke(1.dp, colors.line),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("pdf_editor_issue_date_button"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.pdf_editor_issue_date),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.muted,
                                        )
                                        Text(
                                            text = DominicanFormatter.formatFormDate(issueDate),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.ink,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = colors.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Section 2 header: Line items + Live Total
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_editor_section_items),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.ink,
                            )
                            StatusPill(
                                label = "Total: ${DominicanFormatter.formatMoney(liveTotal, statement.currency)}",
                                backgroundColor = colors.accentSoft,
                                textColor = colors.accentHover,
                                modifier = Modifier.testTag("pdf_editor_live_total"),
                            )
                        }
                    }

                    // Editable line item cards
                    itemsIndexed(lineDrafts, key = { _, item -> item.key }) { index, item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pdf_editor_item_card_$index"),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        onClick = { activeDateTarget = PdfEditorDateTarget.ItemDate(index) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.neutralChipBg,
                                        border = BorderStroke(1.dp, colors.line),
                                        modifier = Modifier.height(40.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CalendarToday,
                                                contentDescription = null,
                                                tint = colors.ink,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = DominicanFormatter.formatFormDate(item.eventDate),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.ink,
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            lineDrafts = lineDrafts.toMutableList().also { it.removeAt(index) }
                                        },
                                        enabled = lineDrafts.size > 1,
                                        modifier = Modifier.testTag("pdf_editor_remove_item_$index"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = stringResource(R.string.pdf_editor_item_remove_cd),
                                            tint = if (lineDrafts.size > 1) colors.danger else colors.muted.copy(alpha = 0.4f),
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = item.serviceDescription,
                                    onValueChange = { next ->
                                        lineDrafts = lineDrafts.toMutableList().also {
                                            it[index] = item.copy(serviceDescription = next)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.pdf_editor_item_service)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pdf_editor_item_service_$index"),
                                )

                                OutlinedTextField(
                                    value = item.venueName,
                                    onValueChange = { next ->
                                        lineDrafts = lineDrafts.toMutableList().also {
                                            it[index] = item.copy(venueName = next)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.pdf_editor_item_venue)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pdf_editor_item_venue_$index"),
                                )

                                OutlinedTextField(
                                    value = item.amountText,
                                    onValueChange = { next ->
                                        if (next.isEmpty() || next.matches(Regex("^-?\\d*\\.?\\d{0,2}$"))) {
                                            lineDrafts = lineDrafts.toMutableList().also {
                                                it[index] = item.copy(amountText = next)
                                            }
                                        }
                                    },
                                    label = {
                                        Text("${stringResource(R.string.pdf_editor_item_amount)} (${statement.currency.symbol})")
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pdf_editor_item_amount_$index"),
                                )

                                OutlinedTextField(
                                    value = item.statusLabel,
                                    onValueChange = { next ->
                                        lineDrafts = lineDrafts.toMutableList().also {
                                            it[index] = item.copy(statusLabel = next)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.pdf_editor_item_status)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                // Status category chips so overdueTotal recalculates cleanly
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(
                                        ReceivableStatus.VENCIDO to stringResource(R.string.status_vencido),
                                        ReceivableStatus.ESTE_MES to stringResource(R.string.status_este_mes),
                                        ReceivableStatus.PROXIMO_MES to stringResource(R.string.status_proximo_mes),
                                    ).forEach { (statusOption, label) ->
                                        val selected = item.status == statusOption
                                        Surface(
                                            onClick = {
                                                val defaultLabel = when (statusOption) {
                                                    ReceivableStatus.VENCIDO -> "Vencido"
                                                    ReceivableStatus.ESTE_MES -> "Por cobrar este mes"
                                                    ReceivableStatus.PROXIMO_MES -> "En plazo"
                                                }
                                                lineDrafts = lineDrafts.toMutableList().also {
                                                    it[index] = item.copy(
                                                        status = statusOption,
                                                        statusLabel = defaultLabel,
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (selected) colors.ink else colors.neutralChipBg,
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                color = if (selected) colors.surface else colors.ink,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add manual adjustment / extra line item button
                    item {
                        OutlinedButton(
                            onClick = {
                                val keyId = "manual_${nextManualKey++}"
                                lineDrafts = lineDrafts + EditableLineDraft(
                                    key = keyId,
                                    activityId = keyId,
                                    eventDate = issueDate,
                                    eventTime = null,
                                    serviceDescription = "Ajuste / servicio adicional",
                                    venueName = "—",
                                    status = ReceivableStatus.ESTE_MES,
                                    overdueDays = 0L,
                                    statusLabel = "Ajuste",
                                    amountText = "0",
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pdf_editor_add_item_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.pdf_editor_add_item),
                                color = colors.accentHover,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // Section 3: Bank accounts selector
                    item {
                        EditorSectionCard(title = stringResource(R.string.pdf_editor_section_banks)) {
                            if (allCandidateBanks.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.pdf_editor_no_banks),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            } else {
                                allCandidateBanks.forEach { bank ->
                                    val checked = bank.id in selectedBankIds
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedBankIds = if (checked) {
                                                    selectedBankIds - bank.id
                                                } else {
                                                    selectedBankIds + bank.id
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                selectedBankIds = if (isChecked) {
                                                    selectedBankIds + bank.id
                                                } else {
                                                    selectedBankIds - bank.id
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = colors.ink,
                                                checkmarkColor = colors.surface,
                                            ),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${bank.label} · ${bank.currency.symbol}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.ink,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${bank.bankName} · ${bank.accountNumber}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.muted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 4: Closing texts & WhatsApp caption
                    item {
                        EditorSectionCard(title = stringResource(R.string.pdf_editor_section_texts)) {
                            OutlinedTextField(
                                value = friendlyNotice,
                                onValueChange = { friendlyNotice = it },
                                label = { Text(stringResource(R.string.pdf_editor_friendly_notice)) },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = closingQuote,
                                onValueChange = { closingQuote = it },
                                label = { Text(stringResource(R.string.pdf_editor_quote)) },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = closingQuoteAttribution,
                                onValueChange = { closingQuoteAttribution = it },
                                label = { Text(stringResource(R.string.pdf_editor_quote_attr)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = gratitudeMessage,
                                onValueChange = { gratitudeMessage = it },
                                label = { Text(stringResource(R.string.pdf_editor_gratitude)) },
                                minLines = 2,
                                maxLines = 5,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = shareCaption,
                                onValueChange = {
                                    shareCaption = it
                                    shareCaptionTouched = true
                                },
                                label = { Text(stringResource(R.string.pdf_editor_whatsapp_caption)) },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_editor_caption_input"),
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            // Bottom action bar
            Surface(
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(50.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.form_cancel),
                            color = colors.ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = ::applyChanges,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.surface,
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("pdf_editor_apply_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.pdf_editor_apply),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    // Shared DatePickerDialog for issueDate or any line item date
    activeDateTarget?.let { target ->
        val initialDate = when (target) {
            PdfEditorDateTarget.IssueDate -> issueDate
            is PdfEditorDateTarget.ItemDate -> lineDrafts.getOrNull(target.index)?.eventDate ?: issueDate
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { activeDateTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            when (target) {
                                PdfEditorDateTarget.IssueDate -> issueDate = picked
                                is PdfEditorDateTarget.ItemDate -> {
                                    if (target.index in lineDrafts.indices) {
                                        lineDrafts = lineDrafts.toMutableList().also {
                                            it[target.index] = it[target.index].copy(eventDate = picked)
                                        }
                                    }
                                }
                            }
                        }
                        activeDateTarget = null
                    }
                ) {
                    Text(stringResource(R.string.form_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDateTarget = null }) {
                    Text(stringResource(R.string.form_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
            content()
        }
    }
}
