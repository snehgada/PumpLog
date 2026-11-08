package com.workouttracker.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Weights are stored as whole grams so 27.5 kg never drifts to 27.4999.
 * Unit conversion happens only at the edges: parsing what the user typed and formatting for display.
 */
object Weights {
    const val GRAMS_PER_KG = 1000
    const val GRAMS_PER_LB = 453.59237

    /** Heaviest weight accepted for a single set: 1,000 kg. */
    const val MAX_GRAMS = 1_000_000

    private const val MAX_INPUT_LENGTH = 9
    private const val MAX_DECIMALS = 2

    /**
     * Parses a typed weight such as "27.5" or "27,5" into grams.
     * Returns null for anything that is not a plain non-negative decimal within range.
     */
    fun parse(input: String, unit: WeightUnit): Int? {
        val text = input.trim().replace(',', '.')
        if (text.isEmpty() || text.length > MAX_INPUT_LENGTH) return null

        var dots = 0
        for (c in text) {
            when {
                c == '.' -> dots++
                c !in '0'..'9' -> return null
            }
        }
        if (dots > 1 || text == ".") return null
        val decimals = text.substringAfter('.', "").length
        if (decimals > MAX_DECIMALS) return null

        val value = text.toBigDecimalOrNull() ?: return null
        val grams = when (unit) {
            WeightUnit.KG -> value.multiply(BigDecimal(GRAMS_PER_KG))
            WeightUnit.LB -> value.multiply(BigDecimal.valueOf(GRAMS_PER_LB))
        }.setScale(0, RoundingMode.HALF_UP)

        if (grams > BigDecimal(MAX_GRAMS)) return null
        return grams.toInt()
    }

    /** "27.5", "30", "100" — no trailing zeros. kg keeps up to 2 decimals, lb rounds to 1. */
    fun format(grams: Int, unit: WeightUnit): String {
        val value = when (unit) {
            WeightUnit.KG -> BigDecimal(grams).divide(BigDecimal(GRAMS_PER_KG)).setScale(2, RoundingMode.HALF_UP)
            WeightUnit.LB -> BigDecimal(grams / GRAMS_PER_LB).setScale(1, RoundingMode.HALF_UP)
        }
        return value.stripTrailingZeros().toPlainString()
    }
}

object Reps {
    const val MAX = 200
    private const val MAX_INPUT_LENGTH = 3

    /** Parses typed reps; 0 is allowed (a failed attempt). */
    fun parse(input: String): Int? {
        val text = input.trim()
        if (text.isEmpty() || text.length > MAX_INPUT_LENGTH) return null
        if (text.any { it !in '0'..'9' }) return null
        return text.toInt().takeIf { it in 0..MAX }
    }
}
