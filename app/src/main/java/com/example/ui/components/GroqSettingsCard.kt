package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.GroqAiSettings
import com.example.domain.model.GroqModelOption
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily

private const val GROQ_KEYS_URL = "https://console.groq.com/keys"

@Composable
fun GroqSettingsCard(
    groqAiSettings: GroqAiSettings,
    onSaveApiKey: (rawKey: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    onClearApiKey: () -> Unit,
    onSelectModel: (GroqModelOption) -> Unit,
    onToggleAnonymize: (Boolean) -> Unit,
    onTestConnection: (overrideKey: String?, onResult: (Result<String>) -> Unit) -> Unit,
    onOpenAiAnalyst: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val haptic = LocalHapticFeedback.current

    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var isSavingKey by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (groqAiSettings.isConfigured) 1.5.dp else 1.dp,
            color = if (groqAiSettings.isConfigured) colors.accent.copy(alpha = 0.55f) else colors.line,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_groq_card"),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header row + status badge
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.accentSoft,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_groq_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        StatusPill(
                            label = stringResource(
                                if (groqAiSettings.isConfigured) {
                                    R.string.settings_groq_status_connected
                                } else {
                                    R.string.settings_groq_status_missing
                                }
                            ),
                            backgroundColor = if (groqAiSettings.isConfigured) colors.successBg else colors.neutralChipBg,
                            textColor = if (groqAiSettings.isConfigured) colors.successText else colors.muted,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_groq_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
            }

            // Saved key indicator
            if (groqAiSettings.hasUserApiKey && !groqAiSettings.maskedKeyPreview.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.successBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = colors.successText,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_groq_key_saved_hint,
                                groqAiSettings.maskedKeyPreview,
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.successText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                val uriHandler = LocalUriHandler.current
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.accentSoft,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_groq_byok_steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink,
                        )
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri(GROQ_KEYS_URL) } },
                            modifier = Modifier.testTag("groq_get_key_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_groq_get_key),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // API Key input field (masked with visibility toggle)
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { next ->
                    apiKeyInput = next.replace("\n", "").replace("\r", "")
                    if (statusFeedback != null) statusFeedback = null
                },
                label = { Text(stringResource(R.string.settings_groq_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_groq_key_hint)) },
                singleLine = true,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { keyVisible = !keyVisible },
                        modifier = Modifier.testTag("settings_groq_key_visibility_btn"),
                    ) {
                        Icon(
                            imageVector = if (keyVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(
                                if (keyVisible) R.string.bank_account_hide_cd else R.string.bank_account_reveal_cd
                            ),
                            tint = colors.muted,
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.ink,
                    unfocusedBorderColor = colors.line,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_groq_key_input"),
            )

            // Save & Test buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isSavingKey = true
                        statusFeedback = null
                        onSaveApiKey(apiKeyInput.trim()) { res ->
                            isSavingKey = false
                            res.onSuccess {
                                apiKeyInput = ""
                                keyVisible = false
                                statusFeedback = true to "API Key guardada y cifrada localmente."
                            }.onFailure { err ->
                                statusFeedback = false to (err.message ?: "No se pudo guardar la API Key.")
                            }
                        }
                    },
                    enabled = apiKeyInput.isNotBlank() && !isSavingKey && !isTestingConnection,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.surface,
                    ),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(48.dp)
                        .testTag("settings_groq_save_key_btn"),
                ) {
                    if (isSavingKey) {
                        CircularProgressIndicator(
                            color = colors.surface,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_groq_save_key),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isTestingConnection = true
                        statusFeedback = null
                        val override = apiKeyInput.trim().takeIf { it.isNotEmpty() }
                        onTestConnection(override) { res ->
                            isTestingConnection = false
                            res.onSuccess { msg ->
                                statusFeedback = true to msg
                            }.onFailure { err ->
                                statusFeedback = false to (err.message ?: "No se pudo verificar la conexión con Groq.")
                            }
                        }
                    },
                    enabled = (apiKeyInput.isNotBlank() || groqAiSettings.isConfigured) && !isTestingConnection && !isSavingKey,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surface,
                        contentColor = colors.ink,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("settings_groq_test_btn"),
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            color = colors.ink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.settings_groq_test_connection),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (groqAiSettings.hasUserApiKey) {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        statusFeedback = null
                        onClearApiKey()
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .height(48.dp)
                        .testTag("settings_groq_remove_key_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = colors.dangerText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.settings_groq_remove_key),
                        color = colors.dangerText,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // Inline test / save feedback banner
            statusFeedback?.let { (isOk, msg) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOk) colors.successBg else colors.dangerBg,
                    border = BorderStroke(
                        1.dp,
                        if (isOk) colors.successText.copy(alpha = 0.3f) else colors.dangerBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_groq_feedback_banner"),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isOk) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = if (isOk) colors.successText else colors.dangerText,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOk) colors.successText else colors.dangerText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            // Model selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_groq_model_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.muted,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroqModelOption.entries.forEach { option ->
                        val selected = groqAiSettings.selectedModel == option
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectModel(option)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) colors.ink else colors.neutralChipBg,
                            border = if (selected) null else BorderStroke(1.dp, colors.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_groq_model_${option.name.lowercase()}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = if (selected) colors.surface else colors.ink,
                                    )
                                    Text(
                                        text = option.badge,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) colors.onDarkMuted else colors.muted,
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.accentBright,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            // Client name anonymization toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_groq_anonymize_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.settings_groq_anonymize_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
                Switch(
                    checked = groqAiSettings.anonymizeClientNames,
                    onCheckedChange = { enabled ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleAnonymize(enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.surface,
                        checkedTrackColor = colors.ink,
                    ),
                    modifier = Modifier.testTag("settings_groq_anonymize_switch"),
                )
            }

            // Security / privacy note
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = colors.successText,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.settings_groq_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }

            // Launch AI Analyst CTA when configured
            if (groqAiSettings.isConfigured) {
                Button(
                    onClick = onOpenAiAnalyst,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.ink,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_groq_open_analyst_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_groq_open_analyst),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
