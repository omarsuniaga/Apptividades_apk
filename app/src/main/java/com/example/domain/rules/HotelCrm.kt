package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.Currency
import com.example.domain.model.Supplier
import java.math.BigDecimal
import java.time.LocalDate

/** A hotel is the venue where you play; a client is who hires you (planner, agency, hotel entertainment dept...). */
enum class CrmAccountType(val label: String, val pluralLabel: String) {
    HOTEL("Hotel", "Hoteles"),
    CLIENTE("Cliente", "Clientes"),
}

enum class CrmContactChannel(val label: String) {
    WHATSAPP("WhatsApp"),
    LLAMADA("Llamada"),
    VISITA("Visita"),
    CORREO("Correo"),
    OTRO("Otro"),
    ;

    companion object {
        fun fromName(raw: String?): CrmContactChannel = entries.firstOrNull { it.name == raw } ?: OTRO
    }
}

/** Relationship stage, derived from how recently you worked with the account. */
enum class CrmStage(val label: String) {
    PROSPECTO("Prospecto"),
    ACTIVO("Activo"),
    DORMIDO("Dormido"),
    INACTIVO("Inactivo"),
}

enum class CrmContactReason {
    /** The scheduled "next contact" date arrived. */
    SCHEDULED_DUE,

    /** Used to work together, no gig lately and nobody has written to them in a while. */
    DORMANT_NOT_CONTACTED,

    /** A prospect that was added manually and never contacted. */
    PROSPECT_NEVER_CONTACTED,
}

/** User-entered CRM data for an account. Identity is [accountKey], which stays stable once created. */
data class CrmProfile(
    val accountKey: String,
    val type: CrmAccountType,
    val displayName: String,
    val contactPerson: String = "",
    val contactRole: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val nextContactDate: LocalDate? = null,
    /** Added by hand as a hotel/client you want to win, even if you never worked with it. */
    val isProspect: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
)

data class CrmContact(
    val id: String,
    val accountKey: String,
    val date: LocalDate,
    val channel: CrmContactChannel,
    val note: String = "",
    val createdAtMillis: Long,
)

/** One row of the CRM: user data merged with everything derived from activities and quotes. */
data class CrmAccount(
    val accountKey: String,
    val type: CrmAccountType,
    val displayName: String,
    val profile: CrmProfile?,
    val stage: CrmStage,
    val gigCount: Int,
    val upcomingGigCount: Int,
    val lastGigDate: LocalDate?,
    val nextGigDate: LocalDate?,
    val daysSinceLastGig: Long?,
    val revenueLast12Months: Map<Currency, BigDecimal>,
    val openQuotes: Int,
    val wonQuotes: Int,
    val lostQuotes: Int,
    val lastContactDate: LocalDate?,
    val nextContactDate: LocalDate?,
    val contactReason: CrmContactReason?,
    val contactPerson: String,
    val phone: String?,
    val email: String?,
    val contacts: List<CrmContact>,
) {
    val needsContact: Boolean get() = contactReason != null
}

data class CrmSummary(
    val countsByStage: Map<CrmStage, Int>,
    val toContactCount: Int,
    val totalCount: Int,
)

object HotelCrmCalculator {

    const val ACTIVE_DAYS = 90L
    const val DORMANT_MAX_DAYS = 365L
    const val RECONTACT_AFTER_DAYS = 30L

    val NEXT_CONTACT_PRESETS_DAYS: List<Long> = listOf(7, 14, 30)

    fun accountKey(type: CrmAccountType, canonicalKey: String): String = "${type.name}:$canonicalKey"

    fun stageOf(gigCount: Int, upcomingGigCount: Int, daysSinceLastGig: Long?): CrmStage = when {
        gigCount == 0 -> CrmStage.PROSPECTO
        upcomingGigCount > 0 -> CrmStage.ACTIVO
        daysSinceLastGig == null -> CrmStage.PROSPECTO
        daysSinceLastGig <= ACTIVE_DAYS -> CrmStage.ACTIVO
        daysSinceLastGig <= DORMANT_MAX_DAYS -> CrmStage.DORMIDO
        else -> CrmStage.INACTIVO
    }

    fun summarize(accounts: List<CrmAccount>): CrmSummary = CrmSummary(
        countsByStage = CrmStage.entries.associateWith { stage -> accounts.count { it.stage == stage } },
        toContactCount = accounts.count { it.needsContact },
        totalCount = accounts.size,
    )

