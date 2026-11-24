/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.utils

import android.content.Context
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import java.util.Collections

class MenuContextBuilder {
    fun build(): Map<String, Any> = Collections.emptyMap()

    fun withAccount(account: Int): MenuContextBuilder = this

    fun withContext(context: Context?): MenuContextBuilder = this

    fun withChat(chat: TLRPC.Chat?): MenuContextBuilder = this

    fun withChatFull(chatFull: TLRPC.ChatFull?): MenuContextBuilder = this

    fun withUser(user: TLRPC.User?): MenuContextBuilder = this

    fun withUserFull(userFull: TLRPC.UserFull?): MenuContextBuilder = this

    fun withBotInfo(botInfo: org.telegram.tgnet.tl.TL_bots.BotInfo?): MenuContextBuilder = this

    fun withEncryptedChat(encryptedChat: TLRPC.EncryptedChat?): MenuContextBuilder = this

    fun withMessage(message: MessageObject?): MenuContextBuilder = this

    fun withGroupedMessage(groupedMessages: MessageObject.GroupedMessages?): MenuContextBuilder = this

    fun withDialogId(dialogId: Long): MenuContextBuilder = this

    companion object {
        @JvmStatic
        fun create(): MenuContextBuilder = MenuContextBuilder()

        @JvmStatic
        fun from(fragment: BaseFragment?): MenuContextBuilder = MenuContextBuilder()
    }
}