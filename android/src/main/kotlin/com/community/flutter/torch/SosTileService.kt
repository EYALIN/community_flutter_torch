package com.community.flutter.torch

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings "SOS" tile (opt-in, declared by the app). A tap opens the app on its SOS screen through
 * the deep link the declaration names in its [META_SOS_LINK] meta-data, and the app runs the pattern
 * while it is in the foreground. The plugin blinks nothing in the background (no foreground service).
 *
 * Without a torch, without the meta-data, or when no activity of the app handles the link, the tile
 * shows as unavailable instead of doing nothing. `open`, like the torch tile.
 */
open class SosTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val intent = sosIntent()
        if (intent == null) {
            render()
            return
        }
        if (isLocked) unlockAndRun { open(intent) } else open(intent)
    }

    private fun open(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (_: Exception) {
            render()
        }
    }

    /** The app's SOS deep link as an explicit-package VIEW intent, or null when the tile cannot work. */
    private fun sosIntent(): Intent? {
        if (TorchCamera.id(this) == null) return null
        val link = TorchCamera.serviceMetaData(this, ComponentName(this, javaClass), META_SOS_LINK) ?: return null
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return if (intent.resolveActivity(packageManager) != null) intent else null
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.label = TorchCamera.string(this, "flashlight_sos_tile_label", "SOS")
        val icon = TorchCamera.res(this, "drawable", "flashlight_ic_sos")
        tile.icon = Icon.createWithResource(this, if (icon != 0) icon else TorchCamera.torchIcon(this))
        tile.state = if (sosIntent() == null) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        /** The `<meta-data>` of the tile's `<service>` naming the deep link that opens the app on SOS. */
        const val META_SOS_LINK = "com.community.flutter.torch.SOS_LINK"
    }
}
