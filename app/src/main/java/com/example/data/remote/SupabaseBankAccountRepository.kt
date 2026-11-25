package com.example.data.remote

import com.example.data.port.AuthRepository
import com.example.data.port.BankAccountRepository
import com.example.data.remote.dto.BankAccountDefaultPatchDto
import com.example.data.remote.dto.BankAccountDto
import com.example.data.remote.dto.BankAccountInsertDto
import com.example.data.remote.dto.BankAccountPatchDto
import com.example.domain.model.AppError
import com.example.domain.model.BankAccount
import com.example.domain.model.BankAccountDraft
import com.example.domain.model.BankAccountType
import com.example.domain.model.Currency
import com.squareup.moshi.Types

/**
 * PostgREST adapter for `lab_bank_accounts`.
 * Matches live schema exactly:
 *   id, owner_id, label (REQUIRED), bank_name, holder_name, account_number,
 *   account_type ('ahorros'|'corriente'|'otro'|null), currency ('DOP'|'USD'), is_default.
 * Enforces ONLY ONE default account per (owner, currency) by clearing prior defaults first.
 */
class SupabaseBankAccountRepository(
    private val httpClient: SupabaseHttpClient,
    private val authRepository: AuthRepository,
) : BankAccountRepository {

    private val listType = Types.newParameterizedType(List::class.java, BankAccountDto::class.java)
    private val listAdapter = httpClient.moshi.adapter<List<BankAccountDto>>(listType)
    private val insertAdapter = httpClient.moshi.adapter(BankAccountInsertDto::class.java)
    private val patchAdapter = httpClient.moshi.adapter(BankAccountPatchDto::class.java)
    private val defaultPatchAdapter = httpClient.moshi.adapter(BankAccountDefaultPatchDto::class.java)

    override suspend fun fetchBankAccounts(ownerId: String): Result<List<BankAccount>> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_bank_accounts")
            .addQueryParameter("select", SELECT_COLUMNS)
            .addQueryParameter("owner_id", "eq.$ownerId")
            .addQueryParameter("order", "is_default.desc,label.asc")
            .build()

        return httpClient.executeAuthenticated(authRepository, "GET", url).map { resp ->
            listAdapter.fromJson(resp.body).orEmpty().map { it.toDomain() }
        }
    }

    override suspend fun createBankAccount(
        ownerId: String,
        draft: BankAccountDraft,
    ): Result<BankAccount> {
        validateDraft(draft)?.let { return Result.failure(AppError.Validation(it)) }

        if (draft.isDefault) {
            clearDefaultsForCurrency(ownerId, draft.currency).onFailure { return Result.failure(it) }
        }

        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_bank_accounts")
            .addQueryParameter("select", SELECT_COLUMNS)
            .build()

        val payload = insertAdapter.toJson(
            BankAccountInsertDto(
                ownerId = ownerId,
                label = draft.label.trim(),
                bankName = draft.bankName.trim(),
                holderName = draft.holderName.trim(),
                accountNumber = draft.accountNumber.trim(),
                accountType = draft.accountType?.dbValue,
                currency = draft.currency.code,
                isDefault = draft.isDefault,
            )
        )

        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "POST",
            url = url,
            jsonBody = payload,
            extraHeaders = mapOf("Prefer" to "return=representation"),
        ).mapCatching { resp ->
            listAdapter.fromJson(resp.body)?.firstOrNull()?.toDomain()
                ?: throw AppError.Network("No se recibió la cuenta bancaria creada.")
        }
    }

    override suspend fun updateBankAccount(
        ownerId: String,
        accountId: String,
        draft: BankAccountDraft,
    ): Result<BankAccount> {
        validateDraft(draft)?.let { return Result.failure(AppError.Validation(it)) }

        if (draft.isDefault) {
            clearDefaultsForCurrency(ownerId, draft.currency).onFailure { return Result.failure(it) }
        }

        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_bank_accounts")
            .addQueryParameter("id", "eq.$accountId")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .addQueryParameter("select", SELECT_COLUMNS)
            .build()

        val payload = patchAdapter.toJson(
            BankAccountPatchDto(
                label = draft.label.trim(),
                bankName = draft.bankName.trim(),
                holderName = draft.holderName.trim(),
                accountNumber = draft.accountNumber.trim(),
                accountType = draft.accountType?.dbValue,
                currency = draft.currency.code,
                isDefault = draft.isDefault,
            )
        )

        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "PATCH",
            url = url,
            jsonBody = payload,
            extraHeaders = mapOf("Prefer" to "return=representation"),
        ).mapCatching { resp ->
            listAdapter.fromJson(resp.body)?.firstOrNull()?.toDomain()
                ?: throw AppError.Network("No se pudo actualizar la cuenta bancaria.")
        }
    }

    override suspend fun setDefaultBankAccount(
        ownerId: String,
        accountId: String,
        currency: Currency,
    ): Result<Unit> {
        // Strict schema rule: clear previous default for (owner, currency) BEFORE setting the new default
        clearDefaultsForCurrency(ownerId, currency).onFailure { return Result.failure(it) }

        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_bank_accounts")
            .addQueryParameter("id", "eq.$accountId")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        val payload = defaultPatchAdapter.toJson(BankAccountDefaultPatchDto(isDefault = true))
        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "PATCH",
            url = url,
            jsonBody = payload,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        ).map { Unit }
    }

    override suspend fun deleteBankAccount(
        ownerId: String,
        accountId: String,
    ): Result<Unit> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_bank_accounts")
            .addQueryParameter("id", "eq.$accountId")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "DELETE",
            url = url,
        ).map { Unit }
    }

    private suspend fun clearDefaultsForCurrency(
        ownerId: String,
        currency: Currency,
    ): Result<Unit> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_bank_accounts")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .addQueryParameter("currency", "eq.${currency.code}")
            .addQueryParameter("is_default", "eq.true")
            .build()

        val payload = defaultPatchAdapter.toJson(BankAccountDefaultPatchDto(isDefault = false))
        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "PATCH",
            url = url,
            jsonBody = payload,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        ).map { Unit }
    }

    private fun validateDraft(draft: BankAccountDraft): String? = when {
        draft.label.trim().isEmpty() -> "La etiqueta de la cuenta es obligatoria (ej. Popular DOP)."
        draft.bankName.trim().isEmpty() -> "Indica el nombre del banco."
        draft.holderName.trim().isEmpty() -> "Indica el nombre del titular."
        draft.accountNumber.trim().isEmpty() -> "Indica el número de cuenta."
        else -> null
    }

    private fun BankAccountDto.toDomain(): BankAccount = BankAccount(
        id = id,
        ownerId = ownerId,
        label = label,
        bankName = bankName,
        holderName = holderName,
        accountNumber = accountNumber,
        accountType = BankAccountType.fromDb(accountType),
        currency = Currency.fromCode(currency),
        isDefault = isDefault,
    )

    companion object {
        const val SELECT_COLUMNS =
            "id,owner_id,label,bank_name,holder_name,account_number,account_type,currency,is_default"
    }
}
