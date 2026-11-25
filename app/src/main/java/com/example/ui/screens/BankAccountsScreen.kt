package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BankAccount
import com.example.domain.model.BankAccountDraft
import com.example.domain.model.BankAccountType
import com.example.domain.model.Currency
import com.example.domain.rules.AccountMasker
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun BankAccountsScreen(
    accounts: List<BankAccount>,
    isMutating: Boolean,
    defaultCurrency: Currency,
    onBack: () -> Unit,
    onSaveAccount: (existing: BankAccount?, draft: BankAccountDraft, onResult: (Result<Unit>) -> Unit) -> Unit,
    onSetDefault: (BankAccount) -> Unit,
    onDeleteAccount: (accountId: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    var editingAccount by remember { mutableStateOf<BankAccount?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf<BankAccount?>(null) }

    val sortedAccounts = remember(accounts) {
        accounts.sortedWith(
            compareBy<BankAccount> { it.currency.code }
                .thenByDescending { it.isDefault }
                .thenBy { it.label.lowercase() }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    testTag = "bank_accounts_back_button",
                    borderColor = colors.line,
                    backgroundColor = colors.surface,
                    tint = colors.ink,
                )
                Text(
                    text = stringResource(R.string.bank_accounts_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (sortedAccounts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        EmptyStateCard(
                            icon = Icons.Outlined.AccountBalance,
                            title = stringResource(R.string.bank_accounts_empty_title),
                            body = stringResource(R.string.bank_accounts_empty_body),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 6.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sortedAccounts, key = { it.id }) { account ->
                            BankAccountCard(
                                account = account,
                                enabled = !isMutating,
                                onEdit = {
                                    editingAccount = account
                                    showFormDialog = true
                                },
                                onMakeDefault = { onSetDefault(account) },
                                onDelete = { deletingAccount = account },
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingAccount = null
                showFormDialog = true
            },
            containerColor = colors.ink,
            contentColor = colors.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("bank_accounts_add_fab"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.bank_accounts_add),
            )
        }
    }

    if (showFormDialog) {
        BankAccountFormDialog(
            existing = editingAccount,
            defaultCurrency = defaultCurrency,
            isMutating = isMutating,
            onDismiss = { showFormDialog = false },
            onSubmit = { draft ->
                onSaveAccount(editingAccount, draft) { res ->
                    if (res.isSuccess) showFormDialog = false
                }
            },
        )
    }

    deletingAccount?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.bank_account_delete_title),
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.bank_account_delete_body, target.label),
                    color = colors.muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.id
                        deletingAccount = null
                        onDeleteAccount(id) {}
                    },
                    modifier = Modifier.testTag("bank_account_confirm_delete"),
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = colors.dangerText,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAccount = null }) {
                    Text(stringResource(R.string.form_cancel), color = colors.muted)
                }
            },
        )
    }
}

