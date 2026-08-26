package com.vellli.statusbarcompanion.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.vellli.statusbarcompanion.R
import com.vellli.statusbarcompanion.model.BarTheme
import java.io.File

/**
 * RecyclerView adapter for displaying the character list.
 * Uses ListAdapter with DiffUtil for efficient updates.
 */
class CharacterAdapter(
    private val onItemClick: (BarTheme) -> Unit,
    private val onDeleteClick: (BarTheme) -> Unit,
    private val onExportClick: (BarTheme) -> Unit,
    private val onToggleActive: (BarTheme, Boolean) -> Unit
) : ListAdapter<BarTheme, CharacterAdapter.CharacterViewHolder>(CharacterDiffCallback()) {

    private var activeCharacterIds: Set<String> = emptySet()

    fun setActiveCharacterIds(ids: Set<String>) {
        val oldIds = activeCharacterIds
        activeCharacterIds = ids
        // Refresh changed items
        currentList.forEachIndexed { index, character ->
            val wasActive = oldIds.contains(character.id)
            val isActive = ids.contains(character.id)
            if (wasActive != isActive) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_character, parent, false)
        return CharacterViewHolder(view)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        val character = getItem(position)
        holder.bind(character, activeCharacterIds.contains(character.id))
    }

    inner class CharacterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCharacter: ImageView = itemView.findViewById(R.id.img_character)
        private val txtName: TextView = itemView.findViewById(R.id.txt_character_name)
        private val txtStatus: TextView = itemView.findViewById(R.id.txt_character_status)
        private val checkboxActive: CheckBox = itemView.findViewById(R.id.checkbox_active)
        private val btnExport: ImageView = itemView.findViewById(R.id.btn_export)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)

        fun bind(theme: BarTheme, isActive: Boolean) {
            txtName.text = theme.name

            // Build status text
            val statusParts = mutableListOf<String>()
            statusParts.add("Elements: ${theme.elements.size}")
            val hasCharging = theme.elements.any { it.chargingImagePath != null }
            val hasLowBattery = theme.elements.any { it.lowBatteryImagePath != null }
            if (hasCharging) statusParts.add("⚡ Charging")
            if (hasLowBattery) statusParts.add("🪫 Low Battery")
            val hasNight = theme.elements.any { it.nightImagePath != null }
            if (hasNight) statusParts.add("🌙 Night")
            txtStatus.text = statusParts.joinToString(" · ")

            // Checkbox handler
            checkboxActive.setOnCheckedChangeListener(null) // Remove previous listener to avoid triggering it
            checkboxActive.isChecked = isActive
            checkboxActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(theme, isChecked)
            }

            // Load thumbnail via Coil (using first element)
            val idleFile = theme.elements.firstOrNull()?.idleImagePath?.let { File(it) }
            if (idleFile != null && idleFile.exists()) {
                imgCharacter.load(idleFile) {
                    crossfade(true)
                    size(96, 96)
                }
            } else {
                imgCharacter.setImageResource(R.drawable.ic_image)
            }

            // Click handlers
            itemView.setOnClickListener { onItemClick(theme) }
            btnExport.setOnClickListener { onExportClick(theme) }
            btnDelete.setOnClickListener { onDeleteClick(theme) }
        }
    }

    private class CharacterDiffCallback : DiffUtil.ItemCallback<BarTheme>() {
        override fun areItemsTheSame(old: BarTheme, new: BarTheme): Boolean {
            return old.id == new.id
        }

        override fun areContentsTheSame(old: BarTheme, new: BarTheme): Boolean {
            return old == new
        }
    }
}