    /**
     * Builds the CRM rows for both account types. Hotels are grouped by venue name (prefix-merged, so
     * "Lopesan" and "Hotel Lopesan, Bávaro" are one hotel); clients are grouped by supplier name.
     * Accounts that need a contact come first, then by relationship stage and revenue.
     */
    fun buildAccounts(
        activities: List<Activity>,
        suppliers: List<Supplier>,
        quotes: List<SavedQuote>,
        profiles: List<CrmProfile>,
        contacts: List<CrmContact>,
        today: LocalDate,
    ): List<CrmAccount> =
        (CrmAccountType.entries.flatMap { type ->
            buildForType(type, activities, suppliers, quotes, profiles, contacts, today)
        }).sortedWith(accountComparator)

    private val accountComparator: Comparator<CrmAccount> =
        compareBy<CrmAccount> { it.contactReason == null }
            .thenBy { reasonRank(it.contactReason) }
            .thenBy { it.nextContactDate ?: LocalDate.MAX }
            .thenBy { it.stage.ordinal }
            .thenByDescending { it.revenueLast12Months.values.fold(BigDecimal.ZERO, BigDecimal::add) }
            .thenBy { it.displayName.lowercase() }

    private fun reasonRank(reason: CrmContactReason?): Int = when (reason) {
        CrmContactReason.SCHEDULED_DUE -> 0
        CrmContactReason.DORMANT_NOT_CONTACTED -> 1
        CrmContactReason.PROSPECT_NEVER_CONTACTED -> 2
        null -> 3
    }

    private fun buildForType(
        type: CrmAccountType,
        activities: List<Activity>,
        suppliers: List<Supplier>,
        quotes: List<SavedQuote>,
        allProfiles: List<CrmProfile>,
        allContacts: List<CrmContact>,
        today: LocalDate,
    ): List<CrmAccount> {
        val profiles = allProfiles.filter { it.type == type }
        val activityName: (Activity) -> String = if (type == CrmAccountType.HOTEL) { a -> a.venueName } else { a -> a.supplierName }
        val quoteName: (SavedQuote) -> String =
            if (type == CrmAccountType.HOTEL) { q -> q.quote.venueName } else { q -> q.quote.clientName }

        val rawNames = buildList {
            activities.forEach { add(activityName(it)) }
            quotes.forEach { add(quoteName(it)) }
            profiles.forEach { add(it.displayName) }
            if (type == CrmAccountType.CLIENTE) suppliers.forEach { add(it.name) }
        }
        val resolution = if (type == CrmAccountType.HOTEL) {
            NameNormalizer.buildVenueResolution(rawNames)
        } else {
            NameNormalizer.buildSupplierResolution(rawNames)
        }

        val activitiesByKey = activities.groupBy { resolution.canonicalKey(activityName(it)) }.filterKeys { it.isNotEmpty() }
        val quotesByKey = quotes.groupBy { resolution.canonicalKey(quoteName(it)) }.filterKeys { it.isNotEmpty() }
        val profilesByKey = profiles.groupBy { resolution.canonicalKey(it.displayName) }.filterKeys { it.isNotEmpty() }
        val suppliersByKey = if (type == CrmAccountType.CLIENTE) {
            suppliers.groupBy { resolution.canonicalKey(it.name) }.filterKeys { it.isNotEmpty() }
        } else {
            emptyMap()
        }

        val keys = linkedSetOf<String>().apply {
            addAll(activitiesByKey.keys)
            addAll(quotesByKey.keys)
            addAll(profilesByKey.keys)
            addAll(suppliersByKey.keys)
        }

        return keys.map { key ->
            val accountActivities = activitiesByKey[key].orEmpty()
            val accountQuotes = quotesByKey[key].orEmpty()
            val keyProfiles = profilesByKey[key].orEmpty()
            val profile = keyProfiles.maxByOrNull { it.updatedAtMillis }
            val supplier = suppliersByKey[key]?.firstOrNull()
            val accountKeys = keyProfiles.map { it.accountKey }.toSet()
            val accountContacts = allContacts
                .filter { it.accountKey in accountKeys }
                .sortedWith(compareByDescending<CrmContact> { it.date }.thenByDescending { it.createdAtMillis })

            val pastGigs = accountActivities.filter { !it.eventDate.isAfter(today) }
            val upcoming = accountActivities.filter { it.eventDate.isAfter(today) }
            val lastGig = pastGigs.maxOfOrNull { it.eventDate }
            val nextGig = upcoming.minOfOrNull { it.eventDate }
            val daysSince = lastGig?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) }
            val stage = stageOf(accountActivities.size, upcoming.size, daysSince)
            val cutoff = today.minusDays(DORMANT_MAX_DAYS)
            val revenue = pastGigs
                .filter { it.eventDate.isAfter(cutoff) }
                .groupBy { it.currency }
                .mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.amount) } }
                .toSortedMap(compareBy { it.ordinal })

            val lastContact = accountContacts.firstOrNull()?.date
            val nextContact = profile?.nextContactDate
            val reason = contactReason(
                stage = stage,
                isProspectProfile = profile?.isProspect == true,
                nextContactDate = nextContact,
                lastContactDate = lastContact,
                today = today,
            )

            val displayName = resolution.canonicalToDisplay[key]
                ?: profile?.displayName
                ?: key

            CrmAccount(
                accountKey = profile?.accountKey ?: accountKey(type, key),
                type = type,
                displayName = displayName,
                profile = profile,
                stage = stage,
                gigCount = accountActivities.size,
                upcomingGigCount = upcoming.size,
                lastGigDate = lastGig,
                nextGigDate = nextGig,
                daysSinceLastGig = daysSince,
                revenueLast12Months = revenue,
                openQuotes = accountQuotes.count { it.status.isOpen },
                wonQuotes = accountQuotes.count { it.status == QuoteStatus.GANADA },
                lostQuotes = accountQuotes.count { it.status == QuoteStatus.PERDIDA },
                lastContactDate = lastContact,
                nextContactDate = nextContact,
                contactReason = reason,
                contactPerson = profile?.contactPerson.orEmpty(),
                phone = profile?.phone?.takeIf { it.isNotBlank() } ?: supplier?.phone?.takeIf { it.isNotBlank() },
                email = profile?.email?.takeIf { it.isNotBlank() } ?: supplier?.email?.takeIf { it.isNotBlank() },
                contacts = accountContacts,
            )
        }
    }

    fun contactReason(
        stage: CrmStage,
        isProspectProfile: Boolean,
        nextContactDate: LocalDate?,
        lastContactDate: LocalDate?,
        today: LocalDate,
    ): CrmContactReason? {
        if (nextContactDate != null && !nextContactDate.isAfter(today)) return CrmContactReason.SCHEDULED_DUE
        // A scheduled future contact means it is already being handled.
        if (nextContactDate != null) return null
        val recentlyContacted = lastContactDate != null &&
            !lastContactDate.isBefore(today.minusDays(RECONTACT_AFTER_DAYS))
        return when {
            (stage == CrmStage.DORMIDO || stage == CrmStage.INACTIVO) && !recentlyContacted ->
                CrmContactReason.DORMANT_NOT_CONTACTED
            stage == CrmStage.PROSPECTO && isProspectProfile && lastContactDate == null ->
                CrmContactReason.PROSPECT_NEVER_CONTACTED
            else -> null
        }
    }
}

