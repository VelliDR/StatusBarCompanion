package com.vellli.statusbarcompanion.model

import com.google.gson.Gson
import java.util.UUID

/**
 * Represents a layered theme containing multiple OverlayElements.
 */
data class BarTheme(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    var elements: MutableList<OverlayElement> = mutableListOf(),
    var isActive: Boolean = false
) {
    fun serialize(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun deserialize(data: String): BarTheme? {
            return try {
                Gson().fromJson(data, BarTheme::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
