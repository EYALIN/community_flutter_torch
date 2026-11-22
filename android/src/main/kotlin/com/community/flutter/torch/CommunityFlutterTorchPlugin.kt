package com.community.flutter.torch

import android.app.StatusBarManager
import android.content.Context
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMethodCodec

/**
 * Torch control with no service: CameraManager.setTorchMode keeps the torch on while the app is in the
 * background. Blink patterns (SOS, strobe) are the app's job while it is in the foreground; a timed
 * steady light is switched off by an AlarmManager alarm ([TorchAutoOff]).
 *
 * Every method call runs on a background task queue, never on the main thread: the camera lookup
 * (cameraIdList + getCameraCharacteristics), setTorchMode and the auto-off alarm are binder calls into
 * the camera / package / alarm services, which can stall for seconds while the camera HAL is busy. The
 * queue is serial, so calls still run in the order Dart sent them (a strobe's on/off stays in order).
 * The camera is looked up on the first call, not when the engine attaches (that was on every cold start).
 */
class CommunityFlutterTorchPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var appContext: Context

    private lateinit var channel: MethodChannel
    private lateinit var events: EventChannel
    private var cameraManager: CameraManager? = null

    /** The torch camera, looked up on the first call (background queue) and read by the main-thread callback. */
    @Volatile
    private var cameraId: String? = null

    /** FLASH_INFO_STRENGTH_MAXIMUM_LEVEL, read once: every blink of a strobe asked the camera service again. */
    private var maxStrengthCache: Int? = null
    private var sink: EventChannel.EventSink? = null
    private val main = Handler(Looper.getMainLooper())

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) main.post { sink?.success(enabled) }
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        cameraManager = binding.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        channel = MethodChannel(
            binding.binaryMessenger,
            "community_flutter_torch",
            StandardMethodCodec.INSTANCE,
            binding.binaryMessenger.makeBackgroundTaskQueue(),
        )
        channel.setMethodCallHandler(this)
        events = EventChannel(binding.binaryMessenger, "community_flutter_torch/state")
        events.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        events.setStreamHandler(null)
        onCancel(null)
    }

    /** The back-facing camera with a flash unit, any camera with a flash as a fallback ([TorchCamera.id]). */
    private fun torchCamera(): String? = cameraId ?: TorchCamera.id(appContext).also { cameraId = it }

    private fun maxStrength(): Int {
        maxStrengthCache?.let { return it }
        val cm = cameraManager ?: return 1
        val id = torchCamera() ?: return 1
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return try {
            (cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1)
                .also { maxStrengthCache = it }
        } catch (e: Exception) {
            1
        }
    }

    // Runs on the background task queue (see the class comment).
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val cm = cameraManager
        val id = torchCamera()
        when (call.method) {
            "isAvailable" -> result.success(id != null)
            "maxStrength" -> result.success(maxStrength())
            "switchOn" -> {
                if (cm == null || id == null) return result.error("NO_TORCH", "This device has no torch", null)
                try {
                    val max = maxStrength()
                    val strength = call.argument<Int>("strength")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && max > 1 && strength != null) {
                        cm.turnOnTorchWithStrengthLevel(id, strength.coerceIn(1, max))
                    } else {
                        cm.setTorchMode(id, true)
                    }
                    // Every switch-on replaces the scheduled off: a timed one arms it again, a plain one clears it.
                    val seconds = call.argument<Int>("autoOffSeconds") ?: 0
                    if (seconds > 0) TorchAutoOff.schedule(appContext, seconds) else TorchAutoOff.cancel(appContext)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("TORCH_ERROR", e.message, null)
                }
            }
            "switchOff" -> {
                if (cm == null || id == null) return result.success(null)
                try {
                    TorchAutoOff.cancel(appContext)
                    cm.setTorchMode(id, false)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("TORCH_ERROR", e.message, null)
                }
            }
            "getFeatures" -> result.success(features())
            "requestAddTile" -> requestAddTile(result)
            else -> result.notImplemented()
        }
    }

    private fun features(): Map<String, Any?> {
        val qs = TorchCamera.declaredService(appContext, TorchTileService::class.java) != null
        val sosTile = TorchCamera.declaredService(appContext, SosTileService::class.java)
        val sos = sosTile != null &&
            TorchCamera.serviceMetaData(appContext, sosTile, SosTileService.META_SOS_LINK) != null
        return mapOf(
            "qsTile" to qs,
            "sosTile" to sos,
            "autoOff" to TorchAutoOff.isDeclared(appContext),
            "canRequestAddTile" to (qs && Build.VERSION.SDK_INT >= 33),
        )
    }

    /** Resolves with StatusBarManager.TILE_ADD_REQUEST_RESULT_* (0 not added, 1 already added, 2 added). */
    private fun requestAddTile(result: MethodChannel.Result) {
        val tile = TorchCamera.declaredService(appContext, TorchTileService::class.java)
            ?: return result.error("NEEDS_QS_TILE", "The app manifest does not declare TorchTileService", null)
        if (Build.VERSION.SDK_INT < 33) {
            return result.error("UNSUPPORTED", "requestAddTile needs Android 13 (API 33)", null)
        }
        try {
            val sbm = appContext.getSystemService(StatusBarManager::class.java)
                ?: return result.error("NO_STATUS_BAR", "no status bar service", null)
            sbm.requestAddTileService(
                tile,
                TorchCamera.string(appContext, "flashlight_tile_label", "Flashlight"),
                Icon.createWithResource(appContext, TorchCamera.torchIcon(appContext)),
                appContext.mainExecutor,
            ) { code -> result.success(code) }
        } catch (e: Exception) {
            result.error("TILE_ERROR", e.message, null)
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sink = events
        cameraManager?.registerTorchCallback(torchCallback, main)
    }

    override fun onCancel(arguments: Any?) {
        sink = null
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }
}
