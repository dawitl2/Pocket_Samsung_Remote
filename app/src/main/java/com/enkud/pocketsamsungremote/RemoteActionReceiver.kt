package com.enkud.pocketsamsungremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val ACTION_REMOTE_POWER = "com.enkud.pocketsamsungremote.action.POWER"
const val ACTION_REMOTE_VOL_DOWN = "com.enkud.pocketsamsungremote.action.VOL_DOWN"
const val ACTION_REMOTE_OK = "com.enkud.pocketsamsungremote.action.OK"
const val ACTION_REMOTE_VOL_UP = "com.enkud.pocketsamsungremote.action.VOL_UP"

class RemoteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RemoteActionRunner(appContext).run(intent.action.orEmpty())
                RemoteControlsNotification.show(appContext)
                RemoteWidgetProvider.updateAll(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class RemoteActionRunner(private val context: Context) {
    private val repository = SettingsRepository(context.settingsDataStore)
    private val tvClient = SamsungTvWebSocketClient()
    private val wakeOnLanClient = WakeOnLanClient()

    suspend fun run(action: String) {
        val settings = repository.settingsFlow.first()
        when (action) {
            ACTION_REMOTE_POWER -> {
                val powerResult = tvClient.sendKey(settings, RemoteCommand.KEY_POWER)
                if (powerResult.isFailure) wakeOnLanClient.wake(settings.tvMac)
            }
            ACTION_REMOTE_VOL_DOWN -> tvClient.sendKey(settings, RemoteCommand.KEY_VOLDOWN)
            ACTION_REMOTE_OK -> tvClient.sendKey(settings, RemoteCommand.KEY_ENTER)
            ACTION_REMOTE_VOL_UP -> tvClient.sendKey(settings, RemoteCommand.KEY_VOLUP)
        }
    }
}