@Composable
private fun BankAccountCard(
    account: BankAccount,
    enabled: Boolean,
    onEdit: () -> Unit,
    onMakeDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (account.isDefault) 1.5.dp else 1.dp,
            color = if (account.isDefault) colors.accent else colors.line,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bank_account_card_${account.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = account.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    label = "${account.currency.symbol} (${account.currency.code})",
                    backgroundColor = colors.neutralChipBg,
                    textColor = colors.ink,
                )
                if (account.isDefault) {
                    StatusPill(
                        label = stringResource(R.string.bank_account_badge_default),
                        backgroundColor = colors.accentSoft,
                        textColor = colors.accentHover,
                    )
                }
            }

            val typeSuffix = account.accountType?.let { " · ${it.label}" }.orEmpty()
            Text(
                text = "${account.bankName}$typeSuffix",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            Text(
                text = account.holderName,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            var isNumberRevealed by remember(account.id) { mutableStateOf(false) }
            val displayedNumber = remember(account.accountNumber, isNumberRevealed) {
                if (isNumberRevealed) account.accountNumber
                else AccountMasker.maskAccountNumber(account.accountNumber)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayedNumber,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colors.accentHover,
                        modifier = Modifier.testTag("bank_account_number_${account.id}"),
                    )
                    if (!isNumberRevealed) {
                        Text(
                            text = stringResource(R.string.bank_account_mask_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                        )
                    }
                }
                IconButton(
                    onClick = { isNumberRevealed = !isNumberRevealed },
                    modifier = Modifier.testTag("bank_account_toggle_mask_${account.id}"),
                ) {
                    Icon(
                        imageVector = if (isNumberRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (isNumberRevealed) R.string.bank_account_hide_cd
                            else R.string.bank_account_reveal_cd
                        ),
                        tint = colors.ink,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!account.isDefault) {
                    OutlinedButton(
                        onClick = onMakeDefault,
                        enabled = enabled,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("bank_account_make_default_${account.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.bank_account_make_default),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.ink,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onEdit,
                    enabled = enabled,
                    modifier = Modifier.testTag("bank_account_edit_${account.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.bank_accounts_edit),
                        tint = colors.ink,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier.testTag("bank_account_delete_${account.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = colors.dangerText,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankAccountFormDialog(
    existing: BankAccount?,
    defaultCurrency: Currency,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (BankAccountDraft) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    var label by remember(existing) { mutableStateOf(existing?.label.orEmpty()) }
    var bankName by remember(existing) { mutableStateOf(existing?.bankName.orEmpty()) }
    var holderName by remember(existing) { mutableStateOf(existing?.holderName.orEmpty()) }
    var accountNumber by remember(existing) { mutableStateOf(existing?.accountNumber.orEmpty()) }
    var accountType by remember(existing) { mutableStateOf(existing?.accountType) }
    var currency by remember(existing) { mutableStateOf(existing?.currency ?: defaultCurrency) }
    var isDefault by remember(existing) { mutableStateOf(existing?.isDefault ?: false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(
                    if (existing == null) R.string.bank_accounts_add else R.string.bank_accounts_edit
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!localError.isNullOrBlank()) {
                    Text(
                        text = localError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dangerText,
                    )
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        localError = null
                    },
                    label = { Text(stringResource(R.string.bank_account_label)) },
                    placeholder = { Text(stringResource(R.string.bank_account_label_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.ink,
                        unfocusedBorderColor = colors.line,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_form_label_input"),
                )

                OutlinedTextField(
                    value = bankName,
                    onValueChange = {
                        bankName = it
                        localError = null
                    },
                    label = { Text(stringResource(R.string.bank_account_bank)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_form_bank_input"),
                )

                OutlinedTextField(
                    value = holderName,
                    onValueChange = {
                        holderName = it
                        localError = null
                    },
                    label = { Text(stringResource(R.string.bank_account_holder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_form_holder_input"),
                )

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = {
                        accountNumber = it
                        localError = null
                    },
                    label = { Text(stringResource(R.string.bank_account_number)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_form_number_input"),
                )

                Text(
                    text = stringResource(R.string.bank_account_currency),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Currency.entries.forEach { curr ->
                        FilterChip(
                            selected = currency == curr,
                            onClick = { currency = curr },
                            label = { Text("${curr.symbol} (${curr.code})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.ink,
                                selectedLabelColor = colors.surface,
                            ),
                            modifier = Modifier.testTag("bank_form_currency_${curr.code.lowercase()}"),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.bank_account_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BankAccountType.entries.forEach { type ->
                        FilterChip(
                            selected = accountType == type,
                            onClick = { accountType = type },
                            label = { Text(type.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.ink,
                                selectedLabelColor = colors.surface,
                            ),
                        )
                    }
                    FilterChip(
                        selected = accountType == null,
                        onClick = { accountType = null },
                        label = { Text(stringResource(R.string.bank_account_type_none)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.ink,
                            selectedLabelColor = colors.surface,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.bank_account_default_switch),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it },
                        modifier = Modifier.testTag("bank_form_default_switch"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanLabel = label.trim()
                    val cleanBank = bankName.trim()
                    val cleanHolder = holderName.trim()
                    val cleanNumber = accountNumber.trim()
                    when {
                        cleanLabel.isEmpty() -> localError = "La etiqueta es obligatoria (ej. Popular DOP)."
                        cleanBank.isEmpty() -> localError = "Indica el nombre del banco."
                        cleanHolder.isEmpty() -> localError = "Indica el nombre del titular."
                        cleanNumber.isEmpty() -> localError = "Indica el número de cuenta."
                        else -> onSubmit(
                            BankAccountDraft(
                                label = cleanLabel,
                                bankName = cleanBank,
                                holderName = cleanHolder,
                                accountNumber = cleanNumber,
                                accountType = accountType,
                                currency = currency,
                                isDefault = isDefault,
                            )
                        )
                    }
                },
                enabled = !isMutating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.surface,
                ),
                modifier = Modifier.testTag("bank_form_save_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.form_cancel), color = colors.muted)
            }
        },
    )
}
