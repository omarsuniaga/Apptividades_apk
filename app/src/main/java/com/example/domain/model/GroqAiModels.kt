package com.example.domain.model

enum class GroqModelOption(
    val modelId: String,
    val displayName: String,
    val badge: String,
) {
    GPT_OSS_120B(
        modelId = "openai/gpt-oss-120b",
        displayName = "GPT-OSS 120B",
        badge = "Recomendado · Análisis profundo",
    ),
    GPT_OSS_20B(
        modelId = "openai/gpt-oss-20b",
        displayName = "GPT-OSS 20B",
        badge = "Ultra rápido · Consultas breves",
    );

    /** gpt-oss models are reasoning models: they accept `reasoning_effort` and spend output tokens thinking. */
    val supportsReasoningEffort: Boolean
        get() = modelId.startsWith("openai/gpt-oss")

    companion object {
        fun fromId(raw: String?): GroqModelOption =
            entries.firstOrNull { it.modelId.equals(raw?.trim(), ignoreCase = true) }
                ?: GPT_OSS_120B
    }
}

data class GroqAiSettings(
    val hasUserApiKey: Boolean = false,
    /** A key is stored but cannot be read anymore: the user has to paste it again. */
    val keyNeedsReentry: Boolean = false,
    val maskedKeyPreview: String? = null,
    val selectedModel: GroqModelOption = GroqModelOption.GPT_OSS_120B,
    val anonymizeClientNames: Boolean = false,
) {
    val isConfigured: Boolean
        get() = hasUserApiKey
}

enum class AiAnalysisPreset(
    val id: String,
    val promptText: String,
) {
    FINANCIAL_DIAGNOSIS(
        id = "financial_diagnosis",
        promptText = "Haz un diagnóstico financiero profesional de mi histórico de 12 meses: identifica tendencias mensuales, margen neto real después de gastos, dependencia de proveedores y recomiéndame acciones concretas para mejorar mi tarifa promedio por toque.",
    ),
    ROUTES_AND_LOGISTICS(
        id = "routes_and_logistics",
        promptText = "Analiza mis rutas, distancias (km), tiempos de traslado, lugares frecuentes y días con múltiples toques. Detecta cuellos de botella logísticos, evalúa si mis tarifas compensan los traslados largos y sugiere cómo optimizar mis salidas y costos de transporte.",
    ),
    RECEIVABLES_STRATEGY(
        id = "receivables_strategy",
        promptText = "Revisa mis cobros pendientes y vencidos según antigüedad y concentración. Dame un plan priorizado de seguimiento para recuperar liquidez rápido y recomendaciones prácticas de política de anticipos.",
    ),
    MONTHLY_GOAL_PLAN(
        id = "monthly_goal_plan",
        promptText = "Evalúa mi avance hacia la meta mensual actual, considerando lo ya tocado, los toques programados y los fines de semana libres que quedan este mes. Dime exactamente qué estrategia seguir para cerrar el mes por encima de la meta.",
    ),
}

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val content: String,
    val modelUsed: String? = null,
    val latencyMs: Long? = null,
    val totalTokens: Int? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class GroqCompletionResult(
    val content: String,
    val model: String,
    val latencyMs: Long,
    val totalTokens: Int?,
)

data class GroqAgendaFinanceAnalysisRequest(
    val activities: List<Activity>,
    val routes: Map<String, CachedActivityRoute>,
    val scheduledDepartures: Map<String, ScheduledDeparture>,
    val financialRecords: Map<String, ActivityFinancialRecord>,
    val settings: LabSettings,
    val privacyPreferences: com.example.data.port.PrivacyPreferences,
    val today: java.time.LocalDate,
    val anonymizeClientNames: Boolean = false,
)
