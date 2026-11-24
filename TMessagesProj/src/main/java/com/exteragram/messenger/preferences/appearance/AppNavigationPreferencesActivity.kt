/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.appearance

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import com.exteragram.messenger.*
import com.exteragram.messenger.drawer.MainMenuItem
import com.exteragram.messenger.drawer.ensureSettingsVisibility
import com.exteragram.messenger.drawer.getDefaultMainMenuLayout
import com.exteragram.messenger.drawer.mainMenuHiddenItems
import com.exteragram.messenger.drawer.mainMenuLayout
import com.exteragram.messenger.drawer.saveMainMenuLayout
import com.exteragram.messenger.config.*
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.components.AltSeekbar
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import kotlin.math.roundToInt
import androidx.core.content.edit
import com.exteragram.messenger.utils.addIf
import com.exteragram.messenger.utils.ui.predictiveback.PredictiveBackAnimationHelper

class AppNavigationPreferencesActivity : BasePreferencesActivity() {

    private val itemDetails = HashMap<Int, ItemInfo>()

    private var reorderIcon: Drawable? = null
    private var resetItem: ActionBarMenuItem? = null
    private var predictiveBackSeekbar: AltSeekbar? = null

    enum class AppNavigationItem {
        DRAWER,
        IMMERSIVE_ANIMATION,
        BOTTOM_NAVIGATION_BAR_MODE,
        PREDICTIVE_BACK_ANIMATION,
        TRANSITION_ANIMATION,
        TABLET_MODE,
        HIDE_NAVIGATION_BAR_LABELS;

        companion object {
            private const val BASE_ID = 150

            fun fromId(id: Int): AppNavigationItem? {
                val index = id - BASE_ID
                if (index < 0 || index >= entries.size)
                    return null
                return entries[index]
            }
        }

        val id: Int get() = BASE_ID + ordinal
    }

    private val stableDividerIds = ArrayList<Int>()
    private var nextDividerId = DIVIDER_BASE_ID
    private lateinit var tabletModeOptions: Array<CharSequence>
    private lateinit var bottomNavigationModes: Array<CharSequence>
    private lateinit var transitionAnimations: Array<CharSequence>

    companion object {
        private const val DIVIDER_BASE_ID = -2000
    }

    private data class ItemInfo(val name: CharSequence, val iconRes: Int)

    override fun initializeOptionStrings() {
        initItemDetails()
        tabletModeOptions = arrayOf(
            getString(R.string.DistanceUnitsAutomatic),
            getString(R.string.PasswordOn),
            getString(R.string.PasswordOff)
        )
        bottomNavigationModes = arrayOf(
            getString(R.string.BottomNavigationModeShow),
            getString(R.string.BottomNavigationModeHide),
            getString(R.string.BottomNavigationModeFloating)
        )
        transitionAnimations = arrayOf(
            getString(R.string.Default),
            getString(R.string.TransitionAnimationAosp),
            getString(R.string.TransitionAnimationSpring)
        )
    }

    private fun initItemDetails() {
        itemDetails[MainMenuItem.PROFILE.id] = ItemInfo(getString(R.string.MyProfile), R.drawable.left_status_profile)
        itemDetails[MainMenuItem.ARCHIVE.id] = ItemInfo(getString(R.string.ArchivedChats), R.drawable.msg_archive)

        itemDetails[MainMenuItem.BOTS.id] = ItemInfo(getString(R.string.FilterBots), R.drawable.msg_bot)
        itemDetails[MainMenuItem.WALLET.id] = ItemInfo(getString(R.string.WalletTitle), R.drawable.ic_gram)

        itemDetails[MainMenuItem.NEW_GROUP.id] = ItemInfo(getString(R.string.NewGroup), R.drawable.msg_groups)
        itemDetails[MainMenuItem.CONTACTS.id] = ItemInfo(getString(R.string.Contacts), R.drawable.msg_contacts)
        itemDetails[MainMenuItem.NEW_CHANNEL.id] = ItemInfo(getString(R.string.NewChannel), R.drawable.msg_channel)
        itemDetails[MainMenuItem.CALLS.id] = ItemInfo(getString(R.string.Calls), R.drawable.msg_calls)
        itemDetails[MainMenuItem.SAVED.id] = ItemInfo(getString(R.string.SavedMessages), R.drawable.msg_saved)
        itemDetails[MainMenuItem.FEED.id] = ItemInfo(getString(R.string.Feed), R.drawable.ic_feed)

        itemDetails[MainMenuItem.SETTINGS.id] = ItemInfo(getString(R.string.Settings), R.drawable.msg_settings_old)

        itemDetails[MainMenuItem.PLUGINS.id] = ItemInfo(getString(R.string.Plugins), R.drawable.msg_plugins)
        itemDetails[MainMenuItem.BROWSER.id] = ItemInfo(getString(R.string.BrowserSettingsTitle), R.drawable.msg2_language)
        itemDetails[MainMenuItem.QR.id] = ItemInfo(getString(R.string.AuthAnotherClient), R.drawable.msg_qrcode)

        mainMenuHiddenItems.removeIf { id -> id == MainMenuItem.DIVIDER.id }

        stableDividerIds.clear()
        nextDividerId = DIVIDER_BASE_ID
        for (id in mainMenuLayout)
            if (id == MainMenuItem.DIVIDER.id)
                stableDividerIds.add(nextDividerId--)
    }

