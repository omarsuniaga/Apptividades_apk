package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.BankAccount
import com.example.domain.model.BankAccountType
import com.example.domain.model.Currency
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.AccountStatementBuilder
import com.example.domain.rules.AuthInputValidator
import com.example.domain.rules.ReceivableStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class AccountStatementBuilderTest {

    private val today = LocalDate.of(2026, 11, 20)

    private fun gig(
        id: String,
        date: LocalDate,
        time: LocalTime? = LocalTime.of(19, 0),
        supplier: String = "José David",
        venue: String = "Club Med Punta Cana",
        description: String? = "Trío de cuerdas",
        amount: String = "5000",
        currency: Currency = Currency.DOP,
        status: PaymentStatus = PaymentStatus.PENDIENTE,
    ) = Activity(
        id = id,
        ownerId = "user-1",
        title = venue,
        description = description,
        eventDate = date,
        eventTime = time,
        supplierName = supplier,
        venueName = venue,
        amount = BigDecimal(amount),
        currency = currency,
        paymentStatus = status,
    )

    private fun bank(
        id: String,
        label: String,
        bankName: String,
        holder: String = "Omar Pineda",
        number: String = "800123456",
        type: BankAccountType? = BankAccountType.AHORROS,
        currency: Currency = Currency.DOP,
        isDefault: Boolean = false,
    ) = BankAccount(
        id = id,
        ownerId = "user-1",
        label = label,
        bankName = bankName,
        holderName = holder,
        accountNumber = number,
        accountType = type,
        currency = currency,
        isDefault = isDefault,
    )

    @Test
    fun mergesAccentsAndSpellingVariantsOrdersByDateAndComputesOverdueDays() {
        val activities = listOf(
            // Newer in-term gig (should be ordered last)
            gig(
                id = "g3",
                date = LocalDate.of(2026, 11, 5),
                time = LocalTime.of(20, 0),
                supplier = "Jose David",
                amount = "6000",
            ),
            // Oldest overdue gig: 2026-08-04 vs 2026-11-20 = 108 days
            gig(
                id = "g1",
                date = LocalDate.of(2026, 8, 4),
                time = LocalTime.of(18, 30),
                supplier = "José David",
                amount = "5500",
            ),
            // Due this month (October gig collected in November)
            gig(
                id = "g2",
                date = LocalDate.of(2026, 10, 12),
                time = LocalTime.of(17, 0),
                supplier = "jose   david ",
                description = "José David", // repeats supplier -> fallback to "Servicio musical"
                amount = "4500",
            ),
            // Should be excluded: already paid
            gig(
                id = "g_paid",
                date = LocalDate.of(2026, 8, 10),
                supplier = "José David",
                amount = "9000",
                status = PaymentStatus.PAGADO,
            ),
            // Should be excluded: today/future
            gig(
                id = "g_today",
                date = today,
                supplier = "José David",
                amount = "7000",
            ),
            // Should be excluded: different currency (USD)
            gig(
                id = "g_usd",
                date = LocalDate.of(2026, 9, 15),
                supplier = "José David",
                amount = "200",
                currency = Currency.USD,
            ),
        )

        val accounts = listOf(
            bank(id = "b_bhd", label = "BHD DOP", bankName = "Banco BHD", isDefault = false, currency = Currency.DOP),
            bank(id = "b_usd", label = "Popular USD", bankName = "Banco Popular", isDefault = true, currency = Currency.USD),
            bank(id = "b_pop", label = "Popular DOP", bankName = "Banco Popular", holder = "Omar Músico", isDefault = true, currency = Currency.DOP),
        )

        val statement = AccountStatementBuilder.build(
            supplierName = "Jose David",
            currency = Currency.DOP,
            activities = activities,
            bankAccounts = accounts,
            today = today,
        )

        assertEquals(3, statement.items.size)
        // Ordered oldest-first
        assertEquals(listOf("g1", "g2", "g3"), statement.items.map { it.activityId })

        // 2026-08-04 to 2026-11-20 is exactly 108 days
        val first = statement.items[0]
        assertEquals(ReceivableStatus.VENCIDO, first.status)
        assertEquals(108L, first.overdueDays)
        assertEquals("Vencido 108 días", first.statusLabel)

        // Second gig: October collected in November + service fallback
        val second = statement.items[1]
        assertEquals(ReceivableStatus.ESTE_MES, second.status)
        assertEquals(0L, second.overdueDays)
        assertEquals("Por cobrar este mes", second.statusLabel)
        assertEquals(AccountStatementBuilder.DEFAULT_SERVICE_LABEL, second.serviceDescription)

        // Third gig: November collected in December
        val third = statement.items[2]
        assertEquals(ReceivableStatus.PROXIMO_MES, third.status)
        assertEquals("En plazo", third.statusLabel)

        // Totals (DOP only, USD excluded)
        assertEquals(BigDecimal("16000"), statement.totalPending)
        assertEquals(BigDecimal("5500"), statement.overdueTotal)

        // Bank accounts filtered to DOP and default first
        assertEquals(listOf("b_pop", "b_bhd"), statement.bankAccounts.map { it.id })
        assertEquals("Omar Músico", statement.issuerName)
    }

    @Test
    fun separatesCurrenciesIntoIndependentStatements() {
        val activities = listOf(
            gig(id = "dop1", date = LocalDate.of(2026, 9, 10), amount = "5000", currency = Currency.DOP),
            gig(id = "usd1", date = LocalDate.of(2026, 9, 11), amount = "180", currency = Currency.USD),
        )
        val accounts = listOf(
            bank(id = "b_dop", label = "Popular DOP", bankName = "Banco Popular", currency = Currency.DOP, isDefault = true),
            bank(id = "b_usd", label = "BHD USD", bankName = "Banco BHD", currency = Currency.USD, isDefault = true),
        )

        val usdStatement = AccountStatementBuilder.build(
            supplierName = "José David",
            currency = Currency.USD,
            activities = activities,
            bankAccounts = accounts,
            today = today,
        )

        assertEquals(Currency.USD, usdStatement.currency)
        assertEquals(listOf("usd1"), usdStatement.items.map { it.activityId })
        assertEquals(BigDecimal("180"), usdStatement.totalPending)
        assertEquals(listOf("b_usd"), usdStatement.bankAccounts.map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySupplierThrows() {
        AccountStatementBuilder.build(
            supplierName = "   ",
            currency = Currency.DOP,
            activities = listOf(gig(id = "g1", date = LocalDate.of(2026, 9, 1))),
            bankAccounts = emptyList(),
            today = today,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun supplierWithNoUnpaidPastActivitiesThrows() {
        AccountStatementBuilder.build(
            supplierName = "Proveedor Fantasma",
            currency = Currency.DOP,
            activities = listOf(gig(id = "g1", date = LocalDate.of(2026, 9, 1), supplier = "Otro")),
            bankAccounts = emptyList(),
            today = today,
        )
    }

    @Test
    fun phoneNormalizationHandlesDominicanAndInternationalNumbers() {
        assertEquals("18095551234", AccountStatementBuilder.normalizePhoneForWhatsApp("(809) 555-1234"))
        assertEquals("18294440000", AccountStatementBuilder.normalizePhoneForWhatsApp("+1 829-444-0000"))
        assertEquals("18493332211", AccountStatementBuilder.normalizePhoneForWhatsApp("849.333.2211"))
        assertEquals("34612345678", AccountStatementBuilder.normalizePhoneForWhatsApp("+34 612 345 678"))
        assertNull(AccountStatementBuilder.normalizePhoneForWhatsApp("12345"))
        assertNull(AccountStatementBuilder.normalizePhoneForWhatsApp(null))
    }

    @Test
    fun fileNameSlugAndCaptionFormatMatchPhase3Spec() {
        assertEquals(
            "resumen-de-actividades-jose-david-2026-11-20.pdf",
            AccountStatementBuilder.buildFileName("José David", today),
        )
        assertEquals(
            "resumen-de-actividades-club-med-punta-cana-2026-11-20.pdf",
            AccountStatementBuilder.buildFileName("  Club Med & Punta Cana!! ", today),
        )

        val pluralCaption = AccountStatementBuilder.buildShareCaption(
            supplierDisplayName = "José David",
            serviceCount = 4,
            totalPending = BigDecimal("22000"),
            currency = Currency.DOP,
        )
        assertTrue(pluralCaption.startsWith("Hola José David, te comparto el resumen de actividades"))
        assertTrue(pluralCaption.contains("(4 servicios, RD\$ 22,000)"))
        assertTrue(pluralCaption.endsWith("Los datos para el pago están en el documento. ¡Gracias!"))

        val singularCaption = AccountStatementBuilder.buildShareCaption(
            supplierDisplayName = "Bárbara",
            serviceCount = 1,
            totalPending = BigDecimal("150"),
            currency = Currency.USD,
        )
        assertTrue(singularCaption.contains("(1 servicio, US\$ 150)"))
    }

    @Test
    fun authInputValidatorEnforcesStrongPasswordsAndEmailSyntax() {
        assertTrue(AuthInputValidator.isValidEmail("omar.musica@example.com"))
        assertFalse(AuthInputValidator.isValidEmail("not-an-email"))

        val weakCheck = AuthInputValidator.checkPasswordStrength("abc123")
        assertFalse(weakCheck.hasMinLength)
        assertFalse(weakCheck.hasUppercase)
        assertTrue(weakCheck.hasLowercase)
        assertTrue(weakCheck.hasDigit)
        assertFalse(weakCheck.isStrong)

        val strongCheck = AuthInputValidator.checkPasswordStrength("Musica2026")
        assertTrue(strongCheck.isStrong)

        assertNotNull(AuthInputValidator.validateSignUp("omar@example.com", "Musica2026", "Otra2026"))
        assertNull(AuthInputValidator.validateSignUp("omar@example.com", "Musica2026", "Musica2026"))
    }
}
