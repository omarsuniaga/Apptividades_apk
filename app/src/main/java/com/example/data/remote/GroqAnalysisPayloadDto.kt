package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Structured, PII-minimized JSON payload sent to Groq for combined analysis of the artist's
 * current agenda, 12-month financial history, receivables, and route/logistics telemetry.
 *
 * Security invariants:
 * - Never contains bank account numbers, user email, phone numbers, or session tokens.
 * - Supplier/client names respect the user's anonymization preference ("Proveedor A, B...").
 */
@JsonClass(generateAdapter = true)
data class GroqAnalysisPayloadDto(
    @Json(name = "fecha_actual") val generatedAt: String,
    @Json(name = "configuracion") val settings: GroqSettingsSummaryDto,
    @Json(name = "agenda_actual") val currentAgenda: GroqAgendaPayloadDto,
    @Json(name = "historico_financiero") val financialHistory: GroqFinancialHistoryPayloadDto,
    @Json(name = "inteligencia_rutas") val routeIntelligence: GroqRouteIntelligencePayloadDto,
)

@JsonClass(generateAdapter = true)
data class GroqSettingsSummaryDto(
    @Json(name = "moneda_principal") val defaultCurrency: String,
    @Json(name = "meta_mensual") val monthlyGoal: Double,
    @Json(name = "conversion_multimoneda") val convertMixedCurrencies: Boolean,
    @Json(name = "tasa_usd_dop") val defaultUsdToDopRate: Double,
    @Json(name = "duracion_evento_min") val defaultEventMinutes: Int,
    @Json(name = "montaje_min") val setupMinutes: Int,
    @Json(name = "desmontaje_min") val teardownMinutes: Int,
    @Json(name = "margen_viaje_min") val travelBufferMinutes: Int,
    @Json(name = "clientes_anonimizados") val anonymizedClients: Boolean,
)

@JsonClass(generateAdapter = true)
data class GroqAgendaPayloadDto(
    @Json(name = "proximos_toques") val upcomingGigs: List<GroqAgendaGigDto>,
    @Json(name = "fines_semana_libres_mes") val freeWeekendDaysThisMonth: List<String>,
    @Json(name = "dias_con_multiples_toques") val multiGigDays: List<GroqMultiGigDayDto>,
)

@JsonClass(generateAdapter = true)
data class GroqAgendaGigDto(
    @Json(name = "fecha") val date: String,
    @Json(name = "hora") val time: String?,
    @Json(name = "titulo") val title: String,
    @Json(name = "lugar") val venue: String,
    @Json(name = "proveedor") val supplier: String,
    @Json(name = "monto") val amount: Double,
    @Json(name = "moneda") val currency: String,
    @Json(name = "estado_pago") val paymentStatus: String,
    @Json(name = "distancia_km") val distanceKm: Double?,
    @Json(name = "traslado_min") val travelMinutes: Int?,
    @Json(name = "salida_recomendada") val recommendedLeaveTime: String?,
)

@JsonClass(generateAdapter = true)
data class GroqMultiGigDayDto(
    @Json(name = "fecha") val date: String,
    @Json(name = "cantidad_toques") val gigCount: Int,
    @Json(name = "transiciones") val transitions: List<String>,
)

@JsonClass(generateAdapter = true)
data class GroqFinancialHistoryPayloadDto(
    @Json(name = "mes_actual") val currentMonth: GroqCurrentMonthFinanceDto,
    @Json(name = "ultimos_12_meses") val last12Months: GroqTwelveMonthFinanceDto,
    @Json(name = "concentracion_proveedores") val supplierConcentration: List<GroqSupplierShareDto>,
    @Json(name = "cuentas_por_cobrar") val receivables: GroqReceivablesPayloadDto,
)

@JsonClass(generateAdapter = true)
data class GroqCurrentMonthFinanceDto(
    @Json(name = "mes") val month: String,
    @Json(name = "facturado") val billed: Double,
    @Json(name = "avance_meta_pct") val goalProgressPercent: Int,
    @Json(name = "toques_faltantes_meta") val missingGigsEstimate: Int,
    @Json(name = "tocadas") val playedCount: Int,
    @Json(name = "por_venir") val upcomingCount: Int,
    @Json(name = "cobrado") val collected: Double,
    @Json(name = "gastos") val expenses: Double,
    @Json(name = "ganancia_neta") val netProfit: Double,
)

