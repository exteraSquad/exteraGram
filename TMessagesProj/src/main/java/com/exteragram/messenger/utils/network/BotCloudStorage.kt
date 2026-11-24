/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.network

import com.exteragram.messenger.getApiBotInfo
import com.exteragram.messenger.utils.chats.ChatUtils
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_bots

class BotCloudStorage private constructor(private val account: Int) {

    fun get(keys: Collection<String>, callback: (Map<String, String>?) -> Unit) {
        val params = JSONObject().put("keys", JSONArray(keys))
        invoke("getStorageValues", params) { result ->
            if (result == null) {
                callback(null)
                return@invoke
            }
            try {
                val json = JSONObject(result)
                val values = HashMap<String, String>()
                for (key in json.keys()) {
                    val value = json.optString(key)
                    if (value.isNotEmpty()) {
                        values[key] = value
                    }
                }
                callback(values)
            } catch (e: Exception) {
                FileLog.e(e)
                callback(null)
            }
        }
    }

    fun set(key: String, value: String, callback: ((Boolean) -> Unit)? = null) {
        invoke("saveStorageValue", JSONObject().put("key", key).put("value", value)) { result ->
            callback?.invoke(result != null)
        }
    }

    private fun invoke(method: String, params: JSONObject, callback: (String?) -> Unit) {
        val botInfo = getApiBotInfo()
        val bot = MessagesController.getInstance(account).getUser(botInfo.first)
        if (bot != null) {
            send(bot, method, params, callback)
        } else {
            ChatUtils.getInstance(account).resolveUser(botInfo.second, botInfo.first) { user ->
                if (user != null) {
                    send(user, method, params, callback)
                } else {
                    callback(null)
                }
            }
        }
    }

    private fun send(bot: TLRPC.User, method: String, params: JSONObject, callback: (String?) -> Unit) {
        val req = TL_bots.invokeWebViewCustomMethod()
        req.bot = MessagesController.getInstance(account).getInputUser(bot)
        req.custom_method = method
        req.params = TLRPC.TL_dataJSON()
        req.params.data = params.toString()
        ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
            AndroidUtilities.runOnUIThread {
                if (response is TLRPC.TL_dataJSON) {
                    callback(response.data)
                } else {
                    if (error != null) {
                        FileLog.e("BotCloudStorage $method: ${error.code} ${error.text}")
                    }
                    callback(null)
                }
            }
        }
    }

    companion object {
        const val MAX_VALUE_LENGTH = 4096
        private val instances = arrayOfNulls<BotCloudStorage>(UserConfig.MAX_ACCOUNT_COUNT)

        @JvmStatic
        fun getInstance(account: Int): BotCloudStorage =
            synchronized(instances) {
                instances[account] ?: BotCloudStorage(account).also { instances[account] = it }
            }
    }
}
