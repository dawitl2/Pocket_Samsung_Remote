package com.enkud.pocketsamsungremote

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("remote_settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestNotificationPermissionIfNeeded()
        setContent {
            RemoteTheme {
                PocketRemoteApp()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            RemoteControlsNotification.show(this)
        }
    }
}

enum class RemoteCommand(val key: String, val title: String) {
    KEY_POWER("KEY_POWER", "Power"),
    KEY_HOME("KEY_HOME", "Home"),
    KEY_BACK("KEY_BACK", "Back"),
    KEY_MENU("KEY_MENU", "Menu"),
    KEY_UP("KEY_UP", "Up"),
    KEY_DOWN("KEY_DOWN", "Down"),
    KEY_LEFT("KEY_LEFT", "Left"),
    KEY_RIGHT("KEY_RIGHT", "Right"),
    KEY_ENTER("KEY_ENTER", "OK"),
    KEY_VOLUP("KEY_VOLUP", "Vol +"),
    KEY_VOLDOWN("KEY_VOLDOWN", "Vol -"),
    KEY_MUTE("KEY_MUTE", "Mute"),
    KEY_CHUP("KEY_CHUP", "Ch +"),
    KEY_CHDOWN("KEY_CHDOWN", "Ch -"),
    KEY_SOURCE("KEY_SOURCE", "Source"),
    KEY_PLAY("KEY_PLAY", "Play"),
    KEY_PAUSE("KEY_PAUSE", "Pause"),
    KEY_REWIND("KEY_REWIND", "Rewind"),
    KEY_FF("KEY_FF", "Fast fwd"),
    KEY_RETURN("KEY_RETURN", "Return"),
    KEY_EXIT("KEY_EXIT", "Exit"),
    KEY_TOOLS("KEY_TOOLS", "Tools"),
    KEY_INFO("KEY_INFO", "Info"),
    KEY_GUIDE("KEY_GUIDE", "Guide"),
    KEY_0("KEY_0", "0"),
    KEY_1("KEY_1", "1"),
    KEY_2("KEY_2", "2"),
    KEY_3("KEY_3", "3"),
    KEY_4("KEY_4", "4"),
    KEY_5("KEY_5", "5"),
    KEY_6("KEY_6", "6"),
    KEY_7("KEY_7", "7"),
    KEY_8("KEY_8", "8"),
    KEY_9("KEY_9", "9"),
    KEY_SEARCH("KEY_SEARCH", "Search"),
    KEY_CONTENTS("KEY_CONTENTS", "Apps");

    companion object {
        fun fromKey(value: String): RemoteCommand? = entries.firstOrNull { it.key == value }
    }
}

enum class YouTubeMode(val label: String) {
    AUTOMATION("Remote automation"),
    API("YouTube API")
}

enum class Screen(val label: String) {
    Remote("Remote"),
    Gestures("Gesture camera"),
    Setup("Setup"),
    Keyboard("Keyboard"),
    YouTube("YouTube"),
    Browser("Browser"),
    Macros("Macros"),
    Settings("Settings"),
    Help("Help")
}

data class TvSettings(
    val tvName: String = "Samsung CU7000 65",
    val tvIp: String = "192.168.1.3",
    val tvMac: String = "68:fc:ca:31:bf:e8",
    val pairingToken: String = "",
    val preferredProtocol: String = "auto",
    val youtubeMode: YouTubeMode = YouTubeMode.AUTOMATION,
    val youtubeApiKey: String = "",
    val experimentalPowerOn: Boolean = false,
    val modelCode: String = "UA65CU7000USGA",
    val serialNumber: String = "08PG3K3W501030J",
    val softwareVersion: String = "T-KSU2ECUABC-0090-2 220.4"
)

data class MacroStep(
    val command: String,
    val delayMs: Long
)

data class Macro(
    val id: String,
    val name: String,
    val commands: List<MacroStep>
)

data class YouTubeResult(
    val title: String,
    val channel: String,
    val videoId: String
)

data class TvConnectionResult(
    val token: String?,
    val message: String
)

data class TvAppShortcut(
    val label: String,
    val appId: String,
    val searchText: String = label
)

val tvAppShortcuts = listOf(
    TvAppShortcut("YouTube", "111299001912"),
    TvAppShortcut("Browser", "org.tizen.browser", "Internet Browser"),
    TvAppShortcut("DStv", "", "DStv Stream"),
    TvAppShortcut("Spotify", "3201606009684"),
    TvAppShortcut("Netflix", "11101200001")
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private object Keys {
        val TV_NAME = stringPreferencesKey("tv_name")
        val TV_IP = stringPreferencesKey("tv_ip")
        val TV_MAC = stringPreferencesKey("tv_mac")
        val PAIRING_TOKEN = stringPreferencesKey("pairing_token")
        val PROTOCOL = stringPreferencesKey("preferred_protocol")
        val YOUTUBE_MODE = stringPreferencesKey("youtube_mode")
        val YOUTUBE_API_KEY = stringPreferencesKey("youtube_api_key")
        val EXPERIMENTAL_POWER_ON = booleanPreferencesKey("experimental_power_on")
        val MACROS = stringPreferencesKey("macros")
    }

    val settingsFlow: Flow<TvSettings> = store.data.map { prefs ->
        TvSettings(
            tvName = prefs[Keys.TV_NAME] ?: "Samsung CU7000 65",
            tvIp = prefs[Keys.TV_IP] ?: "192.168.1.3",
            tvMac = prefs[Keys.TV_MAC] ?: "68:fc:ca:31:bf:e8",
            pairingToken = prefs[Keys.PAIRING_TOKEN] ?: "",
            preferredProtocol = prefs[Keys.PROTOCOL] ?: "auto",
            youtubeMode = runCatching {
                YouTubeMode.valueOf(prefs[Keys.YOUTUBE_MODE] ?: YouTubeMode.AUTOMATION.name)
            }.getOrDefault(YouTubeMode.AUTOMATION),
            youtubeApiKey = prefs[Keys.YOUTUBE_API_KEY] ?: "",
            experimentalPowerOn = prefs[Keys.EXPERIMENTAL_POWER_ON] ?: false
        )
    }

    val macrosFlow: Flow<List<Macro>> = store.data.map { prefs ->
        decodeMacros(prefs[Keys.MACROS]).ifEmpty { defaultMacros }
    }

    suspend fun saveSettings(settings: TvSettings) {
        store.edit { prefs ->
            prefs[Keys.TV_NAME] = settings.tvName
            prefs[Keys.TV_IP] = settings.tvIp
            prefs[Keys.TV_MAC] = settings.tvMac
            prefs[Keys.PAIRING_TOKEN] = settings.pairingToken
            prefs[Keys.PROTOCOL] = settings.preferredProtocol
            prefs[Keys.YOUTUBE_MODE] = settings.youtubeMode.name
            prefs[Keys.YOUTUBE_API_KEY] = settings.youtubeApiKey
            prefs[Keys.EXPERIMENTAL_POWER_ON] = settings.experimentalPowerOn
        }
    }

    suspend fun loadSettings(): TvSettings = settingsFlow.first()

    suspend fun savePairingToken(token: String) {
        store.edit { prefs -> prefs[Keys.PAIRING_TOKEN] = token }
    }

    suspend fun saveMacros(macros: List<Macro>) {
        store.edit { prefs ->
            prefs[Keys.MACROS] = encodeMacros(macros)
        }
    }
}