    override fun getTitle(): String = getString(R.string.AppNavigation)

    override fun createView(context: Context): View {
        val view = super.createView(context)

        if (Build.VERSION.SDK_INT >= 34) {
            val seekbar = object : AltSeekbar(
                context,
                { value ->
                    val wasOff = isPredictiveBackOff(predictiveBackIntensity)
                    val isOff = isPredictiveBackOff(value)
                    predictiveBackIntensity = value
                    predictiveBackSeekbar?.updateHeader(value)
                    if (wasOff != isOff) showRestartBulletin()
                },
                0, PredictiveBackAnimationHelper.MAX_INTENSITY.toInt(),
                getString(R.string.PredictiveBackIntensity),
                getString(R.string.BlurOff), getString(R.string.PredictiveBackMax)
            ) {
                override fun getTextForHeader(): CharSequence {
                    val rounded = (currentValue * 10f).roundToInt() / 10f
                    return when {
                        rounded <= 0 -> leftTextView.text.toString().uppercase(Locale.US)
                        rounded >= PredictiveBackAnimationHelper.MAX_INTENSITY -> rightTextView.text.toString().uppercase(Locale.US)
                        rounded == rounded.toInt().toFloat() -> rounded.toInt().toString()
                        else -> String.format(Locale.US, "%.1f", rounded)
                    }
                }

                override fun useExactEndpointHaptic(): Boolean = true
            }
            predictiveBackSeekbar = seekbar
            val intensity = predictiveBackIntensity.coerceAtMost(PredictiveBackAnimationHelper.MAX_INTENSITY)
            if (intensity != predictiveBackIntensity)
                predictiveBackIntensity = intensity
            seekbar.setProgress(intensity)
        }

        resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset).apply {
            contentDescription = getString(R.string.Reset)
            setOnClickListener { resetToDefault() }
        }

        updateResetButtonVisibility(false)

        listView.allowReorder(true)
        listView.listenReorder(this::updateConfigFromReorder)

