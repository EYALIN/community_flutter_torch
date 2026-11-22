package com.community.flutter.torch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/** Torch helpers shared by the plugin, the Quick Settings tiles and the auto-off receiver. */
internal object TorchCamera {
    private var resolved = false
    private var cached: String? = null

    fun manager(ctx: Context): CameraManager =
        ctx.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** The torch camera id (back-facing with a flash, else any with a flash), or null. */
    @Synchronized
    fun id(ctx: Context): String? {
        if (resolved) return cached
        return try {
            val cm = manager(ctx)
            val withFlash = cm.cameraIdList.filter {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cached = withFlash.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: withFlash.firstOrNull()
            resolved = true
            cached
        } catch (e: Exception) {
            null
        }
    }

    fun setTorch(ctx: Context, on: Boolean) {
        val id = id(ctx) ?: throw IllegalStateException("no flash unit")
        manager(ctx).setTorchMode(id, on)
    }

    /**
     * The host app's declared service whose class is [base] or extends it, or null. The tile classes
     * are open: an app may declare a subclass under another name (e.g. the class names of the Cordova
     * plugin, so the tiles users added survive the update), so they are never addressed by a fixed class.
     */
    fun declaredService(ctx: Context, base: Class<*>): ComponentName? = declared(ctx, base, PackageManager.GET_SERVICES)

    /** The host app's declared receiver whose class is [base] or extends it, or null. */
    fun declaredReceiver(ctx: Context, base: Class<*>): ComponentName? = declared(ctx, base, PackageManager.GET_RECEIVERS)

    @Suppress("DEPRECATION")
    private fun declared(ctx: Context, base: Class<*>, flag: Int): ComponentName? {
        val pkg = ctx.packageName
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flag.toLong()))
            } else {
                ctx.packageManager.getPackageInfo(pkg, flag)
            }
        } catch (e: Exception) {
            null
        } ?: return null
        val components = (if (flag == PackageManager.GET_RECEIVERS) info.receivers else info.services) ?: return null
        for (c in components) {
            val cls = try {
                Class.forName(c.name, false, ctx.classLoader)
            } catch (e: Throwable) {
                continue
            }
            if (base.isAssignableFrom(cls)) return ComponentName(pkg, c.name)
        }
        return null
    }

    /** A `<meta-data>` string of the host app's declaration of [component], or null. */
    @Suppress("DEPRECATION")
    fun serviceMetaData(ctx: Context, component: ComponentName, name: String): String? = try {
        val si = if (Build.VERSION.SDK_INT >= 33) {
            ctx.packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            ctx.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        }
        si.metaData?.getString(name)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    fun res(ctx: Context, type: String, name: String): Int =
        ctx.resources.getIdentifier(name, type, ctx.packageName)

    fun string(ctx: Context, name: String, fallback: String): String {
        val id = res(ctx, "string", name)
        if (id == 0) return fallback
        return try { ctx.getString(id) } catch (e: Exception) { fallback }
    }

    fun torchIcon(ctx: Context): Int {
        val id = res(ctx, "drawable", "flashlight_ic_torch")
        return if (id != 0) id else ctx.applicationInfo.icon
    }
}
