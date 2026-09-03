package com.fan.edgex.overlay

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.TetheringManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.util.concurrent.Executor
import kotlin.math.roundToInt

internal data class QuickSettingsState(
    val wifi: Boolean,
    val bluetooth: Boolean,
    val airplane: Boolean,
    val hotspot: Boolean,
    val doNotDisturb: Boolean,
    val flashlight: Boolean,
    val rotationLocked: Boolean,
    val brightness: Float,
    val volume: Float,
    val mediaArtwork: Bitmap?,
    val mediaAvailable: Boolean,
    val mediaTitle: String?,
    val mediaPlaying: Boolean,
)

internal class QuickSettingsController(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { handler.post(it) }
    private val wifiManager = context.getSystemService(WifiManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val tetheringManager = context.getSystemService(TetheringManager::class.java)
    private val mediaSessionManager = context.getSystemService(MediaSessionManager::class.java)
    private var callback: ((QuickSettingsState) -> Unit)? = null
    private var activeMediaController: MediaController? = null
    private var torchEnabled = false
    private var receiverRegistered = false
    private var torchCallbackRegistered = false
    private var mediaSessionListenerRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            publishState()
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == flashlightCameraId()) {
                torchEnabled = enabled
                publishState()
            }
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishState()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishState()
        override fun onSessionDestroyed() {
            attachMediaController(null)
            publishState()
        }
    }

    private val mediaSessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachMediaController(controllers?.firstOrNull())
        publishState()
    }

    fun start(onStateChanged: (QuickSettingsState) -> Unit) {
        callback = onStateChanged
        if (!receiverRegistered) {
            runCatching {
                context.registerReceiver(receiver, IntentFilter().apply {
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                    addAction("android.media.VOLUME_CHANGED_ACTION")
                })
                receiverRegistered = true
            }
        }
        if (!torchCallbackRegistered) {
            runCatching {
                cameraManager?.registerTorchCallback(torchCallback, handler)
                torchCallbackRegistered = true
            }
        }
        if (!mediaSessionListenerRegistered) {
            runCatching {
                val controller = mediaSessionManager?.getActiveSessions(null)?.firstOrNull()
                attachMediaController(controller)
                mediaSessionManager?.addOnActiveSessionsChangedListener(
                    mediaSessionsChangedListener,
                    null,
                    handler,
                )
                mediaSessionListenerRegistered = mediaSessionManager != null
            }
        }
        publishState()
    }

    fun stop() {
        if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        if (torchCallbackRegistered) runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
        if (mediaSessionListenerRegistered) {
            runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(mediaSessionsChangedListener) }
        }
        attachMediaController(null)
        receiverRegistered = false
        torchCallbackRegistered = false
        mediaSessionListenerRegistered = false
        callback = null
    }

    fun toggleWifi() {
        runCatching { wifiManager?.isWifiEnabled?.let { wifiManager.isWifiEnabled = !it } }
        publishSoon()
    }

    fun toggleBluetooth() {
        runCatching {
            BluetoothAdapter.getDefaultAdapter()?.let { adapter ->
                if (adapter.isEnabled) adapter.disable() else adapter.enable()
            }
        }
        publishSoon()
    }

    fun toggleAirplaneMode() {
        val enabled = !isAirplaneModeEnabled()
        runCatching {
            Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (enabled) 1 else 0)
            context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled))
        }
        publishSoon()
    }

    fun toggleHotspot() {
        runCatching {
            val request = TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI).build()
            if (isHotspotEnabled()) {
                tetheringManager?.stopTethering(
                    request,
                    mainExecutor,
                    object : TetheringManager.StopTetheringCallback {
                        override fun onStopTetheringSucceeded() = publishState()
                        override fun onStopTetheringFailed(error: Int) = publishState()
                    },
                )
            } else {
                tetheringManager?.startTethering(
                    request,
                    mainExecutor,
                    object : TetheringManager.StartTetheringCallback {
                        override fun onTetheringStarted() = publishState()
                        override fun onTetheringFailed(error: Int) = publishState()
                    },
                )
            }
        }
        publishSoon()
    }

    fun toggleDoNotDisturb() {
        runCatching {
            val enabled = notificationManager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            notificationManager?.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            )
        }
        publishSoon()
    }

    fun toggleFlashlight() {
        val cameraId = flashlightCameraId() ?: return
        runCatching { cameraManager?.setTorchMode(cameraId, !torchEnabled) }
        publishSoon()
    }

    fun toggleRotationLock() {
        runCatching {
            val enabled = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (enabled) 0 else 1)
        }
        publishSoon()
    }

    fun launchCamera() {
        runCatching {
            context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun setBrightness(value: Float) {
        val (minimum, maximum) = brightnessRange()
        val level = (minimum + value.coerceIn(0f, 1f) * (maximum - minimum))
        runCatching {
            val displayManager = context.getSystemService(DisplayManager::class.java) ?: return
            displayManager.javaClass.getMethod("setBrightness", Int::class.java, Float::class.java)
                .invoke(displayManager, 0, level)
        }
    }

    fun setVolume(value: Float) {
        val audio = audioManager ?: return
        val minimum = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = (minimum + value.coerceIn(0f, 1f) * (maximum - minimum)).roundToInt()
        audio.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            volume.coerceIn(minimum, maximum),
            0,
        )
    }

    fun mediaPrevious() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun mediaPlayPause() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun mediaNext() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)

    private fun dispatchMediaKey(keyCode: Int) {
        runCatching {
            val now = SystemClock.uptimeMillis()
            audioManager?.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0))
        }
        publishSoon()
    }

    private fun attachMediaController(controller: MediaController?) {
        if (activeMediaController?.sessionToken == controller?.sessionToken) return
        activeMediaController?.let { current ->
            runCatching { current.unregisterCallback(mediaControllerCallback) }
        }
        activeMediaController = controller
        controller?.let { current ->
            runCatching { current.registerCallback(mediaControllerCallback, handler) }
        }
    }

    private fun publishSoon() {
        handler.postDelayed(::publishState, STATE_SETTLE_DELAY_MS)
    }

    private fun publishState() {
        callback?.invoke(readState())
    }

    private fun readState(): QuickSettingsState = QuickSettingsState(
        wifi = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false),
        bluetooth = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false),
        airplane = isAirplaneModeEnabled(),
        hotspot = isHotspotEnabled(),
        doNotDisturb = runCatching {
            notificationManager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false),
        flashlight = torchEnabled,
        rotationLocked = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
        }.getOrDefault(false),
        brightness = readBrightness(),
        volume = readVolume(),
        mediaArtwork = readMediaArtwork(),
        mediaAvailable = activeMediaController != null,
        mediaTitle = readMediaTitle(),
        mediaPlaying = activeMediaController?.playbackState?.state in PLAYING_MEDIA_STATES,
    )

    private fun readMediaArtwork(): Bitmap? = activeMediaController?.metadata?.let { metadata ->
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    private fun readMediaTitle(): String? = activeMediaController?.metadata?.let { metadata ->
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        when {
            !title.isNullOrBlank() && !artist.isNullOrBlank() -> "$title · $artist"
            !title.isNullOrBlank() -> title
            !artist.isNullOrBlank() -> artist
            else -> null
        }
    }

    private fun isAirplaneModeEnabled(): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    }.getOrDefault(false)

    private fun isHotspotEnabled(): Boolean = runCatching {
        val method = wifiManager?.javaClass?.getMethod("getWifiApState") ?: return@runCatching false
        (method.invoke(wifiManager) as? Int) == WIFI_AP_STATE_ENABLED
    }.getOrDefault(false)

    private fun readBrightness(): Float = runCatching {
        val displayManager = context.getSystemService(DisplayManager::class.java)
            ?: return@runCatching DEFAULT_LEVEL
        val current = (displayManager.javaClass.getMethod("getBrightness", Int::class.java)
            .invoke(displayManager, 0) as Float).takeUnless(Float::isNaN)
            ?: return@runCatching DEFAULT_LEVEL
        val (minimum, maximum) = brightnessRange()
        ((current - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
    }.getOrDefault(DEFAULT_LEVEL)

    private fun readVolume(): Float {
        val audio = audioManager ?: return DEFAULT_LEVEL
        val minimum = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val range = (maximum - minimum).coerceAtLeast(1)
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC) - minimum).toFloat() / range
    }

    private fun brightnessRange(): Pair<Float, Float> = runCatching {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(0)
            ?: return@runCatching 0f to 1f
        val info = display.javaClass.getMethod("getBrightnessInfo").invoke(display)
        val minimum = info.javaClass.getField("brightnessMinimum").getFloat(info)
            .takeUnless(Float::isNaN) ?: 0f
        val maximum = info.javaClass.getField("brightnessMaximum").getFloat(info)
            .takeUnless(Float::isNaN) ?: 1f
        minimum to maximum.coerceAtLeast(minimum + 0.001f)
    }.getOrDefault(0f to 1f)

    private fun flashlightCameraId(): String? = runCatching {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    private companion object {
        const val WIFI_AP_STATE_ENABLED = 13
        val PLAYING_MEDIA_STATES = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)
        const val STATE_SETTLE_DELAY_MS = 350L
        const val DEFAULT_LEVEL = 0.5f
    }
}
