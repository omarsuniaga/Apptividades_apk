package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignInRequestDto(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequestDto(
    @Json(name = "refresh_token") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class RecoverPasswordRequestDto(
    val email: String,
    @Json(name = "redirect_to") val redirectTo: String,
)

@JsonClass(generateAdapter = true)
data class AuthIdentityDto(
    val id: String? = null,
    val provider: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthUserDto(
    val id: String,
    val email: String? = null,
    val identities: List<AuthIdentityDto>? = null,
    @Json(name = "confirmation_sent_at") val confirmationSentAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "expires_in") val expiresIn: Long = 3600,
    @Json(name = "expires_at") val expiresAt: Long? = null,
    val user: AuthUserDto,
)

@JsonClass(generateAdapter = true)
data class SupabaseErrorDto(
    val code: String? = null,
    @Json(name = "error_code") val errorCode: String? = null,
    val message: String? = null,
    val details: String? = null,
    val hint: String? = null,
    val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    val msg: String? = null,
)

@JsonClass(generateAdapter = true)
data class ResendConfirmationRequestDto(
    val type: String = "signup",
    val email: String,
)

@JsonClass(generateAdapter = true)
data class UpdatePasswordRequestDto(
    val password: String,
)

@JsonClass(generateAdapter = true)
data class ActivityDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "supplier_id") val supplierId: String? = null,
    @Json(name = "supplier_name") val supplierName: String? = null,
    val title: String? = null,
    @Json(name = "activity_type") val activityType: String? = null,
    @Json(name = "event_date") val eventDate: String,
    @Json(name = "event_time") val eventTime: String? = null,
    @Json(name = "venue_id") val venueId: String? = null,
    @Json(name = "venue_name") val venueName: String? = null,
    @Json(name = "venue_sub_location") val venueSubLocation: String? = null,
    @Json(name = "venue_address") val venueAddress: String? = null,
    val city: String? = null,
    val notes: String? = null,
    val description: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    @Json(name = "payment_status") val paymentStatus: String? = null,
    @Json(name = "paid_at") val paidAt: String? = null,
    val tags: List<String>? = null,
    @Json(name = "series_id") val seriesId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "melodyflow_id") val melodyflowId: String? = null,
)

/**
 * Contract 2.1 insert payload:
 * - Always sends owner_id
 * - supplier_id is null (DB trigger resolves it from supplier_name)
 * - Never writes melodyflow_id, created_at, or updated_at
 */
@JsonClass(generateAdapter = true)
data class ActivityInsertDto(
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "supplier_id") val supplierId: String? = null,
    @Json(name = "supplier_name") val supplierName: String,
    val title: String,
    @Json(name = "activity_type") val activityType: String,
    @Json(name = "event_date") val eventDate: String,
    @Json(name = "event_time") val eventTime: String?,
    @Json(name = "venue_id") val venueId: String? = null,
    @Json(name = "venue_name") val venueName: String,
    @Json(name = "venue_sub_location") val venueSubLocation: String? = null,
    @Json(name = "venue_address") val venueAddress: String? = null,
    val city: String? = null,
    val notes: String? = null,
    val description: String?,
    val amount: Double,
    val currency: String,
    @Json(name = "payment_status") val paymentStatus: String,
    @Json(name = "paid_at") val paidAt: String?,
    val tags: List<String> = emptyList(),
    @Json(name = "series_id") val seriesId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@JsonClass(generateAdapter = true)
data class ActivityPatchDto(
    @Json(name = "supplier_name") val supplierName: String,
    val title: String,
    @Json(name = "activity_type") val activityType: String,
    @Json(name = "event_date") val eventDate: String? = null,
    @Json(name = "event_time") val eventTime: String?,
    @Json(name = "venue_name") val venueName: String,
    @Json(name = "venue_sub_location") val venueSubLocation: String? = null,
    @Json(name = "venue_address") val venueAddress: String? = null,
    val city: String? = null,
    val notes: String? = null,
    val description: String?,
    val amount: Double,
    val currency: String,
    @Json(name = "payment_status") val paymentStatus: String,
    @Json(name = "paid_at") val paidAt: String?,
    val tags: List<String>,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@JsonClass(generateAdapter = true)
data class PaymentPatchDto(
    @Json(name = "payment_status") val paymentStatus: String,
    @Json(name = "paid_at") val paidAt: String?,
)

@JsonClass(generateAdapter = true)
data class SupplierDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
)

