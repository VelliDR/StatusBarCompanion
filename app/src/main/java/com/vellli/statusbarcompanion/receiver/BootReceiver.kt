package com.vellli.statusbarcompanion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vellli.statusbarcompanion.data.CharacterPreferences
import kotlinx.coroutines.runBlocking

/**
 * Receives BOOT_COMPLETED broadcast and notifies the accessibility service
 * to show the overlay if auto-start is enabled in user preferences.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val shouldAutoStart = runBlocking {
            CharacterPreferences.isAutoStartOnBoot(context)
        }

        if (shouldAutoStart) {
            val serviceIntent = Intent(com.vellli.statusbarcompanion.service.StatusBarAccessibilityService.ACTION_RELOAD_CHARACTER)
            context.sendBroadcast(serviceIntent)
        }
    }
}