@JsonClass(generateAdapter = true)
data class GroqTwelveMonthFinanceDto(
    @Json(name = "total_facturado") val totalBilled: Double,
    @Json(name = "total_toques") val totalGigs: Int,
    @Json(name = "promedio_mensual") val averagePerMonth: Double,
    @Json(name = "promedio_por_toque") val averagePerGig: Double,
    @Json(name = "toques_por_mes") val gigsPerMonth: Double,
    @Json(name = "meses_meta_cumplida") val monthsReachedGoal: Int,
    @Json(name = "gastos_totales") val totalExpenses: Double,
    @Json(name = "ganancia_neta") val netProfit: Double,
    @Json(name = "margen_neto_pct") val netMarginPercent: Int,
    @Json(name = "gastos_por_categoria") val expensesByCategory: List<GroqExpenseCategoryDto>,
    @Json(name = "serie_mensual") val monthlySeries: List<GroqMonthlyPointDto>,
)

@JsonClass(generateAdapter = true)
data class GroqExpenseCategoryDto(
    @Json(name = "categoria") val category: String,
    @Json(name = "monto") val amount: Double,
    @Json(name = "porcentaje") val percent: Int,
)

@JsonClass(generateAdapter = true)
data class GroqMonthlyPointDto(
    @Json(name = "mes") val month: String,
    @Json(name = "facturado") val billed: Double,
    @Json(name = "toques") val gigs: Int,
    @Json(name = "cumplio_meta") val reachedGoal: Boolean,
)

@JsonClass(generateAdapter = true)
data class GroqSupplierShareDto(
    @Json(name = "proveedor") val supplier: String,
    @Json(name = "facturado") val totalAmount: Double,
    @Json(name = "participacion_pct") val sharePercent: Int,
    @Json(name = "toques") val gigCount: Int,
)

@JsonClass(generateAdapter = true)
data class GroqReceivablesPayloadDto(
    @Json(name = "total_por_cobrar") val totalReceivable: Double,
    @Json(name = "total_vencido") val overdueTotal: Double,
    @Json(name = "total_en_plazo") val inTermTotal: Double,
    @Json(name = "grupos_vencidos") val overdueGroups: List<GroqReceivableGroupDto>,
    @Json(name = "grupos_en_plazo") val inTermGroups: List<GroqReceivableGroupDto>,
)

@JsonClass(generateAdapter = true)
data class GroqReceivableGroupDto(
    @Json(name = "proveedor") val supplier: String,
    @Json(name = "monto") val amount: Double,
    @Json(name = "moneda") val currency: String,
    @Json(name = "toques") val gigCount: Int,
    @Json(name = "fecha_mas_antigua") val oldestDate: String,
    @Json(name = "dias_pendiente") val daysOutstanding: Long,
)

@JsonClass(generateAdapter = true)
data class GroqRouteIntelligencePayloadDto(
    @Json(name = "rutas_medidas") val measuredRoutesCount: Int,
    @Json(name = "rutas_osrm") val osrmCount: Int,
    @Json(name = "rutas_estimadas") val estimatedCount: Int,
    @Json(name = "distancia_total_km") val totalDistanceKm: Double,
    @Json(name = "distancia_promedio_km") val avgDistanceKm: Double,
    @Json(name = "traslado_promedio_min") val avgTravelMinutes: Int,
    @Json(name = "traslado_maximo_min") val maxTravelMinutes: Int,
    @Json(name = "gastos_transporte_12m") val transportExpenses12m: Double,
    @Json(name = "lugares_frecuentes") val topVenues: List<GroqVenueRouteStatDto>,
)

@JsonClass(generateAdapter = true)
data class GroqVenueRouteStatDto(
    @Json(name = "lugar") val venue: String,
    @Json(name = "toques") val gigs: Int,
    @Json(name = "tarifa_promedio") val avgFee: Double,
    @Json(name = "distancia_promedio_km") val avgDistanceKm: Double?,
    @Json(name = "traslado_promedio_min") val avgTravelMinutes: Int?,
)
