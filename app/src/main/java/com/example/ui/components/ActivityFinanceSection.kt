package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.ReceiptImageStorage
import com.example.domain.model.ActivityExpenseEntry
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.ActivityPaymentEntry
import com.example.domain.model.Currency
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.ActivityFinanceCalculator
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.EffectivePaymentState
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Composable
fun expenseCategoryLabel(category: ExpenseCategory): String = when (category) {
    ExpenseCategory.TRANSPORT -> stringResource(R.string.expense_cat_transport)
    ExpenseCategory.MUSICIANS -> stringResource(R.string.expense_cat_musicians)
    ExpenseCategory.SOUND -> stringResource(R.string.expense_cat_sound)
    ExpenseCategory.LODGING_FOOD -> stringResource(R.string.expense_cat_lodging_food)
    ExpenseCategory.OTHER -> stringResource(R.string.expense_cat_other)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityFinanceSection(
    totalAmount: BigDecimal,
    currency: Currency,
    isPaid: Boolean,
    record: ActivityFinancialRecord,
    today: LocalDate,
    onRecordChanged: (ActivityFinancialRecord) -> Unit,
    onAutoMarkPaid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val breakdown = remember(totalAmount, isPaid, record) {
        ActivityFinanceCalculator.breakdown(
            totalAmount = totalAmount,
            paymentStatus = if (isPaid) PaymentStatus.PAGADO else PaymentStatus.PENDIENTE,
            record = record,
        )
    }

    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var previewReceiptFileName by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = modifier
            .fillMaxWidth()
            .testTag("form_finance_section"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.finance_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.finance_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
                if (breakdown.state == EffectivePaymentState.PAID && totalAmount > BigDecimal.ZERO) {
                    StatusPill(
                        label = stringResource(R.string.finance_metric_settled),
                        backgroundColor = colors.successBg,
                        textColor = colors.successText,
                    )
                }
            }

            // 3-column financial breakdown strip: Abonado · Pendiente · Ganancia neta
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.ground,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FinanceStripMetric(
                        label = stringResource(R.string.finance_metric_paid),
                        value = DominicanFormatter.formatMoney(breakdown.paidAmount, currency),
                        valueColor = if (breakdown.paidAmount > BigDecimal.ZERO) colors.successText else colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    FinanceStripMetric(
                        label = stringResource(R.string.finance_metric_pending),
                        value = if (breakdown.pendingBalance.compareTo(BigDecimal.ZERO) == 0 && totalAmount > BigDecimal.ZERO) {
                            stringResource(R.string.finance_metric_settled)
                        } else {
                            DominicanFormatter.formatMoney(breakdown.pendingBalance, currency)
                        },
                        valueColor = if (breakdown.pendingBalance > BigDecimal.ZERO) colors.accentHover else colors.successText,
                        modifier = Modifier.weight(1f),
                    )
                    FinanceStripMetric(
                        label = stringResource(R.string.finance_metric_net),
                        value = DominicanFormatter.formatMoney(breakdown.netProfit, currency),
                        valueColor = if (breakdown.netProfit < BigDecimal.ZERO) colors.dangerText else colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Partial payments list
            if (record.payments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.finance_payments_heading),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                    record.payments.forEach { entry ->
                        FinanceEntryRow(
                            primaryLabel = DominicanFormatter.formatMoney(entry.amount, currency),
                            secondaryLabel = buildList {
                                add(DominicanFormatter.formatShortDate(entry.date))
                                if (entry.note.isNotBlank()) add(entry.note)
                            }.joinToString(" · "),
                            badgeLabel = null,
                            receiptFileName = entry.receiptFileName,
                            amountColor = colors.successText,
                            onPreviewReceipt = { previewReceiptFileName = it },
                            onDelete = {
                                onRecordChanged(
                                    record.copy(payments = record.payments.filterNot { it.id == entry.id })
                                )
                            },
                            testTagPrefix = "payment_${entry.id}",
                        )
                    }
                }
            }

            // Expenses list
            if (record.expenses.isNotEmpty()) {
                if (record.payments.isNotEmpty()) {
                    HorizontalDivider(thickness = 1.dp, color = colors.line)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.finance_expenses_heading),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                    record.expenses.forEach { entry ->
                        FinanceEntryRow(
                            primaryLabel = "− ${DominicanFormatter.formatMoney(entry.amount, currency)}",
                            secondaryLabel = entry.note.ifBlank { expenseCategoryLabel(entry.category) },
                            badgeLabel = expenseCategoryLabel(entry.category),
                            receiptFileName = entry.receiptFileName,
                            amountColor = colors.dangerText,
                            onPreviewReceipt = { previewReceiptFileName = it },
                            onDelete = {
                                onRecordChanged(
                                    record.copy(expenses = record.expenses.filterNot { it.id == entry.id })
                                )
                            },
                            testTagPrefix = "expense_${entry.id}",
                        )
                    }
                }
            }

            // Action buttons row: + Registrar abono / seña & + Agregar gasto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { showAddPaymentDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.success.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.successBg,
                        contentColor = colors.successText,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("form_add_payment_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.finance_add_payment_cta),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = { showAddExpenseDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.ground,
                        contentColor = colors.ink,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("form_add_expense_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.finance_add_expense_cta),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showAddPaymentDialog) {
        AddPaymentDialog(
            totalAmount = totalAmount,
            pendingBalance = breakdown.pendingBalance,
            currency = currency,
            today = today,
            isAlreadyPaid = isPaid,
            onDismiss = { showAddPaymentDialog = false },
            onSave = { entry ->
                val updated = record.copy(payments = record.payments + entry)
                onRecordChanged(updated)
                val nextBreakdown = ActivityFinanceCalculator.breakdown(
                    totalAmount = totalAmount,
                    paymentStatus = if (isPaid) PaymentStatus.PAGADO else PaymentStatus.PENDIENTE,
                    record = updated,
                )
                if (!isPaid && totalAmount > BigDecimal.ZERO &&
                    nextBreakdown.pendingBalance.compareTo(BigDecimal.ZERO) == 0
                ) {
                    onAutoMarkPaid()
                }
                showAddPaymentDialog = false
            },
        )
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            currency = currency,
            onDismiss = { showAddExpenseDialog = false },
            onSave = { entry ->
                onRecordChanged(record.copy(expenses = record.expenses + entry))
                showAddExpenseDialog = false
            },
        )
    }

    previewReceiptFileName?.let { fileName ->
        ReceiptPreviewDialog(
            fileName = fileName,
            onDismiss = { previewReceiptFileName = null },
        )
    }
}

