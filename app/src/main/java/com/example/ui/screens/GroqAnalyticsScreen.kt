package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.AiAnalysisPreset
import com.example.domain.model.AiChatMessage
import com.example.ui.components.StatusPill
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AppDataState

private data class PresetCardUi(
    val preset: AiAnalysisPreset,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
)

private val PRESET_CARDS = listOf(
    PresetCardUi(
        preset = AiAnalysisPreset.FINANCIAL_DIAGNOSIS,
        titleRes = R.string.ai_preset_finance_title,
        subtitleRes = R.string.ai_preset_finance_sub,
        icon = Icons.Outlined.Insights,
    ),
    PresetCardUi(
        preset = AiAnalysisPreset.ROUTES_AND_LOGISTICS,
        titleRes = R.string.ai_preset_routes_title,
        subtitleRes = R.string.ai_preset_routes_sub,
        icon = Icons.Outlined.Route,
    ),
    PresetCardUi(
        preset = AiAnalysisPreset.RECEIVABLES_STRATEGY,
        titleRes = R.string.ai_preset_receivables_title,
        subtitleRes = R.string.ai_preset_receivables_sub,
        icon = Icons.Outlined.AccountBalanceWallet,
    ),
    PresetCardUi(
        preset = AiAnalysisPreset.MONTHLY_GOAL_PLAN,
        titleRes = R.string.ai_preset_goal_title,
        subtitleRes = R.string.ai_preset_goal_sub,
        icon = Icons.Outlined.Flag,
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroqAnalyticsScreen(
    state: AppDataState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRunPreset: (AiAnalysisPreset, String) -> Unit,
    onSendQuestion: (String) -> Unit,
    onClearConversation: () -> Unit,
    onCopiedFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var promptText by rememberSaveable { mutableStateOf("") }

    val activeMonthsCount = remember(state.balance12Months) {
        state.balance12Months.months.count { it.count > 0 }
    }
    val measuredRoutesCount = remember(state.activityRoutes) {
        state.activityRoutes.size
    }

    LaunchedEffect(state.aiMessages.size, state.isAiAnalyzing) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0 && (state.aiMessages.isNotEmpty() || state.isAiAnalyzing)) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val submitCurrentPrompt = {
        val trimmed = promptText.trim()
        if (trimmed.isNotEmpty() && !state.isAiAnalyzing && state.aiSettings.isConfigured) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            promptText = ""
            onSendQuestion(trimmed)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Editorial top bar
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
                testTag = "ai_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_screen_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = colors.accentHover,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = state.aiSettings.selectedModel.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.aiMessages.isNotEmpty() && !state.isAiAnalyzing) {
                UtilityIconTile(
                    icon = Icons.Outlined.DeleteSweep,
                    contentDescription = stringResource(R.string.ai_clear_chat_cd),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearConversation()
                    },
                    testTag = "ai_clear_chat_button",
                    borderColor = colors.line,
                    backgroundColor = colors.surface,
                    tint = colors.ink,
                    modifier = Modifier.size(48.dp),
                )
            }
            UtilityIconTile(
                icon = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.nav_settings),
                onClick = onOpenSettings,
                testTag = "ai_settings_shortcut_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
                modifier = Modifier.size(48.dp),
            )
        }

        // Main scrollable workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Dataset readiness + privacy strip
                item(key = "ai_dataset_strip") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_dataset_strip_card"),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.ai_dataset_strip,
                                    state.activities.size,
                                    measuredRoutesCount,
                                    activeMonthsCount,
                                ),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.ink,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                StatusPill(
                                    label = stringResource(
                                        if (state.aiSettings.anonymizeClientNames) {
                                            R.string.ai_privacy_badge_anon
                                        } else {
                                            R.string.ai_privacy_badge_safe
                                        }
                                    ),
                                    backgroundColor = colors.successBg,
                                    textColor = colors.successText,
                                )
                                StatusPill(
                                    label = state.aiSettings.selectedModel.badge,
                                    backgroundColor = colors.accentSoft,
                                    textColor = colors.accentHover,
                                )
                            }
                        }
                    }
                }

                if (!state.aiSettings.isConfigured) {
                    item(key = "ai_unconfigured_onboarding") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = colors.surface,
                            border = BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_unconfigured_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.accentSoft,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = colors.accentHover,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = stringResource(R.string.ai_unconfigured_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = colors.ink,
                                    )
                                    Text(
                                        text = stringResource(R.string.ai_unconfigured_body),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.muted,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
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
                                Button(
                                    onClick = onOpenSettings,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.ink,
                                        contentColor = colors.surface,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("ai_open_settings_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.ai_unconfigured_cta),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (state.aiMessages.isEmpty()) {
                        item(key = "ai_presets_header") {
                            Text(
                                text = stringResource(R.string.ai_presets_heading),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        items(PRESET_CARDS, key = { it.preset.id }) { card ->
                            val titleText = stringResource(card.titleRes)
                            PresetActionCard(
                                title = titleText,
                                subtitle = stringResource(card.subtitleRes),
                                icon = card.icon,
                                enabled = !state.isAiAnalyzing,
                                testTag = "ai_preset_${card.preset.id}",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onRunPreset(card.preset, titleText)
                                },
                            )
                        }
                    } else {
                        // Compact preset chips row so user can trigger another analysis mid-session
                        item(key = "ai_compact_presets") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(PRESET_CARDS, key = { "chip_${it.preset.id}" }) { card ->
                                    val titleText = stringResource(card.titleRes)
                                    Surface(
                                        onClick = {
                                            if (!state.isAiAnalyzing) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onRunPreset(card.preset, titleText)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.surface,
                                        border = BorderStroke(1.dp, colors.line),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("ai_preset_${card.preset.id}"),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = card.icon,
                                                contentDescription = null,
                                                tint = colors.accentHover,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = titleText,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                                color = colors.ink,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(state.aiMessages, key = { it.id }) { message ->
                            AiMessageBubble(
                                message = message,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("Análisis Groq", message.content)
                                    )
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCopiedFeedback()
                                },
                            )
                        }
                    }

                    if (state.isAiAnalyzing) {
                        item(key = "ai_thinking_indicator") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_thinking_card"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.accentHover,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.ai_thinking),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = colors.ink,
                                    )
                                }
                            }
                        }
                    }

                    if (!state.aiErrorMessage.isNullOrBlank()) {
                        item(key = "ai_error_banner") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.dangerBg,
                                border = BorderStroke(1.dp, colors.dangerBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_error_banner"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = colors.dangerText,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = state.aiErrorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.dangerText,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "ai_bottom_spacer") {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // Bottom prompt composer (shown when Groq is configured)
        if (state.aiSettings.isConfigured) {
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { if (it.length <= 600) promptText = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.ai_input_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { submitCurrentPrompt() },
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                                focusedContainerColor = colors.ground,
                                unfocusedContainerColor = colors.ground,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_prompt_input"),
                        )

                        val canSend = promptText.isNotBlank() && !state.isAiAnalyzing
                        Surface(
                            onClick = submitCurrentPrompt,
                            enabled = canSend,
                            shape = RoundedCornerShape(14.dp),
                            color = if (canSend) colors.ink else colors.neutralChipBg,
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("ai_send_button"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = stringResource(R.string.ai_send_cd),
                                    tint = if (canSend) colors.surface else colors.muted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.accentSoft,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.accentHover,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AiMessageBubble(
    message: AiChatMessage,
    onCopy: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp,
                ),
                color = colors.ink,
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .testTag("ai_msg_user_${message.id}"),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.surface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    } else {
        val formattedText = remember(message.content) {
            formatLightMarkdown(message.content)
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp,
            ),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_msg_assistant_${message.id}"),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = formattedText,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = colors.ink,
                )
                HorizontalDivider(thickness = 1.dp, color = colors.line)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val metaParts = buildList {
                        message.latencyMs?.let { add("${it} ms") }
                        message.totalTokens?.let { add("$it tokens") }
                    }
                    Text(
                        text = metaParts.joinToString(" · ").ifEmpty { "Groq" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = SpaceGroteskFontFamily,
                        ),
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("ai_copy_${message.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ai_copy_response),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentHover,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders lightweight markdown (**bold**, ### headings, bullet points) into an [AnnotatedString]
 * without bringing in a heavy WebView or HTML parser.
 */
private fun formatLightMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.trim().lines()
    lines.forEachIndexed { lineIdx, rawLine ->
        if (lineIdx > 0) append('\n')
        val trimmed = rawLine.trimStart()
        val isHeading = trimmed.startsWith("#")
        val cleanLine = when {
            trimmed.startsWith("### ") -> trimmed.removePrefix("### ")
            trimmed.startsWith("## ") -> trimmed.removePrefix("## ")
            trimmed.startsWith("# ") -> trimmed.removePrefix("# ")
            trimmed.startsWith("- ") -> "• " + trimmed.removePrefix("- ")
            trimmed.startsWith("* ") -> "• " + trimmed.removePrefix("* ")
            else -> rawLine
        }

        if (isHeading) {
            val start = length
            append(cleanLine.replace("**", ""))
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                start = start,
                end = length,
            )
        } else {
            var cursor = 0
            while (cursor < cleanLine.length) {
                val boldOpen = cleanLine.indexOf("**", startIndex = cursor)
                if (boldOpen < 0) {
                    append(cleanLine.substring(cursor))
                    break
                }
                val boldClose = cleanLine.indexOf("**", startIndex = boldOpen + 2)
                if (boldClose < 0) {
                    append(cleanLine.substring(cursor))
                    break
                }
                if (boldOpen > cursor) {
                    append(cleanLine.substring(cursor, boldOpen))
                }
                val boldStart = length
                append(cleanLine.substring(boldOpen + 2, boldClose))
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    start = boldStart,
                    end = length,
                )
                cursor = boldClose + 2
            }
        }
    }
}
