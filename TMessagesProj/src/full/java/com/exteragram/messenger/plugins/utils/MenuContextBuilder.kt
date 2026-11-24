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
import org.telegram.tgnet.tl.TL_bots
import org.telegram.ui.ActionBar.BaseFragment

class MenuContextBuilder private constructor() {
    private val contextData = HashMap<String, Any>()

    fun withAccount(account: Int): MenuContextBuilder {
        contextData["account"] = account
        return this
    }

    fun withContext(context: Context?): MenuContextBuilder {
        if (context != null) {
            contextData["context"] = context
        }
        return this
    }

    fun withEncryptedChat(encryptedChat: TLRPC.EncryptedChat?): MenuContextBuilder {
        if (encryptedChat != null) {
            contextData["encryptedChat"] = encryptedChat
        }
        return this
    }

    fun withChat(chat: TLRPC.Chat?): MenuContextBuilder {
        if (chat != null) {
            contextData["chat"] = chat
            contextData["chatId"] = chat.id
        }
        return this
    }

    fun withChatFull(chatFull: TLRPC.ChatFull?): MenuContextBuilder {
        if (chatFull != null) {
            contextData["chatFull"] = chatFull
        }
        return this
    }

    fun withUser(user: TLRPC.User?): MenuContextBuilder {
        if (user != null) {
            contextData["user"] = user
            contextData["userId"] = user.id
        }
        return this
    }

    fun withUserFull(userFull: TLRPC.UserFull?): MenuContextBuilder {
        if (userFull != null) {
            contextData["userFull"] = userFull
        }
        return this
    }

    fun withBotInfo(botInfo: TL_bots.BotInfo?): MenuContextBuilder {
        if (botInfo != null) {
            contextData["botInfo"] = botInfo
        }
        return this
    }

    fun withDialogId(dialogId: Long): MenuContextBuilder {
        contextData["dialog_id"] = dialogId
        return this
    }

    fun withMessage(message: MessageObject?): MenuContextBuilder {
        if (message != null) {
            contextData["message"] = message
        }
        return this
    }

    fun withGroupedMessage(groupedMessages: MessageObject.GroupedMessages?): MenuContextBuilder {
        if (groupedMessages != null) {
            contextData["groupedMessages"] = groupedMessages
        }
        return this
    }

    fun withCustom(key: String?, value: Any?): MenuContextBuilder {
        if (key != null && value != null) {
            contextData[key] = value
        }
        return this
    }

    fun build(): Map<String, Any> = contextData

    companion object {
        @JvmStatic
        fun create(): MenuContextBuilder = MenuContextBuilder()

        @JvmStatic
        fun from(fragment: BaseFragment?): MenuContextBuilder {
            if (fragment == null) {
                return create()
            }

            return create()
                .withCustom("fragment", fragment)
                .withAccount(fragment.currentAccount)
                .withContext(fragment.parentActivity)
        }
    }
}