private val defaultMacros = listOf(
    Macro(
        id = "youtube-search",
        name = "YouTube Search Prep",
        commands = listOf(
            MacroStep(RemoteCommand.KEY_HOME.key, 1000),
            MacroStep(RemoteCommand.KEY_CONTENTS.key, 1000),
            MacroStep(RemoteCommand.KEY_SEARCH.key, 500),
            MacroStep(RemoteCommand.KEY_ENTER.key, 250)
        )
    ),
    Macro(
        id = "browser-search",
        name = "Browser Search Prep",
        commands = listOf(
            MacroStep(RemoteCommand.KEY_HOME.key, 1000),
            MacroStep(RemoteCommand.KEY_CONTENTS.key, 1000),
            MacroStep(RemoteCommand.KEY_SEARCH.key, 500)
        )
    ),
    Macro(
        id = "volume-preset",
        name = "Volume Preset",
        commands = listOf(
            MacroStep(RemoteCommand.KEY_MUTE.key, 300),
            MacroStep(RemoteCommand.KEY_VOLUP.key, 250),
            MacroStep(RemoteCommand.KEY_VOLUP.key, 250),
            MacroStep(RemoteCommand.KEY_VOLUP.key, 250)
        )
    )
)

private fun encodeMacros(macros: List<Macro>): String = macros.joinToString("\n") { macro ->
    val steps = macro.commands.joinToString(",") { "${it.command}:${it.delayMs}" }
    listOf(macro.id, Uri.encode(macro.name), steps).joinToString("|")
}

private fun decodeMacros(value: String?): List<Macro> {
    if (value.isNullOrBlank()) return emptyList()
    return value.lines().mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size != 3) return@mapNotNull null
        val steps = parts[2].split(",").mapNotNull { raw ->
            val step = raw.split(":")
            if (step.size != 2) null else MacroStep(step[0], step[1].toLongOrNull() ?: 250)
        }
        Macro(parts[0], Uri.decode(parts[1]), steps)
    }
}

class SamsungTvWebSocketClient {
    private val connectionState = MutableStateFlow("Disconnected")
    val status: StateFlow<String> = connectionState.asStateFlow()
    private val imeState = MutableStateFlow(false)
    val imeActive: StateFlow<Boolean> = imeState.asStateFlow()
    private val touchState = MutableStateFlow(false)
    val pointerEnabled: StateFlow<Boolean> = touchState.asStateFlow()
    private var webSocket: WebSocket? = null
    private var connectedIp: String? = null
    private var activeToken: String? = null

    private val client: OkHttpClient = unsafeClient()
        .newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    suspend fun connect(settings: TvSettings): Result<TvConnectionResult> {
        val ip = settings.tvIp
        val protocol = settings.preferredProtocol
        if (ip.isBlank()) return Result.failure(IllegalArgumentException("TV IP address is empty."))
        val endpoints = when (protocol) {
            "ws" -> listOf(endpoint("ws", ip, 8001, settings.pairingToken))
            "wss" -> listOf(endpoint("wss", ip, 8002, settings.pairingToken))
            else -> listOf(endpoint("wss", ip, 8002, settings.pairingToken), endpoint("ws", ip, 8001, settings.pairingToken))
        }

        disconnect()
        var lastError: Throwable? = null
        for (url in endpoints) {
            connectionState.value = "Connecting to ${Uri.parse(url).scheme}..."
            val result = connectUrl(url)
            if (result.isSuccess) {
                connectedIp = ip
                activeToken = result.getOrNull()?.token ?: settings.pairingToken.ifBlank { null }
                connectionState.value = "Ready"
                return result
            }
            lastError = result.exceptionOrNull()
        }
        connectionState.value = "Not paired"
        return Result.failure(
            lastError ?: IOException("TV opened a socket but did not approve the remote channel.")
        )
    }

    fun disconnect() {
        webSocket?.close(1000, "Reconnect")
        webSocket = null
        connectedIp = null
        activeToken = null
        imeState.value = false
        touchState.value = false
        connectionState.value = "Disconnected"
    }

    suspend fun sendKey(settings: TvSettings, key: RemoteCommand): Result<Unit> {
        if (webSocket == null || connectedIp != settings.tvIp) {
            val connectResult = connect(settings)
            if (connectResult.isFailure) return Result.failure(connectResult.exceptionOrNull() ?: IOException("Could not connect to TV."))
        }
        val json = JSONObject()
            .put("method", "ms.remote.control")
            .put(
                "params",
                JSONObject()
                    .put("Cmd", "Click")
                    .put("DataOfCmd", key.key)
                    .put("Option", "false")
                    .put("TypeOfRemote", "SendRemoteKey")
            )
            .toString()
        if (webSocket?.send(json) == true) return Result.success(Unit)

        val currentToken = activeToken ?: settings.pairingToken
        disconnect()
        val reconnectResult = connect(settings.copy(pairingToken = currentToken))
        if (reconnectResult.isFailure) return Result.failure(reconnectResult.exceptionOrNull() ?: IOException("Could not reconnect to TV."))
        return if (webSocket?.send(json) == true) Result.success(Unit)
        else {
            connectionState.value = "Connection failed"
            Result.failure(IOException("TV WebSocket is not ready. Approve the TV popup, then tap Connect again."))
        }
    }

