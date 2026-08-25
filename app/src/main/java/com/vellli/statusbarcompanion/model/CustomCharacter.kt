package com.vellli.statusbarcompanion.model

import java.util.UUID

/**
 * Represents a custom character that can be displayed on the status bar overlay.
 * All image paths are absolute paths under context.filesDir/characters/{id}/
 */
data class CustomCharacter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val idleImagePath: String,
    val chargingImagePath: String? = null,
    val lowBatteryImagePath: String? = null,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val scale: Float = 1.0f,
    val isActive: Boolean = false
) {
    /**
     * Serialize to a compact string for DataStore storage.
     * Format: id§name§idlePath§chargingPath§lowBattPath§offsetX§offsetY§scale§isActive
     */
    fun serialize(): String {
        return listOf(
            id,
            name,
            idleImagePath,
            chargingImagePath ?: "",
            lowBatteryImagePath ?: "",
            offsetX.toString(),
            offsetY.toString(),
            scale.toString(),
            isActive.toString()
        ).joinToString(DELIMITER)
    }

    companion object {
        private const val DELIMITER = "\u001F" // Unit Separator — safe for file paths

        /**
         * Deserialize from the compact string format.
         * Returns null if the format is invalid.
         */
        fun deserialize(data: String): CustomCharacter? {
            val parts = data.split(DELIMITER)
            if (parts.size < 9) return null
            return try {
                CustomCharacter(
                    id = parts[0],
                    name = parts[1],
                    idleImagePath = parts[2],
                    chargingImagePath = parts[3].ifEmpty { null },
                    lowBatteryImagePath = parts[4].ifEmpty { null },
                    offsetX = parts[5].toIntOrNull() ?: 0,
                    offsetY = parts[6].toIntOrNull() ?: 0,
                    scale = parts[7].toFloatOrNull() ?: 1.0f,
                    isActive = parts[8].toBooleanStrictOrNull() ?: false
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
