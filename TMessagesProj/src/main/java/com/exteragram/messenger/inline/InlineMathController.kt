/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline

import android.graphics.Canvas
import android.graphics.Region
import android.text.Editable
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.MetricAffectingSpan
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.TextView
import androidx.core.graphics.withClip

import com.exteragram.messenger.inline.math.MathExpression
import com.exteragram.messenger.inline.math.MathOptions
import com.exteragram.messenger.inline.math.MathSuggestion
import com.exteragram.messenger.inline.ui.GhostTextLayout
import com.exteragram.messenger.inline.ui.MathRevealAnimation
import com.exteragram.messenger.inlineTimeZones

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedFloat
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.QuoteSpan
import org.telegram.ui.Components.TextStyleSpan

import java.text.DecimalFormatSymbols
import java.util.Locale

class InlineMathController(private val view: TextView, private val delegate: Delegate?) {

    interface Delegate {
        fun runProgrammatic(action: Runnable)
        fun accentColor(): Int
        fun onGhostBoundsChanged()
    }

    companion object {
        private const val GHOST_ALPHA = 0.4f
        private const val ANNOUNCE_DELAY = 600L
    }

    private val ghost = GhostTextLayout()
    private val appear = AnimatedFloat(view, 0L, 120L, CubicBezierInterpolator.EASE_OUT_QUINT)
    private val reveal = MathRevealAnimation(view) { dirty = true }
    private val rates = InlineRates { schedule() }

    private var suggestion: MathSuggestion? = null
    private var dirty = true
    private var layoutWidth = -1

    private var ghostRight = -1f
    private val notifyBounds = Runnable { delegate?.onGhostBoundsChanged() }

    private var announcedValue: String? = null
    private val announce = Runnable {
        val value = suggestion?.value
        if (value != null) {
            announcedValue = value
            AndroidUtilities.makeAccessibilityAnnouncement(
                LocaleController.formatString(R.string.InlineMathResultAnnouncement, value)
            )
        }
    }

    private var decimalSeparator = '.'
    private var separatorLocale: Locale? = null

    private var undoStart = -1
    private var undoEnd = -1
    private var suppressedAt = -1

    private var insertingSelf = false
    private var caretMovedByTouch = false
    private var swallowKeyCode = 0

    init {
        if (InlineResults.isEnabled() && inlineTimeZones) {
            InlineZonePlaces.prepare()
        }
    }

    fun getExtraBottom(): Int = ghost.extraHeight

    fun extendsPast(right: Float): Boolean = !ghost.isEmpty() && ghost.rightEdge() >= right

    fun hasCursorShift(): Boolean = ghost.hasMovedText()

    fun getCursorShiftX(): Float = if (hasCursorShift()) ghost.cursorShiftX else 0f

    fun getCursorShiftY(): Float = if (hasCursorShift()) ghost.cursorShiftY else 0f

    @Suppress("DEPRECATION")
    fun clipReplacedParagraph(canvas: Canvas, top: Int) {
        if (ghost.isEmpty() || ghost.detached) {
            return
        }
        val layout = view.layout ?: return
        val firstLine = layout.getLineForOffset(ghost.paragraphStart)
        val lastLine = layout.getLineForOffset(ghost.paragraphEnd)
        canvas.clipRect(
            0f,
            top + layout.getLineTop(firstLine).toFloat(),
            view.width.toFloat(),
            top + layout.getLineBottom(lastLine).toFloat(),
            Region.Op.DIFFERENCE
        )
    }

    fun draw(canvas: Canvas, left: Int, top: Int, clipTop: Float, clipBottom: Float) {
        val revealing = reveal.isRunning()
        if (!revealing && ghost.isEmpty()) {
            appear.set(0f, true)
            return
        }
        canvas.withClip(0f, clipTop, view.width.toFloat(), clipBottom) {
            translate(left.toFloat(), top.toFloat())
            if (revealing) {
                reveal.draw(this, delegate?.accentColor() ?: view.currentTextColor)
            } else {
                ghost.setAlpha(GHOST_ALPHA * appear.set(1f))
                ghost.draw(this)
            }
        }
    }

