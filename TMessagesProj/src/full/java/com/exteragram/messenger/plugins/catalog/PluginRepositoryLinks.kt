/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog

import android.net.Uri
import com.exteragram.messenger.plugins.catalog.db.PluginRepositoryEntity
import com.exteragram.messenger.plugins.catalog.ui.PluginCatalogActivity
import com.exteragram.messenger.utils.IntentsController
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory

object PluginRepositoryLinks {
    fun build(username: String): String =
        "https://t.me/${IntentsController.PLUGIN_REPOSITORY_LINK}?${IntentsController.PLUGIN_REPOSITORY_QUERY_KEY}=$username"

    fun buildGit(url: String): String =
        "https://t.me/${IntentsController.PLUGIN_REPOSITORY_LINK}?${IntentsController.PLUGIN_REPOSITORY_GIT_QUERY_KEY}=${Uri.encode(url)}"

    fun build(repository: PluginRepositoryEntity, username: String?): String? =
        repository.url?.let(::buildGit) ?: username?.takeIf { it.isNotEmpty() }?.let(::build)

    fun openGit(fragment: BaseFragment, input: String) {
        val activity = fragment.parentActivity ?: return
        val url = GitPluginRepository.normalizeUrl(input)
        if (url == null) {
            BulletinFactory.of(fragment)
                .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.PluginCatalogGitLoadFailed))
                .show()
            return
        }
        val controller = PluginCatalogController.getInstance(fragment.currentAccount)
        controller.loadRepositories { repositories ->
            val existing = repositories.firstOrNull { it.url == url }
            if (existing != null) {
                fragment.presentFragment(PluginCatalogActivity(existing.repositoryId))
                return@loadRepositories
            }
            val dialog = AlertDialog.Builder(activity, fragment.resourceProvider)
                .setTitle(LocaleController.getString(R.string.PluginCatalogAddRepository))
                .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.PluginCatalogAddRepositoryConfirm, url.removePrefix("https://"))))
                .setPositiveButton(LocaleController.getString(R.string.Add)) { _, _ ->
                    addGit(fragment, url) { repository -> fragment.presentFragment(PluginCatalogActivity(repository.repositoryId)) }
                }
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create()
            fragment.showDialog(dialog)
        }
    }

    fun addGit(fragment: BaseFragment, url: String, onAdded: (PluginRepositoryEntity) -> Unit) {
        val activity = fragment.parentActivity ?: return
        val progressDialog = AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER)
        progressDialog.showDelayed(300)
        PluginCatalogController.getInstance(fragment.currentAccount).addGitRepository(url) { status, repository ->
            progressDialog.dismiss()
            if (fragment.fragmentView == null) {
                return@addGitRepository
            }
            if (repository != null) {
                onAdded(repository)
                return@addGitRepository
            }
            val error = when (status) {
                PluginCatalogController.ADD_EXISTS -> R.string.PluginCatalogRepositoryExists
                PluginCatalogController.ADD_UNSUPPORTED -> R.string.PluginCatalogGitUnsupported
                else -> R.string.PluginCatalogGitLoadFailed
            }
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(error)).show()
        }
    }

    fun open(fragment: BaseFragment, username: String) {
        val activity = fragment.parentActivity ?: return
        val progressDialog = AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER)
        progressDialog.showDelayed(300)
        val messagesController = fragment.messagesController
        messagesController.userNameResolver.resolve(username) { peerId ->
            progressDialog.dismiss()
            val chat = if (peerId != null && peerId < 0) messagesController.getChat(-peerId) else null
            if (chat == null || !ChatObject.isChannel(chat)) {
                BulletinFactory.of(fragment)
                    .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.PluginCatalogChannelNotFound))
                    .show()
                return@resolve
            }
            val controller = PluginCatalogController.getInstance(fragment.currentAccount)
            controller.loadRepositories { repositories ->
                if (repositories.any { it.repositoryId == chat.id }) {
                    fragment.presentFragment(PluginCatalogActivity(chat.id))
                } else {
                    showAddAlert(fragment, controller, chat)
                }
            }
        }
    }

    private fun showAddAlert(fragment: BaseFragment, controller: PluginCatalogController, chat: TLRPC.Chat) {
        val activity = fragment.parentActivity ?: return
        val dialog = AlertDialog.Builder(activity, fragment.resourceProvider)
            .setTitle(LocaleController.getString(R.string.PluginCatalogAddRepository))
            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.PluginCatalogAddRepositoryConfirm, chat.title)))
            .setPositiveButton(LocaleController.getString(R.string.Add)) { _, _ ->
                controller.addRepository(chat) { added ->
                    if (added) {
                        controller.syncRepository(chat.id, true, false)
                    }
                    fragment.presentFragment(PluginCatalogActivity(chat.id))
                }
            }
            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            .create()
        fragment.showDialog(dialog)
    }
}
