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
import org.telegram.messenger.Utilities

class UniversalView @JvmOverloads constructor(
    context: Context,
    var delegate: UniversalViewDelegate? = null,
) : View(context) {
    @SuppressLint("WrongCall")
    private fun callSuperOnDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    private fun callSuperOnTouchEvent(event: MotionEvent): Boolean {
        return super.onTouchEvent(event)
    }

    @SuppressLint("WrongCall")
    private fun callSuperOnMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun callSuperOnInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
    }

    override fun onDraw(canvas: Canvas) {
        val localDelegate = delegate
        if (localDelegate != null) {
            localDelegate.onDraw(canvas, ::callSuperOnDraw)
            return
        }
        super.onDraw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        delegate?.onAttachedToWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return delegate?.onTouchEvent(event, ::callSuperOnTouchEvent) ?: super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val localDelegate = delegate
        if (localDelegate != null) {
            localDelegate.onMeasure(widthMeasureSpec, heightMeasureSpec, ::callSuperOnMeasure)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        delegate?.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        val localDelegate = delegate
        if (localDelegate != null) {
            localDelegate.onInitializeAccessibilityNodeInfo(
                info,
                ::callSuperOnInitializeAccessibilityNodeInfo
            )
            return
        }
        super.onInitializeAccessibilityNodeInfo(info)
    }

    interface UniversalViewDelegate {
        fun onDraw(canvas: Canvas, originalMethod: Utilities.Callback<Canvas>) {
            originalMethod.run(canvas)
        }

        fun onAttachedToWindow() {}

        fun onDetachedFromWindow() {}

        fun onTouchEvent(
            event: MotionEvent,
            originalMethod: Utilities.CallbackReturn<MotionEvent, Boolean>,
        ): Boolean = originalMethod.run(event)

        fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
            originalMethod: Utilities.Callback2<Int, Int>,
        ) {
            originalMethod.run(widthMeasureSpec, heightMeasureSpec)
        }

        fun onInitializeAccessibilityNodeInfo(
            info: AccessibilityNodeInfo,
            originalMethod: Utilities.Callback<AccessibilityNodeInfo>,
        ) {
            originalMethod.run(info)
        }
    }
}