    suspend fun sendText(settings: TvSettings, text: String): Result<Unit> {
        if (webSocket == null || connectedIp != settings.tvIp) {
            val connectResult = connect(settings)
            if (connectResult.isFailure) return Result.failure(connectResult.exceptionOrNull() ?: IOException("Could not connect to TV."))
        }
        val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val json = JSONObject()
            .put("method", "ms.remote.control")
            .put(
                "params",
                JSONObject()
                    .put("Cmd", encoded)
                    .put("DataOfCmd", "base64")
                    .put("TypeOfRemote", "SendInputString")
            )
            .toString()
        if (webSocket?.send(json) == true) return Result.success(Unit)

        val currentToken = activeToken ?: settings.pairingToken
        disconnect()
        val reconnectResult = connect(settings.copy(pairingToken = currentToken))
        if (reconnectResult.isFailure) return Result.failure(reconnectResult.exceptionOrNull() ?: IOException("Could not reconnect to TV."))
        return if (webSocket?.send(json) == true) Result.success(Unit)
        else Result.failure(IOException("Direct text input is not ready. Use assisted buttons or reconnect."))
    }

    suspend fun movePointer(
        settings: TvSettings,
        deltaX: Int,
        deltaY: Int
    ): Result<Unit> {
        if (deltaX == 0 && deltaY == 0) return Result.success(Unit)
        if (webSocket == null || connectedIp != settings.tvIp) {
            val connectResult = connect(settings)
            if (connectResult.isFailure) {
                return Result.failure(
                    connectResult.exceptionOrNull() ?: IOException("Could not connect to TV.")
                )
            }
        }
        val json = JSONObject()
            .put("method", "ms.remote.control")
            .put(
                "params",
                JSONObject()
                    .put("Cmd", "Move")
                    .put(
                        "Position",
                        JSONObject()
                            .put("x", deltaX)
                            .put("y", deltaY)
                            .put("Time", System.currentTimeMillis().toString())
                    )
                    .put("TypeOfRemote", "ProcessMouseDevice")
            )
            .toString()
        return if (webSocket?.send(json) == true) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("TV pointer channel is not ready."))
        }
    }

    private suspend fun connectUrl(url: String): Result<TvConnectionResult> = runCatching {
        withTimeout(10000) {
            suspendCancellableCoroutine { continuation ->
                val resumed = AtomicBoolean(false)
                val request = Request.Builder().url(url).build()
                val ws = client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            this@SamsungTvWebSocketClient.webSocket = webSocket
                            connectionState.value = "Waiting for TV approval..."
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val json = runCatching { JSONObject(text) }.getOrNull()
                            val event = json?.optString("event").orEmpty()
                            val data = json?.optJSONObject("data")
                            val token = data?.optString("token").orEmpty()
                                .ifBlank { json?.optString("token").orEmpty() }
                                .ifBlank { null }

                            if (token != null) activeToken = token

                            when {
                                event.equals("ms.remote.imeStart", ignoreCase = true) ->
                                    imeState.value = true
                                event.equals("ms.remote.imeEnd", ignoreCase = true) ->
                                    imeState.value = false
                                event.equals("ms.remote.touchEnable", ignoreCase = true) ->
                                    touchState.value = true
                                event.equals("ms.remote.touchDisable", ignoreCase = true) ->
                                    touchState.value = false
                            }

                            val lower = text.lowercase()
                            if ("unauthorized" in lower || "denied" in lower) {
                                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                                    continuation.resumeWith(Result.failure(IOException("The TV rejected this remote. Remove old remote approvals on the TV, then connect again.")))
                                }
                                return
                            }

                            if (event.contains("ready", ignoreCase = true) ||
                                event.contains("connect", ignoreCase = true) ||
                                token != null
                            ) {
                                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                                    continuation.resume(
                                        TvConnectionResult(
                                            token = token,
                                            message = if (token != null) "TV channel ready. Pairing token saved." else "TV channel ready."
                                        )
                                    )
                                }
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            this@SamsungTvWebSocketClient.webSocket = null
                            if (resumed.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resumeWith(Result.failure(t))
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            connectionState.value = "Disconnected"
                            this@SamsungTvWebSocketClient.webSocket = null
                            if (resumed.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resumeWith(Result.failure(IOException("TV closed the socket before pairing completed.")))
                            }
                        }
                    }
                )
                continuation.invokeOnCancellation { ws.cancel() }
            }
        }
    }

    private fun endpoint(scheme: String, ip: String, port: Int, token: String): String {
        val appName = Base64.encodeToString("D Remote".toByteArray(), Base64.NO_WRAP)
        val tokenParam = token.ifBlank { null }?.let { "&token=${Uri.encode(it)}" }.orEmpty()
        return "$scheme://$ip:$port/api/v2/channels/samsung.remote.control?name=${Uri.encode(appName)}$tokenParam"
    }

    private fun unsafeClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, SecureRandom())
        val trustManager = trustAll[0] as X509TrustManager
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}

class WakeOnLanClient {
    suspend fun wake(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = macAddress.replace(":", "").replace("-", "").trim()
            require(clean.length == 12) { "MAC address should look like 68:fc:ca:31:bf:e8." }
            val macBytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val packet = ByteArray(6 + 16 * macBytes.size) { 0xFF.toByte() }
            for (i in 6 until packet.size) packet[i] = macBytes[(i - 6) % macBytes.size]
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(DatagramPacket(packet, packet.size, java.net.InetAddress.getByName("255.255.255.255"), 9))
            }
        }
    }
}

class SamsungTvAppLaunchClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun launch(settings: TvSettings, app: TvAppShortcut): Result<Unit> = withContext(Dispatchers.IO) {
        if (app.appId.isBlank()) return@withContext Result.failure(IllegalArgumentException("No stable app id saved for ${app.label}."))
        val urls = listOf(
            "http://${settings.tvIp}:8001/api/v2/applications/${Uri.encode(app.appId)}",
            "https://${settings.tvIp}:8002/api/v2/applications/${Uri.encode(app.appId)}"
        )
        var lastError: Throwable? = null
        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            val result = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                }
            }
            if (result.isSuccess) return@withContext Result.success(Unit)
            lastError = result.exceptionOrNull()
        }
        Result.failure(lastError ?: IOException("Could not open ${app.label}."))
    }
}

class YouTubeApiClient {
    private val client = OkHttpClient()

