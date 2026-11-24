/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.nowplaying.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.exteragram.messenger.api.dto.NowPlayingDTO
import com.exteragram.messenger.utils.ui.UIUtil
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import java.util.concurrent.atomic.AtomicBoolean

data class NowPlayingCardData(
    val nowPlayingDTO: NowPlayingDTO,
    val backgroundColor: Int?,
    val accentColor: Int?,
    val coverBitmap: Bitmap?,
    val imageLocation: ImageLocation? = null,
    var userEmoji: Long = -1L
) {

    fun interface Callback {
        fun onDataLoaded(data: NowPlayingCardData)
    }

    companion object {

        @JvmStatic
        fun create(
            nowPlayingDTO: NowPlayingDTO,
            savedMusic: TLRPC.Document?,
            callback: Callback
        ) {
            var imageLocation: ImageLocation? = null

            if (nowPlayingDTO.platform == "TELEGRAM" && savedMusic != null) {
                val thumb = FileLoader.getClosestPhotoSizeWithSize(savedMusic.thumbs, 1000)
                if (thumb != null) {
                    imageLocation = ImageLocation.getForDocument(thumb, savedMusic)
                }
            }

            if (imageLocation == null && !nowPlayingDTO.coverUrl.isNullOrEmpty()) {
                imageLocation = ImageLocation.getForPath(nowPlayingDTO.coverUrl)
            }

            if (imageLocation == null) {
                AndroidUtilities.runOnUIThread {
                    callback.onDataLoaded(NowPlayingCardData(nowPlayingDTO, null, null, null, null))
                }
                return
            }

            val imageReceiver = ImageReceiver(null)
            val finalImageLocation = imageLocation
            val completed = AtomicBoolean(false)
            lateinit var timeoutRunnable: Runnable

            fun finish(data: NowPlayingCardData) {
                if (!completed.compareAndSet(false, true)) {
                    return
                }
                AndroidUtilities.cancelRunOnUIThread(timeoutRunnable)
                imageReceiver.setDelegate(null)
                imageReceiver.onDetachedFromWindow()
                callback.onDataLoaded(data)
            }

            timeoutRunnable = Runnable {
                finish(NowPlayingCardData(nowPlayingDTO, null, null, null, finalImageLocation))
            }

            AndroidUtilities.runOnUIThread {
                imageReceiver.onAttachedToWindow()

                imageReceiver.setDelegate { receiver, set, thumb, _ ->
                    if (set && !thumb) {
                        val bitmap = receiver.bitmap

                        Utilities.themeQueue.postRunnable {
                            val (bgColor, accentColor) = extractColors(bitmap)

                            AndroidUtilities.runOnUIThread {
                                finish(
                                    NowPlayingCardData(nowPlayingDTO, bgColor, accentColor, bitmap, finalImageLocation)
                                )
                            }
                        }
                    }
                }

                AndroidUtilities.runOnUIThread(timeoutRunnable, 15_000)
                imageReceiver.setImage(
                    finalImageLocation, null, null, null, null, 0
                )
            }
        }

        private fun extractColors(bitmap: Bitmap?): Pair<Int?, Int?> {
            if (bitmap == null) return null to null

            val palette = Palette.from(bitmap).generate()

            val extractedColor = palette.darkVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: AndroidUtilities.getDominantColor(bitmap)

            val contrast = ColorUtils.calculateContrast(Color.WHITE, extractedColor)

            var adjustedColor = when {
                contrast > 15f -> UIUtil.adjustHsl(extractedColor, 2f)
                contrast < 10f -> UIUtil.adjustHsl(extractedColor, 0.5f)
                else -> extractedColor
            }

            if (ColorUtils.calculateContrast(Color.WHITE, adjustedColor) < 3.0f) {
                adjustedColor = ColorUtils.blendARGB(adjustedColor, Color.BLACK, 0.3f)
            }

            val backgroundColor = adjustedColor
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(backgroundColor, hsl)

            val targetLum = when (hsl[2]) {
                in 0.0f..0.25f -> 2f
                in 0.25f..0.5f -> 1.5f
                in 0.5f..0.75f -> 1.0f
                else -> 0.5f
            }

            val accentColor = UIUtil.adjustHsl(backgroundColor, targetLum)

            return backgroundColor to accentColor
        }
    }
}