@Composable
private fun FinanceStripMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FinanceEntryRow(
    primaryLabel: String,
    secondaryLabel: String,
    badgeLabel: String?,
    receiptFileName: String?,
    amountColor: androidx.compose.ui.graphics.Color,
    onPreviewReceipt: (String) -> Unit,
    onDelete: () -> Unit,
    testTagPrefix: String,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.ground,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = primaryLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = amountColor,
                        maxLines = 1,
                    )
                    if (!badgeLabel.isNullOrBlank()) {
                        StatusPill(
                            label = badgeLabel,
                            backgroundColor = colors.neutralChipBg,
                            textColor = colors.neutralChipText,
                        )
                    }
                }
                if (secondaryLabel.isNotBlank()) {
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!receiptFileName.isNullOrBlank()) {
                    Surface(
                        onClick = { onPreviewReceipt(receiptFileName) },
                        shape = RoundedCornerShape(8.dp),
                        color = colors.accentSoft,
                        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f)),
                        modifier = Modifier.testTag("${testTagPrefix}_receipt_chip"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ReceiptLong,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.finance_receipt_view),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentHover,
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTagPrefix}_delete_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.finance_delete_entry_cd),
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPaymentDialog(
    totalAmount: BigDecimal,
    pendingBalance: BigDecimal,
    currency: Currency,
    today: LocalDate,
    isAlreadyPaid: Boolean,
    onDismiss: () -> Unit,
    onSave: (ActivityPaymentEntry) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var receiptFileName by remember { mutableStateOf<String?>(null) }
    var isImportingReceipt by remember { mutableStateOf(false) }
    var receiptError by remember { mutableStateOf<String?>(null) }
    val errorFallback = stringResource(R.string.finance_receipt_error)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            isImportingReceipt = true
            receiptError = null
            scope.launch {
                val imported = ReceiptImageStorage.importReceiptFromUri(context, uri)
                isImportingReceipt = false
                imported.onSuccess { fileName ->
                    receiptFileName?.let { ReceiptImageStorage.deleteReceiptFile(context, it) }
                    receiptFileName = fileName
                }.onFailure {
                    receiptError = errorFallback
                }
            }
        }
    }

    val parsedAmount = amountText.toBigDecimalOrNull()
    val willSettle = !isAlreadyPaid &&
        totalAmount > BigDecimal.ZERO &&
        parsedAmount != null &&
        parsedAmount >= pendingBalance &&
        pendingBalance > BigDecimal.ZERO

    AlertDialog(
        onDismissRequest = {
            receiptFileName?.let { ReceiptImageStorage.deleteReceiptFile(context, it) }
            onDismiss()
        },
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.finance_payment_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (totalAmount > BigDecimal.ZERO) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val quarter = totalAmount.multiply(BigDecimal("0.25")).setScale(0, RoundingMode.HALF_UP)
                        val half = totalAmount.multiply(BigDecimal("0.50")).setScale(0, RoundingMode.HALF_UP)
                        QuickAmountChip(
                            label = stringResource(R.string.finance_quick_25),
                            onClick = { amountText = quarter.stripTrailingZeros().toPlainString() },
                            testTag = "payment_quick_25",
                        )
                        QuickAmountChip(
                            label = stringResource(R.string.finance_quick_50),
                            onClick = { amountText = half.stripTrailingZeros().toPlainString() },
                            testTag = "payment_quick_50",
                        )
                        if (pendingBalance > BigDecimal.ZERO) {
                            QuickAmountChip(
                                label = stringResource(R.string.finance_quick_remaining),
                                onClick = { amountText = pendingBalance.stripTrailingZeros().toPlainString() },
                                testTag = "payment_quick_remaining",
                            )
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
                    label = { Text(stringResource(R.string.finance_amount_label, currency.symbol)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_dialog_amount_input"),
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.finance_note_payment_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_dialog_note_input"),
                )

                ReceiptAttachRow(
                    receiptFileName = receiptFileName,
                    isImporting = isImportingReceipt,
                    errorText = receiptError,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveImage = {
                        receiptFileName?.let { ReceiptImageStorage.deleteReceiptFile(context, it) }
                        receiptFileName = null
                    },
                )

                if (willSettle) {
                    Text(
                        text = stringResource(R.string.finance_auto_settled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.successText,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedAmount != null && parsedAmount > BigDecimal.ZERO) {
                        onSave(
                            ActivityPaymentEntry(
                                amount = parsedAmount,
                                date = today,
                                note = noteText.trim(),
                                receiptFileName = receiptFileName,
                            )
                        )
                    }
                },
                enabled = parsedAmount != null && parsedAmount > BigDecimal.ZERO && !isImportingReceipt,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.surface,
                ),
                modifier = Modifier.testTag("payment_dialog_save_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    receiptFileName?.let { ReceiptImageStorage.deleteReceiptFile(context, it) }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.form_cancel), color = colors.muted)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseDialog(
    currency: Currency,
    onDismiss: () -> Unit,
    onSave: (ActivityExpenseEntry) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(ExpenseCategory.TRANSPORT) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var receiptFileName by remember { mutableStateOf<String?>(null) }
    var isImportingReceipt by remember { mutableStateOf(false) }
    var receiptError by remember { mutableStateOf<String?>(null) }
    val errorFallback = stringResource(R.string.finance_receipt_error)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            isImportingReceipt = true
            receiptError = null
            scope.launch {
                val imported = ReceiptImageStorage.importReceiptFromUri(context, uri)
                isImportingReceipt = false
                imported.onSuccess { fileName ->
                    receiptFileName?.let { ReceiptImageStorage.deleteReceiptFile(context, it) }
                    receiptFileName = fileName
                }.onFailure {
                    receiptError = errorFallback
                }
            }
        }
    }

    val parsedAmount = amountText.toBigDecimalOrNull()

    AlertDialog(
        onDismissRequest = {
            receiptFileName?.let { ReceiptImageStorage.deleteReceiptFile(context, it) }
            onDismiss()
        },
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.finance_expense_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.finance_category_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ExpenseCategory.entries.forEach { cat ->
                        val active = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(999.dp),
                            color = if (active) colors.ink else colors.ground,
                            border = BorderStroke(1.dp, if (active) colors.ink else colors.line),
                            modifier = Modifier.testTag("expense_cat_${cat.code}"),
                        ) {
                            Text(
                                text = expenseCategoryLabel(cat),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (active) colors.surface else colors.ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            )
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
                    label = { Text(stringResource(R.string.finance_amount_label, currency.symbol)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_dialog_amount_input"),
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.finance_note_expense_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_dialog_note_input"),
                )

                ReceiptAttachRow(
                    receiptFileName = receiptFileName,
                    isImporting = isImportingReceipt,
                    errorText = receiptError,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveImage = {
                        receiptFileName?.let { ReceiptImageStorage.deleteReceiptFile(context, it) }
                        receiptFileName = null
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedAmount != null && parsedAmount > BigDecimal.ZERO) {
                        onSave(
                            ActivityExpenseEntry(
                                category = selectedCategory,
                                amount = parsedAmount,
                                note = noteText.trim(),
                                receiptFileName = receiptFileName,
                            )
                        )
                    }
                },
                enabled = parsedAmount != null && parsedAmount > BigDecimal.ZERO && !isImportingReceipt,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.surface,
                ),
                modifier = Modifier.testTag("expense_dialog_save_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    receiptFileName?.let { ReceiptImageStorage.deleteReceiptFile(context, it) }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.form_cancel), color = colors.muted)
            }
        },
    )
}

@Composable
private fun QuickAmountChip(
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = colors.accentSoft,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
        modifier = Modifier.testTag(testTag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.accentHover,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ReceiptAttachRow(
    receiptFileName: String?,
    isImporting: Boolean,
    errorText: String?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isImporting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.finance_receipt_importing),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        } else if (receiptFileName != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.successBg,
                border = BorderStroke(1.dp, colors.success.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = null,
                            tint = colors.successText,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.finance_receipt_attached),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.successText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.finance_receipt_remove_cd),
                            tint = colors.successText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = onPickImage,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("finance_attach_receipt_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.finance_receipt_attach),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
            }
        }

        if (!errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.dangerText,
            )
        }
    }
}

@Composable
private fun ReceiptPreviewDialog(
    fileName: String,
    onDismiss: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    var bitmap by remember(fileName) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(fileName) { mutableStateOf(true) }

    LaunchedEffect(fileName) {
        isLoading = true
        bitmap = ReceiptImageStorage.loadBitmapForDisplay(context, fileName, maxDimension = 1280)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 32.dp)
                .testTag("receipt_preview_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.finance_receipt_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("receipt_preview_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.nav_close),
                            tint = colors.ink,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 480.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.ground),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoading -> CircularProgressIndicator(color = colors.accent)
                        bitmap != null -> Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.finance_receipt_preview_title),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Text(
                            text = stringResource(R.string.finance_receipt_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
}