    suspend fun search(apiKey: String, query: String): Result<List<YouTubeResult>> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Add a YouTube API key in Settings first." }
            require(query.isNotBlank()) { "Search text is empty." }
            val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=8&q=${
                Uri.encode(query)
            }&key=${Uri.encode(apiKey)}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) error("YouTube API returned HTTP ${response.code}.")
                val json = JSONObject(response.body?.string().orEmpty())
                val items = json.optJSONArray("items") ?: return@use emptyList()
                buildList {
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val id = item.getJSONObject("id").optString("videoId")
                        val snippet = item.getJSONObject("snippet")
                        if (id.isNotBlank()) {
                            add(
                                YouTubeResult(
                                    title = snippet.optString("title"),
                                    channel = snippet.optString("channelTitle"),
                                    videoId = id
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response)
            }
        }
    )
    continuation.invokeOnCancellation { cancel() }
}

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application.settingsDataStore)
    private val tvClient = SamsungTvWebSocketClient()
    private val wakeOnLanClient = WakeOnLanClient()
    private val youTubeApiClient = YouTubeApiClient()
    private val appLaunchClient = SamsungTvAppLaunchClient()
    private var connectJob: Job? = null
    private var pointerJob: Job? = null

    val settings = repository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TvSettings()
    )
    val macros = repository.macrosFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        defaultMacros
    )
    val connectionStatus = tvClient.status
    val imeActive = tvClient.imeActive
    val pointerEnabled = tvClient.pointerEnabled

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()
    private val _youtubeResults = MutableStateFlow<List<YouTubeResult>>(emptyList())
    val youtubeResults = _youtubeResults.asStateFlow()

    fun saveSettings(settings: TvSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
            _message.value = "Settings saved."
        }
    }

    fun connect() {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            val currentSettings = repository.loadSettings()
            val result = tvClient.connect(currentSettings)
            _message.value = result.fold(
                { connection ->
                    connection.token?.takeIf { it.isNotBlank() }?.let { token ->
                        repository.savePairingToken(token)
                    }
                    connection.message
                },
                { it.readableMessage() }
            )
        }
    }

    fun startQuickControls(context: Context) {
        RemoteControlsNotification.show(context)
    }

    fun disconnect() {
        tvClient.disconnect()
        _message.value = "Disconnected."
    }

    fun sendKey(key: RemoteCommand) {
        viewModelScope.launch {
            val result = tvClient.sendKey(repository.loadSettings(), key)
            if (result.isFailure) _message.value = result.exceptionOrNull().readableMessage()
        }
    }

    fun sendText(text: String) {
        viewModelScope.launch {
            val result = tvClient.sendText(repository.loadSettings(), text)
            _message.value = result.fold({ "Text sent. If the TV ignored it, use assisted mode." }, { it.readableMessage() })
        }
    }

    fun sendHandwritingText(text: String) {
        if (text.isEmpty()) return
        viewModelScope.launch {
            val result = tvClient.sendText(repository.loadSettings(), text)
            if (result.isFailure) {
                _message.value = result.exceptionOrNull().readableMessage()
            }
        }
    }

    fun movePointer(deltaX: Int, deltaY: Int) {
        if (pointerJob?.isActive == true) return
        pointerJob = viewModelScope.launch {
            val result = tvClient.movePointer(repository.loadSettings(), deltaX, deltaY)
            if (result.isFailure && connectionStatus.value != "Ready") {
                _message.value = result.exceptionOrNull().readableMessage()
            }
        }
    }

    fun sendDigitOrFallback(text: String) {
        viewModelScope.launch {
            val currentSettings = repository.loadSettings()
            text.forEach { char ->
                val command = when (char) {
                    '0' -> RemoteCommand.KEY_0
                    '1' -> RemoteCommand.KEY_1
                    '2' -> RemoteCommand.KEY_2
                    '3' -> RemoteCommand.KEY_3
                    '4' -> RemoteCommand.KEY_4
                    '5' -> RemoteCommand.KEY_5
                    '6' -> RemoteCommand.KEY_6
                    '7' -> RemoteCommand.KEY_7
                    '8' -> RemoteCommand.KEY_8
                    '9' -> RemoteCommand.KEY_9
                    ' ' -> RemoteCommand.KEY_RIGHT
                    else -> null
                }
                if (command != null) {
                    tvClient.sendKey(currentSettings, command)
                    delay(120)
                }
            }
            _message.value = "Fallback key simulation finished where supported."
        }
    }

    fun wakeTv() {
        viewModelScope.launch {
            val currentSettings = repository.loadSettings()
            val result = wakeOnLanClient.wake(currentSettings.tvMac)
            _message.value = result.fold(
                { "Wake-on-LAN packet sent. TV standby network settings must allow it." },
                { it.readableMessage() }
            )
        }
    }

    fun togglePower() {
        viewModelScope.launch {
            val currentSettings = repository.loadSettings()
            val result = tvClient.sendKey(currentSettings, RemoteCommand.KEY_POWER)
            if (result.isFailure) {
                val wakeResult = wakeOnLanClient.wake(currentSettings.tvMac)
                _message.value = wakeResult.fold(
                    { "Power command failed, so Wake-on-LAN was sent." },
                    { result.exceptionOrNull().readableMessage() }
                )
            }
        }
    }

    fun openTvApp(app: TvAppShortcut) {
        viewModelScope.launch {
            val currentSettings = repository.loadSettings()
            val direct = appLaunchClient.launch(currentSettings, app)
            if (direct.isSuccess) {
                _message.value = "Opening ${app.label}."
                return@launch
            }
            tvClient.sendKey(currentSettings, RemoteCommand.KEY_HOME)
            delay(600)
            tvClient.sendKey(currentSettings, RemoteCommand.KEY_SEARCH)
            delay(300)
            tvClient.sendText(currentSettings, app.searchText)
            delay(300)
            tvClient.sendKey(currentSettings, RemoteCommand.KEY_ENTER)
            _message.value = "Tried TV search fallback for ${app.label}."
        }
    }

    fun runMacro(macro: Macro) {
        viewModelScope.launch {
            val currentSettings = repository.loadSettings()
            _message.value = "Running ${macro.name}..."
            macro.commands.forEach { step ->
                RemoteCommand.fromKey(step.command)?.let { tvClient.sendKey(currentSettings, it) }
                delay(step.delayMs.coerceAtLeast(50))
            }
            _message.value = "${macro.name} finished."
        }
    }

    fun upsertMacro(macro: Macro) {
        viewModelScope.launch {
            val next = macros.value.filterNot { it.id == macro.id } + macro
            repository.saveMacros(next)
            _message.value = "Macro saved."
        }
    }

    fun deleteMacro(macro: Macro) {
        viewModelScope.launch {
            repository.saveMacros(macros.value.filterNot { it.id == macro.id })
            _message.value = "Macro deleted."
        }
    }

    fun searchYouTube(query: String) {
        viewModelScope.launch {
            val result = youTubeApiClient.search(repository.loadSettings().youtubeApiKey, query)
            result.onSuccess {
                _youtubeResults.value = it
                _message.value = "Found ${it.size} YouTube results."
            }.onFailure {
                _youtubeResults.value = emptyList()
                _message.value = it.readableMessage()
            }
        }
    }
}

