package com.example.ui.screens

import android.app.Activity as AndroidActivity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Supplier
import com.example.domain.rules.AccountStatementBuilder
import com.example.domain.rules.NameNormalizer
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.viewmodel.AppDataState

@Composable
fun SuppliersScreen(
    state: AppDataState,
    onBack: () -> Unit,
    onSaveSupplier: (Supplier?, String, String?, String?, String?, (Result<Unit>) -> Unit) -> Unit,
    onDeleteSupplier: (String, (Result<Unit>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    var query by remember { mutableStateOf("") }
    var editingSupplier by remember { mutableStateOf<Supplier?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Supplier?>(null) }

    val gigCountByKey = remember(state.activities) {
        state.activities
            .map { NameNormalizer.normalizeKey(it.supplierName) }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
    }

    val filteredSuppliers = remember(state.suppliers, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.suppliers
        else state.suppliers.filter {
            it.name.lowercase().contains(q) ||
                it.phone.orEmpty().lowercase().contains(q) ||
                it.email.orEmpty().lowercase().contains(q)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
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
                testTag = "suppliers_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
            Text(
                text = stringResource(R.string.suppliers_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            UtilityIconTile(
                icon = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.suppliers_add),
                onClick = { showCreateDialog = true },
                testTag = "suppliers_add_button",
                borderColor = colors.ink,
                backgroundColor = colors.ink,
                tint = colors.surface,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.suppliers_search_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = colors.muted,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.ink,
                            unfocusedBorderColor = colors.line,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("suppliers_search_input"),
                    )
                }

                if (filteredSuppliers.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.Groups,
                            title = stringResource(R.string.suppliers_empty_title),
                            body = stringResource(R.string.suppliers_empty_body),
                            actionLabel = stringResource(R.string.suppliers_add),
                            onAction = { showCreateDialog = true },
                        )
                    }
                } else {
                    items(filteredSuppliers, key = { it.id }) { supplier ->
                        val count = gigCountByKey[NameNormalizer.normalizeKey(supplier.name)] ?: 0
                        SupplierRowCard(
                            supplier = supplier,
                            gigCount = count,
                            onClick = { editingSupplier = supplier },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog || editingSupplier != null) {
        SupplierEditDialog(
            existing = editingSupplier,
            onDismiss = {
                showCreateDialog = false
                editingSupplier = null
            },
            onSave = { name, phone, email, notes ->
                val target = editingSupplier
                onSaveSupplier(target, name, phone, email, notes) { r ->
                    if (r.isSuccess) {
                        showCreateDialog = false
                        editingSupplier = null
                    }
                }
            },
            onRequestDelete = editingSupplier?.let { sup ->
                {
                    editingSupplier = null
                    deleteCandidate = sup
                }
            },
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.suppliers_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.suppliers_delete_body, candidate.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = candidate.id
                        deleteCandidate = null
                        onDeleteSupplier(id) {}
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger,
                        contentColor = colors.surface,
                    ),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.form_cancel), color = colors.muted)
                }
            },
        )
    }
}

@Composable
private fun SupplierRowCard(
    supplier: Supplier,
    gigCount: Int,
    onClick: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("supplier_card_${supplier.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = supplier.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (gigCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.gig_count, gigCount, gigCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            }

            val context = LocalContext.current
            val waPhone = remember(supplier.phone) {
                AccountStatementBuilder.normalizePhoneForWhatsApp(supplier.phone)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Phone,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = supplier.phone?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.suppliers_no_phone),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (supplier.phone.isNullOrBlank()) colors.muted else colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (waPhone != null) {
                    Surface(
                        onClick = {
                            val waIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://wa.me/$waPhone"),
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(waIntent) }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.successBg,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("supplier_whatsapp_${supplier.id}"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Chat,
                                contentDescription = stringResource(R.string.suppliers_open_whatsapp_cd, supplier.name),
                                tint = colors.successText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (!supplier.email.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = supplier.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
            }

            if (!supplier.notes.isNullOrBlank()) {
                Text(
                    text = supplier.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SupplierEditDialog(
    existing: Supplier?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String?) -> Unit,
    onRequestDelete: (() -> Unit)?,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var phone by remember(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var email by remember(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var pickedContactName by remember(existing?.id) { mutableStateOf<String?>(null) }

    // Zero-permission Android contact picker (includes WhatsApp contacts synced to Contacts)
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == AndroidActivity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            runCatching {
                context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val contactName = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                        val contactNumber = if (numberIdx >= 0) cursor.getString(numberIdx)?.trim().orEmpty() else ""
                        if (contactNumber.isNotEmpty()) {
                            phone = contactNumber
                        }
                        if (contactName.isNotEmpty()) {
                            if (name.isBlank()) {
                                name = contactName
                                pickedContactName = null
                            } else if (!name.trim().equals(contactName, ignoreCase = true)) {
                                pickedContactName = contactName
                            }
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(
                    if (existing == null) R.string.suppliers_add else R.string.suppliers_edit
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val pickIntent = Intent(
                            Intent.ACTION_PICK,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        )
                        runCatching { contactPickerLauncher.launch(pickIntent) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.accentSoft,
                        contentColor = colors.accentHover,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("supplier_pick_contact_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.suppliers_pick_contact_cta),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!pickedContactName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.suppliers_contact_imported_hint, pickedContactName!!),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                name = pickedContactName!!
                                pickedContactName = null
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.suppliers_contact_use_name),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.accentHover,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.suppliers_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.suppliers_phone_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.suppliers_email_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.suppliers_notes_label)) },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onRequestDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onRequestDelete,
                        border = BorderStroke(1.dp, colors.dangerBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.dangerText),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_delete), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onSave(name.trim(), phone.trim().ifEmpty { null }, email.trim().ifEmpty { null }, notes.trim().ifEmpty { null })
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.surface,
                ),
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
