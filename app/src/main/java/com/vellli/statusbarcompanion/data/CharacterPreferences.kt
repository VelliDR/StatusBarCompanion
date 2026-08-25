package com.vellli.statusbarcompanion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vellli.statusbarcompanion.model.BarTheme
import com.vellli.statusbarcompanion.model.OverlayElement
import com.vellli.statusbarcompanion.model.CustomCharacter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Singleton DataStore instance via extension property */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "character_preferences"
)

/**
 * Manages persistence of character data and app settings using DataStore.
 *
 * Characters are stored as serialized strings in a StringSet.
 * App settings (auto-start, service state) are stored as individual keys.
 */
object CharacterPreferences {

    // Keys
    private val KEY_CHARACTERS = stringSetPreferencesKey("characters")
    private val KEY_ACTIVE_CHARACTER_ID = stringPreferencesKey("active_character_id")
    private val KEY_AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
    private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")

    // ─── Character CRUD ────────────────────────────────────────────────

    /**
     * Observe all characters as a Flow.
     */
    fun observeCharacters(context: Context): Flow<List<BarTheme>> {
        return context.dataStore.data.map { prefs ->
            val serializedSet = prefs[KEY_CHARACTERS] ?: emptySet()
            serializedSet.mapNotNull { data ->
                if (data.contains("\u001F")) {
                    // Legacy CustomCharacter migration
                    CustomCharacter.deserialize(data)?.let { old ->
                        BarTheme(
                            id = old.id,
                            name = old.name,
                            isActive = old.isActive,
                            elements = mutableListOf(
                                OverlayElement(
                                    idleImagePath = old.idleImagePath,
                                    chargingImagePath = old.chargingImagePath,
                                    lowBatteryImagePath = old.lowBatteryImagePath,
                                    offsetX = old.offsetX,
                                    offsetY = old.offsetY,
                                    scale = old.scale
                                )
                            )
                        )
                    }
                } else {
                    BarTheme.deserialize(data)
                }
            }.sortedBy { it.name.lowercase() }
        }
    }

    /**
     * Save or update a character.
     */
    suspend fun saveCharacter(context: Context, theme: BarTheme) {
        context.dataStore.edit { prefs ->
            val currentSet = prefs[KEY_CHARACTERS]?.toMutableSet() ?: mutableSetOf()
            // Remove old version if exists (by ID)
            currentSet.removeAll { serialized ->
                if (serialized.contains("\u001F")) {
                    CustomCharacter.deserialize(serialized)?.id == theme.id
                } else {
                    BarTheme.deserialize(serialized)?.id == theme.id
                }
            }
            currentSet.add(theme.serialize())
            prefs[KEY_CHARACTERS] = currentSet
        }
    }

    /**
     * Delete a character by ID.
     */
    suspend fun deleteCharacter(context: Context, characterId: String) {
        context.dataStore.edit { prefs ->
            val currentSet = prefs[KEY_CHARACTERS]?.toMutableSet() ?: mutableSetOf()
            currentSet.removeAll { serialized ->
                if (serialized.contains("\u001F")) {
                    CustomCharacter.deserialize(serialized)?.id == characterId
                } else {
                    BarTheme.deserialize(serialized)?.id == characterId
                }
            }
            prefs[KEY_CHARACTERS] = currentSet

            // Clear active if it was the deleted one
            if (prefs[KEY_ACTIVE_CHARACTER_ID] == characterId) {
                prefs.remove(KEY_ACTIVE_CHARACTER_ID)
            }
        }
    }

    /**
     * Get a character by ID (one-shot read).
     */
    suspend fun getCharacter(context: Context, characterId: String): BarTheme? {
        val prefs = context.dataStore.data.first()
        val serializedSet = prefs[KEY_CHARACTERS] ?: emptySet()
        return serializedSet.mapNotNull { data ->
            if (data.contains("\u001F")) {
                CustomCharacter.deserialize(data)?.let { old ->
                    BarTheme(
                        id = old.id,
                        name = old.name,
                        isActive = old.isActive,
                        elements = mutableListOf(
                            OverlayElement(
                                idleImagePath = old.idleImagePath,
                                chargingImagePath = old.chargingImagePath,
                                lowBatteryImagePath = old.lowBatteryImagePath,
                                offsetX = old.offsetX,
                                offsetY = old.offsetY,
                                scale = old.scale
                            )
                        )
                    )
                }
            } else {
                BarTheme.deserialize(data)
            }
        }.find { it.id == characterId }
    }

    // ─── Active Character ──────────────────────────────────────────────

    /**
     * Set the active character that the overlay will display.
     */
    suspend fun setActiveCharacter(context: Context, characterId: String?) {
        context.dataStore.edit { prefs ->
            if (characterId != null) {
                prefs[KEY_ACTIVE_CHARACTER_ID] = characterId
            } else {
                prefs.remove(KEY_ACTIVE_CHARACTER_ID)
            }
        }
    }

    /**
     * Observe the active character as a Flow.
     */
    fun observeActiveCharacter(context: Context): Flow<BarTheme?> {
        return context.dataStore.data.map { prefs ->
            val activeId = prefs[KEY_ACTIVE_CHARACTER_ID] ?: return@map null
            val serializedSet = prefs[KEY_CHARACTERS] ?: emptySet()
            serializedSet.mapNotNull { data ->
                if (data.contains("\u001F")) {
                    CustomCharacter.deserialize(data)?.let { old ->
                        BarTheme(
                            id = old.id,
                            name = old.name,
                            isActive = old.isActive,
                            elements = mutableListOf(
                                OverlayElement(
                                    idleImagePath = old.idleImagePath,
                                    chargingImagePath = old.chargingImagePath,
                                    lowBatteryImagePath = old.lowBatteryImagePath,
                                    offsetX = old.offsetX,
                                    offsetY = old.offsetY,
                                    scale = old.scale
                                )
                            )
                        )
                    }
                } else {
                    BarTheme.deserialize(data)
                }
            }.find { it.id == activeId }
        }
    }

    /**
     * Get active character (one-shot read).
     */
    suspend fun getActiveCharacter(context: Context): BarTheme? {
        return observeActiveCharacter(context).first()
    }

    // ─── App Settings ──────────────────────────────────────────────────

    /**
     * Observe auto-start on boot preference.
     */
    fun observeAutoStartOnBoot(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_AUTO_START_ON_BOOT] ?: false
        }
    }

    /**
     * Set auto-start on boot.
     */
    suspend fun setAutoStartOnBoot(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_START_ON_BOOT] = enabled
        }
    }

    /**
     * Get auto-start on boot (one-shot read).
     */
    suspend fun isAutoStartOnBoot(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_AUTO_START_ON_BOOT] ?: false
    }

    /**
     * Observe service enabled state.
     */
    fun observeServiceEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_SERVICE_ENABLED] ?: false
        }
    }

    /**
     * Get service enabled state (one-shot read).
     */
    suspend fun isServiceEnabled(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_SERVICE_ENABLED] ?: false
    }

    /**
     * Set service enabled state.
     */
    suspend fun setServiceEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVICE_ENABLED] = enabled
        }
    }
}