private fun Throwable?.readableMessage(): String =
    this?.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Check same Wi-Fi, TV power, IP, and the TV permission popup."

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Color(0xFF6EE7F9),
            secondary = Color(0xFFA7F3D0),
            tertiary = Color(0xFFF9A8D4),
            background = Color(0xFF070A0F),
            surface = Color(0xFF101620),
            surfaceVariant = Color(0xFF171F2B),
            onPrimary = Color(0xFF061016),
            onSurface = Color(0xFFEAF0F8),
            onSurfaceVariant = Color(0xFFB9C3D1)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketRemoteApp(viewModel: RemoteViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val macros by viewModel.macros.collectAsState()
    val status by viewModel.connectionStatus.collectAsState()
    val message by viewModel.message.collectAsState()
    val youtubeResults by viewModel.youtubeResults.collectAsState()
    val imeActive by viewModel.imeActive.collectAsState()
    val pointerEnabled by viewModel.pointerEnabled.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.Remote) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        if (message.isNotBlank()) snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(Unit) {
        viewModel.startQuickControls(context)
        viewModel.connect()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RemoteBackground()
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
        when (screen) {
            Screen.Remote -> RemoteScreen(settings, status, viewModel) { screen = it }
            Screen.Gestures -> GestureCameraScreen(
                onBack = { screen = Screen.Remote },
                imeActive = imeActive,
                pointerEnabled = pointerEnabled,
                onCommand = { viewModel.sendKey(it) },
                onPointerMove = { x, y -> viewModel.movePointer(x, y) },
                onText = { viewModel.sendHandwritingText(it) }
            )
            else -> HiddenScreenHost(
                screen = screen,
                settings = settings,
                macros = macros,
                youtubeResults = youtubeResults,
                viewModel = viewModel,
                onBack = { screen = Screen.Remote }
            )
        }
    }
}

@Composable
private fun RemoteScreen(
    settings: TvSettings,
    status: String,
    viewModel: RemoteViewModel,
    openScreen: (Screen) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showLive by remember { mutableStateOf(false) }
    var trackpadMode by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RemoteTopBar(
                status = status,
                onMenu = { showMenu = true },
                onPower = { viewModel.togglePower() },
                onLive = { showLive = !showLive }
            )
            Spacer(Modifier.height(30.dp))
            GlassRemoteShell(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 316.dp),
                trackpadMode = trackpadMode,
                onToggleTrackpad = { trackpadMode = !trackpadMode },
                onCommand = { viewModel.sendKey(it) },
                onOpenApp = { viewModel.openTvApp(it) },
                onOpenCamera = { openScreen(Screen.Gestures) }
            )
        }

        if (showLive) {
            LiveIsland(
                status = status,
                settings = settings,
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 46.dp)
            )
        }

        if (showMenu) {
            RemoteMenuSheet(
                onDismiss = { showMenu = false },
                onOpen = {
                    showMenu = false
                    openScreen(it)
                }
            )
        }
    }
}

@Composable
private fun RemoteTopBar(
    status: String,
    onMenu: () -> Unit,
    onPower: () -> Unit,
    onLive: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        RemoteGlyphButton(
            glyph = RemoteGlyph.Menu,
            onClick = onMenu,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(36.dp),
            iconSize = 19.dp
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onPower),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.remote_power),
                contentDescription = "Power",
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(72.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
                .clickable(onClick = onLive),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val liveColor = if (status == "Ready") Color(0xFF42F58D) else Color.White.copy(alpha = 0.56f)
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(liveColor, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "LIVE",
                color = liveColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GlassRemoteShell(
    modifier: Modifier,
    trackpadMode: Boolean,
    onToggleTrackpad: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    onOpenApp: (TvAppShortcut) -> Unit,
    onOpenCamera: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RemoteControlPanel(
            trackpadMode = trackpadMode,
            onToggleTrackpad = onToggleTrackpad,
            onCommand = onCommand,
            modifier = Modifier
                .fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        AppShortcutRow(onOpenApp, onOpenCamera)
    }
}

@Composable
private fun RemoteControlPanel(
    trackpadMode: Boolean,
    onToggleTrackpad: () -> Unit,
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(30.dp)
    Column(
        modifier = modifier
            .shadow(18.dp, panelShape, clip = false)
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF202329).copy(alpha = 0.94f),
                        Color(0xFF0C0E12).copy(alpha = 0.96f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), panelShape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TrackpadToggleButton(trackpadMode, onToggleTrackpad)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        if (trackpadMode) {
            TrackpadPanel(onCommand)
        } else {
            GlassDPad(onCommand)
        }
        Spacer(Modifier.height(18.dp))
        VolumeGlassBar(onCommand)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassCircleButton(RemoteGlyph.Home, RemoteCommand.KEY_HOME, onCommand, Modifier.size(46.dp))
            GlassCircleButton(RemoteGlyph.Back, RemoteCommand.KEY_BACK, onCommand, Modifier.size(46.dp))
        }
    }
}

