package com.vellli.statusbarcompanion.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.load
import coil.request.CachePolicy
import com.vellli.statusbarcompanion.data.CharacterPreferences
import com.vellli.statusbarcompanion.data.ImageStorageManager
import com.vellli.statusbarcompanion.model.BarTheme
import com.vellli.statusbarcompanion.model.OverlayElement
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

class StatusBarAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_LIVE_PREVIEW = "com.vellli.ACTION_LIVE_PREVIEW"
        const val ACTION_RELOAD_CHARACTER = "com.vellli.ACTION_RELOAD_CHARACTER"
        const val ACTION_REQUEST_SCREENSHOT = "com.vellli.ACTION_REQUEST_SCREENSHOT"
        const val ACTION_SCREENSHOT_READY = "com.vellli.ACTION_SCREENSHOT_READY"
    }

    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var activeTheme: BarTheme? = null
    private var isCharging = false
    private var batteryLevel = 100
    private var isLandscape = false
    private var isScreenOn = true

    private lateinit var imageLoader: ImageLoader
    private val mainHandler = Handler(Looper.getMainLooper())

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val newLevel = if (scale > 0) (level * 100) / scale else level
                    val status = intent.getIntExtra(
                        BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN
                    )
                    val newCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    if (newLevel != batteryLevel || newCharging != isCharging) {
                        batteryLevel = newLevel
                        isCharging = newCharging
                        updateOverlayFrame()
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    isCharging = true
                    updateOverlayFrame()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCharging = false
                    updateOverlayFrame()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    hideOverlay()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (!isLandscape) {
                        showOverlay()
                        updateOverlayFrame()
                    }
                }
                ACTION_LIVE_PREVIEW -> {
                    val themeJson = intent.getStringExtra("EXTRA_THEME_JSON")
                    if (themeJson != null) {
                        activeTheme = BarTheme.deserialize(themeJson)
                        if (overlayContainer == null) {
                            createOverlayView()
                        } else {
                            updateOverlayFrame()
                        }
                    }
                }
                ACTION_RELOAD_CHARACTER -> {
                    val isEnabled = runBlocking {
                        CharacterPreferences.isServiceEnabled(applicationContext)
                    }
                    if (!isEnabled) {
                        removeOverlayView()
                        return
                    }

                    val newTheme = runBlocking {
                        CharacterPreferences.getActiveCharacter(applicationContext)
                    }
                    if (newTheme?.id != activeTheme?.id || newTheme?.serialize() != activeTheme?.serialize()) {
                        activeTheme = newTheme
                        removeOverlayView()
                        if (activeTheme != null) {
                            createOverlayView()
                        }
                    } else if (activeTheme != null) {
                        if (overlayContainer == null) {
                            createOverlayView()
                        }
                        updateOverlayFrame()
                    }
                }
                ACTION_REQUEST_SCREENSHOT -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        takeScreenshot(
                            Display.DEFAULT_DISPLAY,
                            mainExecutor,
                            object : TakeScreenshotCallback {
                                override fun onSuccess(screenshot: ScreenshotResult) {
                                    val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                                    if (bitmap != null) {
                                        // Crop to top 150dp approx (status bar area)
                                        val heightPx = Math.min(bitmap.height, (150 * resources.displayMetrics.density).toInt())
                                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, heightPx)
                                        
                                        val file = File(cacheDir, "screenshot_cache.png")
                                        val fos = FileOutputStream(file)
                                        cropped.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                        fos.close()
                                        
                                        cropped.recycle()
                                        bitmap.recycle()
                                        screenshot.hardwareBuffer.close()
                                        
                                        val readyIntent = Intent(ACTION_SCREENSHOT_READY)
                                        readyIntent.putExtra("EXTRA_SCREENSHOT_PATH", file.absolutePath)
                                        sendBroadcast(readyIntent)
                                    }
                                }
                                override fun onFailure(errorCode: Int) {
                                    // Handle failure if needed
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private var orientationListener: OrientationEventListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        imageLoader = ImageLoader.Builder(this)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
            .build()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Fetch initial battery status
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent != null) {
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryLevel = if (scale > 0) (level * 100) / scale else level
        }

        val isEnabled = runBlocking {
            CharacterPreferences.isServiceEnabled(applicationContext)
        }
        activeTheme = runBlocking {
            CharacterPreferences.getActiveCharacter(applicationContext)
        }

        if (isEnabled && activeTheme != null) {
            createOverlayView()
        }
        
        registerSystemReceiver()
        setupOrientationListener()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        // Not used
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        unregisterSystemReceiver()
        orientationListener?.disable()
        orientationListener = null
        removeOverlayView()
        imageLoader.shutdown()
        super.onDestroy()
    }

    private fun createOverlayView() {
        val theme = activeTheme ?: return

        overlayContainer = FrameLayout(this).apply {
            visibility = View.VISIBLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, // Take up full screen to allow positioning anywhere
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        try {
            windowManager?.addView(overlayContainer, params)
            layoutParams = params
            updateOverlayFrame()
        } catch (_: Exception) {}
    }

    private fun removeOverlayView() {
        overlayContainer?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        overlayContainer = null
        layoutParams = null
    }

    private fun hideOverlay() {
        overlayContainer?.visibility = View.GONE
    }

    private fun showOverlay() {
        overlayContainer?.visibility = View.VISIBLE
    }

    private fun updateOverlayFrame() {
        val container = overlayContainer ?: return
        val theme = activeTheme ?: return
        if (!isScreenOn || isLandscape) return

        val density = resources.displayMetrics.density

        // Ensure we have the correct number of children
        while (container.childCount < theme.elements.size) {
            container.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            })
        }
        while (container.childCount > theme.elements.size) {
            container.removeViewAt(container.childCount - 1)
        }

        for (i in theme.elements.indices) {
            val element = theme.elements[i]
            val imageView = container.getChildAt(i) as ImageView

            val imagePath = when {
                batteryLevel <= 20 &&
                        element.lowBatteryImagePath != null &&
                        ImageStorageManager.imageExists(element.lowBatteryImagePath) -> {
                    element.lowBatteryImagePath
                }
                isCharging &&
                        element.chargingImagePath != null &&
                        ImageStorageManager.imageExists(element.chargingImagePath) -> {
                    element.chargingImagePath
                }
                else -> element.idleImagePath
            }

            if (ImageStorageManager.imageExists(imagePath)) {
                val sizePx = (32 * element.scale * density).toInt().coerceAtLeast(1)
                
                val lp = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                    gravity = Gravity.TOP or Gravity.END // Relative to top-end
                    topMargin = (element.offsetY * density).toInt()
                    rightMargin = (element.offsetX * density).toInt() 
                }
                imageView.layoutParams = lp

                imageView.load(File(imagePath), imageLoader) {
                    crossfade(false)
                    size(sizePx)
                }
            } else {
                imageView.setImageDrawable(null)
            }
        }
    }

    private fun registerSystemReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(ACTION_LIVE_PREVIEW)
            addAction(ACTION_RELOAD_CHARACTER)
            addAction(ACTION_REQUEST_SCREENSHOT)
        }
        registerReceiver(systemReceiver, filter, RECEIVER_EXPORTED)
    }

    private fun unregisterSystemReceiver() {
        try {
            unregisterReceiver(systemReceiver)
        } catch (_: IllegalArgumentException) {}
    }

    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val newLandscape = when (orientation) {
                    in 60..120 -> true
                    in 240..300 -> true
                    else -> false
                }

                if (newLandscape != isLandscape) {
                    isLandscape = newLandscape
                    if (isLandscape) {
                        hideOverlay()
                    } else if (isScreenOn) {
                        showOverlay()
                        updateOverlayFrame()
                    }
                }
            }
        }
        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }
    }
}
