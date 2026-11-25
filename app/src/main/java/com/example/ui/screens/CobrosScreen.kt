package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Currency
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.ReceivableGroup
import com.example.domain.rules.ReceivableStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingStateBlock
import com.example.ui.components.OfflineSyncBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AppDataState
import java.math.BigDecimal

@Composable
fun CobrosScreen(
    state: AppDataState,
    onToggleSelectGroup: (String) -> Unit,
    onMarkSelectedPaid: () -> Unit,
    onPreviewStatementPdf: (supplierDisplayName: String, currency: Currency) -> Unit,
    onSendStatementPdf: (supplierDisplayName: String, currency: Currency) -> Unit,
    onDownloadStatementPdf: (supplierDisplayName: String, currency: Currency) -> Unit,
    onOpenBankAccountsSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val summary = state.receivablesSummary
    val currentMonthName = DominicanFormatter.formatMonthNameLower(state.today.month)
    val nextMonthName = DominicanFormatter.formatMonthNameLower(state.today.plusMonths(1).month)

    val selectedGroups = summary.groups.filter { it.groupId in state.selectedReceivableGroupIds }
    val selectedGigCount = selectedGroups.sumOf { it.count }
    val selectedTotalAmount = selectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.totalAmount) }
    val selectedCurrency = selectedGroups.firstOrNull()?.currency ?: summary.defaultCurrency

    // Show ONE "Enviar estado de cuenta (PDF)" + download button per (supplier, currency)
    // attached to the highest-priority (first) section card where that supplier appears.
    val primaryGroupIdBySupplierCurrency = remember(summary.groups) {
        val map = linkedMapOf<String, String>()
        for (group in summary.groups) {
            if (group.supplierKey == "__no_supplier__") continue
            val scKey = "${group.supplierKey}_${group.currency.code}"
            if (scKey !in map) {
                map[scKey] = group.groupId
            }
        }
        map.values.toSet()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = if (selectedGigCount > 0) 172.dp else 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Eyebrow + Hero total
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.cobros_eyebrow),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                    Text(
                        text = DominicanFormatter.formatMoney(summary.totalReceivable, summary.defaultCurrency),
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("cobros_hero_total"),
                    )
                }
            }

            if (!state.isOnline || state.isOfflineCacheActive || state.pendingSyncCount > 0) {
                item(key = "cobros_offline_sync_banner") {
                    OfflineSyncBanner(
                        isOnline = state.isOnline,
                        isOfflineCacheActive = state.isOfflineCacheActive,
                        lastSyncedAtMillis = state.lastSyncedAtMillis,
                        pendingSyncCount = state.pendingSyncCount,
                        isSyncing = state.isSyncingPending || state.isLoading,
                        onSyncNow = onRefresh,
                    )
                }
            }

            // Two summary tiles: Vencido + En plazo (mes vencido)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerBorder),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cobros_tile_overdue),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.dangerText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = DominicanFormatter.formatMoney(summary.overdueTotal, summary.defaultCurrency),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                ),
                                color = colors.dangerText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cobros_tile_in_term),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = DominicanFormatter.formatMoney(summary.inTermTotal, summary.defaultCurrency),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                ),
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Phase 3 #5: One-line notice with link to Settings when there are no bank accounts (PDF still allowed)
            if (state.bankAccounts.isEmpty() && !state.isLoading) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.accentSoft,
                        border = BorderStroke(1.dp, colors.accentBright),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenBankAccountsSettings)
                            .testTag("cobros_no_bank_accounts_banner"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalance,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.cobros_no_bank_accounts_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                text = stringResource(R.string.cobros_no_bank_accounts_cta),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline,
                                ),
                                color = colors.accentHover,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item { ErrorBanner(message = state.errorMessage, onRetry = onRefresh) }
            }

            if (state.isLoading && state.activities.isEmpty()) {
                item { LoadingStateBlock() }
            } else if (summary.groups.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.TaskAlt,
                        title = stringResource(R.string.cobros_empty_title),
                        body = stringResource(R.string.cobros_empty_body),
                    )
                }
            } else {
                // Section 1: Vencidos
                if (summary.overdueGroups.isNotEmpty()) {
                    item {
                        SectionEyebrow(
                            text = stringResource(R.string.cobros_section_overdue),
                            color = colors.dangerText,
                        )
                    }
                    items(summary.overdueGroups, key = { it.groupId }) { group ->
                        ReceivableGroupCard(
                            group = group,
                            selected = group.groupId in state.selectedReceivableGroupIds,
                            statusLabel = stringResource(R.string.status_vencido),
                            showPdfActions = group.groupId in primaryGroupIdBySupplierCurrency,
                            onToggleSelect = { onToggleSelectGroup(group.groupId) },
                            onPreviewPdf = { onPreviewStatementPdf(group.supplierDisplayName, group.currency) },
                            onSendPdf = { onSendStatementPdf(group.supplierDisplayName, group.currency) },
                            onDownloadPdf = { onDownloadStatementPdf(group.supplierDisplayName, group.currency) },
                        )
                    }
                }

                // Section 2: Se cobran en {mes actual}
                if (summary.dueThisMonthGroups.isNotEmpty()) {
                    item {
                        SectionEyebrow(
                            text = stringResource(R.string.cobros_section_this_month, currentMonthName),
                            color = colors.muted,
                        )
                    }
                    items(summary.dueThisMonthGroups, key = { it.groupId }) { group ->
                        ReceivableGroupCard(
                            group = group,
                            selected = group.groupId in state.selectedReceivableGroupIds,
                            statusLabel = DominicanFormatter.formatMonthNameCapitalized(state.today.month),
                            showPdfActions = group.groupId in primaryGroupIdBySupplierCurrency,
                            onToggleSelect = { onToggleSelectGroup(group.groupId) },
                            onPreviewPdf = { onPreviewStatementPdf(group.supplierDisplayName, group.currency) },
                            onSendPdf = { onSendStatementPdf(group.supplierDisplayName, group.currency) },
                            onDownloadPdf = { onDownloadStatementPdf(group.supplierDisplayName, group.currency) },
                        )
                    }
                }

                // Section 3: Se cobran en {mes siguiente}
                if (summary.dueNextMonthGroups.isNotEmpty()) {
                    item {
                        SectionEyebrow(
                            text = stringResource(R.string.cobros_section_next_month, nextMonthName),
                            color = colors.muted,
                        )
                    }
                    items(summary.dueNextMonthGroups, key = { it.groupId }) { group ->
                        ReceivableGroupCard(
                            group = group,
                            selected = group.groupId in state.selectedReceivableGroupIds,
                            statusLabel = DominicanFormatter.formatMonthNameCapitalized(state.today.plusMonths(1).month),
                            showPdfActions = group.groupId in primaryGroupIdBySupplierCurrency,
                            onToggleSelect = { onToggleSelectGroup(group.groupId) },
                            onPreviewPdf = { onPreviewStatementPdf(group.supplierDisplayName, group.currency) },
                            onSendPdf = { onSendStatementPdf(group.supplierDisplayName, group.currency) },
                            onDownloadPdf = { onDownloadStatementPdf(group.supplierDisplayName, group.currency) },
                        )
                    }
                }
            }
        }

        // Sticky green bulk mark-paid button above bottom navigation bar
        if (selectedGigCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 104.dp),
            ) {
                Button(
                    onClick = onMarkSelectedPaid,
                    enabled = !state.isMutating,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.success,
                        contentColor = colors.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("cobros_mark_paid_button"),
                ) {
                    if (state.isMutating) {
                        CircularProgressIndicator(
                            color = colors.surface,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.mark_paid_action,
                                selectedGigCount,
                                selectedGigCount,
                                DominicanFormatter.formatMoney(selectedTotalAmount, selectedCurrency),
                            ),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionEyebrow(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ReceivableGroupCard(
    group: ReceivableGroup,
    selected: Boolean,
    statusLabel: String,
    showPdfActions: Boolean,
    onToggleSelect: () -> Unit,
    onPreviewPdf: () -> Unit,
    onSendPdf: () -> Unit,
    onDownloadPdf: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val isOverdue = group.status == ReceivableStatus.VENCIDO
    val gigsText = pluralStringResource(R.plurals.gig_count, group.count, group.count)
    val daysText = pluralStringResource(
        R.plurals.day_count,
        group.daysOutstanding.toInt(),
        group.daysOutstanding.toInt(),
    )

    val subtitle = if (isOverdue) {
        val oldestFormatted = DominicanFormatter.formatShortDate(group.oldestDate)
        if (group.count > 1) {
            "$gigsText · ${stringResource(R.string.cobros_oldest_prefix, oldestFormatted, daysText)}"
        } else {
            "$gigsText · $oldestFormatted ($daysText)"
        }
    } else {
        val datesStr = DominicanFormatter.formatDayListInMonth(group.sortedDates)
        buildList {
            add(gigsText)
            if (!group.primaryVenue.isNullOrBlank()) add(group.primaryVenue)
            if (datesStr.isNotEmpty()) add(datesStr)
        }.joinToString(" · ")
    }

    val selectDescription = stringResource(R.string.cobros_select_group_cd, group.supplierDisplayName)

    Surface(
        onClick = onToggleSelect,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.ink else colors.line,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("receivable_card_${group.groupId}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.ink,
                        uncheckedColor = colors.muted,
                        checkmarkColor = colors.surface,
                    ),
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = selectDescription },
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = group.supplierDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        StatusPill(
                            label = statusLabel,
                            backgroundColor = if (isOverdue) colors.dangerBg else colors.neutralChipBg,
                            textColor = if (isOverdue) colors.dangerText else colors.neutralChipText,
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                    if (group.totalPartialDeposits > BigDecimal.ZERO) {
                        StatusPill(
                            label = stringResource(
                                R.string.cobros_partial_deposits_badge,
                                DominicanFormatter.formatMoney(group.totalPartialDeposits, group.currency),
                            ),
                            backgroundColor = colors.successBg,
                            textColor = colors.successText,
                            modifier = Modifier.testTag("receivable_partial_badge_${group.groupId}"),
                        )
                    }
                }

                Text(
                    text = DominicanFormatter.formatMoney(group.totalAmount, group.currency),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                    ),
                    color = colors.ink,
                    maxLines = 1,
                )
            }

            if (showPdfActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onPreviewPdf,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isOverdue) colors.dangerBorder else colors.line,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.surface,
                            contentColor = if (isOverdue) colors.dangerText else colors.ink,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("preview_pdf_statement_${group.groupId}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.cobros_preview_pdf_cta),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        onClick = onSendPdf,
                        shape = RoundedCornerShape(12.dp),
                        color = if (isOverdue) colors.dangerBg else colors.accentSoft,
                        border = BorderStroke(
                            1.dp,
                            if (isOverdue) colors.dangerBorder else colors.accent.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_pdf_statement_${group.groupId}"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = stringResource(
                                    R.string.cobros_send_pdf_cd,
                                    group.supplierDisplayName,
                                ),
                                tint = if (isOverdue) colors.dangerText else colors.accentHover,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }

                    Surface(
                        onClick = onDownloadPdf,
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("download_pdf_statement_${group.groupId}"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = stringResource(
                                    R.string.cobros_download_pdf_cd,
                                    group.supplierDisplayName,
                                ),
                                tint = colors.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
