package com.exteragram.messenger.baselineprofile

import android.graphics.Rect
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val targetPackage: String
        get() = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw Exception("targetAppId not passed as instrumentation runner arg")

    @Test
    fun startup() {
        rule.collect(
            packageName = targetPackage,
            includeInStartupProfile = true,
            maxIterations = 8,
            stableIterations = 3
        ) {
            pressHome()
            startActivityAndWait()
            device.awaitDialogs()
        }
    }

    @Test
    fun userJourney() {
        rule.collect(
            packageName = targetPackage,
            includeInStartupProfile = false,
            maxIterations = 3,
            stableIterations = 2
        ) {
            pressHome()
            startActivityAndWait()

            with(device) {
                awaitDialogs()
                val dialogs = mainTabsSignature()

                browseDialogs()
                browseFolders()
                browseDrawer()
                browseSearch()
                browseChat()
                browseMainTabs(dialogs)
            }
        }
    }

    private fun UiDevice.awaitDialogs() {
        wait(Until.hasObject(By.scrollable(true)), UI_TIMEOUT_MS)
        waitForIdle(UI_TIMEOUT_MS)
        sleepSafe(SETTLE_MS)
    }

    private fun UiDevice.browseDialogs() {
        repeat(4) {
            scrollForward()
            sleepSafe(SCROLL_PAUSE_MS)
        }
        repeat(4) {
            scrollBackward()
            sleepSafe(SCROLL_PAUSE_MS)
        }
    }

    private fun UiDevice.browseFolders() {
        val strip = folderStrip() ?: return

        val y = strip.centerY()
        val near = strip.left + strip.width() / 6
        val far = strip.right - strip.width() / 6

        swipe(far, y, near, y, SWIPE_STEPS)
        sleepSafe(SCROLL_PAUSE_MS)
        click(far, y)
        sleepSafe(FOLDER_SETTLE_MS)
        browseDialogs()

        swipe(near, y, far, y, SWIPE_STEPS)
        sleepSafe(SCROLL_PAUSE_MS)
        click(near, y)
        sleepSafe(FOLDER_SETTLE_MS)
    }

    private fun UiDevice.browseDrawer() {
        val menu = topBarButton(onLeft = true) ?: return
        val before = screenSignature()

        click(menu.centerX(), menu.centerY())
        sleepSafe(SETTLE_MS)

        if (screenSignature() == before) {
            return
        }
        scrollForward()
        sleepSafe(SCROLL_PAUSE_MS)
        returnTo(before)
    }

    private fun UiDevice.browseSearch() {
        val before = screenSignature()

        val inlineField = findObject(By.clazz(EDIT_TEXT))
        if (inlineField != null) {
            inlineField.click()
        } else {
            val search = topBarButton(onLeft = false) ?: return
            click(search.centerX(), search.centerY())
        }
        sleepSafe(SETTLE_MS)

        val field = findObject(By.clazz(EDIT_TEXT)) ?: return
        field.text = SEARCH_QUERY
        sleepSafe(SEARCH_RESULTS_MS)

        repeat(2) {
            scrollForward()
            sleepSafe(SCROLL_PAUSE_MS)
        }
        repeat(2) {
            scrollBackward()
            sleepSafe(SCROLL_PAUSE_MS)
        }

        returnTo(before)
    }

    private fun UiDevice.browseChat() {
        val before = screenSignature()

        click(displayWidth / 2, (displayHeight * CHAT_ROW_Y).toInt())
        sleepSafe(SETTLE_MS)

        if (screenSignature() == before) {
            return
        }

        repeat(4) {
            scrollBackward()
            sleepSafe(SCROLL_PAUSE_MS)
        }
        repeat(3) {
            scrollForward()
            sleepSafe(SCROLL_PAUSE_MS)
        }

        openEmojiPanel()
        openChatProfile()
        returnTo(before)
    }

    private fun UiDevice.openEmojiPanel() {
        val input = boundsOf(By.clazz(EDIT_TEXT)) { it.centerY() > displayHeight / 2 }
            .maxByOrNull { it.bottom } ?: return
        val button = clickableBounds {
            it.right <= input.left && it.centerY() in input.top..input.bottom
        }.maxByOrNull { it.right } ?: return

        click(button.centerX(), button.centerY())
        sleepSafe(SETTLE_MS)

        val x = displayWidth / 2
        val top = (displayHeight * EMOJI_SCROLL_TOP).toInt()
        val bottom = (displayHeight * EMOJI_SCROLL_BOTTOM).toInt()
        repeat(3) {
            swipe(x, bottom, x, top, SWIPE_STEPS)
            sleepSafe(SCROLL_PAUSE_MS)
        }
        repeat(3) {
            swipe(x, top, x, bottom, SWIPE_STEPS)
            sleepSafe(SCROLL_PAUSE_MS)
        }

        pressBack()
        sleepSafe(BACK_SETTLE_MS)
    }

    private fun UiDevice.openChatProfile() {
        val before = screenSignature()

        click((displayWidth * CHAT_TITLE_X).toInt(), actionBarCenterY())
        sleepSafe(SETTLE_MS)

        if (screenSignature() == before) {
            return
        }
        repeat(2) {
            scrollForward()
            sleepSafe(SCROLL_PAUSE_MS)
        }
        repeat(2) {
            scrollBackward()
            sleepSafe(SCROLL_PAUSE_MS)
        }
    }

    private fun UiDevice.browseMainTabs(dialogsSignature: String) {
        val tabs = bottomRowItems()
        if (tabs.size < MIN_MAIN_TABS || mainTabsSignature() != dialogsSignature) {
            return
        }
        for (index in 1 until tabs.size) {
            click(tabs[index].centerX(), tabs[index].centerY())
            sleepSafe(TAB_SETTLE_MS)

            repeat(2) {
                scrollForward()
                sleepSafe(SCROLL_PAUSE_MS)
            }
            repeat(2) {
                scrollBackward()
                sleepSafe(SCROLL_PAUSE_MS)
            }
        }
        click(tabs[0].centerX(), tabs[0].centerY())
        sleepSafe(TAB_SETTLE_MS)
    }

    private fun UiDevice.bottomRowItems(): List<Rect> =
        clickableBounds {
            it.centerY() > displayHeight * TABS_BAND && it.width() < displayWidth * TABS_MAX_WIDTH
        }
            .groupBy { it.centerY() }
            .values
            .maxByOrNull { it.size }
            ?.takeIf { it.size >= 2 }
            ?.sortedBy { it.centerX() }
            .orEmpty()

    private fun UiDevice.mainTabsSignature(): String {
        val bottom = bottomRowItems()
        return "${bottom.size}/${bottom.firstOrNull()?.centerY() ?: -1}"
    }

    private fun UiDevice.screenSignature(): String {
        val bottom = bottomRowItems()
        return listOf(
            bottom.size,
            bottom.firstOrNull()?.centerY() ?: -1,
            folderStrip()?.top ?: -1,
            if (hasObject(By.clazz(EDIT_TEXT))) 1 else 0
        ).joinToString("/")
    }

    private fun UiDevice.returnTo(signature: String) {
        repeat(MAX_BACK_STEPS) {
            if (screenSignature() == signature) {
                return
            }
            pressBack()
            sleepSafe(BACK_SETTLE_MS)
        }
    }

    private fun UiDevice.folderStrip(): Rect? {
        val actionBarBottom = topBarButton(onLeft = true)?.bottom ?: return null
        return scrollableBounds {
            it.top >= actionBarBottom &&
                it.top < displayHeight * FOLDERS_MAX_TOP &&
                it.height() < displayHeight * FOLDERS_MAX_HEIGHT &&
                it.width() > displayWidth * FOLDERS_MIN_WIDTH
        }.minByOrNull { it.top }
    }

    private fun UiDevice.topBarButton(onLeft: Boolean): Rect? =
        clickableBounds {
            it.centerY() < displayHeight * ACTION_BAR_BAND &&
                it.width() < displayWidth * ACTION_BAR_MAX_WIDTH &&
                if (onLeft) {
                    it.centerX() < displayWidth * ACTION_BAR_LEFT
                } else {
                    it.centerX() > displayWidth * ACTION_BAR_RIGHT
                }
        }.minByOrNull { it.top }

    private fun UiDevice.actionBarCenterY(): Int =
        (topBarButton(onLeft = true) ?: topBarButton(onLeft = false))?.centerY()
            ?: (displayHeight * ACTION_BAR_FALLBACK_Y).toInt()

    private fun UiDevice.clickableBounds(filter: (Rect) -> Boolean): List<Rect> =
        boundsOf(By.clickable(true), filter)

    private fun UiDevice.scrollableBounds(filter: (Rect) -> Boolean): List<Rect> =
        boundsOf(By.scrollable(true), filter)

    private fun UiDevice.boundsOf(selector: BySelector, filter: (Rect) -> Boolean): List<Rect> =
        findObjects(selector).mapNotNull { object2 ->
            runCatching { object2.visibleBounds }.getOrNull()
                ?.takeIf { !it.isEmpty && filter(it) }
        }

    private fun UiDevice.scrollForward() {
        val x = displayWidth / 2
        swipe(x, (displayHeight * SCROLL_BOTTOM).toInt(), x, (displayHeight * SCROLL_TOP).toInt(), SWIPE_STEPS)
    }

    private fun UiDevice.scrollBackward() {
        val x = displayWidth / 2
        swipe(x, (displayHeight * SCROLL_TOP).toInt(), x, (displayHeight * SCROLL_BOTTOM).toInt(), SWIPE_STEPS)
    }

    private fun sleepSafe(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            // ignore
        }
    }

    private companion object {
        const val EDIT_TEXT = "android.widget.EditText"
        const val SEARCH_QUERY = "a"

        const val UI_TIMEOUT_MS = 15000L
        const val SETTLE_MS = 1500L
        const val SCROLL_PAUSE_MS = 600L
        const val BACK_SETTLE_MS = 1000L
        const val TAB_SETTLE_MS = 2000L
        const val FOLDER_SETTLE_MS = 1200L
        const val SEARCH_RESULTS_MS = 2500L

        const val SWIPE_STEPS = 12
        const val MIN_MAIN_TABS = 3
        const val MAX_BACK_STEPS = 3

        const val SCROLL_TOP = 0.28f
        const val SCROLL_BOTTOM = 0.78f
        const val CHAT_ROW_Y = 0.55f
        const val CHAT_TITLE_X = 0.42f
        const val EMOJI_SCROLL_TOP = 0.7f
        const val EMOJI_SCROLL_BOTTOM = 0.9f

        const val ACTION_BAR_BAND = 0.2f
        const val ACTION_BAR_LEFT = 0.25f
        const val ACTION_BAR_RIGHT = 0.6f
        const val ACTION_BAR_MAX_WIDTH = 0.35f
        const val ACTION_BAR_FALLBACK_Y = 0.1f

        const val TABS_BAND = 0.85f
        const val TABS_MAX_WIDTH = 0.75f

        const val FOLDERS_MAX_TOP = 0.35f
        const val FOLDERS_MAX_HEIGHT = 0.12f
        const val FOLDERS_MIN_WIDTH = 0.5f
    }
}