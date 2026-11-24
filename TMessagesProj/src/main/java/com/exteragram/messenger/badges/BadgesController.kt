/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.badges

import android.widget.FrameLayout
import com.exteragram.messenger.api.ApiController
import com.exteragram.messenger.api.db.ExteraDatabase
import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.api.dto.ProfileDTO
import com.exteragram.messenger.api.model.ProfileStatus
import com.exteragram.messenger.badges.source.ApiBadgeSource
import com.exteragram.messenger.components.SupporterBottomSheet
import com.exteragram.messenger.components.supporter.SupporterEnterFrom
import com.exteragram.messenger.utils.backgroundScope
import com.exteragram.messenger.utils.chats.ChatUtils
import com.exteragram.messenger.utils.network.RemoteUtils
import com.exteragram.messenger.utils.text.LocaleUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.BulletinFactory
import java.util.function.Consumer

object BadgesController {

    private val scope = backgroundScope()
    private val apiBadgeSource: ApiBadgeSource

    private val trustedPluginsCache = CachedRemoteSet(RemoteUtils.TRUSTED_PLUGINS, setOf(2562664432L))

    private const val NAME_PLACEHOLDER = "{name}"

    val DEV_BADGE = BadgeDTO(5359407509327085568L, null)
    val SUPPORTER_BADGE = BadgeDTO(5391059537102927631L, null)
    val TRUSTED_BADGE = BadgeDTO(5452008215409629764L, null) // TODO: update emoji

    init {
        val profileDao = ExteraDatabase.getInstance().profileDao()
        apiBadgeSource = ApiBadgeSource(profileDao)

        scope.launch {
            try {
                apiBadgeSource.loadToCache()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
            }
            val schemaStale = ApiController.isProfilesSchemaStale()
            AndroidUtilities.runOnUIThread {
                notifyBadgesChanged()
                if (!schemaStale) SupporterWelcomeController.check()
            }
        }
    }

    fun onProfilesSynced(profiles: List<ProfileDTO>) {
        apiBadgeSource.applySync(profiles)
        AndroidUtilities.runOnUIThread {
            notifyBadgesChanged()
            SupporterWelcomeController.check()
        }
    }

