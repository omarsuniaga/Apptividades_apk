package com.example.data.remote

import com.example.data.local.LocalDeparturePrefs
import com.example.data.local.SessionDataStore
import com.example.data.port.AuthRepository
import com.example.data.port.SettingsRepository
import com.example.data.remote.dto.SettingsCoreUpsertDto
import com.example.data.remote.dto.SettingsDto
import com.example.data.remote.dto.SettingsFullUpsertDto
import com.example.domain.model.AppError
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

class SupabaseSettingsRepository(
    private val httpClient: SupabaseHttpClient,
    private val authRepository: AuthRepository,
    private val sessionDataStore: SessionDataStore,
) : SettingsRepository {

    private val listType = Types.newParameterizedType(List::class.java, SettingsDto::class.java)
    private val listAdapter = httpClient.moshi.adapter<List<SettingsDto>>(listType)
    private val coreUpsertAdapter = httpClient.moshi.adapter(SettingsCoreUpsertDto::class.java)
    private val fullUpsertAdapter = httpClient.moshi.adapter(SettingsFullUpsertDto::class.java).serializeNulls()

    override suspend fun fetchSettings(ownerId: String): Result<LabSettings> {
        val localGoal = sessionDataStore.loadMonthlyGoal()
        val localDep = sessionDataStore.loadDeparturePrefs()
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_settings")
            .addQueryParameter("select", "*")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "GET",
            url = url,
        ).getOrElse { return Result.failure(it) }

        val dto = listAdapter.fromJson(response.body)?.firstOrNull()
        if (dto == null) {
            return Result.success(
                LabSettings(
                    ownerId = ownerId,
                    setupMinutes = localDep.setupMinutes,
                    monthlyGoal = localGoal,
                    prepMinutes = localDep.prepMinutes,
                    travelBufferMinutes = localDep.travelBufferMinutes,
                    internalTransferInMinutes = localDep.internalTransferInMinutes,
                    preStartMarginMinutes = localDep.preStartMarginMinutes,
                    internalTransferOutMinutes = localDep.internalTransferOutMinutes,
                    homeLatitude = localDep.homeLatitude,
                    homeLongitude = localDep.homeLongitude,
                    scoreReminderDays = localDep.scoreReminderDays,
                    contingencyEnabled = localDep.contingencyEnabled,
                    contingencyLeadMinutes = localDep.contingencyLeadMinutes,
                    monthlyGoalSyncedOnServer = false,
                )
            )
        }

        val serverGoal = dto.monthlyGoal?.let { BigDecimal.valueOf(it) }
        if (serverGoal != null) {
            sessionDataStore.saveMonthlyGoal(serverGoal)
        }

        val effectiveSetup = (dto.setupMinutes ?: localDep.setupMinutes).coerceIn(0, 240)
        val effectivePrep = dto.prepMinutes ?: localDep.prepMinutes
        val effectiveBuffer = dto.travelBufferMinutes ?: localDep.travelBufferMinutes
        val effectiveHomeLat = dto.homeLatitude ?: localDep.homeLatitude
        val effectiveHomeLng = dto.homeLongitude ?: localDep.homeLongitude

        val effectiveScoreDays = (dto.scoreReminderDays ?: localDep.scoreReminderDays).coerceIn(0, 14)

        sessionDataStore.saveDeparturePrefs(
            LocalDeparturePrefs(
                setupMinutes = effectiveSetup,
                prepMinutes = effectivePrep,
                travelBufferMinutes = effectiveBuffer,
                internalTransferInMinutes = localDep.internalTransferInMinutes,
                preStartMarginMinutes = localDep.preStartMarginMinutes,
                internalTransferOutMinutes = localDep.internalTransferOutMinutes,
                homeLatitude = effectiveHomeLat,
                homeLongitude = effectiveHomeLng,
                scoreReminderDays = effectiveScoreDays,
                contingencyEnabled = localDep.contingencyEnabled,
                contingencyLeadMinutes = localDep.contingencyLeadMinutes,
            )
        )

        return Result.success(
            LabSettings(
                ownerId = dto.ownerId,
                defaultCurrency = Currency.fromCode(dto.defaultCurrency),
                setupMinutes = (dto.setupMinutes ?: 45).coerceIn(0, 240),
                eventMinutes = (dto.eventMinutes ?: 120).coerceIn(1, 1440),
                teardownMinutes = (dto.teardownMinutes ?: 45).coerceIn(0, 240),
                monthlyGoal = serverGoal ?: localGoal,
                prepMinutes = effectivePrep.coerceIn(0, 240),
                travelBufferMinutes = effectiveBuffer.coerceIn(0, 120),
                internalTransferInMinutes = localDep.internalTransferInMinutes.coerceIn(0, 180),
                preStartMarginMinutes = localDep.preStartMarginMinutes.coerceIn(0, 180),
                internalTransferOutMinutes = localDep.internalTransferOutMinutes.coerceIn(0, 180),
                homeLatitude = effectiveHomeLat,
                homeLongitude = effectiveHomeLng,
                scoreReminderDays = effectiveScoreDays,
                contingencyEnabled = localDep.contingencyEnabled,
                contingencyLeadMinutes = localDep.contingencyLeadMinutes.coerceIn(5, 120),
                monthlyGoalSyncedOnServer = serverGoal != null,
            )
        )
    }

    override suspend fun saveSettings(ownerId: String, settings: LabSettings): Result<LabSettings> {
        sessionDataStore.saveMonthlyGoal(settings.monthlyGoal)
        sessionDataStore.saveDeparturePrefs(
            LocalDeparturePrefs(
                setupMinutes = settings.setupMinutes.coerceIn(0, 240),
                prepMinutes = settings.prepMinutes,
                travelBufferMinutes = settings.travelBufferMinutes,
                internalTransferInMinutes = settings.internalTransferInMinutes.coerceIn(0, 180),
                preStartMarginMinutes = settings.preStartMarginMinutes.coerceIn(0, 180),
                internalTransferOutMinutes = settings.internalTransferOutMinutes.coerceIn(0, 180),
                homeLatitude = settings.homeLatitude,
                homeLongitude = settings.homeLongitude,
                scoreReminderDays = settings.scoreReminderDays.coerceIn(0, 14),
                contingencyEnabled = settings.contingencyEnabled,
                contingencyLeadMinutes = settings.contingencyLeadMinutes.coerceIn(5, 120),
            )
        )

        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_settings")
            .addQueryParameter("on_conflict", "owner_id")
            .build()

        val fullDto = SettingsFullUpsertDto(
            ownerId = ownerId,
            defaultCurrency = settings.defaultCurrency.code,
            setupMinutes = settings.setupMinutes.coerceIn(0, 240),
            eventMinutes = settings.eventMinutes.coerceIn(1, 1440),
            teardownMinutes = settings.teardownMinutes.coerceIn(0, 240),
            monthlyGoal = settings.monthlyGoal.toDouble(),
            prepMinutes = settings.prepMinutes.coerceIn(0, 240),
            travelBufferMinutes = settings.travelBufferMinutes.coerceIn(0, 120),
            homeLatitude = settings.homeLatitude,
            homeLongitude = settings.homeLongitude,
        )

        val fullAttempt = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "POST",
            url = url,
            jsonBody = fullUpsertAdapter.toJson(fullDto),
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=representation"),
        )

        if (fullAttempt.isSuccess) {
            return Result.success(settings.copy(ownerId = ownerId, monthlyGoalSyncedOnServer = true))
        }

        // If Phase 0 columns do not exist yet on server, save core 2.1 columns seamlessly while keeping departure prefs locally
        val err = fullAttempt.exceptionOrNull()
        if (err is AppError.ServerFeaturePending) {
            val coreDto = SettingsCoreUpsertDto(
                ownerId = ownerId,
                defaultCurrency = settings.defaultCurrency.code,
                setupMinutes = settings.setupMinutes.coerceIn(0, 240),
                eventMinutes = settings.eventMinutes.coerceIn(1, 1440),
                teardownMinutes = settings.teardownMinutes.coerceIn(0, 240),
            )
            val coreAttempt = httpClient.executeAuthenticated(
                authRepository = authRepository,
                method = "POST",
                url = url,
                jsonBody = coreUpsertAdapter.toJson(coreDto),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=representation"),
            )
            return coreAttempt.map {
                settings.copy(ownerId = ownerId, monthlyGoalSyncedOnServer = false)
            }
        }

        return Result.failure(err ?: AppError.Network("No se pudieron guardar los ajustes."))
    }

    override fun observeLocalMonthlyGoal(): Flow<BigDecimal> = sessionDataStore.observeMonthlyGoal()

    override suspend fun saveLocalMonthlyGoal(goal: BigDecimal) {
        sessionDataStore.saveMonthlyGoal(goal)
    }
}
