package com.exteragram.extras

import android.content.ComponentName
import android.content.pm.PackageManager
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig

object AppIcons {
    enum class Icon(val mf: String) {
        DEFAULT("defaulticon"),
        MATERIALYOU("materialyouicon"),
        YANDEXALISA("yandexalisaicon"),
        THE8055U("the8055uicon"),
        ITSV1EDS("itsv1edsicon"),
        ASSCATCHEM("asscatchemicon"),
        GHOULGHOULCHAN("ghoulghoulchanicon"),
    }

    fun setIcon(variant: Int) {
        setIcon(Icon.values()[variant])
    }

    private fun setIcon(icon: Icon) {
        Icon.values().forEach {
            if (it == icon) {
                enableComponent(it.mf)
            } else {
                disableComponent(it.mf)
            }
        }
    }

    private fun enableComponent(name: String) {
        ApplicationLoader.applicationContext.packageManager.setComponentEnabledSetting(
            ComponentName(BuildConfig.APPLICATION_ID, "org.telegram.messenger.$name"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
    }

    private fun disableComponent(name: String) {
        ApplicationLoader.applicationContext.packageManager.setComponentEnabledSetting(
            ComponentName(BuildConfig.APPLICATION_ID, "org.telegram.messenger.$name"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
        )
    }
}