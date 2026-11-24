/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.ui.PluginsActivity
import com.exteragram.messenger.preferences.appearance.AppearancePreferencesActivity
import com.exteragram.messenger.preferences.chats.ChatsPreferencesActivity
import com.exteragram.messenger.preferences.components.CommunityCardCell
import com.exteragram.messenger.preferences.components.HeaderSettingsCell
import com.exteragram.messenger.useSystemIconShape
import com.exteragram.messenger.utils.addIf
import com.exteragram.messenger.utils.system.VibratorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController.formatPluralString
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.LaunchActivity
import org.telegram.ui.community.CommunitySheet
import java.util.ArrayList

class MainPreferencesActivity : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    enum class PreferenceItem {
        HEADER_CELL,
        GENERAL_CATEGORY,
        APPEARANCE_CATEGORY,
        CHATS_CATEGORY,
        PLUGINS_CATEGORY,
        OTHER_CATEGORY,
        CHANNEL,
        FORUM,
        CROWDIN,
        COMMUNITY,
        SOURCE_CODE;

        val id: Int = ordinal + 1
    }

    private lateinit var headerSettingsCell: HeaderSettingsCell
    private var communityRequested = false

    override fun getTitle(): String = getString(R.string.Preferences)
    override fun needHideTitle(): Boolean = true

    override fun onFragmentCreate(): Boolean {
        notificationCenter.addWeakObserver(this, NotificationCenter.chatInfoDidLoad)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        notificationCenter.removeObserver(this, NotificationCenter.chatInfoDidLoad)
        super.onFragmentDestroy()
    }

    override fun createView(context: Context): View {
        headerSettingsCell = HeaderSettingsCell(context)
        fragmentView = super.createView(context)
        if (parentLayout?.isRightLayout == true) actionBar.setBackButtonImage(R.drawable.ic_ab_close)
        requestCommunity()
        return fragmentView
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asCustomShadow(headerSettingsCell, 198).setId(PreferenceItem.HEADER_CELL.id))

        items.add(UItem.asHeader(getString(R.string.Categories)))
        items.add(uButton(PreferenceItem.GENERAL_CATEGORY, getString(R.string.General), R.drawable.msg_media, linkAlias = "general"))
        items.add(uButton(PreferenceItem.APPEARANCE_CATEGORY, getString(R.string.Appearance), R.drawable.msg_theme, linkAlias = "appearance"))
        items.add(uButton(PreferenceItem.CHATS_CATEGORY, getString(R.string.SearchAllChatsShort), R.drawable.msg_discussion, linkAlias = "chats"))
        items.addIf(PluginsController.isPluginEngineSupported(), uButton(PreferenceItem.PLUGINS_CATEGORY, getString(R.string.Plugins), R.drawable.msg_plugins, linkAlias = "plugins"))
        items.add(uButton(PreferenceItem.OTHER_CATEGORY, getString(R.string.LocalOther), R.drawable.msg_fave, linkAlias = "other"))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.Links)))
        val community = findCommunity()
        val linkedPeers = community?.let { messagesController.getChatFull(it.id)?.linked_peers }
        val chat = community ?: findChannel()
        val title = chat?.title ?: getString(R.string.exteraAppName)
        val subtitle = if (linkedPeers.isNullOrEmpty()) "@$CHANNEL_USERNAME · @$FORUM_USERNAME" else formatPluralString("CommunityWithChats", linkedPeers.size)
        items.add(
            CommunityCardCell.Factory.of(PreferenceItem.COMMUNITY.id, chat, title, subtitle)
                .setLinkAlias("community", this)
                .setLinkAlias("channel", this)
                .setLinkAlias("chat", this)
        )
        items.add(uButton(PreferenceItem.CROWDIN, getString(R.string.Crowdin), R.drawable.msg_translate, "Crowdin", linkAlias = "crowdin"))
        items.add(uButton(PreferenceItem.SOURCE_CODE, getString(R.string.SourceCode), R.drawable.msg_delete, "GitHub", showDivider = false, linkAlias = "sourceCode"))
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id <= 0 || item.id > PreferenceItem.entries.size) return
        val clickedItem = PreferenceItem.entries[item.id - 1]
        when (clickedItem) {
            PreferenceItem.HEADER_CELL -> if (!BuildVars.PM_BUILD) (parentActivity as? LaunchActivity)?.checkAppUpdate(true)
            PreferenceItem.GENERAL_CATEGORY -> presentFragment(GeneralPreferencesActivity())
            PreferenceItem.APPEARANCE_CATEGORY -> presentFragment(AppearancePreferencesActivity())
            PreferenceItem.CHATS_CATEGORY -> presentFragment(ChatsPreferencesActivity())
            PreferenceItem.PLUGINS_CATEGORY -> presentFragment(PluginsActivity())
            PreferenceItem.OTHER_CATEGORY -> presentFragment(OtherPreferencesActivity())
            PreferenceItem.CHANNEL -> messagesController.openByUserName(CHANNEL_USERNAME, this, 1)
            PreferenceItem.FORUM -> messagesController.openByUserName(FORUM_USERNAME, this, 1)
            PreferenceItem.COMMUNITY -> {
                val community = findCommunity()
                if (community != null) {
                    showDialog(CommunitySheet(this, community.id))
                } else {
                    showCommunityOptions(view)
                }
            }
            PreferenceItem.CROWDIN -> Browser.openUrl(parentActivity, "https://crowdin.com/project/exteralocales")
            PreferenceItem.SOURCE_CODE -> Browser.openUrl(parentActivity, "https://github.com/exteraSquad/exteraGram")
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        if (item.id <= 0 || item.id > PreferenceItem.entries.size) return false
        val clickedItem = PreferenceItem.entries[item.id - 1]
        if (clickedItem == PreferenceItem.HEADER_CELL) {
            useSystemIconShape = !useSystemIconShape
            view.performHapticFeedback(VibratorUtils.getType(HapticFeedbackConstants.KEYBOARD_TAP), HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            view.invalidate()
            return true
        }
        return super.onLongClick(item, view, position, x, y)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id != NotificationCenter.chatInfoDidLoad) return
        val chatFull = args.getOrNull(0) as? TLRPC.ChatFull ?: return
        val source = findChat(COMMUNITY_SOURCE_USERNAME) ?: return
        if (chatFull.id == source.id || chatFull.id == source.linked_community_id) {
            findCommunity()?.let { if (chatFull.id == source.id) loadCommunityInfo(it) }
            listView.adapter?.update(true)
        }
    }

    private fun showCommunityOptions(view: View) {
        ItemOptions.makeOptions(this, view).apply {
            add(R.drawable.msg_channel, getString(R.string.ProfileChannel)) {
                messagesController.openByUserName(CHANNEL_USERNAME, this@MainPreferencesActivity, 1)
            }
            add(R.drawable.msg_groups, getString(R.string.SearchAllChatsShort)) {
                messagesController.openByUserName(FORUM_USERNAME, this@MainPreferencesActivity, 1)
            }
            setScrimViewBackground(listView.getClipBackground(view))
            setDismissOnMoveOutside(true)
            show()
        }
    }

    private fun findChat(username: String): TLRPC.Chat? = messagesController.getUserOrChat(username) as? TLRPC.Chat

    private fun findChannel(): TLRPC.Chat? = findChat(CHANNEL_USERNAME)

    private fun findCommunity(): TLRPC.Chat? =
        findChat(COMMUNITY_SOURCE_USERNAME)?.linked_community_id?.takeIf { it != 0L }?.let(messagesController::getChat)

    private fun requestCommunity() {
        if (communityRequested) return
        communityRequested = true
        if (findChannel() == null) {
            messagesController.userNameResolver.resolve(CHANNEL_USERNAME) { _ ->
                if (findChannel() != null) listView.adapter?.update(true)
            }
        }
        val source = findChat(COMMUNITY_SOURCE_USERNAME)
        if (source != null) {
            requestCommunity(source)
            return
        }
        messagesController.userNameResolver.resolve(COMMUNITY_SOURCE_USERNAME) { _ ->
            val resolved = findChat(COMMUNITY_SOURCE_USERNAME) ?: return@resolve
            requestCommunity(resolved)
            listView.adapter?.update(true)
        }
    }

    private fun requestCommunity(source: TLRPC.Chat) {
        if (source.linked_community_id == 0L) return
        val community = messagesController.getChat(source.linked_community_id)
        if (community == null) {
            messagesController.loadFullChat(source.id, classGuid, false)
        } else {
            loadCommunityInfo(community)
        }
    }

    private fun loadCommunityInfo(community: TLRPC.Chat) {
        if (messagesController.getChatFull(community.id) == null) {
            messagesController.loadFullChat(community.id, classGuid, false)
        }
    }

    override fun getListTopPadding(topInset: Int): Int =
        topInset + ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(20f)

    private companion object {
        const val CHANNEL_USERNAME = "exteraGram"
        const val FORUM_USERNAME = "exteraForum"
        const val COMMUNITY_SOURCE_USERNAME = "exteraDevLog"
    }
}