    private fun notifyBadgesChanged() {
        for (account in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            if (UserConfig.getInstance(account).isClientActivated) {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS)
            }
        }
    }

    @JvmOverloads
    fun getBadge(obj: TLObject? = UserConfig.getInstance(UserConfig.selectedAccount).currentUser): BadgeDTO? {
        try {
            val (id, isUser) = when (obj) {
                is TLRPC.User -> obj.id to true
                is TLRPC.Chat -> obj.id to false
                else -> return null
            }
            if (!isUser && isTrusted(id)) {
                return TRUSTED_BADGE
            }
            val apiBadge = apiBadgeSource.getBadge(id, isUser)
            if (apiBadge != null) {
                return apiBadge
            }
        } catch (e: Exception) {
            FileLog.e(e)
        }

        return null
    }

    @JvmOverloads
    fun hasBadge(obj: TLObject = UserConfig.getInstance(UserConfig.selectedAccount).currentUser): Boolean {
        return getBadge(obj) != null
    }

    fun isSupporter(id: Long): Boolean {
        val status = apiBadgeSource.getStatus(id)
        return status == ProfileStatus.SUPPORTER || status == ProfileStatus.DEVELOPER
    }

    fun getNumber(id: Long): Int? {
        return apiBadgeSource.getNumber(id)?.takeIf { it >= 0 && isSupporter(id) }
    }

    @JvmStatic
    fun getNumber(peer: TLObject?): Int? {
        return peerId(peer)?.let { getNumber(it) }
    }

    @JvmStatic
    fun formatNumber(number: Int): String {
        return "#$number"
    }

    fun isTrusted(id: Long): Boolean {
        return trustedPluginsCache.contains(id)
    }

    fun isExtera(id: Long): Boolean {
        return apiBadgeSource.isDeveloper(id)
    }

    fun isExtera(chat: TLRPC.Chat?): Boolean {
        return chat?.let { isExtera(it.id) } == true
    }

    fun getDefaultBadge(peer: TLObject?): BadgeDTO? {
        val id = peerId(peer) ?: return null
        return if (apiBadgeSource.isDeveloper(id)) DEV_BADGE else SUPPORTER_BADGE
    }

    fun getCustomBadgeId(peer: TLObject?): Long {
        val badge = getBadge(peer ?: return 0) ?: return 0
        val defaultBadge = getDefaultBadge(peer) ?: return 0
        return if (badge.documentId != defaultBadge.documentId) badge.documentId else 0
    }

    fun shouldUseSecondaryBadgeSlot(user: TLRPC.User?, badge: BadgeDTO? = getBadge(user)): Boolean {
        user ?: return false
        badge ?: return false
        return canChangeBadge(user) && (badge != getDefaultBadge(user) || isDeveloper(user))
    }

    fun getSecondaryBadge(user: TLRPC.User?): BadgeDTO? {
        val badge = getBadge(user) ?: return null
        return badge.takeIf { shouldUseSecondaryBadgeSlot(user, it) }
    }

    @JvmOverloads
    fun canChangeBadge(user: TLRPC.User = UserConfig.getInstance(UserConfig.selectedAccount).currentUser): Boolean {
        return user.let { apiBadgeSource.canChangeBadge(it.id) }
    }

    fun canChangeBadge(id: Long): Boolean {
        return apiBadgeSource.canChangeBadge(id)
    }

    fun updateBadge(badge: BadgeDTO?, callback: Consumer<String?>) {
        val query = if (badge != null) {
            val text = badge.text
            if (text.isNullOrEmpty()) {
                "badge ${badge.documentId}"
            } else {
                "badge ${badge.documentId} $text"
            }
        } else {
            callback.accept(null)
            return
        }

        ChatUtils.getInstance(UserConfig.selectedAccount).sendBotRequest(query, false) { response ->
            if ("ok" == response) {
                scope.launch {
                    apiBadgeSource.updateLocalBadge(UserConfig.getInstance(UserConfig.selectedAccount).clientUserId, badge)
                    callback.accept(response)
                }
            } else {
                callback.accept(response)
            }
        }
    }

    @JvmOverloads
    fun isDeveloper(user: TLRPC.User = UserConfig.getInstance(UserConfig.selectedAccount).currentUser): Boolean {
        return user.let { apiBadgeSource.isDeveloper(it.id) }
    }

    fun hasCustomBadge(peer: TLObject?): Boolean {
        val id = peerId(peer) ?: return false
        return !apiBadgeSource.isDeveloper(id) && (!isSupporter(id) || getCustomBadgeId(peer) != 0L)
    }

    private fun peerId(peer: TLObject?): Long? = when (peer) {
        is TLRPC.User -> peer.id
        is TLRPC.Chat -> peer.id
        else -> null
    }

    @JvmOverloads
    fun openProfileBadge(
        fragment: BaseFragment,
        user: TLRPC.User?,
        resourcesProvider: Theme.ResourcesProvider?,
        enterFrom: SupporterEnterFrom? = null
    ) {
        user ?: return
        val isDev = isDeveloper(user)
        if (!isDev && user.id == UserConfig.getInstance(fragment.currentAccount).clientUserId && isSupporter(user.id)) {
            SupporterBottomSheet.showAlert(fragment, null, resourcesProvider, enterFrom)
        } else if (isDev || hasCustomBadge(user)) {
            val badge = getBadge(user) ?: return
            val fallbackText = LocaleController.formatString(if (isDev) R.string.Developer else R.string.Supporter, user.first_name)
            showBadgeBulletin(fragment, badge, formatBadgeText(badge, UserObject.getUserName(user), fallbackText), resourcesProvider, fragment.currentAccount, null, if (isDev) {
                null
            } else {
                Runnable { SupporterBottomSheet.showAlert(fragment, user, resourcesProvider) }
            })
        } else {
            SupporterBottomSheet.showAlert(fragment, user, resourcesProvider, enterFrom)
        }
    }

    @JvmOverloads
    fun showBadgeBulletin(
        fragment: BaseFragment,
        chat: TLRPC.Chat?,
        resourcesProvider: Theme.ResourcesProvider? = fragment.getResourceProvider(),
        account: Int = fragment.getCurrentAccount(),
        containerLayout: FrameLayout? = null,
        showButton: Boolean? = null
    ) {
        chat ?: return
        val badge = getBadge(chat) ?: return
        showBadgeBulletin(fragment, badge, chat, resourcesProvider, account, containerLayout, showButton)
    }

    @JvmOverloads
    fun showBadgeBulletin(
        fragment: BaseFragment,
        badge: BadgeDTO,
        chat: TLRPC.Chat?,
        resourcesProvider: Theme.ResourcesProvider? = fragment.getResourceProvider(),
        account: Int = fragment.getCurrentAccount(),
        containerLayout: FrameLayout? = null,
        showButton: Boolean? = null
    ) {
        chat ?: return
        val isExtera = isExtera(chat)
        val isTrusted = isTrusted(chat.id)
        val fallbackText = if (isExtera) {
            LocaleController.formatString(R.string.OfficialChannel, chat.title)
        } else if (isTrusted) {
            LocaleController.getString(R.string.PluginSourceTrustedInfo)
        } else {
            LocaleController.formatString(R.string.Supporter, chat.title)
        }
        val text = formatBadgeText(badge, chat.title, fallbackText)
        val btn = showButton ?: !(isExtera || isTrusted)
        showBadgeBulletin(fragment, badge, text, resourcesProvider, account, containerLayout, if (btn) {
            Runnable { SupporterBottomSheet.showAlert(fragment, chat, resourcesProvider) }
        } else {
            null
        })
    }

    private fun showBadgeBulletin(
        fragment: BaseFragment,
        badge: BadgeDTO,
        text: CharSequence,
        resourcesProvider: Theme.ResourcesProvider?,
        account: Int,
        containerLayout: FrameLayout?,
        onButton: Runnable?
    ) {
        val bulletinFactory = if (containerLayout != null) {
            BulletinFactory.of(containerLayout, resourcesProvider)
        } else {
            BulletinFactory.of(fragment)
        }
        val bulletin = bulletinFactory.createEmojiBulletin(
            AnimatedEmojiDrawable.findDocument(account, badge.documentId),
            text,
            if (onButton != null) LocaleController.getString(R.string.FragmentUsernameOpen) else null,
            onButton
        )
        if (onButton == null) {
            bulletin.wrapContent()
        }
        bulletin.show()
    }

    @JvmStatic
    fun formatBadgeCaption(text: String, name: String?, fragment: BaseFragment? = null, onClick: Runnable? = null): CharSequence {
        return LocaleUtils.fullyFormatText(text.replace(NAME_PLACEHOLDER, name.orEmpty()), fragment, onClick)
    }

    private fun formatBadgeText(badge: BadgeDTO, name: String?, fallbackText: CharSequence): CharSequence {
        return badge.text?.let { formatBadgeCaption(it, name) } ?: fallbackText
    }
}