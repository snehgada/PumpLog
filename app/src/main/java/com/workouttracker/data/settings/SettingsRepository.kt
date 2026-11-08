package com.workouttracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.workouttracker.domain.Increments
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {

    private val preferences: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val weightUnit: Flow<WeightUnit> = preferences.map { prefs ->
        WeightUnit.entries.firstOrNull { it.name == prefs[WEIGHT_UNIT] } ?: WeightUnit.KG
    }

    val increments: Flow<Increments> = preferences.map { prefs ->
        Increments(
            barbellGrams = prefs[BARBELL_INCREMENT]?.takeIf { it in INCREMENT_RANGE } ?: DEFAULT_BARBELL_INCREMENT,
            dumbbellGrams = prefs[DUMBBELL_INCREMENT]?.takeIf { it in INCREMENT_RANGE } ?: DEFAULT_DUMBBELL_INCREMENT,
        )
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        store.edit { it[WEIGHT_UNIT] = unit.name }
    }

    suspend fun setIncrements(increments: Increments) {
        require(increments.barbellGrams in INCREMENT_RANGE && increments.dumbbellGrams in INCREMENT_RANGE) {
            "increments must be between 0.25 kg and 20 kg"
        }
        store.edit {
            it[BARBELL_INCREMENT] = increments.barbellGrams
            it[DUMBBELL_INCREMENT] = increments.dumbbellGrams
        }
    }

    /** Whether the First launch screen has been dismissed. */
    suspend fun isWelcomeDone(): Boolean = preferences.first()[WELCOME_DONE] ?: false

    suspend fun markWelcomeDone() {
        store.edit { it[WELCOME_DONE] = true }
    }

    suspend fun isSeeded(): Boolean = preferences.first()[ROUTINES_SEEDED] ?: false

    suspend fun markSeeded() {
        store.edit { it[ROUTINES_SEEDED] = true }
    }

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val BARBELL_INCREMENT = intPreferencesKey("barbell_increment_grams")
        val DUMBBELL_INCREMENT = intPreferencesKey("dumbbell_increment_grams")
        val ROUTINES_SEEDED = booleanPreferencesKey("routines_seeded")
        val WELCOME_DONE = booleanPreferencesKey("welcome_done")

        const val DEFAULT_BARBELL_INCREMENT = 2_500
        const val DEFAULT_DUMBBELL_INCREMENT = 2_000
        val INCREMENT_RANGE = 250..20_000
    }
}
