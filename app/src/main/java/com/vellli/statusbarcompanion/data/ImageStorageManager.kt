package com.vellli.statusbarcompanion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.vellli.statusbarcompanion.model.BarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages local image storage for character assets.
 *
 * All imported images are downsampled to a maximum of 128×128 pixels
 * to ensure zero RAM bloat, then saved as WebP (lossy, quality 90)
 * into context.filesDir/characters/{characterId}/.
 *
 * GIF files are copied as-is since they contain animation frames
 * that cannot be represented as a single bitmap.
 */
object ImageStorageManager {

    private const val MAX_DIMENSION = 128
    private const val CHARACTERS_DIR = "characters"
    private const val WEBP_QUALITY = 90

    /**
     * Import an image from a content URI, downsample it, and save locally.
     *
     * @param context Application context
     * @param uri Source content URI from Photo Picker
     * @param characterId Unique character identifier for directory naming
     * @param imageType Subfolder name (e.g., "idle", "charging", "low_battery")
     * @return Absolute path to the saved file, or null on failure
     */
    fun importImage(
        context: Context,
        uri: Uri,
        characterId: String,
        imageType: String
    ): String? {
        return try {
            val targetDir = getCharacterDir(context, characterId)
            if (!targetDir.exists()) targetDir.mkdirs()

            val mimeType = context.contentResolver.getType(uri)
            val isGif = mimeType?.contains("gif", ignoreCase = true) == true

            if (isGif) {
                // GIF: copy as-is to preserve animation frames
                val targetFile = File(targetDir, "${imageType}.gif")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output, bufferSize = 4096)
                    }
                }
                targetFile.absolutePath
            } else {
                // Static image: downsample to 128×128 max, save as WebP
                val bitmap = decodeSampledBitmap(context, uri) ?: return null
                val targetFile = File(targetDir, "${imageType}.webp")
                FileOutputStream(targetFile).use { output ->
                    bitmap.compress(
                        Bitmap.CompressFormat.WEBP_LOSSY,
                        WEBP_QUALITY,
                        output
                    )
                }
                bitmap.recycle()
                targetFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Delete all stored assets for a character.
     */
    fun deleteCharacterAssets(context: Context, characterId: String) {
        val dir = getCharacterDir(context, characterId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    /**
     * Check if an image file exists at the given path.
     */
    fun imageExists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return File(path).exists()
    }

    /**
     * Export a BarTheme to a .sbc zip file at the given SAF Uri.
     */
    suspend fun exportTheme(context: Context, theme: BarTheme, outputUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(outputUri)?.use { fos ->
                    ZipOutputStream(fos).use { zos ->
                        // 1. Write the theme.json
                        val jsonEntry = ZipEntry("theme.json")
                        zos.putNextEntry(jsonEntry)
                        zos.write(theme.serialize().toByteArray(Charsets.UTF_8))
                        zos.closeEntry()

                        // 2. Write all referenced images
                        val allPaths = mutableSetOf<String>()
                        theme.elements.forEach { el ->
                            el.idleImagePath.let { allPaths.add(it) }
                            el.chargingImagePath?.let { allPaths.add(it) }
                            el.lowBatteryImagePath?.let { allPaths.add(it) }
                            el.nightImagePath?.let { allPaths.add(it) }
                        }

                        allPaths.forEach { absPath ->
                            val file = File(absPath)
                            if (file.exists()) {
                                val fileEntry = ZipEntry(file.name)
                                zos.putNextEntry(fileEntry)
                                file.inputStream().use { fis ->
                                    fis.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Import a BarTheme from a .sbc zip file at the given SAF Uri.
     * Zip Slip vulnerability is mitigated by checking entry names.
     */
    suspend fun importTheme(context: Context, inputUri: Uri): BarTheme? {
        return withContext(Dispatchers.IO) {
            try {
                var importedTheme: BarTheme? = null
                val extractedFiles = mutableMapOf<String, File>()
                
                // We will create a temporary random directory to avoid ID collisions during extraction
                val tempId = java.util.UUID.randomUUID().toString()
                val targetDir = getCharacterDir(context, tempId)
                if (!targetDir.exists()) targetDir.mkdirs()

                context.contentResolver.openInputStream(inputUri)?.use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            // Zip Slip protection: ensure entry name doesn't contain path traversal
                            val entryName = entry.name
                            if (entryName.contains("..") || entryName.contains("/")) {
                                zis.closeEntry()
                                entry = zis.nextEntry
                                continue
                            }

                            if (entryName == "theme.json") {
                                val jsonString = zis.readBytes().toString(Charsets.UTF_8)
                                importedTheme = BarTheme.deserialize(jsonString)
                            } else if (entryName.endsWith(".webp") || entryName.endsWith(".gif") || entryName.endsWith(".png") || entryName.endsWith(".jpg")) {
                                val outFile = File(targetDir, entryName)
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                extractedFiles[entryName] = outFile
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                if (importedTheme == null) {
                    targetDir.deleteRecursively()
                    return@withContext null
                }

                // Give the theme a new ID so it doesn't collide with existing themes
                val finalTheme = importedTheme!!.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "${importedTheme!!.name} (Imported)",
                    isActive = false
                )
                
                // Rename the temp directory to the new final ID
                val finalDir = getCharacterDir(context, finalTheme.id)
                targetDir.renameTo(finalDir)

                // Update paths in the elements
                finalTheme.elements.forEach { el ->
                    // Function to map the old absolute path to the newly extracted file path
                    fun remapPath(oldPath: String?): String? {
                        if (oldPath.isNullOrBlank()) return null
                        val fileName = File(oldPath).name
                        return if (extractedFiles.containsKey(fileName)) {
                            File(finalDir, fileName).absolutePath
                        } else null
                    }
                    
                    // We must update the values, but they are val in OverlayElement.
                    // Wait, idleImagePath is val. We need to recreate the element.
                }

                // Re-map elements because paths are vals
                finalTheme.elements = finalTheme.elements.map { el ->
                    fun remapPath(oldPath: String?): String? {
                        if (oldPath.isNullOrBlank()) return null
                        val fileName = File(oldPath).name
                        return if (extractedFiles.containsKey(fileName)) {
                            File(finalDir, fileName).absolutePath
                        } else null
                    }
                    
                    val newIdle = remapPath(el.idleImagePath) ?: el.idleImagePath
                    
                    el.copy(
                        idleImagePath = newIdle,
                        chargingImagePath = remapPath(el.chargingImagePath),
                        lowBatteryImagePath = remapPath(el.lowBatteryImagePath),
                        nightImagePath = remapPath(el.nightImagePath)
                    )
                }.toMutableList()

                // Save to DataStore
                CharacterPreferences.saveCharacter(context, finalTheme)
                finalTheme

            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Get the character-specific storage directory.
     */
    private fun getCharacterDir(context: Context, characterId: String): File {
        return File(context.filesDir, "$CHARACTERS_DIR/$characterId")
    }

    /**
     * Decode a bitmap from URI with inSampleSize calculation to ensure
     * the resulting bitmap fits within MAX_DIMENSION × MAX_DIMENSION.
     *
     * Uses two-pass decoding:
     * 1. First pass: decode bounds only (zero memory allocation)
     * 2. Second pass: decode with calculated inSampleSize
     * 3. Final exact resize if still larger than MAX_DIMENSION
     */
    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        // Pass 1: Get dimensions without loading pixels
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        // Calculate inSampleSize (power of 2)
        options.inSampleSize = calculateInSampleSize(
            options.outWidth,
            options.outHeight,
            MAX_DIMENSION,
            MAX_DIMENSION
        )

        // Pass 2: Decode with downsampling
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        // Final resize to exactly fit within MAX_DIMENSION if still larger
        return if (rawBitmap.width > MAX_DIMENSION || rawBitmap.height > MAX_DIMENSION) {
            val ratio = minOf(
                MAX_DIMENSION.toFloat() / rawBitmap.width,
                MAX_DIMENSION.toFloat() / rawBitmap.height
            )
            val targetWidth = (rawBitmap.width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (rawBitmap.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
            if (scaled !== rawBitmap) rawBitmap.recycle()
            scaled
        } else {
            rawBitmap
        }
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2
     * and keeps both dimensions >= the requested dimensions.
     */
    private fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