        return view
    }

    private fun isPredictiveBackOff(value: Float): Boolean = (value * 10f).roundToInt() / 10f <= 0

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val allowPredictive = Build.VERSION.SDK_INT >= 34 && predictiveBackSeekbar != null
        reorderIcon = reorderIcon ?: ContextCompat.getDrawable(context, R.drawable.list_reorder)

        items.add(UItem.asHeader(getString(R.string.General)))
        items.add(uButton(AppNavigationItem.TABLET_MODE.id, getString(R.string.TabletMode), value = tabletModeOptions[tabletMode], linkAlias = "tabletMode"))
        items.add(uButton(AppNavigationItem.BOTTOM_NAVIGATION_BAR_MODE.id, getString(R.string.BottomNavigationBarMode), value = bottomNavigationModes[getMode()], linkAlias = "bottomNavigationBarMode"))
        items.addIf(visible(), uCheck(AppNavigationItem.HIDE_NAVIGATION_BAR_LABELS.id, getString(R.string.HideNavigationBarLabels), hideNavigationBarLabels, linkAlias = "hideNavigationBarLabels"))
        items.add(uButton(AppNavigationItem.TRANSITION_ANIMATION.id, getString(R.string.TransitionAnimation), value = transitionAnimations[transitionAnimation.ordinal], linkAlias = "transitionAnimation"))
        items.add(UItem.asShadow(getString(R.string.TransitionAnimationInfo)))

        items.addIf(allowPredictive, UItem.asCustom(AppNavigationItem.PREDICTIVE_BACK_ANIMATION.id, predictiveBackSeekbar).setLinkAlias("predictiveBackAnimation", this))
        items.addIf(allowPredictive, UItem.asShadow(getString(R.string.PredictiveBackInfo)))

        items.add(UItem.asHeader(getString(R.string.AppNavigation)))
        items.add(uCheck(AppNavigationItem.DRAWER.id, getString(R.string.NavigationDrawer), navigationDrawer, linkAlias = "navigationDrawer"))
        items.addIf(navigationDrawer, uCheck(AppNavigationItem.IMMERSIVE_ANIMATION.id, getString(R.string.NavigationDrawerImmersiveAnimation), immersiveDrawerAnimation, linkAlias = "immersiveDrawerAnimation"))
        items.add(UItem.asShadow(getString(R.string.NavigationDrawerInfo)))

        addMenuSection(items, adapter, getString(R.string.MainMenuItems), mainMenuLayout, true)
        items.add(UItem.asShadow(getString(R.string.MainMenuItemsInfo)))

        if (mainMenuHiddenItems.isNotEmpty()) {
            addMenuSection(items, adapter, getString(R.string.MainMenuHiddenItems), mainMenuHiddenItems, false)
            items.add(UItem.asShadow(null))
        }
    }

    private fun addMenuSection(items: ArrayList<UItem>, adapter: UniversalAdapter, header: String, ids: ArrayList<Int>, mainSection: Boolean) {
        adapter.whiteSectionStart()
        items.add(UItem.asHeader(header))
        adapter.reorderSectionStart()

        var dividerIndex = 0
        for (id in ids) {
            if (id == MainMenuItem.PLUGINS.id && !PluginsController.isPluginEngineSupported())
                continue
            if (id == MainMenuItem.BOTS.id && MediaDataController.getInstance(currentAccount).attachMenuBots?.bots?.none { it.show_in_side_menu } != false)
                continue

            if (id == MainMenuItem.DIVIDER.id) {
                if (mainSection && dividerIndex < stableDividerIds.size)
                    createMenuItem(stableDividerIds[dividerIndex], null)?.let { items.add(it) }
                else if (!mainSection)
                    createMenuItem(MainMenuItem.DIVIDER.id, null)?.let { items.add(it) }
                dividerIndex++
            } else {
                itemDetails[id]?.let { info ->
                    createMenuItem(id, info)?.let { items.add(it) }
                }
            }
        }

        adapter.reorderSectionEnd()
        if (mainSection)
            items.add(uButton(-200, getString(R.string.MainMenuAddDivider), icon = R.drawable.msg_add, accent = true, searchable = false))
        adapter.whiteSectionEnd()
    }

    private fun createMenuItem(id: Int, info: ItemInfo?): UItem? = when {
        id <= DIVIDER_BASE_ID || id == MainMenuItem.DIVIDER.id -> uButton(id, getString(R.string.ArticleCommandDivider), icon = R.drawable.msg_block, searchable = false)
        info != null -> uButton(id, info.name, icon = info.iconRes, searchable = false)
        else -> null
    }?.apply { object2 = reorderIcon }

    private fun updateConfigFromReorder(sectionId: Int, uItems: ArrayList<UItem>) {
        val newIds = ArrayList<Int>()
        val newStableIds = ArrayList<Int>()

        for (item in uItems) {
            val isDivider = item.id <= DIVIDER_BASE_ID || item.id == MainMenuItem.DIVIDER.id
            if (isDivider) {
                if (sectionId == 0) {
                    newIds.add(MainMenuItem.DIVIDER.id)
                    newStableIds.add(if (item.id <= DIVIDER_BASE_ID) item.id else nextDividerId--)
                }
            } else {
                newIds.add(item.id)
            }
        }

        val previousIds = if (sectionId == 0) mainMenuLayout else mainMenuHiddenItems
        val botsIndex = previousIds.indexOf(MainMenuItem.BOTS.id)
        if (botsIndex >= 0 && !newIds.contains(MainMenuItem.BOTS.id)) {
            newIds.add(botsIndex.coerceAtMost(newIds.size), MainMenuItem.BOTS.id)
        }

        if (sectionId == 0) {
            stableDividerIds.clear()
            stableDividerIds.addAll(newStableIds)
            if (hidden()) {
                val settingsId = MainMenuItem.SETTINGS.id
                if (!newIds.contains(settingsId))
                    newIds.add(settingsId)
            }
            mainMenuLayout.clear()
            mainMenuLayout.addAll(newIds)
        } else if (sectionId == 1) {
            if (hidden()) newIds.remove(MainMenuItem.SETTINGS.id)
            mainMenuHiddenItems.clear()
            mainMenuHiddenItems.addAll(newIds)
        }

        saveAndNotify()
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val id = item.id
        val navigationItem = AppNavigationItem.fromId(id)

        if (navigationItem != null) {
            when (navigationItem) {
                AppNavigationItem.DRAWER -> {
                    toggleBooleanSettingAndRefresh(item) { navigationDrawer = it }
                    notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
                    listView.adapter?.update(true)
                    parentLayout.rebuildFragments(0)
                }
                AppNavigationItem.IMMERSIVE_ANIMATION ->
                    toggleBooleanSettingAndRefresh(item) { immersiveDrawerAnimation = it }
                AppNavigationItem.BOTTOM_NAVIGATION_BAR_MODE ->
                    showListDialog(item, bottomNavigationModes, getString(R.string.BottomNavigationBarMode), getMode()) { i ->
                        preferences.edit { putInt("bottomNavigationBarMode", i) }
                        setMode(i)
                        ensureSettingsVisibility()
                        notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
                        refreshEditorList()
                        updateResetButtonVisibility()
                        parentLayout.rebuildFragments(0)
                    }
                AppNavigationItem.HIDE_NAVIGATION_BAR_LABELS -> {
                    toggleBooleanSettingAndRefresh(item) { hideNavigationBarLabels = it }
                    parentLayout.rebuildFragments(0)
                }
                AppNavigationItem.TRANSITION_ANIMATION ->
                    showListDialog(item, transitionAnimations, getString(R.string.TransitionAnimation), transitionAnimation.ordinal) { i ->
                        transitionAnimation = TransitionAnimation.entries[i]
                        if (transitionAnimation != TransitionAnimation.DEFAULT) {
                            MessagesController.getGlobalMainSettings().edit {
                                putBoolean("view_animations", true)
                            }
                            SharedConfig.setAnimationsEnabled(true)
                        }
                    }
                AppNavigationItem.TABLET_MODE ->
                    showListDialog(item, tabletModeOptions, getString(R.string.TabletMode), tabletMode) { i ->
                        tabletMode = i
                        showRestartBulletin()
                    }
                else -> {}
            }
            return
        }

        if (id == -200) {
            stableDividerIds.add(nextDividerId--)
            mainMenuLayout.add(MainMenuItem.DIVIDER.id)
            saveAndNotify()
            return
        }

        if (id <= DIVIDER_BASE_ID) {
            val dividerIndexInList = stableDividerIds.indexOf(id)

            if (dividerIndexInList != -1) {
                var indexToRemove = -1
                var currentDividerCount = 0
                for (i in mainMenuLayout.indices) {
                    if (mainMenuLayout[i] == MainMenuItem.DIVIDER.id) {
                        if (currentDividerCount == dividerIndexInList) {
                            indexToRemove = i
                            break
                        }
                        currentDividerCount++
                    }
                }

                if (indexToRemove != -1) {
                    stableDividerIds.removeAt(dividerIndexInList)
                    mainMenuLayout.removeAt(indexToRemove)
                    saveAndNotify()
                }
            }
            return
        }

        if (id == MainMenuItem.DIVIDER.id) {
            mainMenuHiddenItems.remove(id)
            saveAndNotify()
            return
        }

        if (hidden() && id == MainMenuItem.SETTINGS.id && mainMenuLayout.contains(id)) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MainMenuRemoveSettingsInfo)).show()
            return
        }

        if (mainMenuLayout.contains(id)) {
            mainMenuLayout.remove(id)
            if (id != MainMenuItem.DIVIDER.id && !mainMenuHiddenItems.contains(id)) {
                mainMenuHiddenItems.add(0, id)
            }
        } else if (mainMenuHiddenItems.contains(id)) {
            mainMenuHiddenItems.remove(id)
            mainMenuLayout.add(id)
        }

        saveAndNotify()
    }

    private fun saveAndNotify() {
        saveMainMenuLayout()
        notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
        refreshEditorList()
        updateResetButtonVisibility()
    }

    private fun refreshEditorList() = listView.apply {
        hideSelector(false)
        cancelClickRunnables(false)
        adapter.update(true)
    }

    private fun updateResetButtonVisibility(animated: Boolean = true) {
        resetItem ?: return
        val isModified = mainMenuLayout != getDefaultMainMenuLayout()
        AndroidUtilities.updateViewVisibilityAnimated(resetItem, isModified, 0.5f, animated)
    }

    private fun resetToDefault() {
        mainMenuLayout.clear()
        mainMenuLayout.addAll(getDefaultMainMenuLayout())

        mainMenuHiddenItems.clear()
        for (item in MainMenuItem.entries) {
            if (item != MainMenuItem.DIVIDER && !mainMenuLayout.contains(item.id)) {
                if (item == MainMenuItem.PLUGINS && !PluginsController.isPluginEngineSupported())
                    continue
                mainMenuHiddenItems.add(item.id)
            }
        }

        stableDividerIds.clear()
        nextDividerId = DIVIDER_BASE_ID
        for (id in mainMenuLayout)
            if (id == MainMenuItem.DIVIDER.id)
                stableDividerIds.add(nextDividerId--)

        saveMainMenuLayout()
        notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)

        refreshEditorList()
        updateResetButtonVisibility()
    }
}