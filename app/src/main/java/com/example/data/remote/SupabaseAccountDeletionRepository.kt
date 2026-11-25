package com.example.data.remote

import com.example.data.port.AccountDeletionPort
import com.example.data.port.AuthRepository
import com.example.domain.model.AppError

/**
 * Deletes all user-owned cloud records in Supabase for Play account & data deletion compliance.
 * Removes rows scoped strictly by owner_id across user tables and attempts the optional
 * `rpc/delete_user_account` endpoint if deployed on the backend instance.
 */
class SupabaseAccountDeletionRepository(
    private val httpClient: SupabaseHttpClient,
    private val authRepository: AuthRepository,
) : AccountDeletionPort {

    private val coreTables = listOf(
        "lab_activities",
        "lab_bank_accounts",
        "lab_suppliers",
        "lab_settings",
    )

    private val optionalTables = listOf(
        "lab_venues",
    )

    override suspend fun deleteAllUserCloudData(ownerId: String): Result<Unit> {
        val cleanOwnerId = ownerId.trim()
        if (cleanOwnerId.isEmpty()) {
            return Result.failure(AppError.Unauthorized("Sesión inválida para eliminar la cuenta."))
        }

        for (table in coreTables) {
            val result = deleteRowsForOwner(table, cleanOwnerId)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                if (err !is AppError.ServerFeaturePending) {
                    return Result.failure(err ?: AppError.Network("No se pudieron eliminar tus datos en la nube."))
                }
            }
        }

        for (table in optionalTables) {
            val result = deleteRowsForOwner(table, cleanOwnerId)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                if (err !is AppError.ServerFeaturePending) {
                    // Best-effort on optional tables; continue cleanup
                }
            }
        }

        // Best-effort RPC call if the Supabase project exposes a delete_user_account function.
        val rpcUrl = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/rpc/delete_user_account")
            .build()
        httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "POST",
            url = rpcUrl,
            jsonBody = "{}",
        )

        return Result.success(Unit)
    }

    private suspend fun deleteRowsForOwner(table: String, ownerId: String): Result<Unit> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/$table")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()
        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "DELETE",
            url = url,
        ).map { Unit }
    }
}
