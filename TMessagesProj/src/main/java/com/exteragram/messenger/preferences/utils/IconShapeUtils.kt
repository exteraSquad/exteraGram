/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.utils

import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.PathParser
import com.exteragram.messenger.useSystemIconShape
import org.telegram.messenger.AndroidUtilities.dpf2
import org.telegram.messenger.FileLog
import kotlin.math.max
import kotlin.math.min

object IconShapeHelper {

    private const val DEFAULT_CIRCLE_PATH = "M50,0A50,50,0,0,1,50,100A50,50,0,0,1,50,0"

    private var cachedSystemPath: Path? = null
    private var isSystemPathSquare: Boolean = false
    private var cacheInitialized = false

    private val scratchRect = RectF()
    private val scratchMatrix = Matrix()

    fun getFinalIconShapePath(width: Float, height: Float, cornerRadius: Float = 0f): Path {
        val useSystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && useSystemIconShape

        if (useSystem && !cacheInitialized) {
            initSystemPathCache()
        }

        val sourcePath = if (useSystem) cachedSystemPath ?: getDefaultPath() else getDefaultPath()

        val targetW = dpf2(width)
        val targetH = dpf2(height)
        val radiusPx = dpf2(cornerRadius)

        if (radiusPx > 0f && useSystem && isSystemPathSquare) {
            val roundRectPath = Path()
            scratchRect.set(0f, 0f, targetW, targetH)
            roundRectPath.addRoundRect(
                scratchRect,
                radiusPx,
                radiusPx,
                Path.Direction.CW
            )
            return roundRectPath
        }

        return resizePath(sourcePath, targetW, targetH)
    }

    private fun resizePath(path: Path?, width: Float, height: Float): Path {
        val resultPath = Path()
        if (path == null || path.isEmpty || width <= 0 || height <= 0) {
            return resultPath
        }

        path.computeBounds(scratchRect, true)

        if (scratchRect.width() <= 0 || scratchRect.height() <= 0) {
            return resultPath
        }

        scratchMatrix.reset()
        val destRect = RectF(0f, 0f, width, height)
        scratchMatrix.setRectToRect(scratchRect, destRect, Matrix.ScaleToFit.FILL)

        path.transform(scratchMatrix, resultPath)

        return resultPath
    }

    private fun getDefaultPath(): Path {
        return PathParser.createPathFromPathData(DEFAULT_CIRCLE_PATH)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initSystemPathCache() {
        try {
            var path: Path? = null

            val transparentDrawable = 0.toDrawable()
            val adaptiveIconDrawable = AdaptiveIconDrawable(transparentDrawable, transparentDrawable)
            val iconMask = adaptiveIconDrawable.iconMask
            if (iconMask != null && !iconMask.isEmpty) {
                path = Path(iconMask)
            }

            if (path == null) {
                val systemRes = Resources.getSystem()
                val resId = systemRes.getIdentifier("config_icon_mask", "string", "android")
                if (resId != 0) {
                    val pathString = systemRes.getString(resId)
                    if (!TextUtils.isEmpty(pathString)) {
                        path = PathParser.createPathFromPathData(pathString)
                    }
                }
            }

            if (path != null && !path.isEmpty) {
                cachedSystemPath = path
                val bounds = RectF()
                path.computeBounds(bounds, true)
                isSystemPathSquare = calculateIfShouldUseRoundedRect(path, bounds)
            } else {
                cachedSystemPath = null
                isSystemPathSquare = false
            }

        } catch (e: Exception) {
            FileLog.e(e)
            cachedSystemPath = null
            isSystemPathSquare = false
        } finally {
            cacheInitialized = true
        }
    }

    private fun calculateIfShouldUseRoundedRect(path: Path, bounds: RectF): Boolean {
        if (path.isEmpty) return false

        val clip = Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())

        val region = Region()
        region.setPath(path, clip)

        if (region.isRect) {
            return true
        }

        return hasSharpCorners(path, bounds)
    }

    private fun hasSharpCorners(path: Path, bounds: RectF): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return false
        }

        val minDimension = min(bounds.width(), bounds.height())
        val tolerance = max(3, (minDimension * 0.10f).toInt())

        val pathRegion = Region()
        val clipRegion = Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        pathRegion.setPath(path, clipRegion)

        val corners = arrayOf(
            Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.left.toInt() + tolerance, bounds.top.toInt() + tolerance),
            Rect(bounds.right.toInt() - tolerance, bounds.top.toInt(), bounds.right.toInt(), bounds.top.toInt() + tolerance),
            Rect(bounds.right.toInt() - tolerance, bounds.bottom.toInt() - tolerance, bounds.right.toInt(), bounds.bottom.toInt()),
            Rect(bounds.left.toInt(), bounds.bottom.toInt() - tolerance, bounds.left.toInt() + tolerance, bounds.bottom.toInt())
        )

        val tempRegion = Region()
        for (cornerRect in corners) {
            tempRegion.set(pathRegion)
            if (!tempRegion.op(cornerRect, Region.Op.INTERSECT)) {
                return false
            }
        }
        return true
    }
}