    fun invalidateState() {
        if (!insertingSelf) {
            val caret = view.selectionStart
            if (caret != suppressedAt) {
                suppressedAt = -1
            }
            if (caret != undoEnd || view.selectionEnd != undoEnd) {
                clearUndo()
            }
            if (reveal.isRunning() && reveal.isCaretOutside(caret)) {
                reveal.cancel()
            }
        }
        schedule()
    }

    fun onTextChanged() {
        if (!insertingSelf) {
            reveal.cancel()
            clearUndo()
            suppressedAt = -1
            caretMovedByTouch = false
        }
        schedule()
    }

    fun onTouchDown() {
        if (!caretMovedByTouch) {
            caretMovedByTouch = true
            schedule()
        }
    }

    fun onFocusChanged(focused: Boolean) {
        if (!focused) {
            reveal.cancel()
            clearUndo()
        }
        schedule()
    }

    fun cancel() {
        AndroidUtilities.cancelRunOnUIThread(announce)
        announcedValue = null
        reveal.cancel()
        clearUndo()
        suppressedAt = -1
        caretMovedByTouch = false
        suggestion = null
        ghost.clear()
        schedule()
    }

    fun updateOnMeasure(): Boolean {
        val before = ghost.extraHeight
        val width = view.layout?.width ?: 0
        if (width != layoutWidth) {
            layoutWidth = width
            dirty = true
        }
        update()
        val right = ghost.rightEdge()
        if (right != ghostRight) {
            ghostRight = right
            AndroidUtilities.cancelRunOnUIThread(notifyBounds)
            AndroidUtilities.runOnUIThread(notifyBounds)
        }
        if (AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
            updateAnnouncement()
        }
        return ghost.extraHeight != before
    }

    private fun schedule() {
        dirty = true
        view.invalidate()
        if (ghost.extraHeight != 0 || suggestion != null || canTrigger()) {
            view.requestLayout()
        }
    }

    private fun canTrigger(): Boolean {
        if (!InlineResults.isEnabled()) {
            return false
        }
        val text = view.text ?: return false
        val caret = view.selectionStart
        return caret in 1..text.length && text[caret - 1] == '='
    }

    private fun updateAnnouncement() {
        val value = suggestion?.value
        if (value == null) {
            announcedValue = null
            AndroidUtilities.cancelRunOnUIThread(announce)
        } else if (value != announcedValue) {
            AndroidUtilities.cancelRunOnUIThread(announce)
            AndroidUtilities.runOnUIThread(announce, ANNOUNCE_DELAY)
        }
    }

    private fun options(): MathOptions {
        val locale = LocaleController.getInstance().currentLocale ?: Locale.US
        if (locale != separatorLocale) {
            separatorLocale = locale
            decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        }
        return InlineResults.options(rates, decimalSeparator)
    }

