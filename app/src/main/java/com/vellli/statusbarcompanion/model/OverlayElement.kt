package com.vellli.statusbarcompanion.model

import java.util.UUID

/**
 * Represents a single layer (image or GIF) in a BarTheme.
 */
data class OverlayElement(
    val id: String = UUID.randomUUID().toString(),
    val idleImagePath: String,
    val chargingImagePath: String? = null,
    val lowBatteryImagePath: String? = null,
    var offsetX: Int = 0,
    var offsetY: Int = 0,
    var scale: Float = 1.0f
)