@JsonClass(generateAdapter = true)
data class SupplierInsertDto(
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val notes: String?,
)

@JsonClass(generateAdapter = true)
data class SupplierPatchDto(
    val name: String,
    val phone: String?,
    val email: String?,
    val notes: String?,
)

@JsonClass(generateAdapter = true)
data class SettingsDto(
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "default_currency") val defaultCurrency: String? = null,
    @Json(name = "setup_minutes") val setupMinutes: Int? = null,
    @Json(name = "event_minutes") val eventMinutes: Int? = null,
    @Json(name = "teardown_minutes") val teardownMinutes: Int? = null,
    @Json(name = "monthly_goal") val monthlyGoal: Double? = null,
    @Json(name = "prep_minutes") val prepMinutes: Int? = null,
    @Json(name = "travel_buffer_minutes") val travelBufferMinutes: Int? = null,
    @Json(name = "home_latitude") val homeLatitude: Double? = null,
    @Json(name = "home_longitude") val homeLongitude: Double? = null,
    @Json(name = "score_reminder_days") val scoreReminderDays: Int? = null,
)

@JsonClass(generateAdapter = true)
data class SettingsCoreUpsertDto(
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "default_currency") val defaultCurrency: String,
    @Json(name = "setup_minutes") val setupMinutes: Int,
    @Json(name = "event_minutes") val eventMinutes: Int,
    @Json(name = "teardown_minutes") val teardownMinutes: Int,
)

@JsonClass(generateAdapter = true)
data class SettingsFullUpsertDto(
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "default_currency") val defaultCurrency: String,
    @Json(name = "setup_minutes") val setupMinutes: Int,
    @Json(name = "event_minutes") val eventMinutes: Int,
    @Json(name = "teardown_minutes") val teardownMinutes: Int,
    @Json(name = "monthly_goal") val monthlyGoal: Double,
    @Json(name = "prep_minutes") val prepMinutes: Int,
    @Json(name = "travel_buffer_minutes") val travelBufferMinutes: Int,
    @Json(name = "home_latitude") val homeLatitude: Double?,
    @Json(name = "home_longitude") val homeLongitude: Double?,
)

@JsonClass(generateAdapter = true)
data class SignUpRequestDto(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null,
)

@JsonClass(generateAdapter = true)
data class SignUpResponseDto(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "expires_at") val expiresAt: Long? = null,
    val user: AuthUserDto? = null,
    val id: String? = null,
    val email: String? = null,
    val identities: List<AuthIdentityDto>? = null,
    @Json(name = "confirmation_sent_at") val confirmationSentAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class BankAccountDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    val label: String,
    @Json(name = "bank_name") val bankName: String,
    @Json(name = "holder_name") val holderName: String,
    @Json(name = "account_number") val accountNumber: String,
    @Json(name = "account_type") val accountType: String? = null,
    val currency: String = "DOP",
    @Json(name = "is_default") val isDefault: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class BankAccountInsertDto(
    @Json(name = "owner_id") val ownerId: String,
    val label: String,
    @Json(name = "bank_name") val bankName: String,
    @Json(name = "holder_name") val holderName: String,
    @Json(name = "account_number") val accountNumber: String,
    @Json(name = "account_type") val accountType: String?,
    val currency: String,
    @Json(name = "is_default") val isDefault: Boolean,
)

@JsonClass(generateAdapter = true)
data class BankAccountPatchDto(
    val label: String,
    @Json(name = "bank_name") val bankName: String,
    @Json(name = "holder_name") val holderName: String,
    @Json(name = "account_number") val accountNumber: String,
    @Json(name = "account_type") val accountType: String?,
    val currency: String,
    @Json(name = "is_default") val isDefault: Boolean,
)

@JsonClass(generateAdapter = true)
data class BankAccountDefaultPatchDto(
    @Json(name = "is_default") val isDefault: Boolean,
)

/**
 * Matches corrected live schema for lab_devices: push_token / device_name / last_seen_at (NOT fcm_token).
 */
@JsonClass(generateAdapter = true)
data class DeviceHeartbeatUpsertDto(
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "push_token") val pushToken: String,
    @Json(name = "last_seen_at") val lastSeenAt: String,
)