/** Ready-to-send WhatsApp messages for reconnecting with hotels and clients. */
object CrmMessageBuilder {

    fun build(account: CrmAccount, issuerName: String): String {
        val greetingName = account.contactPerson.trim().ifBlank { account.displayName.trim() }
        val greeting = if (greetingName.isNotEmpty()) "Hola $greetingName," else "Hola,"
        val signature = issuerName.trim().ifBlank { "Omar" }
        val place = if (account.type == CrmAccountType.HOTEL) " en ${account.displayName}" else ""
        return when (account.stage) {
            CrmStage.PROSPECTO ->
                "$greeting un gusto saludarte. Soy $signature, violinista con experiencia en eventos y hoteles de Punta Cana. " +
                    "Me encantaría presentarte mi propuesta de música en vivo$place. " +
                    "¿Te puedo enviar mi portafolio y tarifas, o coordinamos una breve conversación?"

            CrmStage.ACTIVO ->
                "$greeting gracias por la confianza en mi música$place. " +
                    "Quedo atento a tus próximas fechas para ir reservando mi agenda. ¡Un saludo!"

            CrmStage.DORMIDO, CrmStage.INACTIVO -> {
                val last = account.lastGigDate?.let { " (mi última presentación fue el ${formatDate(it)})" }.orEmpty()
                "$greeting espero que estés muy bien. Hace un tiempo que no coincidimos$place$last. " +
                    "Quería saludarte y ponerme a tu disposición para los próximos eventos y la temporada. " +
                    "¿Tienes alguna fecha en la que pueda apoyarte? ¡Saludos!"
            }
        }
    }

    private fun formatDate(date: LocalDate): String =
        "${date.dayOfMonth} de ${DominicanFormatter.formatMonthNameLower(date.month)} de ${date.year}"
}
