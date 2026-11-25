package com.example.domain.rules

import java.text.Normalizer
import java.util.Locale

/**
 * Rule 2.3 #2: Name normalization and canonical display selection.
 *
 * - Remove accents, lowercase, drop leading "hotel ", remove non-alphanumerics.
 * - For venues, a key of at least 5 characters that is a prefix of a longer key joins it.
 * - Display the most frequent spelling; on ties, prefer the longer and accented one.
 */
object NameNormalizer {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]")
    private val LEADING_HOTEL_REGEX = Regex("^hotel\\s+")

    fun normalizeKey(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val withoutAccents = stripAccents(trimmed).lowercase(Locale.ROOT)
        val withoutHotel = LEADING_HOTEL_REGEX.replace(withoutAccents, "")
        return NON_ALNUM_REGEX.replace(withoutHotel, "")
    }

    /**
     * Resolves a list of raw venue names into unified canonical keys (prefix merging for keys >= 5 chars)
     * and chooses the preferred display name per canonical key.
     */
    fun buildVenueResolution(rawNames: Iterable<String>): NameResolution {
        val trimmedList = rawNames.map { it.trim() }.filter { it.isNotEmpty() }
        val baseKeyToRaw = linkedMapOf<String, MutableList<String>>()
        for (raw in trimmedList) {
            val baseKey = normalizeKey(raw)
            if (baseKey.isEmpty()) continue
            baseKeyToRaw.getOrPut(baseKey) { mutableListOf() }.add(raw)
        }

        val allKeys = baseKeyToRaw.keys.sortedByDescending { it.length }
        val mergedKeyMap = linkedMapOf<String, String>()
        for (key in allKeys) {
            val target = if (key.length >= 5) {
                allKeys.firstOrNull { longer ->
                    longer.length > key.length && longer.startsWith(key)
                } ?: key
            } else {
                key
            }
            mergedKeyMap[key] = target
        }

        // Flatten transitive prefix chains (e.g., A -> B -> C becomes A -> C)
        fun resolveFinal(k: String): String {
            var current = k
            val visited = hashSetOf<String>()
            while (mergedKeyMap[current] != null && mergedKeyMap[current] != current && visited.add(current)) {
                current = mergedKeyMap[current]!!
            }
            return current
        }

        val finalBaseToCanonical = linkedMapOf<String, String>()
        val canonicalToSpellings = linkedMapOf<String, MutableList<String>>()
        for ((baseKey, raws) in baseKeyToRaw) {
            val canonical = resolveFinal(baseKey)
            finalBaseToCanonical[baseKey] = canonical
            canonicalToSpellings.getOrPut(canonical) { mutableListOf() }.addAll(raws)
        }

        val canonicalDisplayNames = linkedMapOf<String, String>()
        val mergedVariantsPerCanonical = linkedMapOf<String, Int>()
        for ((canonical, spellings) in canonicalToSpellings) {
            canonicalDisplayNames[canonical] = pickPreferredSpelling(spellings)
            val distinctSpellings = spellings.toSet().size
            mergedVariantsPerCanonical[canonical] = (distinctSpellings - 1).coerceAtLeast(0)
        }

        return NameResolution(
            baseKeyToCanonical = finalBaseToCanonical,
            canonicalToDisplay = canonicalDisplayNames,
            mergedVariantsPerCanonical = mergedVariantsPerCanonical,
        )
    }

    /**
     * Resolves supplier names without prefix merging (exact normalized key match:
     * "José David" and "Jose David" share the same key "josedavid").
     */
    fun buildSupplierResolution(rawNames: Iterable<String>): NameResolution {
        val canonicalToSpellings = linkedMapOf<String, MutableList<String>>()
        val baseKeyToCanonical = linkedMapOf<String, String>()
        for (raw in rawNames.map { it.trim() }.filter { it.isNotEmpty() }) {
            val key = normalizeKey(raw)
            if (key.isEmpty()) continue
            baseKeyToCanonical[key] = key
            canonicalToSpellings.getOrPut(key) { mutableListOf() }.add(raw)
        }

        val canonicalDisplayNames = linkedMapOf<String, String>()
        val mergedVariantsPerCanonical = linkedMapOf<String, Int>()
        for ((canonical, spellings) in canonicalToSpellings) {
            canonicalDisplayNames[canonical] = pickPreferredSpelling(spellings)
            mergedVariantsPerCanonical[canonical] = (spellings.toSet().size - 1).coerceAtLeast(0)
        }

        return NameResolution(
            baseKeyToCanonical = baseKeyToCanonical,
            canonicalToDisplay = canonicalDisplayNames,
            mergedVariantsPerCanonical = mergedVariantsPerCanonical,
        )
    }

    fun pickPreferredSpelling(spellings: List<String>): String {
        val counts = spellings.groupingBy { it }.eachCount()
        return counts.entries.maxWith(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key.length }
                .thenBy { countAccentedChars(it.key) }
                .thenByDescending { it.key }
        ).key
    }

    private fun stripAccents(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(normalized, "")
    }

    private fun countAccentedChars(input: String): Int {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.findAll(normalized).count()
    }
}

data class NameResolution(
    val baseKeyToCanonical: Map<String, String>,
    val canonicalToDisplay: Map<String, String>,
    val mergedVariantsPerCanonical: Map<String, Int>,
) {
    fun canonicalKey(raw: String?): String {
        val base = NameNormalizer.normalizeKey(raw)
        if (base.isEmpty()) return ""
        return baseKeyToCanonical[base] ?: base
    }

    fun displayName(raw: String?): String {
        val key = canonicalKey(raw)
        if (key.isEmpty()) return raw?.trim().orEmpty()
        return canonicalToDisplay[key] ?: raw?.trim().orEmpty()
    }

    val totalMergedVariants: Int
        get() = mergedVariantsPerCanonical.values.sum()
}
