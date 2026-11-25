package com.example.data.remote

import com.example.data.port.AuthRepository
import com.example.data.port.SupplierRepository
import com.example.data.remote.dto.SupplierDto
import com.example.data.remote.dto.SupplierInsertDto
import com.example.data.remote.dto.SupplierPatchDto
import com.example.domain.model.AppError
import com.example.domain.model.Supplier
import com.squareup.moshi.Types

class SupabaseSupplierRepository(
    private val httpClient: SupabaseHttpClient,
    private val authRepository: AuthRepository,
) : SupplierRepository {

    private val listType = Types.newParameterizedType(List::class.java, SupplierDto::class.java)
    private val listAdapter = httpClient.moshi.adapter<List<SupplierDto>>(listType)
    private val insertAdapter = httpClient.moshi.adapter(SupplierInsertDto::class.java).serializeNulls()
    private val patchAdapter = httpClient.moshi.adapter(SupplierPatchDto::class.java).serializeNulls()

    override suspend fun fetchSuppliers(ownerId: String): Result<List<Supplier>> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_suppliers")
            .addQueryParameter("select", "*")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .addQueryParameter("order", "name.asc")
            .build()

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "GET",
            url = url,
        ).getOrElse { return Result.failure(it) }

        val dtos = listAdapter.fromJson(response.body).orEmpty()
        return Result.success(dtos.map { it.toDomain() })
    }

    override suspend fun createSupplier(
        ownerId: String,
        name: String,
        phone: String?,
        email: String?,
        notes: String?,
    ): Result<Supplier> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_suppliers")
            .build()

        val dto = SupplierInsertDto(
            ownerId = ownerId,
            name = name.trim().take(160),
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            email = email?.trim()?.takeIf { it.isNotEmpty() },
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        )

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "POST",
            url = url,
            jsonBody = insertAdapter.toJson(dto),
            extraHeaders = mapOf("Prefer" to "return=representation"),
        ).getOrElse { return Result.failure(it) }

        val created = listAdapter.fromJson(response.body)?.firstOrNull()?.toDomain()
            ?: return Result.failure(AppError.Network("No se pudo crear el proveedor."))
        return Result.success(created)
    }

    override suspend fun updateSupplier(ownerId: String, supplier: Supplier): Result<Supplier> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_suppliers")
            .addQueryParameter("id", "eq.${supplier.id}")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        val patch = SupplierPatchDto(
            name = supplier.name.trim().take(160),
            phone = supplier.phone?.trim()?.takeIf { it.isNotEmpty() },
            email = supplier.email?.trim()?.takeIf { it.isNotEmpty() },
            notes = supplier.notes?.trim()?.takeIf { it.isNotEmpty() },
        )

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "PATCH",
            url = url,
            jsonBody = patchAdapter.toJson(patch),
            extraHeaders = mapOf("Prefer" to "return=representation"),
        ).getOrElse { return Result.failure(it) }

        val updated = listAdapter.fromJson(response.body)?.firstOrNull()?.toDomain()
            ?: return Result.failure(AppError.Network("No se pudo actualizar el proveedor."))
        return Result.success(updated)
    }

    override suspend fun deleteSupplier(ownerId: String, supplierId: String): Result<Unit> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_suppliers")
            .addQueryParameter("id", "eq.$supplierId")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "DELETE",
            url = url,
        ).map { Unit }
    }

    private fun SupplierDto.toDomain() = Supplier(
        id = id,
        ownerId = ownerId,
        name = name,
        phone = phone,
        email = email,
        notes = notes,
    )
}
