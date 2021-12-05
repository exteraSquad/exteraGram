package com.exterasquad.messenger.preferences

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LaunchActivity
import com.exterasquad.messenger.ExteraPreferencesNav
import ua.itaysonlab.tgkit.ktx.category
import ua.itaysonlab.tgkit.ktx.textDetail
import ua.itaysonlab.tgkit.ktx.textIcon
import ua.itaysonlab.tgkit.ktx.tgKitScreen
import com.exterasquad.extras.ExteraExtras
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow

import android.os.Build

import android.app.assist.AssistContent
import org.telegram.messenger.R
import java.lang.String


class MainPreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("exteraPreferences", R.string.exteraPreferences)) {
        category(LocaleController.getString("exteraCategories", R.string.exteraCategories)) {
            textIcon {
                title = LocaleController.getString("exteraAppearance", R.string.exteraAppearance)
                icon = R.drawable.msg_theme
                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(ExteraPreferencesNav.createAppearance())
                }
            }
            textIcon {
                title = LocaleController.getString("exteraChats", R.string.exteraChats)
                icon = R.drawable.menu_chats
                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(ExteraPreferencesNav.createChats())
                }
            }
            textIcon {
                title = LocaleController.getString("exteraSecurity", R.string.exteraSecurity)
                icon = R.drawable.menu_secret
                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(ExteraPreferencesNav.createSecurity())
                }
            }

            category(LocaleController.getString("AboutExtera", R.string.AboutExtera)) {
                textDetail {
                    title = "exteraGram | v" + ExteraExtras.exteraVersion + " (" + ExteraExtras.exteraCodename + ")"
                    detail = LocaleController.getString("AboutExteraDesc", R.string.AboutExteraDesc)
                }

                textIcon {
                    title = LocaleController.getString("exteraWebsite", R.string.exteraWebsite)
                    listener = TGKitTextIconRow.TGTIListener {
                        goToWebsite(it)
                    }
                }

                textIcon {
                    title = LocaleController.getString("exteraGithub", R.string.exteraGithub)
                    listener = TGKitTextIconRow.TGTIListener {
                        goToGithub(it)
                    }
                }

                textIcon {
                    title = LocaleController.getString("exteraChannel", R.string.exteraChannel)
                    listener = TGKitTextIconRow.TGTIListener {
                        goToChannel(it)
                    }
                }
                textIcon {
                    title = LocaleController.getString("exteraChat", R.string.exteraChat)
                    value = LocaleController.getString("languageRussian", R.string.languageRussian)
                    listener = TGKitTextIconRow.TGTIListener {
                        goToRUChat(it)
                    }
                }
                textIcon {
                    title = LocaleController.getString("exteraChat", R.string.exteraChat)
                    value = LocaleController.getString("languageEnglish", R.string.languageEnglish)
                    listener = TGKitTextIconRow.TGTIListener {
                        goToENChat(it)
                    }
                }
            }
        }
    }

    companion object {
        private fun goToWebsite(bf: BaseFragment) {
            val openURL = Intent(android.content.Intent.ACTION_VIEW)
            openURL.data = Uri.parse("https://exterasquad.github.io/")
            bf.parentActivity.startActivity(openURL)
        }

        private fun goToGithub(bf: BaseFragment) {
            val openURL = Intent(android.content.Intent.ACTION_VIEW)
            openURL.data = Uri.parse("https://github.com/exteraSquad/exteraGram/")
            bf.parentActivity.startActivity(openURL)
        }

        private fun goToChannel(bf: BaseFragment) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/exteraGram"))
            val componentName = ComponentName(bf.parentActivity.packageName, LaunchActivity::class.java.name)
            intent.component = componentName
            intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, bf.parentActivity.packageName)
            bf.parentActivity.startActivity(intent)
        }

        private fun goToRUChat(bf: BaseFragment) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/exteraChat"))
            val componentName = ComponentName(bf.parentActivity.packageName, LaunchActivity::class.java.name)
            intent.component = componentName
            intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, bf.parentActivity.packageName)
            bf.parentActivity.startActivity(intent)
        }

        private fun goToENChat(bf: BaseFragment) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/en_exteraChat"))
            val componentName = ComponentName(bf.parentActivity.packageName, LaunchActivity::class.java.name)
            intent.component = componentName
            intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, bf.parentActivity.packageName)
            bf.parentActivity.startActivity(intent)
        }

        fun onProvideAssistContent(outContent: AssistContent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                outContent.webUri = Uri.parse(
                    String.format(
                        "https://t.me/exteraGram"
                    )
                )
            }
        }

    }
}