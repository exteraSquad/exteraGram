package com.exteragram.messenger.extras

import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors

object Monet {
    val default = Color.argb(0xFF, 0x3D, 0xDC, 0x84)

    @ColorInt
    @JvmStatic
    fun getAccentColor(ctx: Context): Int {
        return when {
            Build.VERSION.SDK_INT >= 31 -> {
                ContextCompat.getColor(ctx, android.R.color.system_accent1_200)
            }
            else -> {
                reqAttrFromDevice(ctx, android.R.attr.colorAccent, default, 29)
            }
        }
    }

    @ColorInt
    @JvmStatic
    fun getBackgroundColor(ctx: Context): Int {
        return reqAttrFromDevice(ctx, android.R.attr.colorBackground, -1, 31)
    }

    @ColorInt
    @JvmStatic
    fun getAccentColorDark(ctx: Context): Int {
        if (Build.VERSION.SDK_INT < 31) return getAccentColor(ctx)
        return reqAttrFromDeviceDark(ctx, org.telegram.messenger.R.attr.colorPrimaryDark, default, 29)
    }

    @ColorInt
    @JvmStatic
    fun getBackgroundColorDark(ctx: Context): Int {
        if (Build.VERSION.SDK_INT < 31) return getBackgroundColor(ctx)
        return reqAttrFromDeviceDark(ctx, org.telegram.messenger.R.attr.colorSecondary, -1, 31)
    }

    @ColorInt
    private fun reqAttrFromDevice(ctx: Context, @AttrRes attr: Int, default: Int, minApi: Int): Int {
        if (Build.VERSION.SDK_INT < minApi) return default
        return MaterialColors.getColor(getCtx(ctx), attr, default)
    }

    @ColorInt
    private fun reqAttrFromDeviceDark(ctx: Context, @AttrRes attr: Int, default: Int, minApi: Int): Int {
        if (Build.VERSION.SDK_INT < minApi) return default
        return MaterialColors.getColor(getCtxDark(ctx), attr, default)
    }

    private fun getCtx(ctx: Context) = ContextThemeWrapper(ctx, android.R.style.Theme_DeviceDefault_Light)

    private fun getCtxDark(ctx: Context) = ContextThemeWrapper(ctx, org.telegram.messenger.R.style.Theme_Material3_DynamicColors_Dark)

}