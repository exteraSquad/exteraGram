/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.hooks

import org.telegram.messenger.SendMessagesHelper
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC

interface PluginsHooks {
    fun executePreRequestHook(requestName: String, account: Int, request: TLObject?): TLObject?

    fun executePostRequestHook(requestName: String, account: Int, response: TLObject?, error: TLRPC.TL_error?): PostRequestResult

    fun executeUpdateHook(updateName: String, account: Int, update: TLRPC.Update?): TLRPC.Update?

    fun executeUpdatesHook(containerName: String, account: Int, updates: TLRPC.Updates?): TLRPC.Updates?

    fun executeSendMessageHook(account: Int, params: SendMessagesHelper.SendMessageParams?): SendMessagesHelper.SendMessageParams?

    class PostRequestResult(
        var response: TLObject?,
        var error: TLRPC.TL_error?,
    )
}