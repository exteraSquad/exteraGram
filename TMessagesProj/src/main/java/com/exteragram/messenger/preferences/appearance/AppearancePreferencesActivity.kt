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
import android.os.Build
import android.view.View
import com.exteragram.messenger.*
import com.exteragram.messenger.appicons.AppIcon
import com.exteragram.messenger.appicons.AppIconController
import com.exteragram.messenger.appicons.AppIconPreviewDrawable
import com.exteragram.messenger.appicons.ui.AppIconsActivity
import com.exteragram.messenger.icons.ui.IconPacksActivity
import com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.SwitchGroup
import com.exteragram.messenger.preferences.appearance.components.AvatarCornersPreviewCell
import com.exteragram.messenger.preferences.appearance.components.ChatListPreviewCell
import com.exteragram.messenger.preferences.appearance.components.FabShapeCell
import com.exteragram.messenger.preferences.appearance.components.FilterTabsPreviewCell
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.addIf
import com.exteragram.messenger.utils.folders.FolderCounters
import com.exteragram.messenger.utils.network.RemoteUtils
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.INavigationLayout.REBUILD_FLAG_REBUILD_LAST
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class AppearancePreferencesActivity : BasePreferencesActivity() {

    companion object {
        private const val APP_ICON_PREVIEW_SIZE_DP = 34
    }

    enum class AppearanceItem {
        AVATAR_CORNERS_PREVIEW,
        SINGLE_CORNER_RADIUS,
        CHAT_LIST_PREVIEW,
        FORCE_SNOW,
        HIDE_ACTION_BAR_STATUS,
        CENTER_TITLE,
        HIDE_STORIES,
        HIDE_FLOATING_BUTTON,
        HIDE_DIALOGS_SEARCH_BAR,
        SENDER_MINI_AVATARS,
        ACTION_BAR_TITLE,
        PILL_STACK,
        FOLDERS_PREVIEW,
        TAB_TITLE,
        TAB_COUNTER,
        HIDE_ALL_CHATS,
        APP_NAVIGATION_SETTINGS,
        APP_ICON,
        ICON_PACKS,
        FAB_SHAPE,
        SECTION_RADIUS,
        SEPARATED_HEADERS,
        DIVIDER_STYLE,
        MD3_STYLES,
        NEW_LOADING_STYLE,
        NEW_SLIDER_STYLE,
        NEW_SWITCH_STYLE,
        USE_SYSTEM_FONTS,
        USE_SYSTEM_EMOJI,
        GOOEY_AVATAR_ANIMATION,
        CUSTOM_THEMES,
        GLASS_OUTLINE_STYLE,
        HEADER_BLUR_STYLE,
        GLASS_MESSAGE_MENU,
        NEW_CHAT_HEADER_STYLE,
        NEW_NAVIGATION_BAR_STYLE,
        NEW_FAB_STYLE,
        NEW_PROFILE_ACTIONS_STYLE,
        NEW_PLAYER_STYLE;

        val id: Int = ordinal + 1
    }

    private lateinit var titles: Array<CharSequence>
    private lateinit var tabIconsOptions: Array<CharSequence>
    private lateinit var tabCounterModes: Array<CharSequence>
    private lateinit var dividerStyles: Array<CharSequence>
    private lateinit var glassOutlineStyles: Array<CharSequence>
    private val headerBlurStyles = HeaderBlurStyle.entries.filter { it != HeaderBlurStyle.PROGRESSIVE || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

    private var avatarCornersPreviewCell: AvatarCornersPreviewCell? = null
    private var chatListPreviewCell: ChatListPreviewCell? = null
    private var filterTabsPreviewCell: FilterTabsPreviewCell? = null
    private var fabShapeCell: FabShapeCell? = null

    private var appIconDrawableIcon: AppIcon? = null
    private var appIconDrawableShape = false
    private var appIconDrawable: AppIconPreviewDrawable? = null

    private val md3Styles =
        SwitchGroup(this, AppearanceItem.MD3_STYLES, R.string.MaterialDesign3, linkAlias = "md3Styles").apply {
            onChanged(this@AppearancePreferencesActivity::updateMD3Styles)
            add(AppearanceItem.NEW_LOADING_STYLE, R.string.NewLoadingStyle, ::newLoadingStyle)
            add(AppearanceItem.NEW_SLIDER_STYLE, R.string.NewSliderStyle, ::newSliderStyle)
            add(AppearanceItem.NEW_SWITCH_STYLE, R.string.NewSwitchStyle, ::newSwitchStyle)
            add(AppearanceItem.NEW_CHAT_HEADER_STYLE, R.string.ChatHeader, ::newChatHeaderStyle)
            markNew("Appearance-M3Styles-ChatHeader")
            add(AppearanceItem.NEW_NAVIGATION_BAR_STYLE, R.string.BottomNavigationBarMode, ::newNavigationBarStyle)
            markNew("Appearance-M3Styles-NavigationBar")
            add(AppearanceItem.NEW_FAB_STYLE, R.string.NewFabStyle, ::newFabStyle)
            markNew("Appearance-M3Styles-FloatingButton")
            add(AppearanceItem.NEW_PROFILE_ACTIONS_STYLE, R.string.NewProfileActionsStyle, ::newProfileActionsStyle)
            markNew("Appearance-M3Styles-ProfileButtons")
            add(AppearanceItem.NEW_PLAYER_STYLE, R.string.NewPlayerStyle, ::newPlayerStyle)
            markNew("Appearance-M3Styles-Player")
        }

    override fun initializeOptionStrings() {
        titles = arrayOf(
            getString(R.string.exteraAppName),
            getString(R.string.Username),
            getString(R.string.ActionBarTitleName),
            getString(R.string.FilterChats)
        )
        tabIconsOptions = arrayOf(
            getString(R.string.TabTitleStyleTextWithIcons),
            getString(R.string.TabTitleStyleTextOnly),
            getString(R.string.TabTitleStyleIconsOnly)
        )
        tabCounterModes = arrayOf(
            getString(R.string.FilterAllChats),
            getString(R.string.TabCounterUnmuted),
            getString(R.string.BlurOff)
        )
        dividerStyles = arrayOf(
            getString(R.string.DividerStyleHidden),
            getString(R.string.DividerStyleLine),
            getString(R.string.DividerStyleSegments)
        )
        glassOutlineStyles = arrayOf(
            getString(R.string.GlassOutlineGlare),
            getString(R.string.GlassOutlineSolid),
            getString(R.string.GlassOutlineHidden)
        )
    }

    override fun createView(context: Context): View {
        avatarCornersPreviewCell = AvatarCornersPreviewCell(context, this, resourceProvider, RemoteUtils.getIntConfigValue(RemoteUtils.PREFERENCES_PREVIEW_STYLE, 0))
        chatListPreviewCell = ChatListPreviewCell(context)
        filterTabsPreviewCell = FilterTabsPreviewCell(context)
        fabShapeCell = object : FabShapeCell(context) {
            override fun rebuildFragments() = parentLayout.rebuildFragments(0)
        }
        return super.createView(context)
    }

    override fun getTitle(): String = getString(R.string.Appearance)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asCustom(AppearanceItem.AVATAR_CORNERS_PREVIEW.id, avatarCornersPreviewCell).setLinkAlias("avatarCorners", this))
        items.add(uCheck(AppearanceItem.SINGLE_CORNER_RADIUS, getString(R.string.SingleCornerRadius), singleCornerRadius, linkAlias = "singleCornerRadius"))
        items.add(UItem.asShadow(getString(R.string.SingleCornerRadiusInfo)))

        items.add(UItem.asHeader(getString(R.string.ListOfChats)))
        items.add(UItem.asCustom(AppearanceItem.CHAT_LIST_PREVIEW.id, chatListPreviewCell))
        items.add(uCheck(AppearanceItem.FORCE_SNOW, getString(R.string.ForceSnow), forceSnow, subtext = getString(R.string.ForceSnowInfo), linkAlias = "forceSnow"))
        items.addIf(userConfig.isPremium, uCheck(AppearanceItem.HIDE_ACTION_BAR_STATUS, getString(R.string.HideActionBarStatus), hideActionBarStatus, linkAlias = "hideActionBarStatus"))
        items.add(uCheck(AppearanceItem.CENTER_TITLE, getString(R.string.CenterTitle), centerTitle, linkAlias = "centerTitle"))
        items.add(uCheck(AppearanceItem.HIDE_STORIES, getString(R.string.HideStories), hideStories, linkAlias = "hideStories"))
        items.add(uCheck(AppearanceItem.HIDE_FLOATING_BUTTON, getString(R.string.HideFloatingButton), hideFloatingButton, linkAlias = "hideFloatingButton"))
        items.add(uCheck(AppearanceItem.HIDE_DIALOGS_SEARCH_BAR, getString(R.string.HideDialogsSearchBar), hideDialogsSearchBar, linkAlias = "hideDialogsSearchBar"))
        items.add(uCheck(AppearanceItem.SENDER_MINI_AVATARS, getString(R.string.SenderMiniAvatars), senderMiniAvatars, linkAlias = "senderMiniAvatars"))
        items.add(uButton(AppearanceItem.ACTION_BAR_TITLE, getString(R.string.ActionBarTitle), value = titles[titleText], linkAlias = "actionBarTitle"))
        items.add(UItem.asShadow(getString(R.string.ListOfChatsInfo)))

        items.add(UItem.asHeader(getString(R.string.Filters)))
        items.add(UItem.asCustom(AppearanceItem.FOLDERS_PREVIEW.id, filterTabsPreviewCell))
        items.add(uButton(AppearanceItem.TAB_TITLE, getString(R.string.TabTitleStyle), value = tabIconsOptions[TabIconsMode.entries.indexOf(tabIcons)], linkAlias = "tabTitleStyle"))
        items.add(uButton(AppearanceItem.TAB_COUNTER, getString(R.string.TabCounter), value = tabCounterModes[tabCounterMode.ordinal], linkAlias = "tabCounter"))
        items.add(uCheck(AppearanceItem.HIDE_ALL_CHATS, LocaleController.formatString(R.string.HideAllChats, getString(R.string.FilterAllChats)), hideAllChats, linkAlias = "hideAllChats"))
        items.add(UItem.asShadow(getString(R.string.FoldersInfo)))

        items.add(uButton(AppearanceItem.APP_NAVIGATION_SETTINGS, getString(R.string.AppNavigation), R.drawable.msg_newphone, subtext = getString(R.string.AppNavigationInfo), linkAlias = "appNavigationSettings"))
        items.add(uButton(AppearanceItem.APP_ICON, getString(R.string.AppIcon), R.drawable.menu_edit_appearance, subtext = AppIconController.getSelectedIcon().getTitle(), linkAlias = "appIcon")
            .onBind { view ->
                if (view !is TextCell) return@onBind
                view.apply {
                    valueImageView.visibility = View.VISIBLE
                    valueImageView.setImageDrawable(getAppIconDrawable())
                    valueImageRight = (view.heightDp - APP_ICON_PREVIEW_SIZE_DP) / 2
                }
            }
        )
        items.add(uButton(AppearanceItem.ICON_PACKS, getString(R.string.IconPacks), R.drawable.msg_sticker, subtext = getString(R.string.IconPacksInfo), linkAlias = "iconPacks"))
        items.add(uButton(AppearanceItem.PILL_STACK, getString(R.string.PillStackPills), R.drawable.outline_header_search, subtext = getString(R.string.PillStackPillsInfo), linkAlias = "pillStack"))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.Appearance)))
        items.add(UItem.asCustom(AppearanceItem.FAB_SHAPE.id, fabShapeCell).setLinkAlias("fabShape", this))
        items.add(uCheck(AppearanceItem.USE_SYSTEM_FONTS, getString(R.string.UseSystemFonts), useSystemFonts, linkAlias = "useSystemFonts"))
        items.add(uCheck(AppearanceItem.USE_SYSTEM_EMOJI, getString(R.string.UseSystemEmoji), SharedConfig.useSystemEmoji, linkAlias = "useSystemEmoji"))
        md3Styles.fill(items)
        items.add(uCheck(AppearanceItem.GOOEY_AVATAR_ANIMATION, getString(R.string.GooeyAvatarAnimation), gooeyAvatarAnimation, linkAlias = "gooeyAvatarAnimation"))
        items.add(uCheck(AppearanceItem.CUSTOM_THEMES, getString(R.string.CustomChatThemes), customThemes, linkAlias = "customThemes"))
        items.add(UItem.asShadow(getString(R.string.CustomChatThemesInfo)))

        items.add(UItem.asHeader(if (SettingsRegistry.markAsNewFeature("Appearance-Sections")) LocaleUtils.applyNewSpan(getString(R.string.Sections)) else getString(R.string.Sections)))
        items.add(createSectionRadiusSliderItem().setSearchable(this).setLinkAlias("sectionRadius", this))
        val segmentedDividers = dividerStyle == DividerStyle.SEGMENTS
        val separatedHeadersCheck = uCheck(AppearanceItem.SEPARATED_HEADERS, getString(R.string.SeparateHeaders), sectionsSeparatedHeaders, linkAlias = "sectionsSeparatedHeaders")
        separatedHeadersCheck.setEnabled(!segmentedDividers)
        items.add(separatedHeadersCheck)
        items.add(uButton(AppearanceItem.DIVIDER_STYLE, getString(R.string.DividerStyle), value = dividerStyles[dividerStyle.ordinal], linkAlias = "dividerStyle"))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.BlurOptions)))
        items.add(uButton(AppearanceItem.GLASS_OUTLINE_STYLE, getString(R.string.GlassOutlineStyle), value = glassOutlineStyles[glassOutlineStyle.ordinal], linkAlias = "glassOutlineStyle"))
        items.add(uCheck(AppearanceItem.GLASS_MESSAGE_MENU, getString(R.string.GlassMessageMenu), glassMessageMenu, getString(R.string.GlassMessageMenuInfo), linkAlias = "glassMessageMenu"))
        items.addIf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, uButton(AppearanceItem.HEADER_BLUR_STYLE, getString(R.string.HeaderBlurStyle), value = getHeaderBlurStyleName(currentHeaderBlurStyle()), linkAlias = "headerBlurStyle"))
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id <= 0 || item.id > AppearanceItem.entries.size) return
        val clickedItem = AppearanceItem.entries[item.id - 1]
        when (clickedItem) {
            AppearanceItem.SINGLE_CORNER_RADIUS -> {
                toggleBooleanSettingAndRefresh(item) { singleCornerRadius = it }
                parentLayout.rebuildFragments(0)
            }
            AppearanceItem.ACTION_BAR_TITLE ->
                showListDialog(item, titles, getString(R.string.ActionBarTitle), titleText) { i ->
                    titleText = i
                    handleActionBarTitleClick()
                }
            AppearanceItem.PILL_STACK -> presentFragment(PillStackPreferencesActivity())
            AppearanceItem.HIDE_STORIES -> {
                toggleBooleanSettingAndRefresh(item) { hideStories = it }
                notificationCenter.postNotificationName(NotificationCenter.storiesEnabledUpdate)
            }
            AppearanceItem.HIDE_ACTION_BAR_STATUS -> {
                toggleBooleanSettingAndRefresh(item) { hideActionBarStatus = it }
                chatListPreviewCell?.updateStatus(true)
                parentLayout.rebuildFragments(0)
            }
            AppearanceItem.CENTER_TITLE -> {
                toggleBooleanSettingAndRefresh(item) { centerTitle = it }
                chatListPreviewCell?.updateCentered(true)
                actionBar.refreshTitlePosition(true)
                parentLayout.rebuildFragments(0)
            }
            AppearanceItem.HIDE_FLOATING_BUTTON -> {
                toggleBooleanSettingAndRefresh(item) { hideFloatingButton = it }
                parentLayout.rebuildFragments(0)
            }
            AppearanceItem.HIDE_DIALOGS_SEARCH_BAR -> {
                toggleBooleanSettingAndRefresh(item) { hideDialogsSearchBar = it }
                parentLayout.rebuildFragments(0)
            }
            AppearanceItem.SENDER_MINI_AVATARS ->
                toggleBooleanSettingAndRefresh(item) { senderMiniAvatars = it }
            AppearanceItem.TAB_TITLE ->
                showListDialog(item, tabIconsOptions, getString(R.string.TabTitleStyle), TabIconsMode.entries.indexOf(tabIcons)) { i ->
                    tabIcons = TabIconsMode.entries[i]
                    handleTabTitleClick()
                }
            AppearanceItem.TAB_COUNTER ->
                showListDialog(item, tabCounterModes, getString(R.string.TabCounter), tabCounterMode.ordinal) { i ->
                    tabCounterMode = TabCounterMode.entries[i]
                    handleTabCounterClick()
                }
            AppearanceItem.HIDE_ALL_CHATS -> {
                toggleBooleanSettingAndRefresh(item) { hideAllChats = it }
                handleHideAllChatsClick()
            }
            AppearanceItem.APP_NAVIGATION_SETTINGS -> presentFragment(AppNavigationPreferencesActivity())
            AppearanceItem.APP_ICON -> presentFragment(AppIconsActivity())
            AppearanceItem.ICON_PACKS -> presentFragment(IconPacksActivity())
            AppearanceItem.SEPARATED_HEADERS -> {
                if (dividerStyle == DividerStyle.SEGMENTS) return
                toggleBooleanSettingAndRefresh(item) { sectionsSeparatedHeaders = it }
                listView.invalidateItemDecorations()
            }
            AppearanceItem.DIVIDER_STYLE ->
                showListDialog(item, dividerStyles, getString(R.string.DividerStyle), dividerStyle.ordinal) { i ->
                    dividerStyle = DividerStyle.entries[i]
                    if (dividerStyle == DividerStyle.SEGMENTS) sectionsSeparatedHeaders = true
                    handleDividerStyleChange()
                }
            AppearanceItem.USE_SYSTEM_FONTS -> {
                toggleBooleanSettingAndRefresh(item) { useSystemFonts = it }
                handleUseSystemFontsClick()
            }
            AppearanceItem.USE_SYSTEM_EMOJI -> handleUseSystemEmojiClick(item)
            AppearanceItem.MD3_STYLES, AppearanceItem.NEW_LOADING_STYLE, AppearanceItem.NEW_SLIDER_STYLE, AppearanceItem.NEW_SWITCH_STYLE,
            AppearanceItem.NEW_CHAT_HEADER_STYLE, AppearanceItem.NEW_NAVIGATION_BAR_STYLE, AppearanceItem.NEW_FAB_STYLE,
            AppearanceItem.NEW_PROFILE_ACTIONS_STYLE, AppearanceItem.NEW_PLAYER_STYLE -> md3Styles.onClick(item)
            AppearanceItem.GOOEY_AVATAR_ANIMATION -> toggleBooleanSettingAndRefresh(item) { gooeyAvatarAnimation = it }
            AppearanceItem.CUSTOM_THEMES -> toggleBooleanSettingAndRefresh(item) { customThemes = it }
            AppearanceItem.FORCE_SNOW -> {
                toggleBooleanSettingAndRefresh(item) { forceSnow = it }
                chatListPreviewCell?.invalidate()
            }
            AppearanceItem.GLASS_OUTLINE_STYLE ->
                showListDialog(item, glassOutlineStyles, getString(R.string.GlassOutlineStyle), glassOutlineStyle.ordinal) { i ->
                    glassOutlineStyle = GlassOutlineStyle.entries[i]
                    parentLayout.rebuildFragments(0)
                }
            AppearanceItem.HEADER_BLUR_STYLE ->
                showListDialog(item, headerBlurStyles.map(::getHeaderBlurStyleName).toTypedArray(), getString(R.string.HeaderBlurStyle), headerBlurStyles.indexOf(currentHeaderBlurStyle())) { i ->
                    headerBlurStyle = headerBlurStyles[i]
                    updateHeaderFade()
                    parentLayout.rebuildFragments(0)
                }
            AppearanceItem.GLASS_MESSAGE_MENU -> {
                toggleBooleanSettingAndRefresh(item) { glassMessageMenu = it }
                handleGlassMessageMenuChange()
            }
            else -> {}
        }
    }

    private fun createSectionRadiusSliderItem(): UItem {
        val item = UItem.asIntSlideView(
            1,
            0,
            getSectionRadiusDp(),
            SECTION_RADIUS_MAX_DP,
            this::formatSectionRadius
        ) { value ->
            sectionRadius = value.toFloat()
            handleSectionRadiusChange()
        }
        item.id = AppearanceItem.SECTION_RADIUS.id
        item.text = getString(R.string.Sections)
        return item
    }

    private fun formatSectionRadius(value: Int): CharSequence = when (value) {
        0 -> getString(R.string.BlurOff)
        SECTION_RADIUS_MAX_DP -> getString(R.string.PredictiveBackMax)
        else -> "$value dp"
    }

    private fun handleDividerStyleChange() {
        Theme.applyCommonTheme()
        listView.invalidate()
        listView.invalidateItemDecorations()
        avatarCornersPreviewCell?.invalidate()
        chatListPreviewCell?.invalidate()
        fabShapeCell?.invalidate()
        filterTabsPreviewCell?.invalidate()
        parentLayout.rebuildFragments(0)
    }

    private fun handleSectionRadiusChange() {
        listView.apply {
            setSections()
            invalidate()
            invalidateItemDecorations()
        }
        parentLayout.rebuildFragments(0)
    }

    private fun updateMD3Styles() {
        avatarCornersPreviewCell?.updateSliderStyle()
        parentLayout.rebuildFragments(0)
    }

    private fun handleActionBarTitleClick() {
        chatListPreviewCell?.updateStatus(true)
        notificationCenter.postNotificationName(NotificationCenter.currentUserPremiumStatusChanged)
    }

    private fun handleTabTitleClick() =
        notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)

    private fun handleTabCounterClick() {
        FolderCounters.recountAll()
        notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)
    }

    private fun getAppIconDrawable(): AppIconPreviewDrawable {
        val icon = AppIconController.getSelectedIcon()
        val systemShape = useSystemIconShape
        var drawable = appIconDrawable
        if (drawable == null || appIconDrawableIcon != icon || appIconDrawableShape != systemShape) {
            appIconDrawableIcon = icon
            appIconDrawableShape = systemShape
            drawable = AppIconPreviewDrawable(icon, AndroidUtilities.dp(APP_ICON_PREVIEW_SIZE_DP.toFloat()))
            appIconDrawable = drawable
        }
        return drawable
    }

    private fun handleHideAllChatsClick() {
        notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)
        notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
    }

    private fun handleUseSystemFontsClick() {
        AndroidUtilities.clearTypefaceCache()
        parentLayout.rebuildFragments(REBUILD_FLAG_REBUILD_LAST)
    }

    private fun currentHeaderBlurStyle() = if (headerBlurStyle in headerBlurStyles) headerBlurStyle else HeaderBlurStyle.DEFAULT

    private fun getHeaderBlurStyleName(style: HeaderBlurStyle): CharSequence = getString(
        when (style) {
            HeaderBlurStyle.DEFAULT -> R.string.HeaderBlurDefault
            HeaderBlurStyle.PROGRESSIVE -> R.string.HeaderBlurProgressive
            HeaderBlurStyle.FADE -> R.string.HeaderBlurFade
        }
    )

    private fun handleGlassMessageMenuChange() {
        if (!glassMessageMenu || SharedConfig.chatBlurEnabled()) return
        BulletinFactory.of(this).createSimpleBulletin(
            R.raw.info,
            getString(R.string.GlassMessageMenuBlurOff),
            getString(R.string.Enable),
            SharedConfig::toggleChatBlur
        ).show()
    }

    private fun handleUseSystemEmojiClick(item: UItem) {
        SharedConfig.toggleUseSystemEmoji()
        item.setChecked(!SharedConfig.useSystemEmoji)
        parentLayout.rebuildFragments(0)
        listView.adapter?.update(true)
    }
}