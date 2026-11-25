package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Activity
import com.example.domain.model.Currency
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.DominicanFormatter
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AppDataState
import java.math.BigDecimal
import java.time.YearMonth

@Composable
fun ReportsScreen(
    state: AppDataState,
    onBack: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onExportMonthCsv: (List<Activity>, YearMonth) -> Unit,
    onExport12MonthsCsv: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenQuoteCreator: () -> Unit = {},
    onOpenAiAnalyst: () -> Unit = {},
) {
    val colors = ApptividadesThemeDefaults.colors
    val month = state.reportsMonth
    val monthActivities = remember(state.activities, month) {
        state.activities.filter { YearMonth.from(it.eventDate) == month }
    }

    val totalsByCurrency = remember(monthActivities) {
        Currency.entries.mapNotNull { curr ->
            val items = monthActivities.filter { it.currency == curr }
            if (items.isEmpty()) null
            else {
                val total = items.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.amount) }
                val paid = items.filter { it.paymentStatus == PaymentStatus.PAGADO }
                    .fold(BigDecimal.ZERO) { acc, a -> acc.add(a.amount) }
                val pending = total.subtract(paid)
                Triple(curr, items.size, Triple(total, paid, pending))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                testTag = "reports_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
            Text(
                text = stringResource(R.string.reports_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Month selector card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UtilityIconTile(
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.agenda_prev_month),
                            onClick = onPrevMonth,
                            testTag = "reports_prev_month",
                            borderColor = colors.line,
                            backgroundColor = colors.ground,
                            tint = colors.ink,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = DominicanFormatter.formatMonthYear(month),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.activity_count,
                                    monthActivities.size,
                                    monthActivities.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        UtilityIconTile(
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.agenda_next_month),
                            onClick = onNextMonth,
                            testTag = "reports_next_month",
                            borderColor = colors.line,
                            backgroundColor = colors.ground,
                            tint = colors.ink,
                        )
                    }
                }

                // Totals summary card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reports_month_summary),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        if (totalsByCurrency.isEmpty()) {
                            Text(
                                text = stringResource(R.string.agenda_empty_day),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        } else {
                            totalsByCurrency.forEachIndexed { idx, (curr, count, triple) ->
                                val (total, paid, pending) = triple
                                if (idx > 0) {
                                    HorizontalDivider(color = colors.line)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "${curr.symbol} · ${
                                                pluralStringResource(R.plurals.gig_count, count, count)
                                            }",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.muted,
                                        )
                                        Text(
                                            text = DominicanFormatter.formatMoney(total, curr),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = SpaceGroteskFontFamily,
                                            ),
                                            color = colors.ink,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "${stringResource(R.string.inicio_metric_collected)}: ${DominicanFormatter.formatMoney(paid, curr)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.successText,
                                        )
                                        Text(
                                            text = "${stringResource(R.string.status_por_cobrar)}: ${DominicanFormatter.formatMoney(pending, curr)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.accentHover,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Professional PDF Quote creator card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reports_quote_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RequestQuote,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.reports_create_quote_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink,
                                )
                                Text(
                                    text = stringResource(R.string.reports_create_quote_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                        }
                        Button(
                            onClick = onOpenQuoteCreator,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.ink,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reports_create_quote_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RequestQuote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.reports_create_quote_cta),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Groq AI Analyst card (Historical & Route Intelligence)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reports_ai_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.reports_ai_card_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink,
                                )
                                Text(
                                    text = stringResource(R.string.reports_ai_card_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenAiAnalyst,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.ink),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.surface,
                                contentColor = colors.ink,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reports_open_ai_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.reports_ai_card_cta),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // CSV exports
                Button(
                    onClick = { onExportMonthCsv(monthActivities, month) },
                    enabled = monthActivities.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("reports_export_month_csv"),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.reports_export_csv_full),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedButton(
                    onClick = onExport12MonthsCsv,
                    enabled = state.activities.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.line),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surface,
                        contentColor = colors.ink,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("reports_export_12m_csv"),
                ) {
                    Text(
                        text = stringResource(R.string.reports_export_all_csv),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.accentSoft,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.reports_pdf_phase_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink,
                        modifier = Modifier.padding(14.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
