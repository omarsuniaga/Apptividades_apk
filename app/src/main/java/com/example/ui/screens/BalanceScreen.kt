package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.rules.Balance12Months
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.MonthlyBarPoint
import com.example.domain.rules.NetProfitSummary12Months
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingStateBlock
import com.example.ui.components.StatusPill
import com.example.ui.components.expenseCategoryLabel
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AppDataState
import java.math.BigDecimal
import java.time.YearMonth

@Composable
fun BalanceScreen(
    state: AppDataState,
    onSelectMonth: (YearMonth?) -> Unit,
    onUpdateMonthlyGoal: (BigDecimal) -> Unit,
    onExportCsv: () -> Unit,
    onOpenReports: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val balance = state.balance12Months
    var showGoalDialog by remember { mutableStateOf(false) }

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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header + CSV / Reports buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DominicanFormatter.formatMonthRange(balance.startMonth, balance.endMonth),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                        Text(
                            text = stringResource(R.string.balance_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenReports,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.line),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.surface,
                                contentColor = colors.ink,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("balance_reports_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.nav_reports),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                        OutlinedButton(
                            onClick = onExportCsv,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.line),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.surface,
                                contentColor = colors.ink,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("balance_export_csv_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.balance_export_csv),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
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
            } else if (balance.totalGigs == 0) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.BarChart,
                        title = stringResource(R.string.balance_empty_title),
                        body = stringResource(R.string.balance_empty_body),
                    )
                }
            } else {
                // 3 KPI tiles
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KpiTile(
                            label = stringResource(R.string.balance_kpi_avg_month),
                            value = DominicanFormatter.formatMoney(balance.averagePerMonth, balance.currency),
                            modifier = Modifier.weight(1.15f),
                        )
                        KpiTile(
                            label = stringResource(R.string.balance_kpi_gigs_month),
                            value = DominicanFormatter.formatOneDecimal(balance.gigsPerMonth),
                            modifier = Modifier.weight(0.85f),
                        )
                        KpiTile(
                            label = stringResource(R.string.balance_kpi_per_gig),
                            value = DominicanFormatter.formatMoney(balance.averagePerGig, balance.currency),
                            modifier = Modifier.weight(1.05f),
                        )
                    }
                }

                // 12-month real net profit & expense breakdown card
                item {
                    NetProfitRealCard(summary = state.netProfit12Months)
                }

                // 12-month bar chart card
                item {
                    MonthlyBarChartCard(
                        balance = balance,
                        selectedMonth = state.selectedBalanceMonth,
                        onSelectMonth = onSelectMonth,
                        onEditGoalClick = { showGoalDialog = true },
                    )
                }

                // Supplier dependency card
                if (balance.supplierShares.isNotEmpty()) {
                    item {
                        SupplierDependencyCard(balance = balance)
                    }
                }

                // Top 4 venues card
                if (balance.venueRankings.isNotEmpty()) {
                    item {
                        TopVenuesCard(balance = balance)
                    }
                }
            }
        }
    }

    if (showGoalDialog) {
        EditGoalDialog(
            currentGoal = state.settings.monthlyGoal,
            currencySymbol = state.settings.defaultCurrency.symbol,
            onDismiss = { showGoalDialog = false },
            onSave = { newGoal ->
                showGoalDialog = false
                onUpdateMonthlyGoal(newGoal)
            },
        )
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 15.sp,
                ),
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NetProfitRealCard(
    summary: NetProfitSummary12Months,
) {
    val colors = ApptividadesThemeDefaults.colors
    val palette = colors.sharePalette

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("balance_net_profit_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.balance_net_profit_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(
                    label = stringResource(R.string.balance_net_margin_pill, summary.profitMarginPercent),
                    backgroundColor = if (summary.profitMarginPercent >= 70) colors.successBg else colors.accentSoft,
                    textColor = if (summary.profitMarginPercent >= 70) colors.successText else colors.accentHover,
                )
            }

            Text(
                text = DominicanFormatter.formatMoney(summary.netTotal, summary.currency),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = SpaceGroteskFontFamily,
                    fontFeatureSettings = "tnum",
                ),
                color = if (summary.netTotal < BigDecimal.ZERO) colors.dangerText else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("balance_net_profit_hero"),
            )

            // 2-column sub-strip: Facturado bruto & Gastos registrados + Neto/mes & Neto/toque
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.ground,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.balance_net_gross_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted,
                            )
                            Text(
                                text = DominicanFormatter.formatMoney(summary.grossTotal, summary.currency),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.balance_net_expenses_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted,
                            )
                            Text(
                                text = "− ${DominicanFormatter.formatMoney(summary.totalExpenses, summary.currency)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = if (summary.hasRecordedExpenses) colors.dangerText else colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.balance_net_avg_month),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted,
                            )
                            Text(
                                text = DominicanFormatter.formatMoney(summary.averageNetPerMonth, summary.currency),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.balance_net_avg_gig),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted,
                            )
                            Text(
                                text = DominicanFormatter.formatMoney(summary.averageNetPerGig, summary.currency),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (summary.hasRecordedExpenses && summary.expensesByCategory.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.balance_net_expenses_dist_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                    summary.expensesByCategory.forEachIndexed { idx, item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = expenseCategoryLabel(item.category),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${item.percent}% · ${DominicanFormatter.formatCompactMoney(item.total, summary.currency)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = colors.muted,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.neutralChipBg),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((item.percent / 100f).coerceIn(0.05f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(palette[idx % palette.size]),
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.balance_net_expenses_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun MonthlyBarChartCard(
    balance: Balance12Months,
    selectedMonth: YearMonth?,
    onSelectMonth: (YearMonth?) -> Unit,
    onEditGoalClick: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val selectedBar: MonthlyBarPoint? = balance.months.firstOrNull { it.month == selectedMonth }

    val maxBarValue = maxOf(
        balance.monthlyGoal.toDouble(),
        balance.months.maxOfOrNull { it.total.toDouble() } ?: 0.0,
        1.0,
    ) * 1.08

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("balance_chart_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.balance_chart_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEditGoalClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("balance_edit_goal_button"),
                ) {
                    Text(
                        text = stringResource(
                            R.string.balance_goal_line,
                            DominicanFormatter.formatMoney(balance.monthlyGoal, balance.currency),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentHover,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.balance_edit_goal),
                        tint = colors.accentHover,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            if (selectedBar != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.accentSoft,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DominicanFormatter.formatMonthYear(selectedBar.month),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentHover,
                        )
                        Text(
                            text = "${DominicanFormatter.formatMoney(selectedBar.total, balance.currency)} · ${
                                pluralStringResource(R.plurals.gig_count, selectedBar.count, selectedBar.count)
                            }",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = SpaceGroteskFontFamily,
                            ),
                            color = colors.ink,
                        )
                    }
                }
            }

            // 12-bar chart with dashed amber goal line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            ) {
                // Dashed goal line drawn cleanly via Canvas
                val goalRatio = (balance.monthlyGoal.toDouble() / maxBarValue).toFloat().coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 22.dp),
                ) {
                    val y = size.height * (1f - goalRatio)
                    drawLine(
                        color = colors.accent,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    balance.months.forEach { bar ->
                        val fraction = (bar.total.toDouble() / maxBarValue).toFloat().coerceIn(0.03f, 1f)
                        val isSelected = bar.month == selectedMonth
                        val barColor = when {
                            bar.reachedGoal -> colors.accent
                            else -> colors.ink3
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectMonth(if (isSelected) null else bar.month)
                                }
                                .testTag("balance_bar_${bar.month}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.84f)
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            if (isSelected) colors.accentBright else barColor
                                        ),
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = DominicanFormatter.formatMonthInitial(bar.month.month),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isSelected) colors.ink else colors.muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Summary insight line
            val summaryText = if (balance.lowestMonth != null) {
                val lowName = "${DominicanFormatter.formatMonthNameLower(balance.lowestMonth.month.month)} ${balance.lowestMonth.month.year}"
                stringResource(R.string.balance_chart_summary, balance.monthsReachedGoal, lowName)
            } else {
                stringResource(R.string.balance_chart_summary_simple, balance.monthsReachedGoal)
            }
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun SupplierDependencyCard(
    balance: Balance12Months,
) {
    val colors = ApptividadesThemeDefaults.colors
    val palette = colors.sharePalette

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("balance_dependency_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.balance_dependency_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )

            // Segmented horizontal bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                balance.supplierShares.forEachIndexed { index, share ->
                    val weight = share.percent.coerceAtLeast(1).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(palette[index % palette.size]),
                    )
                }
            }

            // Legend rows
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                balance.supplierShares.forEachIndexed { index, share ->
                    val label = if (share.isOtherBucket) {
                        pluralStringResource(
                            R.plurals.more_suppliers,
                            share.otherSupplierCount,
                            share.otherSupplierCount,
                        )
                    } else {
                        share.displayName
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette[index % palette.size]),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pluralStringResource(R.plurals.gig_count, share.gigCount, share.gigCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                        Text(
                            text = "${share.percent}% · ${DominicanFormatter.formatCompactMoney(share.totalAmount)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = colors.ink,
                        )
                    }
                }
            }

            // Amber insight callout
            if (balance.topSupplierName != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.accentSoft,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            R.string.balance_dependency_insight,
                            balance.top3Count,
                            balance.top3SharePercent,
                            balance.topSupplierName,
                            DominicanFormatter.formatMoney(balance.topSupplierMonthlyImpact, balance.currency),
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.ink,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopVenuesCard(
    balance: Balance12Months,
) {
    val colors = ApptividadesThemeDefaults.colors

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("balance_venues_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.balance_venues_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )

            balance.venueRankings.forEach { venue ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = venue.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${
                                pluralStringResource(R.plurals.gig_count, venue.gigCount, venue.gigCount)
                            } · ${DominicanFormatter.formatCompactMoney(venue.totalAmount, balance.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.neutralChipBg),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(venue.fractionOfTop.coerceIn(0.05f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.ink),
                        )
                    }
                }
            }

            if (balance.totalMergedVenueVariants > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.merged_spellings_note,
                        balance.totalMergedVenueVariants,
                        balance.totalMergedVenueVariants,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun EditGoalDialog(
    currentGoal: BigDecimal,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (BigDecimal) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    var goalText by remember { mutableStateOf(currentGoal.stripTrailingZeros().toPlainString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.balance_goal_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
            )
        },
        text = {
            OutlinedTextField(
                value = goalText,
                onValueChange = { next ->
                    if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        goalText = next
                    }
                },
                label = { Text(stringResource(R.string.balance_goal_dialog_hint, currencySymbol)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = goalText.toBigDecimalOrNull()
                    if (parsed != null && parsed > BigDecimal.ZERO) {
                        onSave(parsed)
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
