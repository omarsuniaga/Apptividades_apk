package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Activity
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.SupplierMessagePreset
import com.example.domain.rules.SupplierQuickMessageBuilder
import com.example.ui.theme.ApptividadesThemeDefaults

/**
 * Quick communication sheet allowing the artist to notify the supplier on arrival,
 * upon finishing the performance, or for any open situation, with full free-text editing
 * before sending via WhatsApp, sharing, or copying.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupplierQuickMessageSheet(
    activity: Activity,
    supplierDisplayName: String,
    venueDisplayName: String,
    supplierRawPhone: String?,
    onFeedback: (String) -> Unit,
    onDismiss: () -> Unit,
    initialPreset: SupplierMessagePreset = SupplierMessagePreset.ARRIVED,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val resolvedSupplier = supplierDisplayName.ifBlank { activity.supplierName }.trim()
    val resolvedVenue = venueDisplayName.ifBlank { activity.venueName }.trim()

    var selectedPreset by remember(activity.id, initialPreset) { mutableStateOf(initialPreset) }
    var messageText by remember(activity.id, initialPreset) {
        mutableStateOf(
            SupplierQuickMessageBuilder.buildPresetMessage(
                preset = initialPreset,
                supplierDisplayName = resolvedSupplier,
                venueName = resolvedVenue,
                eventTime = activity.eventTime,
            )
        )
    }

    val copiedMsg = stringResource(R.string.supplier_msg_copied)
    val openErrorMsg = stringResource(R.string.supplier_msg_open_error)

    fun selectPreset(preset: SupplierMessagePreset) {
        selectedPreset = preset
        messageText = SupplierQuickMessageBuilder.buildPresetMessage(
            preset = preset,
            supplierDisplayName = resolvedSupplier,
            venueName = resolvedVenue,
            eventTime = activity.eventTime,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.ground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("supplier_quick_message_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.supplier_msg_sheet_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
                val subtitle = buildList {
                    if (resolvedSupplier.isNotEmpty()) add(resolvedSupplier)
                    if (resolvedVenue.isNotEmpty()) add(resolvedVenue)
                    activity.eventTime?.let { add(DominicanFormatter.formatTime12h(it)) }
                }.joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val phoneHint = if (!supplierRawPhone.isNullOrBlank()) {
                    stringResource(R.string.supplier_msg_phone_saved, supplierRawPhone.trim())
                } else {
                    stringResource(R.string.supplier_msg_no_phone)
                }
                Text(
                    text = phoneHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!supplierRawPhone.isNullOrBlank()) colors.successText else colors.muted,
                )
            }

            // 4 Preset cards (2x2): Ya llegué / Voy retrasado / Terminé / Otra situación
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PresetTile(
                        icon = Icons.Outlined.LocationOn,
                        label = stringResource(R.string.supplier_msg_preset_arrived),
                        selected = selectedPreset == SupplierMessagePreset.ARRIVED,
                        onClick = { selectPreset(SupplierMessagePreset.ARRIVED) },
                        testTag = "supplier_msg_preset_arrived",
                        modifier = Modifier.weight(1f),
                    )
                    PresetTile(
                        icon = Icons.Outlined.Schedule,
                        label = stringResource(R.string.supplier_msg_preset_delay),
                        selected = selectedPreset == SupplierMessagePreset.DELAY,
                        onClick = { selectPreset(SupplierMessagePreset.DELAY) },
                        testTag = "supplier_msg_preset_delay",
                        isUrgent = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PresetTile(
                        icon = Icons.Outlined.CheckCircle,
                        label = stringResource(R.string.supplier_msg_preset_finished),
                        selected = selectedPreset == SupplierMessagePreset.FINISHED,
                        onClick = { selectPreset(SupplierMessagePreset.FINISHED) },
                        testTag = "supplier_msg_preset_finished",
                        modifier = Modifier.weight(1f),
                    )
                    PresetTile(
                        icon = Icons.Outlined.EditNote,
                        label = stringResource(R.string.supplier_msg_preset_custom),
                        selected = selectedPreset == SupplierMessagePreset.CUSTOM,
                        onClick = { selectPreset(SupplierMessagePreset.CUSTOM) },
                        testTag = "supplier_msg_preset_custom",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Quick-insert situation chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.supplier_msg_snippets_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SupplierQuickMessageBuilder.quickSituationSnippets.forEachIndexed { idx, snippet ->
                        Surface(
                            onClick = {
                                messageText = SupplierQuickMessageBuilder.appendSnippet(messageText, snippet)
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier.testTag("supplier_msg_snippet_$idx"),
                        ) {
                            Text(
                                text = "+ $snippet",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }

            // Open, freely editable message field
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text(stringResource(R.string.supplier_msg_input_label)) },
                placeholder = { Text(stringResource(R.string.supplier_msg_input_hint)) },
                minLines = 4,
                maxLines = 7,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.ink,
                    unfocusedBorderColor = colors.line,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("supplier_msg_text_input"),
            )

            // Primary WhatsApp button
            Button(
                onClick = {
                    val trimmed = messageText.trim()
                    if (trimmed.isNotEmpty()) {
                        val launched = launchWhatsAppMessage(context, supplierRawPhone, trimmed)
                        if (launched) {
                            onDismiss()
                        } else {
                            onFeedback(openErrorMsg)
                        }
                    }
                },
                enabled = messageText.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.success,
                    contentColor = colors.surface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("supplier_msg_send_whatsapp_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.supplier_msg_send_whatsapp),
                    fontWeight = FontWeight.Bold,
                )
            }

            // Secondary actions: Share (SMS / other apps) & Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val trimmed = messageText.trim()
                        if (trimmed.isNotEmpty()) {
                            val shared = sharePlainText(context, trimmed)
                            if (shared) onDismiss() else onFeedback(openErrorMsg)
                        }
                    },
                    enabled = messageText.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("supplier_msg_share_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.supplier_msg_share_other),
                        color = colors.ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedButton(
                    onClick = {
                        val trimmed = messageText.trim()
                        if (trimmed.isNotEmpty()) {
                            copyToClipboard(context, trimmed)
                            onFeedback(copiedMsg)
                            onDismiss()
                        }
                    },
                    enabled = messageText.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("supplier_msg_copy_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.supplier_msg_copy),
                        color = colors.ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PresetTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    isUrgent: Boolean = false,
) {
    val colors = ApptividadesThemeDefaults.colors
    val containerColor = when {
        selected && isUrgent -> colors.danger
        selected -> colors.ink
        isUrgent -> colors.dangerBg
        else -> colors.surface
    }
    val borderColor = when {
        selected && isUrgent -> colors.danger
        selected -> colors.ink
        isUrgent -> colors.dangerBorder
        else -> colors.line
    }
    val iconTint = when {
        selected -> colors.surface
        isUrgent -> colors.danger
        else -> colors.accentHover
    }
    val textColor = when {
        selected -> colors.surface
        isUrgent -> colors.dangerText
        else -> colors.ink
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(54.dp)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                ),
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun launchWhatsAppMessage(
    context: Context,
    rawPhone: String?,
    message: String,
): Boolean {
    val waUrl = SupplierQuickMessageBuilder.buildWhatsAppUrl(rawPhone, message)
    if (waUrl != null) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(intent) }.isSuccess) {
            return true
        }
    }

    // No saved phone (or wa.me handler unavailable): target WhatsApp / Business directly, else share chooser
    val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
    for (pkg in whatsappPackages) {
        val directIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(pkg)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(directIntent) }.isSuccess) {
            return true
        }
    }

    return sharePlainText(context, message)
}

private fun sharePlainText(context: Context, message: String): Boolean {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(chooser) }.isSuccess
}

private fun copyToClipboard(context: Context, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Mensaje proveedor", message))
}