@Composable
private fun TrackpadToggleButton(trackpadMode: Boolean, onToggleTrackpad: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (trackpadMode) 0.20f else 0.08f))
            .border(1.dp, Color.White.copy(alpha = if (trackpadMode) 0.32f else 0.14f), CircleShape)
            .clickable(onClick = onToggleTrackpad),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.hand_click_icon),
            contentDescription = "Trackpad",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun GlassDPad(onCommand: (RemoteCommand) -> Unit) {
    Box(
        modifier = Modifier
            .size(178.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color(0xFF26313A).copy(alpha = 0.64f),
                        Color.Black.copy(alpha = 0.34f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
    ) {
        DirectionWedge(RemoteGlyph.Up, RemoteCommand.KEY_UP, onCommand, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        DirectionWedge(RemoteGlyph.Down, RemoteCommand.KEY_DOWN, onCommand, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        DirectionWedge(RemoteGlyph.Left, RemoteCommand.KEY_LEFT, onCommand, Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
        DirectionWedge(RemoteGlyph.Right, RemoteCommand.KEY_RIGHT, onCommand, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        GlassCircleButton(
            glyph = null,
            command = RemoteCommand.KEY_ENTER,
            onCommand = onCommand,
            modifier = Modifier
                .align(Alignment.Center)
                .size(70.dp)
        )
    }
}

@Composable
private fun DirectionWedge(
    glyph: RemoteGlyph,
    command: RemoteCommand,
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .holdRemote(command, onCommand),
        contentAlignment = Alignment.Center
    ) {
        RemoteGlyphIcon(glyph, Modifier.size(14.dp), Color.White.copy(alpha = 0.92f), 2.dp)
    }
}

@Composable
private fun VolumeGlassBar(onCommand: (RemoteCommand) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .holdRemote(RemoteCommand.KEY_VOLDOWN, onCommand),
            contentAlignment = Alignment.Center
        ) { RemoteGlyphIcon(RemoteGlyph.Minus, Modifier.size(14.dp), Color.White) }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(34.dp)
                .holdRemote(RemoteCommand.KEY_VOLUP, onCommand),
            contentAlignment = Alignment.Center
        ) { RemoteGlyphIcon(RemoteGlyph.Plus, Modifier.size(14.dp), Color.White) }
    }
}

@Composable
private fun GlassCircleButton(
    glyph: RemoteGlyph?,
    command: RemoteCommand,
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.07f),
                        Color.Black.copy(alpha = 0.08f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
            .holdRemote(command, onCommand),
        contentAlignment = Alignment.Center
    ) {
        glyph?.let { RemoteGlyphIcon(it, Modifier.size(18.dp), Color.White.copy(alpha = 0.94f), 2.dp) }
    }
}

private enum class RemoteGlyph { Menu, Up, Down, Left, Right, Home, Back, Minus, Plus }

@Composable
private fun RemoteGlyphButton(
    glyph: RemoteGlyph,
    onClick: () -> Unit,
    modifier: Modifier,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RemoteGlyphIcon(glyph, Modifier.size(iconSize), Color.White.copy(alpha = 0.92f), 2.2.dp)
    }
}

@Composable
private fun RemoteGlyphIcon(
    glyph: RemoteGlyph,
    modifier: Modifier,
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp
) {
    Canvas(modifier = modifier) {
        val style = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        fun polyline(points: List<Offset>) {
            if (points.isEmpty()) return
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color = color, style = style)
        }

        val w = size.width
        val h = size.height
        when (glyph) {
            RemoteGlyph.Menu -> {
                listOf(0.24f, 0.50f, 0.76f).forEach { y ->
                    drawLine(color, Offset(w * 0.12f, h * y), Offset(w * 0.88f, h * y), strokeWidth.toPx(), StrokeCap.Round)
                }
            }
            RemoteGlyph.Up -> polyline(listOf(Offset(w * 0.18f, h * 0.64f), Offset(w * 0.50f, h * 0.34f), Offset(w * 0.82f, h * 0.64f)))
            RemoteGlyph.Down -> polyline(listOf(Offset(w * 0.18f, h * 0.36f), Offset(w * 0.50f, h * 0.66f), Offset(w * 0.82f, h * 0.36f)))
            RemoteGlyph.Left -> polyline(listOf(Offset(w * 0.64f, h * 0.18f), Offset(w * 0.34f, h * 0.50f), Offset(w * 0.64f, h * 0.82f)))
            RemoteGlyph.Right -> polyline(listOf(Offset(w * 0.36f, h * 0.18f), Offset(w * 0.66f, h * 0.50f), Offset(w * 0.36f, h * 0.82f)))
            RemoteGlyph.Home -> {
                polyline(listOf(Offset(w * 0.14f, h * 0.48f), Offset(w * 0.50f, h * 0.18f), Offset(w * 0.86f, h * 0.48f)))
                polyline(listOf(Offset(w * 0.24f, h * 0.43f), Offset(w * 0.24f, h * 0.84f), Offset(w * 0.76f, h * 0.84f), Offset(w * 0.76f, h * 0.43f)))
            }
            RemoteGlyph.Back -> {
                polyline(listOf(Offset(w * 0.42f, h * 0.22f), Offset(w * 0.14f, h * 0.50f), Offset(w * 0.42f, h * 0.78f)))
                drawLine(color, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), strokeWidth.toPx(), StrokeCap.Round)
            }
            RemoteGlyph.Minus -> drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), strokeWidth.toPx(), StrokeCap.Round)
            RemoteGlyph.Plus -> {
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), strokeWidth.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.82f), strokeWidth.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun TrackpadPanel(onCommand: (RemoteCommand) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .trackpadGestures(onCommand),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.hand_click_icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(36.dp)
        )
    }
}

private fun Modifier.trackpadGestures(onCommand: (RemoteCommand) -> Unit): Modifier =
    pointerInput(Unit) {
        var drag = Offset.Zero
        detectDragGestures(
            onDragStart = { drag = Offset.Zero },
            onDrag = { _, amount -> drag += amount },
            onDragEnd = {
                val command = if (kotlin.math.abs(drag.x) > kotlin.math.abs(drag.y)) {
                    if (drag.x > 0) RemoteCommand.KEY_RIGHT else RemoteCommand.KEY_LEFT
                } else {
                    if (drag.y > 0) RemoteCommand.KEY_DOWN else RemoteCommand.KEY_UP
                }
                onCommand(command)
            }
        )
    }.pointerInput(Unit) {
        detectTapGestures(onTap = { onCommand(RemoteCommand.KEY_ENTER) })
    }

private fun Modifier.holdRemote(command: RemoteCommand, onCommand: (RemoteCommand) -> Unit): Modifier =
    pointerInput(command) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown()
                var job: Job? = launch {
                    onCommand(command)
                    delay(360)
                    while (down.pressed) {
                        onCommand(command)
                        delay(140)
                    }
                }
                waitForUpOrCancellation()
                job?.cancel()
            }
        }
    }

