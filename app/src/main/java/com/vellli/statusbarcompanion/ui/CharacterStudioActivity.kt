package com.vellli.statusbarcompanion.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.slider.Slider
import com.vellli.statusbarcompanion.R
import com.vellli.statusbarcompanion.data.CharacterPreferences
import com.vellli.statusbarcompanion.data.ImageStorageManager
import com.vellli.statusbarcompanion.model.BarTheme
import com.vellli.statusbarcompanion.model.OverlayElement
import com.vellli.statusbarcompanion.service.StatusBarAccessibilityService
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class CharacterStudioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHARACTER_ID = "character_id"
    }

    // State
    private var themeId: String = UUID.randomUUID().toString()
    private val elements = mutableListOf<OverlayElement>()
    private var activeElementIndex: Int = -1
    private var isEditing = false

    // Views
    private lateinit var inputName: EditText
    private lateinit var previewContainer: FrameLayout
    private lateinit var imgScreenshotBg: ImageView
    private lateinit var btnRefreshScreenshot: Button
    
    private lateinit var layersContainer: LinearLayout
    private lateinit var btnAddElement: Button
    private lateinit var btnRemoveElement: Button

    private lateinit var imgIdlePreview: ImageView
    private lateinit var imgChargingPreview: ImageView
    private lateinit var imgLowBatteryPreview: ImageView
    private lateinit var sliderOffsetX: Slider
    private lateinit var sliderOffsetY: Slider
    private lateinit var sliderScale: Slider
    private lateinit var labelOffsetX: TextView
    private lateinit var labelOffsetY: TextView
    private lateinit var labelScale: TextView

    // ─── Broadcast Receiver for Screenshot ─────────────────────────────
    
    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == StatusBarAccessibilityService.ACTION_SCREENSHOT_READY) {
                val path = intent.getStringExtra("EXTRA_SCREENSHOT_PATH")
                if (path != null && File(path).exists()) {
                    imgScreenshotBg.load(File(path))
                }
            }
        }
    }

    // ─── Photo Pickers ─────────────────────────────────────────────────

    private val pickIdleImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { handleImagePicked(it, "idle") } }

    private val pickChargingImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { handleImagePicked(it, "charging") } }

    private val pickLowBatteryImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { handleImagePicked(it, "low_battery") } }

    // ─── Lifecycle ─────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_studio)

        bindViews()
        setupListeners()

        val filter = IntentFilter(StatusBarAccessibilityService.ACTION_SCREENSHOT_READY)
        registerReceiver(screenshotReceiver, filter, RECEIVER_EXPORTED)

        // Check if editing existing theme
        val editId = intent.getStringExtra(EXTRA_CHARACTER_ID)
        if (editId != null) {
            isEditing = true
            themeId = editId
            loadExistingTheme(editId)
        } else {
            // New theme, start with 1 element
            addNewElement()
        }

        // Request a fresh screenshot
        requestScreenshot()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenshotReceiver)
    }

    // ─── View Binding ──────────────────────────────────────────────────

    private fun bindViews() {
        inputName = findViewById(R.id.input_name)
        previewContainer = findViewById(R.id.preview_container)
        imgScreenshotBg = findViewById(R.id.img_screenshot_bg)
        btnRefreshScreenshot = findViewById(R.id.btn_refresh_screenshot)
        
        layersContainer = findViewById(R.id.layers_container)
        btnAddElement = findViewById(R.id.btn_add_element)
        btnRemoveElement = findViewById(R.id.btn_remove_element)

        imgIdlePreview = findViewById(R.id.img_idle_preview)
        imgChargingPreview = findViewById(R.id.img_charging_preview)
        imgLowBatteryPreview = findViewById(R.id.img_low_battery_preview)
        
        sliderOffsetX = findViewById(R.id.slider_offset_x)
        sliderOffsetY = findViewById(R.id.slider_offset_y)
        sliderScale = findViewById(R.id.slider_scale)
        labelOffsetX = findViewById(R.id.label_offset_x)
        labelOffsetY = findViewById(R.id.label_offset_y)
        labelScale = findViewById(R.id.label_scale)
    }

    private fun setupListeners() {
        btnRefreshScreenshot.setOnClickListener {
            requestScreenshot()
        }
        
        btnAddElement.setOnClickListener {
            addNewElement()
        }
        
        btnRemoveElement.setOnClickListener {
            if (activeElementIndex >= 0 && activeElementIndex < elements.size) {
                elements.removeAt(activeElementIndex)
                if (elements.isEmpty()) {
                    addNewElement()
                } else {
                    selectElement(Math.max(0, activeElementIndex - 1))
                }
            }
        }

        sliderOffsetX.addOnChangeListener { _, value, fromUser ->
            labelOffsetX.text = "X Offset: ${value.toInt()} dp"
            if (fromUser) updateActiveElement { it.copy(offsetX = value.toInt()) }
        }
        sliderOffsetY.addOnChangeListener { _, value, fromUser ->
            labelOffsetY.text = "Y Offset: ${value.toInt()} dp"
            if (fromUser) updateActiveElement { it.copy(offsetY = value.toInt()) }
        }
        sliderScale.addOnChangeListener { _, value, fromUser ->
            labelScale.text = "Scale: ${"%.1f".format(value)}x"
            if (fromUser) updateActiveElement { it.copy(scale = value) }
        }

        findViewById<Button>(R.id.btn_pick_idle).setOnClickListener {
            pickIdleImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<Button>(R.id.btn_pick_charging).setOnClickListener {
            pickChargingImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<Button>(R.id.btn_pick_low_battery).setOnClickListener {
            pickLowBatteryImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveTheme()
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            finish()
        }
    }
    
    private fun requestScreenshot() {
        Toast.makeText(this, "Taking screenshot of Home Screen...", Toast.LENGTH_SHORT).show()
        
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            sendBroadcast(Intent(StatusBarAccessibilityService.ACTION_REQUEST_SCREENSHOT))
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val returnIntent = Intent(this, CharacterStudioActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(returnIntent)
            }, 800)
        }, 800)
    }

    // ─── Elements Management ───────────────────────────────────────────
    
    private fun addNewElement() {
        elements.add(OverlayElement(idleImagePath = ""))
        selectElement(elements.size - 1)
    }
    
    private fun selectElement(index: Int) {
        if (index < 0 || index >= elements.size) return
        activeElementIndex = index
        renderLayersList()
        updateUIForActiveElement()
        updateLivePreview()
    }
    
    private fun renderLayersList() {
        layersContainer.removeAllViews()
        for ((i, element) in elements.withIndex()) {
            val view = LayoutInflater.from(this).inflate(R.layout.item_layer_thumbnail, layersContainer, false)
            val imgThumbnail = view.findViewById<ImageView>(R.id.img_thumbnail)
            val indicator = view.findViewById<View>(R.id.view_selected_indicator)
            
            if (ImageStorageManager.imageExists(element.idleImagePath)) {
                imgThumbnail.load(File(element.idleImagePath))
            } else {
                imgThumbnail.setImageResource(R.drawable.ic_image)
            }
            
            indicator.visibility = if (i == activeElementIndex) View.VISIBLE else View.GONE
            
            view.setOnClickListener {
                selectElement(i)
            }
            layersContainer.addView(view)
        }
    }
    
    private fun updateActiveElement(modifier: (OverlayElement) -> OverlayElement) {
        if (activeElementIndex in elements.indices) {
            elements[activeElementIndex] = modifier(elements[activeElementIndex])
            updateLivePreview()
        }
    }
    
    private fun updateUIForActiveElement() {
        if (activeElementIndex !in elements.indices) return
        val element = elements[activeElementIndex]
        
        sliderOffsetX.value = element.offsetX.toFloat().coerceIn(sliderOffsetX.valueFrom, sliderOffsetX.valueTo)
        sliderOffsetY.value = element.offsetY.toFloat().coerceIn(sliderOffsetY.valueFrom, sliderOffsetY.valueTo)
        sliderScale.value = element.scale.coerceIn(sliderScale.valueFrom, sliderScale.valueTo)
        
        if (ImageStorageManager.imageExists(element.idleImagePath)) {
            imgIdlePreview.load(File(element.idleImagePath))
        } else {
            imgIdlePreview.setImageResource(R.drawable.ic_image)
        }
        
        val chargingPath = element.chargingImagePath
        if (chargingPath != null && ImageStorageManager.imageExists(chargingPath)) {
            imgChargingPreview.load(File(chargingPath))
        } else {
            imgChargingPreview.setImageResource(R.drawable.ic_image)
        }
        
        val lowBatteryPath = element.lowBatteryImagePath
        if (lowBatteryPath != null && ImageStorageManager.imageExists(lowBatteryPath)) {
            imgLowBatteryPreview.load(File(lowBatteryPath))
        } else {
            imgLowBatteryPreview.setImageResource(R.drawable.ic_image)
        }
    }

    // ─── Image Handling ────────────────────────────────────────────────

    private fun handleImagePicked(uri: Uri, imageType: String) {
        if (activeElementIndex !in elements.indices) return
        
        // Use a unique sub-ID for this element to prevent overwriting other elements' images
        val elementImageId = "${themeId}_${activeElementIndex}"
        val path = ImageStorageManager.importImage(
            context = this,
            uri = uri,
            characterId = elementImageId,
            imageType = imageType
        )

        if (path == null) {
            Toast.makeText(this, "Failed to import image", Toast.LENGTH_SHORT).show()
            return
        }

        updateActiveElement {
            when (imageType) {
                "idle" -> it.copy(idleImagePath = path)
                "charging" -> it.copy(chargingImagePath = path)
                "low_battery" -> it.copy(lowBatteryImagePath = path)
                else -> it
            }
        }
        renderLayersList() // Update thumbnail
        updateUIForActiveElement()
    }

    private fun updateLivePreview() {
        // Remove all old dynamic image views (keep the screenshot bg at index 0)
        while (previewContainer.childCount > 1) {
            previewContainer.removeViewAt(1)
        }
        
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val previewScale = if (previewContainer.width > 0 && screenWidth > 0) {
            previewContainer.width.toFloat() / screenWidth.toFloat()
        } else {
            val paddingPx = 72 * density
            (screenWidth - paddingPx) / screenWidth
        }
        
        // Draw elements from bottom layer to top layer
        for ((index, element) in elements.withIndex()) {
            val previewPath = element.idleImagePath
            if (!ImageStorageManager.imageExists(previewPath)) continue
            
            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            
            val sizePx = (32 * element.scale * density * previewScale).toInt().coerceAtLeast(1)
            
            // Note: in preview, we anchor to TOP|END of the FrameLayout, just like StatusBarAccessibilityService
            val lp = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                topMargin = (element.offsetY * density * previewScale).toInt()
                rightMargin = (element.offsetX * density * previewScale).toInt()
            }
            
            previewContainer.addView(imageView, lp)
            imageView.load(File(previewPath))

            if (index == activeElementIndex) {
                var initialX = 0f
                var initialY = 0f
                var initialOffsetX = 0f
                var initialOffsetY = 0f

                imageView.setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            v.parent.requestDisallowInterceptTouchEvent(true)
                            initialX = event.rawX
                            initialY = event.rawY
                            initialOffsetX = elements[activeElementIndex].offsetX.toFloat()
                            initialOffsetY = elements[activeElementIndex].offsetY.toFloat()
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialX
                            val dy = event.rawY - initialY
                            
                            val realDxDp = (dx / previewScale) / density
                            val realDyDp = (dy / previewScale) / density
                            
                            val newOffsetX = kotlin.math.round(initialOffsetX - realDxDp)
                            val newOffsetY = kotlin.math.round(initialOffsetY + realDyDp)
                            
                            elements[activeElementIndex] = elements[activeElementIndex].copy(
                                offsetX = newOffsetX.toInt(),
                                offsetY = newOffsetY.toInt()
                            )
                            
                            val lp2 = v.layoutParams as FrameLayout.LayoutParams
                            lp2.rightMargin = (newOffsetX * density * previewScale).toInt()
                            lp2.topMargin = (newOffsetY * density * previewScale).toInt()
                            v.layoutParams = lp2
                            sliderOffsetX.value = newOffsetX.coerceIn(sliderOffsetX.valueFrom, sliderOffsetX.valueTo)
                            sliderOffsetY.value = newOffsetY.coerceIn(sliderOffsetY.valueFrom, sliderOffsetY.valueTo)
                            true
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            v.parent.requestDisallowInterceptTouchEvent(false)
                            val activeThemePreview = BarTheme(
                                id = themeId,
                                name = inputName.text.toString().trim(),
                                elements = elements
                            )
                            val intent = Intent(StatusBarAccessibilityService.ACTION_LIVE_PREVIEW).apply {
                                putExtra("EXTRA_THEME_JSON", activeThemePreview.serialize())
                            }
                            sendBroadcast(intent)
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        // Live preview broadcast - send the whole theme object serialized
        val activeThemePreview = BarTheme(
            id = themeId,
            name = inputName.text.toString().trim(),
            elements = elements
        )
        val intent = Intent(StatusBarAccessibilityService.ACTION_LIVE_PREVIEW).apply {
            putExtra("EXTRA_THEME_JSON", activeThemePreview.serialize())
        }
        sendBroadcast(intent)
    }

    // ─── Save ──────────────────────────────────────────────────────────

    private fun saveTheme() {
        val name = inputName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a theme name", Toast.LENGTH_SHORT).show()
            return
        }
        
        val validElements = elements.filter { ImageStorageManager.imageExists(it.idleImagePath) }
        if (validElements.isEmpty()) {
            Toast.makeText(this, "Please add at least one element with an idle image", Toast.LENGTH_SHORT).show()
            return
        }

        val theme = BarTheme(
            id = themeId,
            name = name,
            elements = validElements.toMutableList()
        )

        lifecycleScope.launch {
            CharacterPreferences.saveCharacter(applicationContext, theme)

            // If this is the first/only theme, auto-activate it
            val activeTheme = CharacterPreferences.getActiveCharacter(applicationContext)
            if (activeTheme == null) {
                CharacterPreferences.setActiveCharacter(applicationContext, theme.id)
            }

            // Notify overlay service to refresh
            restartOverlayIfRunning()

            runOnUiThread {
                Toast.makeText(
                    this@CharacterStudioActivity,
                    "Theme saved! ✨",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    // ─── Load Existing ─────────────────────────────────────────────────

    private fun loadExistingTheme(themeId: String) {
        lifecycleScope.launch {
            val theme = CharacterPreferences.getCharacter(applicationContext, themeId)
                ?: return@launch

            runOnUiThread {
                inputName.setText(theme.name)
                elements.clear()
                elements.addAll(theme.elements)
                
                if (elements.isEmpty()) {
                    addNewElement()
                } else {
                    selectElement(0)
                }
            }
        }
    }

    // ─── Service Restart Helper ────────────────────────────────────────

    private fun restartOverlayIfRunning() {
        val intent = Intent(StatusBarAccessibilityService.ACTION_RELOAD_CHARACTER)
        sendBroadcast(intent)
    }
}
