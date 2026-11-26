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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.HotelInsight
import com.example.domain.rules.HotelInsightType
import com.example.domain.rules.HotelSeasonReport
import com.example.domain.rules.HotelSeasonRow
import com.example.domain.rules.MonthSeasonPoint
import com.example.domain.rules.SeasonLevel
import com.example.domain.rules.UpcomingMonthOpportunity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import java.math.BigDecimal
import java.time.Month
import java.time.YearMonth

@Composable
fun HotelSeasonScreen(
    report: HotelSeasonReport,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onOpenCrm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val topHotel = remember(report) { report.hotels.firstOrNull { !it.isUnassigned && it.totalIncome.signum() > 0 } }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UtilityIconTile(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                onClick = onBack,
                testTag = "hotelseason_back",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
            Text(
                text = stringResource(R.string.hotelseason_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            UtilityIconTile(
                icon = Icons.Outlined.Download,
                contentDescription = stringResource(R.string.hotelseason_export),
                onClick = onExportCsv,
                testTag = "hotelseason_export",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("hotelseason_list"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(report, topHotel) }

            item { InsightsCard(report.insights, report, onOpenCrm) }

            item { SectionTitle(R.string.hotelseason_seasons_title) }
            item { SeasonChart(report) }

            item { SectionTitle(R.string.hotelseason_upcoming_title) }
            if (report.upcoming.isEmpty()) {
                item { MutedText(stringResource(R.string.hotelseason_upcoming_empty)) }
            } else {
                items(report.upcoming, key = { it.month.toString() }) { UpcomingCard(it, report) }
            }

            item { SectionTitle(R.string.hotelseason_hotels_title) }
            if (report.hotels.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.Hotel,
                        title = stringResource(R.string.hotelseason_hotels_title),
                        body = stringResource(R.string.hotelseason_hotels_empty),
                    )
                }
            } else {
                items(report.hotels, key = { it.key }) { HotelCard(it, report) }
            }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = ApptividadesThemeDefaults.colors.ink,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = ApptividadesThemeDefaults.colors.muted)
}

private fun money(amount: BigDecimal, report: HotelSeasonReport): String =
    DominicanFormatter.formatMoney(amount, report.currency)

private fun shortMonth(month: Month): String =
    DominicanFormatter.formatShortMonth(month).replaceFirstChar { it.uppercase() }

private fun monthYear(ym: YearMonth): String = "${shortMonth(ym.month)} ${ym.year}"

@Composable
private fun SummaryCard(report: HotelSeasonReport, topHotel: HotelSeasonRow?) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.heroCardBg,
        modifier = Modifier.fillMaxWidth().testTag("hotelseason_summary"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.hotelseason_total_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onDarkMuted,
            )
            Text(
                text = money(report.totalIncome, report),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.heroCardText,
                modifier = Modifier.testTag("hotelseason_total"),
            )
            val change = report.changePercent
            Text(
                text = when {
                    change == null -> stringResource(R.string.hotelseason_vs_previous_none)
                    change >= 0 -> stringResource(R.string.hotelseason_vs_previous_up, change)
                    else -> stringResource(R.string.hotelseason_vs_previous_down, -change)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (change != null && change < 0) colors.dangerBg else colors.heroCardText,
                modifier = Modifier.testTag("hotelseason_change"),
            )
            topHotel?.let {
                Text(
                    stringResource(R.string.hotelseason_top_hotel, it.displayName, it.sharePercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onDarkMuted,
                )
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: List<HotelInsight>, report: HotelSeasonReport, onOpenCrm: () -> Unit) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth().testTag("hotelseason_insights"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.hotelseason_insights_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
            if (insights.isEmpty()) {
                MutedText(stringResource(R.string.hotelseason_insights_empty))
            } else {
                insights.forEachIndexed { index, insight ->
                    Text(
                        text = "• ${insightText(insight, report)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                        modifier = Modifier.testTag("hotelseason_insight_$index"),
                    )
                }
                Button(
                    onClick = onOpenCrm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.surface),
                    modifier = Modifier.testTag("hotelseason_open_crm"),
                ) { Text(stringResource(R.string.hotelseason_open_crm), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun insightText(insight: HotelInsight, report: HotelSeasonReport): String {
    val subject = insight.subject.orEmpty()
    val percent = insight.percent ?: 0
    val amountText = insight.amount?.let { money(it, report) }.orEmpty()
    return when (insight.type) {
        HotelInsightType.QUIET_MONTH ->
            stringResource(R.string.hotelseason_insight_quiet, insight.month?.let(::monthYear).orEmpty(), amountText)
        HotelInsightType.DROPPED ->
            stringResource(R.string.hotelseason_insight_dropped, subject, -percent, amountText)
        HotelInsightType.HIGH_SEASON_ONLY ->
            stringResource(R.string.hotelseason_insight_high_only, subject, percent)
        HotelInsightType.TOP_CONCENTRATION ->
            stringResource(R.string.hotelseason_insight_concentration, subject, percent)
        HotelInsightType.GREW ->
            stringResource(R.string.hotelseason_insight_grew, subject, percent, amountText)
        HotelInsightType.NEW_HOTEL ->
            stringResource(R.string.hotelseason_insight_new, subject, amountText)
    }
}

@Composable
private fun levelLabel(level: SeasonLevel): String = when (level) {
    SeasonLevel.ALTA -> stringResource(R.string.hotelseason_level_high)
    SeasonLevel.MEDIA -> stringResource(R.string.hotelseason_level_medium)
    SeasonLevel.BAJA -> stringResource(R.string.hotelseason_level_low)
}

@Composable
private fun levelColor(level: SeasonLevel): Color {
    val c = ApptividadesThemeDefaults.colors
    return when (level) {
        SeasonLevel.ALTA -> c.success
        SeasonLevel.MEDIA -> c.accent
        SeasonLevel.BAJA -> c.muted
    }
}

@Composable
private fun SeasonChart(report: HotelSeasonReport) {
    val colors = ApptividadesThemeDefaults.colors
    val max = report.seasonProfile.maxOfOrNull { it.averageIncome } ?: BigDecimal.ZERO
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth().testTag("hotelseason_seasons"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                report.seasonProfile.forEach { point -> SeasonBar(point, max, report.hasSeasonHistory, Modifier.weight(1f)) }
            }
            if (report.hasSeasonHistory) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeasonLevel.entries.forEach { level ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(levelColor(level)))
                            Text(levelLabel(level), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                        }
                    }
                }
                MutedText(stringResource(R.string.hotelseason_seasons_hint))
            } else {
                Text(
                    stringResource(R.string.hotelseason_seasons_little_history),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.testTag("hotelseason_little_history"),
                )
            }
        }
    }
}

@Composable
private fun SeasonBar(point: MonthSeasonPoint, max: BigDecimal, classified: Boolean, modifier: Modifier) {
    val colors = ApptividadesThemeDefaults.colors
    val fraction = if (max.signum() <= 0) 0f else point.averageIncome.toFloat() / max.toFloat()
    val barColor = if (classified && point.monthsCounted > 0) levelColor(point.level) else colors.line
    Column(
        modifier = modifier.testTag("season_bar_${point.monthOfYear}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((8 + 74 * fraction).dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(barColor),
        )
        Text(
            text = shortMonth(Month.of(point.monthOfYear)).take(1),
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
        )
    }
}

@Composable
private fun UpcomingCard(opportunity: UpcomingMonthOpportunity, report: HotelSeasonReport) {
    val colors = ApptividadesThemeDefaults.colors
    val progress = if (opportunity.typicalIncome.signum() <= 0) {
        0f
    } else {
        (opportunity.bookedIncome.toFloat() / opportunity.typicalIncome.toFloat()).coerceIn(0f, 1f)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, if (opportunity.gap.signum() > 0 && progress < 0.5f) colors.accent else colors.line),
        modifier = Modifier.fillMaxWidth().testTag("upcoming_${opportunity.month}"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monthYear(opportunity.month),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (report.hasSeasonHistory) {
                    StatusPill(
                        label = levelLabel(opportunity.level),
                        backgroundColor = colors.neutralChipBg,
                        textColor = colors.neutralChipText,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = colors.accent,
                trackColor = colors.line,
            )
            Text(
                stringResource(
                    R.string.hotelseason_upcoming_line,
                    money(opportunity.bookedIncome, report),
                    money(opportunity.typicalIncome, report),
                    opportunity.bookedGigs,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            Text(
                text = if (opportunity.gap.signum() > 0) {
                    stringResource(R.string.hotelseason_upcoming_gap, money(opportunity.gap, report))
                } else {
                    stringResource(R.string.hotelseason_upcoming_covered)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (opportunity.gap.signum() > 0) colors.accentHover else colors.successText,
                modifier = Modifier.testTag("upcoming_gap_${opportunity.month}"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotelCard(row: HotelSeasonRow, report: HotelSeasonReport) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth().testTag("hotel_${row.key}"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (row.isUnassigned) stringResource(R.string.hotelseason_no_place) else row.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = money(row.totalIncome, report),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val change = row.changePercent
                when {
                    row.isNew -> StatusPill(stringResource(R.string.hotelseason_new), colors.infoBg, colors.info, Modifier.testTag("hotel_change_${row.key}"))
                    row.totalIncome.signum() == 0 -> StatusPill(stringResource(R.string.hotelseason_no_income), colors.dangerBg, colors.dangerText, Modifier.testTag("hotel_change_${row.key}"))
                    change != null && change >= 0 -> StatusPill(stringResource(R.string.hotelseason_change_up, change), colors.successBg, colors.successText, Modifier.testTag("hotel_change_${row.key}"))
                    change != null -> StatusPill(stringResource(R.string.hotelseason_change_down, -change), colors.dangerBg, colors.dangerText, Modifier.testTag("hotel_change_${row.key}"))
                }
                if (row.onlyHighSeason) {
                    StatusPill(stringResource(R.string.hotelseason_only_high), colors.accentSoft, colors.accentHover, Modifier.testTag("hotel_high_only_${row.key}"))
                }
            }

            if (row.gigCount > 0) {
                Text(
                    stringResource(R.string.hotelseason_hotel_line, row.gigCount, money(row.averagePerGig, report), row.sharePercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            row.peakMonth?.let {
                Text(stringResource(R.string.hotelseason_peak, monthYear(it)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }

            HeatStrip(row, report)
        }
    }
}

@Composable
private fun HeatStrip(row: HotelSeasonRow, report: HotelSeasonReport) {
    val colors = ApptividadesThemeDefaults.colors
    val max = row.monthlyIncome.maxOrNull() ?: BigDecimal.ZERO
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
            row.monthlyIncome.forEachIndexed { i, value ->
                val alpha = if (max.signum() <= 0 || value.signum() <= 0) 0f else 0.25f + 0.75f * (value.toFloat() / max.toFloat())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (alpha == 0f) colors.line.copy(alpha = 0.5f) else colors.accent.copy(alpha = alpha))
                        .testTag("heat_${row.key}_$i"),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${shortMonth(report.months.first().month)} ${report.months.first().year}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${shortMonth(report.months.last().month)} ${report.months.last().year}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
            )
        }
    }
}