@Composable
private fun AppShortcutRow(
    onOpenApp: (TvAppShortcut) -> Unit,
    onOpenCamera: () -> Unit
) {
    val ordered = listOf("Spotify", "Browser", "DStv", "YouTube").mapNotNull { label ->
        tvAppShortcuts.firstOrNull { it.label == label }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ordered.forEach { app ->
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                    .clickable { onOpenApp(app) },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(app.iconRes()),
                    contentDescription = app.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (app.label == "DStv") 3.dp else 6.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .clickable(onClick = onOpenCamera),
            contentAlignment = Alignment.Center
        ) {
            CameraShortcutIcon()
        }
    }
}

@Composable
private fun CameraShortcutIcon() {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val body = androidx.compose.ui.geometry.Rect(
            left = size.width * 0.08f,
            top = size.height * 0.28f,
            right = size.width * 0.92f,
            bottom = size.height * 0.82f
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = body.topLeft,
            size = body.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = Color(0xFF55D6FF),
            radius = size.minDimension * 0.14f,
            center = Offset(size.width * 0.53f, size.height * 0.55f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = Color.White.copy(alpha = 0.92f),
            start = Offset(size.width * 0.28f, size.height * 0.28f),
            end = Offset(size.width * 0.38f, size.height * 0.15f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.92f),
            start = Offset(size.width * 0.38f, size.height * 0.15f),
            end = Offset(size.width * 0.58f, size.height * 0.15f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.92f),
            start = Offset(size.width * 0.58f, size.height * 0.15f),
            end = Offset(size.width * 0.68f, size.height * 0.28f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun TvAppShortcut.iconRes(): Int =
    when (label) {
        "YouTube" -> R.drawable.youtube_logo
        "DStv" -> R.drawable.dstv_logo
        "Browser" -> R.drawable.chrome_logo
        "Spotify" -> R.drawable.spotify_logo
        else -> R.drawable.app_logo
    }

@Composable
private fun RemoteMenuSheet(onDismiss: () -> Unit, onOpen: (Screen) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .width(270.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF101620).copy(alpha = 0.94f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("D Remote", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            listOf(Screen.Gestures, Screen.Setup, Screen.Keyboard, Screen.YouTube, Screen.Browser, Screen.Macros, Screen.Settings, Screen.Help).forEach { item ->
                Text(
                    item.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpen(item) }
                        .padding(12.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun LiveIsland(
    status: String,
    settings: TvSettings,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(244.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF080A0D).copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Text(if (status == "Ready") "Connected" else status, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(settings.tvIp, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Connect") }
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text("Stop") }
        }
    }
}

@Composable
private fun HiddenScreenHost(
    screen: Screen,
    settings: TvSettings,
    macros: List<Macro>,
    youtubeResults: List<YouTubeResult>,
    viewModel: RemoteViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTextButton("‹", fontSize = 42.sp, onClick = onBack)
            Text(screen.label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        when (screen) {
            Screen.Setup -> SetupScreen(settings, viewModel)
            Screen.Keyboard -> KeyboardScreen(viewModel)
            Screen.YouTube -> YouTubeScreen(settings, youtubeResults, viewModel)
            Screen.Browser -> BrowserScreen(viewModel)
            Screen.Macros -> MacroScreen(macros, viewModel)
            Screen.Settings -> SettingsScreen(settings, viewModel)
            Screen.Help -> TroubleshootingScreen(settings)
            Screen.Gestures -> Unit
            Screen.Remote -> Unit
        }
    }
}

@Composable
private fun IconTextButton(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(56.dp)
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = 0.90f), fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RemoteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF171A20),
                        Color(0xFF0B0D11),
                        Color(0xFF14100F)
                    )
                )
            )
    )
}

@Composable
private fun SetupScreen(settings: TvSettings, viewModel: RemoteViewModel) {
    var draft by remember(settings) { mutableStateOf(settings) }
    ScreenContainer {
        SectionCard {
            Text("TV setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SettingsFields(draft) { draft = it }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.saveSettings(draft) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save setup")
            }
        }
    }
}

@Composable
private fun KeyboardScreen(viewModel: RemoteViewModel) {
    var text by remember { mutableStateOf("") }
    ScreenContainer {
        SectionCard {
            Text("Keyboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Direct text works on some Samsung TVs. If ignored, use the quick assisted buttons below.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text to send") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.sendText(text) }, modifier = Modifier.weight(1f)) { Text("Send text") }
                OutlinedButton(onClick = { text = "" }, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { viewModel.sendKey(RemoteCommand.KEY_BACK) }, modifier = Modifier.weight(1f)) { Text("Backspace") }
                OutlinedButton(onClick = { viewModel.sendText(" ") }, modifier = Modifier.weight(1f)) { Text("Space") }
                OutlinedButton(onClick = { viewModel.sendKey(RemoteCommand.KEY_ENTER) }, modifier = Modifier.weight(1f)) { Text("Enter") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { viewModel.sendKey(RemoteCommand.KEY_SEARCH) }, modifier = Modifier.weight(1f)) { Text("Search focus") }
                OutlinedButton(onClick = { viewModel.sendDigitOrFallback(text) }, modifier = Modifier.weight(1f)) { Text("Fallback keys") }
            }
        }
    }
}