    private fun update() {
        if (!dirty) {
            return
        }
        dirty = false
        suggestion = null
        ghost.clear()
        if (!InlineResults.isEnabled() || reveal.isRunning() || suppressedAt >= 0) {
            return
        }
        if (!view.isFocused || !view.isEnabled) {
            return
        }
        val layout = view.layout ?: return
        val text = view.text ?: return
        val caret = view.selectionStart
        if (caret <= 0 || caret != view.selectionEnd || caret > text.length) {
            return
        }
        val found = MathExpression.suggestionAt(text, caret, options()) ?: return
        if (text is Spannable && BaseInputConnection.getComposingSpanStart(text) != -1) {
            return
        }
        if (text is Spanned && text.getSpans(caret - 1, caret, MetricAffectingSpan::class.java).isNotEmpty()) {
            return
        }
        if (layout.getParagraphDirection(layout.getLineForOffset(caret)) != Layout.DIR_LEFT_TO_RIGHT) {
            return
        }
        val paragraphStart = text.lastIndexOf('\n', caret - 1) + 1
        val newline = text.indexOf('\n', caret)
        val paragraphEnd = if (newline < 0) text.length else newline
        val lastParagraph = paragraphEnd >= text.length
        if (hasUnsupportedSpans(text, paragraphStart, paragraphEnd)) {
            if (!lastParagraph || !ghost.buildDetached(view, layout, paragraphEnd, found.insertText)) {
                return
            }
            suggestion = found
            return
        }
        if (!ghost.build(view, layout, paragraphStart, paragraphEnd, caret, found.insertText)) {
            return
        }
        val blocked = (ghost.extraHeight > 0 && !lastParagraph) ||
                (ghost.hasMovedText() && caretMovedByTouch)
        if (blocked && (!lastParagraph || !ghost.buildDetached(view, layout, paragraphEnd, found.insertText))) {
            ghost.clear()
            return
        }
        suggestion = found
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            if (swallowKeyCode != 0 && event.keyCode == swallowKeyCode) {
                swallowKeyCode = 0
                return true
            }
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) {
            return false
        }
        if (event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) {
            return false
        }
        val accepted = when (event.keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_RIGHT -> commit()
            KeyEvent.KEYCODE_DEL -> undo()
            else -> false
        }
        if (accepted) {
            swallowKeyCode = event.keyCode
        }
        return accepted
    }

    fun wrap(connection: InputConnection): InputConnection = object : InputConnectionWrapper(connection, true) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text != null && text.length == 1 && text[0] == ' ' && suggestion != null) {
                finishComposingText()
                if (commit()) {
                    return true
                }
            }
            return super.commitText(text, newCursorPosition)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0 && undo()) {
                return true
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0 && undo()) {
                return true
            }
            return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
        }
    }

    private fun commit(): Boolean {
        val found = suggestion ?: return false
        val text = view.text as? Editable ?: return false
        if (found.insertAt != view.selectionStart || view.selectionStart != view.selectionEnd) {
            return false
        }
        if (found.insertAt > text.length) {
            return false
        }
        val lead = found.insertText.length - found.value.length
        val count = found.value.length
        val originX = FloatArray(count)
        val originY = FloatArray(count)
        ghost.readInsertedPositions(lead, count, originX, originY)
        BaseInputConnection.removeComposingSpans(text)
        if (!edit { text.insert(found.insertAt, found.insertText) }) {
            return false
        }
        Selection.setSelection(text, found.insertAt + found.insertText.length)
        suggestion = null
        ghost.clear()
        dirty = true
        reveal.begin(text, found.insertAt + lead, count, originX, originY)
        undoStart = found.insertAt
        undoEnd = found.insertAt + found.insertText.length
        appear.set(0f, true)
        view.requestLayout()
        view.invalidate()
        return true
    }

    private fun undo(): Boolean {
        if (undoStart < 0) {
            return false
        }
        val text = view.text as? Editable ?: return false
        if (undoEnd > text.length || view.selectionStart != undoEnd || view.selectionEnd != undoEnd) {
            clearUndo()
            return false
        }
        reveal.cancel()
        val restored = undoStart
        if (!edit { text.delete(undoStart, undoEnd) }) {
            return false
        }
        Selection.setSelection(text, restored)
        clearUndo()
        suppressedAt = restored
        dirty = true
        view.requestLayout()
        view.invalidate()
        return true
    }

    private fun edit(block: () -> Unit): Boolean {
        var applied = true
        val action = Runnable {
            insertingSelf = true
            try {
                block()
            } catch (e: Exception) {
                FileLog.e(e)
                applied = false
            } finally {
                insertingSelf = false
            }
        }
        if (delegate != null) {
            delegate.runProgrammatic(action)
        } else {
            action.run()
        }
        return applied
    }

    private fun hasUnsupportedSpans(text: CharSequence, start: Int, end: Int): Boolean {
        if (text !is Spanned) {
            return false
        }
        if (text.getSpans(start, end, AnimatedEmojiSpan::class.java).isNotEmpty()) {
            return true
        }
        if (text.getSpans(start, end, QuoteSpan::class.java).isNotEmpty()) {
            return true
        }
        return text.getSpans(start, end, TextStyleSpan::class.java).any { it.isSpoiler }
    }

    private fun clearUndo() {
        undoStart = -1
        undoEnd = -1
    }
}
