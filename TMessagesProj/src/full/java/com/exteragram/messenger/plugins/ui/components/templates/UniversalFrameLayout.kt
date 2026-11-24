/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components.templates

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import org.telegram.messenger.Utilities

class UniversalFrameLayout @JvmOverloads constructor(
    context: Context,
    var universalFrameLayoutListener: UniversalFrameLayoutListener? = null,
) : FrameLayout(context) {
    @SuppressLint("WrongCall")
    private fun callSuperOnLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
    }

    @SuppressLint("WrongCall")
    private fun callSuperOnMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun callSuperSetTranslationX(translationX: Float) {
        super.setTranslationX(translationX)
    }

    private fun callSuperSetTranslationY(translationY: Float) {
        super.setTranslationY(translationY)
    }

    private fun callSuperOnDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    private fun callSuperOnAttachedToWindow() {
        super.onAttachedToWindow()
    }

    private fun callSuperDispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
    }

    private fun callSuperRequestLayout() {
        super.requestLayout()
    }

    private fun callSuperInvalidate() {
        super.invalidate()
    }

    @SuppressLint("WrongCall")
    private fun callSuperOnDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    private fun callSuperOnInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
    }

    private fun callSuperOnInterceptTouchEvent(ev: MotionEvent): Boolean {
        return super.onInterceptTouchEvent(ev)
    }

    private fun callSuperOnTouchEvent(event: MotionEvent): Boolean {
        return super.onTouchEvent(event)
    }

    private fun callSuperDrawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        return super.drawChild(canvas, child, drawingTime)
    }

    private fun callSuperSetVisibility(visibility: Int) {
        super.setVisibility(visibility)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.onLayout(changed, left, top, right, bottom, ::callSuperOnLayout)
            return
        }
        super.onLayout(changed, left, top, right, bottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.onMeasure(widthMeasureSpec, heightMeasureSpec, ::callSuperOnMeasure)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun setTranslationX(translationX: Float) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.setTranslationX(translationX, ::callSuperSetTranslationX)
            return
        }
        super.setTranslationX(translationX)
    }

    override fun setTranslationY(translationY: Float) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.setTranslationY(translationY, ::callSuperSetTranslationY)
            return
        }
        super.setTranslationY(translationY)
    }

    override fun onAttachedToWindow() {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.onAttachedToWindow(::callSuperOnAttachedToWindow)
            return
        }
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.onDetachedFromWindow(::callSuperOnDetachedFromWindow)
            return
        }
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.dispatchDraw(canvas, ::callSuperDispatchDraw)
            return
        }
        super.dispatchDraw(canvas)
    }

    override fun requestLayout() {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.requestLayout(::callSuperRequestLayout)
            return
        }
        super.requestLayout()
    }

    override fun invalidate() {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.invalidate(::callSuperInvalidate)
            return
        }
        super.invalidate()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.invalidate(l, t, r, b, ::callSuperInvalidate)
            return
        }
        super.invalidate(l, t, r, b)
    }

    override fun onDraw(canvas: Canvas) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.onDraw(canvas, ::callSuperOnDraw)
            return
        }
        super.onDraw(canvas)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.onInitializeAccessibilityNodeInfo(
                info,
                ::callSuperOnInitializeAccessibilityNodeInfo
            )
            return
        }
        super.onInitializeAccessibilityNodeInfo(info)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return universalFrameLayoutListener?.onInterceptTouchEvent(
            ev,
            ::callSuperOnInterceptTouchEvent
        )
            ?: super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return universalFrameLayoutListener?.onTouchEvent(event, ::callSuperOnTouchEvent)
            ?: super.onTouchEvent(event)
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long
    ): Boolean {
        return universalFrameLayoutListener?.drawChild(
            canvas,
            child,
            drawingTime,
            ::callSuperDrawChild
        )
            ?: super.drawChild(canvas, child, drawingTime)
    }

    override fun setVisibility(visibility: Int) {
        val listener = universalFrameLayoutListener
        if (listener != null) {
            listener.setVisibility(visibility, ::callSuperSetVisibility)
            return
        }
        super.setVisibility(visibility)
    }

    interface UniversalFrameLayoutListener {
        fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            originalMethod: Utilities.Callback5<Boolean, Int, Int, Int, Int>,
        ) {
            originalMethod.run(changed, left, top, right, bottom)
        }

        fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
            originalMethod: Utilities.Callback2<Int, Int>,
        ) {
            originalMethod.run(widthMeasureSpec, heightMeasureSpec)
        }

        fun setTranslationX(translationX: Float, originalMethod: Utilities.Callback<Float>) {
            originalMethod.run(translationX)
        }

        fun setTranslationY(translationY: Float, originalMethod: Utilities.Callback<Float>) {
            originalMethod.run(translationY)
        }

        fun onAttachedToWindow(originalMethod: Runnable) {
            originalMethod.run()
        }

        fun onDetachedFromWindow(originalMethod: Runnable) {
            originalMethod.run()
        }

        fun dispatchDraw(canvas: Canvas, originalMethod: Utilities.Callback<Canvas>) {
            originalMethod.run(canvas)
        }

        fun requestLayout(originalMethod: Runnable) {
            originalMethod.run()
        }

        fun invalidate(originalMethod: Runnable) {
            originalMethod.run()
        }

        fun invalidate(l: Int, t: Int, r: Int, b: Int, originalMethod: Runnable) {
            originalMethod.run()
        }

        fun onDraw(canvas: Canvas, originalMethod: Utilities.Callback<Canvas>) {
            originalMethod.run(canvas)
        }

        fun onInitializeAccessibilityNodeInfo(
            info: AccessibilityNodeInfo,
            originalMethod: Utilities.Callback<AccessibilityNodeInfo>,
        ) {
            originalMethod.run(info)
        }

        fun onInterceptTouchEvent(
            ev: MotionEvent,
            originalMethod: Utilities.CallbackReturn<MotionEvent, Boolean>,
        ): Boolean = originalMethod.run(ev)

        fun onTouchEvent(
            event: MotionEvent,
            originalMethod: Utilities.CallbackReturn<MotionEvent, Boolean>,
        ): Boolean = originalMethod.run(event)

        fun drawChild(
            canvas: Canvas,
            child: View,
            drawingTime: Long,
            originalMethod: Utilities.Callback3Return<Canvas, View, Long, Boolean>,
        ): Boolean = originalMethod.run(canvas, child, drawingTime)

        fun setVisibility(visibility: Int, originalMethod: Utilities.Callback<Int>) {
            originalMethod.run(visibility)
        }
    }
}