@Composable
private fun YouTubeScreen(settings: TvSettings, results: List<YouTubeResult>, viewModel: RemoteViewModel) {
    var query by remember { mutableStateOf("") }
    ScreenContainer {
        SectionCard {
            Text("YouTube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AssistChip(onClick = {}, label = { Text(settings.youtubeMode.label) })
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search videos or channels") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        viewModel.sendKey(RemoteCommand.KEY_HOME)
                        viewModel.sendKey(RemoteCommand.KEY_CONTENTS)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Open YouTube flow") }
                OutlinedButton(onClick = { viewModel.sendText(query) }, modifier = Modifier.weight(1f)) { Text("Send search") }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.sendKey(RemoteCommand.KEY_ENTER) }, modifier = Modifier.fillMaxWidth()) { Text("OK / Search") }
        }

        if (settings.youtubeMode == YouTubeMode.API && settings.youtubeApiKey.isNotBlank()) {
            SectionCard {
                Text("API search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.searchYouTube(query) }, modifier = Modifier.fillMaxWidth()) { Text("Search YouTube API") }
                Spacer(Modifier.height(8.dp))
                results.forEach { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .border(1.dp, Color(0xFFE1E5EE), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(result.title, fontWeight = FontWeight.Bold)
                        Text(result.channel, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { viewModel.sendText(result.title) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Search this on TV")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserScreen(viewModel: RemoteViewModel) {
    var value by remember { mutableStateOf("") }
    ScreenContainer {
        SectionCard {
            Text("Browser", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Uses remote automation because Samsung TVs do not expose a general browser API.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("URL or search") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.sendText(value) }, modifier = Modifier.weight(1f)) { Text("Send to TV") }
                OutlinedButton(onClick = { viewModel.sendKey(RemoteCommand.KEY_ENTER) }, modifier = Modifier.weight(1f)) { Text("Enter") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { viewModel.sendKey(RemoteCommand.KEY_BACK) }, modifier = Modifier.weight(1f)) { Text("Back") }
                OutlinedButton(onClick = { viewModel.sendKey(RemoteCommand.KEY_FF) }, modifier = Modifier.weight(1f)) { Text("Forward") }
                OutlinedButton(onClick = { viewModel.sendKey(RemoteCommand.KEY_HOME) }, modifier = Modifier.weight(1f)) { Text("Home") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroScreen(macros: List<Macro>, viewModel: RemoteViewModel) {
    var macroId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var macroName by remember { mutableStateOf("") }
    var selectedCommand by remember { mutableStateOf(RemoteCommand.KEY_HOME) }
    var delayText by remember { mutableStateOf("500") }
    val steps = remember { mutableStateListOf<MacroStep>() }
    var expanded by remember { mutableStateOf(false) }

    ScreenContainer {
        SectionCard {
            Text("Saved macros", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            macros.forEach { macro ->
                Divider(Modifier.padding(vertical = 8.dp))
                Text(macro.name, fontWeight = FontWeight.Bold)
                Text(macro.commands.joinToString("  •  ") { it.command }, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.runMacro(macro) }, modifier = Modifier.weight(1f)) { Text("Run") }
                    OutlinedButton(
                        onClick = {
                            macroId = macro.id
                            macroName = macro.name
                            steps.clear()
                            steps.addAll(macro.commands)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Edit") }
                    TextButton(onClick = { viewModel.deleteMacro(macro) }) { Text("Delete") }
                }
            }
        }

        SectionCard {
            Text("Create or edit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = macroName,
                onValueChange = { macroName = it },
                label = { Text("Macro name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedCommand.key,
                    onValueChange = {},
                    label = { Text("Command") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    RemoteCommand.entries.forEach { command ->
                        DropdownMenuItem(
                            text = { Text(command.key) },
                            onClick = {
                                selectedCommand = command
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = delayText,
                onValueChange = { delayText = it },
                label = { Text("Delay after command (ms)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { steps.add(MacroStep(selectedCommand.key, delayText.toLongOrNull() ?: 500)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Add step") }
                TextButton(onClick = { steps.clear() }, modifier = Modifier.weight(1f)) { Text("Clear steps") }
            }
            Spacer(Modifier.height(8.dp))
            Text(steps.joinToString("  •  ") { "${it.command} (${it.delayMs})" }.ifBlank { "No steps yet." })
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (macroName.isNotBlank() && steps.isNotEmpty()) {
                        viewModel.upsertMacro(Macro(macroId, macroName, steps.toList()))
                        macroId = UUID.randomUUID().toString()
                        macroName = ""
                        steps.clear()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save macro") }
        }
    }
}

@Composable
private fun SettingsScreen(settings: TvSettings, viewModel: RemoteViewModel) {
    var draft by remember(settings) { mutableStateOf(settings) }
    ScreenContainer {
        SectionCard {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SettingsFields(draft) { draft = it }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Use YouTube API mode", modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.youtubeMode == YouTubeMode.API,
                    onCheckedChange = { draft = draft.copy(youtubeMode = if (it) YouTubeMode.API else YouTubeMode.AUTOMATION) }
                )
            }
            OutlinedTextField(
                value = draft.youtubeApiKey,
                onValueChange = { draft = draft.copy(youtubeApiKey = it) },
                label = { Text("YouTube API key (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.saveSettings(draft) }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
        }
    }
}

@Composable
private fun SettingsFields(draft: TvSettings, onChange: (TvSettings) -> Unit) {
    OutlinedTextField(
        value = draft.tvName,
        onValueChange = { onChange(draft.copy(tvName = it)) },
        label = { Text("TV name") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = draft.tvIp,
        onValueChange = { onChange(draft.copy(tvIp = it)) },
        label = { Text("TV IP address") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = draft.tvMac,
        onValueChange = { onChange(draft.copy(tvMac = it)) },
        label = { Text("TV MAC address") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("auto", "ws", "wss").forEach { protocol ->
            FilterChip(
                selected = draft.preferredProtocol == protocol,
                onClick = { onChange(draft.copy(preferredProtocol = protocol)) },
                label = { Text(protocol.uppercase()) }
            )
        }
    }
}

@Composable
private fun TroubleshootingScreen(settings: TvSettings) {
    ScreenContainer {
        SectionCard {
            Text("Troubleshooting", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("TV: ${settings.tvName}")
            Text("IP: ${settings.tvIp}")
            Text("MAC: ${settings.tvMac}")
            Text("Model: ${settings.modelCode}")
            Text("Serial: ${settings.serialNumber}")
            Text("Software: ${settings.softwareVersion}")
            Divider(Modifier.padding(vertical = 12.dp))
            listOf(
                "Make sure the phone and TV are on the same Wi-Fi.",
                "Make sure both devices are on the 192.168.1.x subnet.",
                "Turn the TV on before first pairing.",
                "If the TV shows a device approval prompt, approve D Remote.",
                "On the TV, look for Device Connect Manager or Mobile Device Manager and remove old Pocket Samsung Remote approvals if pairing gets stuck.",
                "If the TV changed IP address, update Setup.",
                "If WSS fails, try AUTO or WS in Settings.",
                "Restart the TV if it rejected the pairing token.",
                "Use SmartThings as a sanity check that local control is allowed."
            ).forEach { item ->
                Text("- $item", modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun ScreenContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}
