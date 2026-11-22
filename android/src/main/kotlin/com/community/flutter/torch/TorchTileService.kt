package com.community.flutter.torch

import android.graphics.drawable.Icon
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings "Flashlight" tile. Declared in the app manifest that opts in (see README); `open`, so
 * an app can declare a subclass under the name its tile had before (users keep the added tile).
 * The torch stays on after the panel closes (CameraManager.setTorchMode), with no service: Android
 * switches it off only if the app's process dies.
 */
open class TorchTileService : TileService() {
    private val main = Handler(Looper.getMainLooper())
    private var callback: CameraManager.TorchCallback? = null
    private var cameraId: String? = null
    private var torchOn = false
    private var available = false

    override fun onStartListening() {
        super.onStartListening()
        cameraId = TorchCamera.id(this)
        available = cameraId != null
        if (cameraId != null && callback == null) {
            callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id != cameraId) return
                    torchOn = enabled
                    available = true
                    render()
                }

                override fun onTorchModeUnavailable(id: String) {
                    if (id != cameraId) return
                    torchOn = false
                    available = false
                    render()
                }
            }
            try {
                TorchCamera.manager(this).registerTorchCallback(callback!!, main)
            } catch (e: Exception) {
                callback = null
            }
        }
        render()
    }

    override fun onStopListening() {
        callback?.let {
            try { TorchCamera.manager(this).unregisterTorchCallback(it) } catch (_: Exception) {}
        }
        callback = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (cameraId == null || !available) {
            render()
            return
        }
        val target = !torchOn
        // A manual switch replaces a scheduled auto-off of the app's timer.
        TorchAutoOff.cancel(this)
        try {
            TorchCamera.setTorch(this, target)
            torchOn = target
        } catch (_: Exception) {
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.label = TorchCamera.string(this, "flashlight_tile_label", "Flashlight")
        tile.icon = Icon.createWithResource(this, TorchCamera.torchIcon(this))
        tile.state = when {
            cameraId == null || !available -> Tile.STATE_UNAVAILABLE
            torchOn -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
