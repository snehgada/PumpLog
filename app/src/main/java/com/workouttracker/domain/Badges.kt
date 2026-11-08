package com.workouttracker.domain

object Badges {

    /**
     * Short labels for routine badges, unique within [names] where possible:
     * a single initial if no other routine shares it (Upper → "U"),
     * otherwise the initial plus the first later letter that makes it unique (Push → "Pu", Pull → "Pl").
     */
    fun labels(names: List<String>): List<String> {
        val initials = names.map { it.trim().firstOrNull()?.uppercaseChar() }
        val used = mutableSetOf<String>()
        return names.mapIndexed { index, raw ->
            val name = raw.trim()
            val initial = initials[index] ?: return@mapIndexed ""
            val label = if (initials.count { it == initial } == 1) {
                initial.toString()
            } else {
                name.drop(1).asSequence()
                    .filter { it.isLetterOrDigit() }
                    .map { "$initial${it.lowercaseChar()}" }
                    .firstOrNull { it !in used }
                    ?: initial.toString()
            }
            used += label
            label
        }
    }
}
