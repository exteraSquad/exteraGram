/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

@file:JvmName("ExteraServices")

package com.exteragram.messenger

import com.exteragram.messenger.adblock.backend.AdBlockManager
import com.exteragram.messenger.api.ApiController
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.plugins.PluginsConstants
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.utils.network.RemoteUtils
import org.telegram.messenger.ApplicationLoader
import java.util.concurrent.atomic.AtomicBoolean

private val servicesStarted = AtomicBoolean(false)

internal fun startServices() {
    if (!servicesStarted.compareAndSet(false, true)) return
    ApiController.init()
    RemoteUtils.init()
    PluginsController.getInstance().init(pluginsSafeMode) {
        PluginsController.getInstance().executeOnAppEvent(PluginsConstants.APP_START)
        if (!ApplicationLoader.mainInterfacePaused) {
            PluginsController.getInstance().executeOnAppEvent(PluginsConstants.APP_RESUME)
        }
    }
    AdBlockManager.preload()
    IconManager.prefetchCustomPacks()
}
