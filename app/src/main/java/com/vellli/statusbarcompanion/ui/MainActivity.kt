package com.vellli.statusbarcompanion.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.vellli.statusbarcompanion.R
import com.vellli.statusbarcompanion.data.CharacterPreferences
import com.vellli.statusbarcompanion.data.ImageStorageManager
import com.vellli.statusbarcompanion.model.BarTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main dashboard activity.
 *
 * Features:
 * - Overlay permission check and grant flow
 * - Service start/stop toggle
 * - Auto-start on boot checkbox
 * - Character list with tap-to-activate and delete
 * - FAB to open Character Studio
 */
class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var permissionCard: LinearLayout
    private lateinit var switchService: SwitchMaterial
    private lateinit var checkboxAutoStart: CheckBox
    private lateinit var recyclerCharacters: RecyclerView
    private lateinit var labelEmpty: TextView
    private lateinit var btnAddCharacter: Button
    private lateinit var btnImportCharacter: Button
    private lateinit var btnGrantPermission: Button

    // Adapter
    private lateinit var characterAdapter: CharacterAdapter

    // Permission launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionState()
    }

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No action needed, just request */ }

    // Export launcher
    private var pendingExportTheme: BarTheme? = null
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            pendingExportTheme?.let { theme ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val success = ImageStorageManager.exportTheme(applicationContext, theme, it)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(this@MainActivity, "Exported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Import launcher
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                val theme = ImageStorageManager.importTheme(applicationContext, it)
                withContext(Dispatchers.Main) {
                    if (theme != null) {
                        CharacterPreferences.saveCharacter(applicationContext, theme)
                        Toast.makeText(this@MainActivity, "Imported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Import failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupRecyclerView()
        setupListeners()
        requestNotificationPermissionIfNeeded()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    // ─── View Binding ──────────────────────────────────────────────────

    private fun bindViews() {
        permissionCard = findViewById(R.id.permission_card)
        switchService = findViewById(R.id.switch_service)
        checkboxAutoStart = findViewById(R.id.checkbox_auto_start)
        recyclerCharacters = findViewById(R.id.recycler_characters)
        labelEmpty = findViewById(R.id.label_empty)
        btnAddCharacter = findViewById(R.id.btn_add_character)
        btnImportCharacter = findViewById(R.id.btn_import_character)
        btnGrantPermission = findViewById(R.id.btn_grant_permission)
    }

    private fun setupRecyclerView() {
        characterAdapter = CharacterAdapter(
            onItemClick = { character -> onCharacterTapped(character) },
            onDeleteClick = { character -> onCharacterDelete(character) },
            onExportClick = { character -> onCharacterExport(character) },
            onToggleActive = { character, isActive -> onToggleActive(character, isActive) }
        )
        recyclerCharacters.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = characterAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        // Service toggle
        switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startOverlayService()
            } else {
                stopOverlayService()
            }
            lifecycleScope.launch {
                CharacterPreferences.setServiceEnabled(applicationContext, isChecked)
            }
        }

        // Auto-start on boot
        checkboxAutoStart.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                CharacterPreferences.setAutoStartOnBoot(applicationContext, isChecked)
            }
        }

        // Add character
        btnAddCharacter.setOnClickListener {
            val intent = Intent(this, CharacterStudioActivity::class.java)
            startActivity(intent)
        }

        // Import character
        btnImportCharacter.setOnClickListener {
            importLauncher.launch("application/zip")
        }

        // Grant overlay permission
        btnGrantPermission.setOnClickListener {
            requestAccessibilityPermission()
        }
    }

    // ─── Data Observation ──────────────────────────────────────────────

    private fun observeData() {
        lifecycleScope.launch {
            // Combine characters list with active character IDs
            combine(
                CharacterPreferences.observeCharacters(applicationContext),
                CharacterPreferences.observeActiveCharacterIds(applicationContext),
                CharacterPreferences.observeServiceEnabled(applicationContext),
                CharacterPreferences.observeAutoStartOnBoot(applicationContext)
            ) { characters, activeIds, serviceEnabled, autoStart ->
                DataState(characters, activeIds, serviceEnabled, autoStart)
            }.collectLatest { state ->
                // Update character list
                characterAdapter.submitList(state.characters)
                characterAdapter.setActiveCharacterIds(state.activeIds)

                // Show/hide empty state
                if (state.characters.isEmpty()) {
                    labelEmpty.visibility = View.VISIBLE
                    recyclerCharacters.visibility = View.GONE
                } else {
                    labelEmpty.visibility = View.GONE
                    recyclerCharacters.visibility = View.VISIBLE
                }

                // Sync switch without triggering listener
                switchService.setOnCheckedChangeListener(null)
                switchService.isChecked = state.serviceEnabled
                switchService.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) startOverlayService() else stopOverlayService()
                    lifecycleScope.launch {
                        CharacterPreferences.setServiceEnabled(applicationContext, isChecked)
                    }
                }

                // Sync checkbox without triggering listener
                checkboxAutoStart.setOnCheckedChangeListener(null)
                checkboxAutoStart.isChecked = state.autoStart
                checkboxAutoStart.setOnCheckedChangeListener { _, isChecked ->
                    lifecycleScope.launch {
                        CharacterPreferences.setAutoStartOnBoot(applicationContext, isChecked)
                    }
                }
            }
        }
    }

    private data class DataState(
        val characters: List<BarTheme>,
        val activeIds: Set<String>,
        val serviceEnabled: Boolean,
        val autoStart: Boolean
    )

    // ─── Character Actions ─────────────────────────────────────────────

    private fun onCharacterTapped(character: BarTheme) {
        editCharacter(character)
    }

    private fun onToggleActive(character: BarTheme, isActive: Boolean) {
        lifecycleScope.launch {
            if (isActive) {
                CharacterPreferences.addActiveCharacter(applicationContext, character.id)
            } else {
                CharacterPreferences.removeActiveCharacter(applicationContext, character.id)
            }
            // Restart overlay to apply
            if (switchService.isChecked) {
                startOverlayService()
            }
        }
    }

    private fun onCharacterExport(character: BarTheme) {
        pendingExportTheme = character
        exportLauncher.launch("${character.name}.zip")
    }

    private fun editCharacter(character: BarTheme) {
        val intent = Intent(this, CharacterStudioActivity::class.java).apply {
            putExtra(CharacterStudioActivity.EXTRA_CHARACTER_ID, character.id)
        }
        startActivity(intent)
    }

    private fun onCharacterDelete(character: BarTheme) {
        AlertDialog.Builder(this, R.style.Theme_StatusBarCompanion)
            .setTitle("Delete ${character.name}?")
            .setMessage("This will permanently delete the theme and its images.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    CharacterPreferences.deleteCharacter(applicationContext, character.id)
                    ImageStorageManager.deleteCharacterAssets(applicationContext, character.id)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "${character.name} deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Service Control ───────────────────────────────────────────────

    private fun startOverlayService() {
        if (!isAccessibilityServiceEnabled()) {
            switchService.isChecked = false
            requestAccessibilityPermission()
            return
        }
        
        // Notify the accessibility service to reload character and show it
        val intent = Intent(com.vellli.statusbarcompanion.service.StatusBarAccessibilityService.ACTION_RELOAD_CHARACTER).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun stopOverlayService() {
        // Send intent to hide it, or just let data store handle it
        val intent = Intent(com.vellli.statusbarcompanion.service.StatusBarAccessibilityService.ACTION_RELOAD_CHARACTER).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ─── Permissions ───────────────────────────────────────────────────

    private fun isAccessibilityServiceEnabled(): Boolean {
        val prefString = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        return prefString.contains("$packageName/${com.vellli.statusbarcompanion.service.StatusBarAccessibilityService::class.java.name}")
    }

    private fun updatePermissionState() {
        if (isAccessibilityServiceEnabled()) {
            permissionCard.visibility = View.GONE
        } else {
            permissionCard.visibility = View.VISIBLE
        }
    }

    private fun requestAccessibilityPermission() {
        Toast.makeText(this, "Lütfen İndirilen Uygulamalar'dan StatusBar Companion'ı aktifleştirin", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
