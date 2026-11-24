/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog.ui

import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.exteragram.messenger.PLACEHOLDERS
import com.exteragram.messenger.plugins.catalog.GitPluginRepository
import com.exteragram.messenger.plugins.catalog.PluginCatalogController
import com.exteragram.messenger.plugins.catalog.PluginRepositoryLinks
import com.exteragram.messenger.plugins.catalog.db.PluginRepositoryEntity
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.utils.ui.AddButtonDrawable
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.DialogsActivity

class PluginRepositoriesActivity : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {
    private var addDrawable: AddButtonDrawable? = null
    private var repositories: List<PluginRepositoryEntity> = emptyList()
    private var loaded = false
    private val syncRequested = HashSet<Long>()

    private val controller: PluginCatalogController
        get() = PluginCatalogController.getInstance(currentAccount)

    override fun getTitle(): String = getString(R.string.PluginCatalog)

    override fun needHideTitle(): Boolean = true

    override fun onFragmentCreate(): Boolean {
        notificationCenter.addObserver(this, NotificationCenter.pluginCatalogUpdated)
        controller.refreshFolderRepositories(false)
        reloadRepositories()
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        notificationCenter.removeObserver(this, NotificationCenter.pluginCatalogUpdated)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.pluginCatalogUpdated) {
            val repositoryId = args.getOrNull(0) as? Long ?: return
            if (repositoryId == PluginCatalogController.ALL_REPOSITORIES) {
                reloadRepositories()
            } else if (repositories.any { it.repositoryId == repositoryId }) {
                updateList()
            }
        }
    }

    private fun reloadRepositories() {
        controller.loadRepositories { result ->
            repositories = result
            loaded = true
            for (repository in result) {
                if (syncRequested.add(repository.repositoryId)) {
                    controller.syncRepository(repository.repositoryId,
                        force = false,
                        loadMetadata = false
                    )
                }
            }
            updateList()
        }
    }

    private fun updateList() {
        if (fragmentView != null) {
            listView.adapter.update(true)
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val folderRepositories = repositories.filter {
            it.source == PluginRepositoryEntity.SOURCE_FOLDER && (it.syncedAt == 0L || it.pluginsCount > 0)
        }.sortedByDescending { it.pluginsCount }
        val userRepositories = repositories
            .filter { it.source == PluginRepositoryEntity.SOURCE_USER }
            .sortedByDescending { it.pluginsCount }

        items.add(UItem.asTopView(getTitle(), getString(R.string.PluginCatalogInfo), PLACEHOLDERS, HEADER_EMOJI))
        items.add(UItem.asShadow())

        if (folderRepositories.isNotEmpty() || !loaded || controller.isResolvingFolder()) {
            items.add(UItem.asHeader(getString(R.string.PluginCatalogVerified)))
            if (folderRepositories.isEmpty()) {
                for (i in 0 until 3) {
                    items.add(UItem.asFlicker(FLICKER_ID_START + i, FlickerLoadingView.USERS_TYPE))
                }
            } else {
                folderRepositories.forEach { items.add(CatalogRepositoryCell.Factory.of(it, getSubtitle(it))) }
            }
            items.add(UItem.asShadow())
        }

        items.add(UItem.asHeader(getString(R.string.PluginCatalogYourRepositories)))
        userRepositories.forEach { items.add(CatalogRepositoryCell.Factory.of(it, getSubtitle(it))) }
        items.add(UItem.asButton(ADD_REPOSITORY_ID, getAddDrawable(), getString(R.string.PluginCatalogAddRepository)).accent().apply {
            pad = CatalogPluginCell.TEXT_START.toInt()
        })
        items.add(UItem.asShadow(AndroidUtilities.replaceTags(getString(R.string.PluginCatalogRepositoriesInfo))))
    }

    private fun getSubtitle(repository: PluginRepositoryEntity): CharSequence = when {
        repository.syncedAt != 0L -> LocaleController.formatPluralString("PluginCatalogPlugins", repository.pluginsCount)
        controller.isSyncing(repository.repositoryId) -> getString(R.string.PluginCatalogUpdating)
        else -> getString(R.string.PluginCatalogNotLoaded)
    }

    private fun getAddDrawable(): AddButtonDrawable {
        val drawable = addDrawable ?: AddButtonDrawable(context).also {
            it.translateX = AndroidUtilities.dp(4f).toFloat()
            addDrawable = it
        }
        drawable.updateColors(resourceProvider)
        return drawable
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id == ADD_REPOSITORY_ID) {
            showAddOptions(view)
            return
        }
        val repository = item.`object` as? PluginRepositoryEntity ?: return
        presentFragment(PluginCatalogActivity(repository.repositoryId))
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val repository = item.`object` as? PluginRepositoryEntity ?: return false
        ItemOptions.makeOptions(this, view).apply {
            val url = repository.url
            if (url == null) {
                add(R.drawable.msg_channel, getString(R.string.OpenChannel2)) {
                    presentFragment(ChatActivity.of(-repository.repositoryId))
                }
            } else {
                GitPluginRepository.getWebUrl(url, repository.homepage)?.let { webUrl ->
                    add(R.drawable.msg_openin, getString(R.string.PluginCatalogOpenRepository)) { Browser.openUrl(parentActivity, webUrl) }
                }
            }
            val username = messagesController.getChat(repository.repositoryId)?.let(ChatObject::getPublicUsername) ?: repository.username
            val link = PluginRepositoryLinks.build(repository, username)
            if (link != null) {
                add(R.drawable.msg_share, getString(R.string.ShareLink)) { shareRepositoryLink(link) }
                add(R.drawable.msg_copy, getString(R.string.CopyLink)) {
                    if (AndroidUtilities.addToClipboard(link)) {
                        BulletinFactory.of(this@PluginRepositoriesActivity).createCopyBulletin(getString(R.string.LinkCopied)).show()
                    }
                }
            }
            if (repository.source == PluginRepositoryEntity.SOURCE_USER) {
                add(R.drawable.msg_delete, getString(R.string.PluginCatalogRemoveRepository), true) {
                    removeRepository(repository)
                }
            }
            setScrimViewBackground(listView.getClipBackground(view))
            setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)
            setDismissOnMoveOutside(true)
            show()
        }
        return true
    }

    private fun shareRepositoryLink(link: String) {
        showShareLinkAlert(
            link,
            R.string.PluginRepositoryLinkToSavedMessages,
            R.string.PluginRepositoryLinkToUser,
            R.string.PluginRepositoryLinkToChats,
        )
    }

    private fun showAddOptions(view: View) {
        ItemOptions.makeOptions(this, view).apply {
            add(R.drawable.msg_channel, getString(R.string.ChooseChannel)) { openChannelPicker() }
            add(R.drawable.msg_link2, getString(R.string.PluginCatalogEnterLink)) {
                showLinkDialog(getString(R.string.PluginCatalogLinkHint)) { input ->
                    parseUsername(input)?.also(::resolveAndAdd)
                }
            }
            add(R.drawable.msg_language, getString(R.string.PluginCatalogGitRepository)) {
                showLinkDialog(getString(R.string.PluginCatalogGitLinkHint)) { input ->
                    GitPluginRepository.normalizeUrl(input)?.also(::addGitRepository)
                }
            }
            setScrimViewBackground(listView.getClipBackground(view))
            setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)
            show()
        }
    }

    private fun openChannelPicker() {
        val args = Bundle().apply {
            putBoolean("onlySelect", true)
            putBoolean("checkCanWrite", false)
            putBoolean("searchGlobalChannels", true)
            putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY)
        }
        val fragment = DialogsActivity(args)
        fragment.setDelegate { dialogsFragment, dids, _, _, _, _, _, _ ->
            val chat = dids.firstOrNull()?.let { messagesController.getChat(-it.dialogId) }
            dialogsFragment.finishFragment()
            if (chat != null) {
                addRepository(chat)
            }
            true
        }
        presentFragment(fragment)
    }

    private fun showLinkDialog(hint: String, onSubmit: (String?) -> String?) {
        val context = parentActivity ?: return
        val editText = EditTextBoldCursor(context).apply {
            lineYFix = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider))
            setHintText(hint)
            isFocusable = true
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = null
            setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourceProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourceProvider),
            )
            setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider))
            setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 0f, 24f, 12f))
        }
        val dialog = AlertDialog.Builder(context, resourceProvider).apply {
            setTitle(getString(R.string.PluginCatalogAddRepository))
            setView(container)
            setWidth(AndroidUtilities.dp(292f))
            setPositiveButton(getString(R.string.Add)) { dialogInterface, _ ->
                if (onSubmit(editText.text?.toString()) == null) {
                    AndroidUtilities.shakeView(editText)
                    return@setPositiveButton
                }
                dialogInterface.dismiss()
            }
            setNegativeButton(getString(R.string.Cancel)) { dialogInterface, _ -> dialogInterface.dismiss() }
        }.create().apply {
            setOnDismissListener { AndroidUtilities.hideKeyboard(editText) }
            setOnShowListener {
                editText.requestFocus()
                AndroidUtilities.showKeyboard(editText)
            }
            setDismissDialogByButtons(false)
        }
        showDialog(dialog)
    }

    private fun resolveAndAdd(username: String) {
        val context = parentActivity ?: return
        val progressDialog = AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER)
        progressDialog.showDelayed(300)
        messagesController.userNameResolver.resolve(username) { peerId ->
            progressDialog.dismiss()
            if (fragmentView == null) {
                return@resolve
            }
            val chat = if (peerId != null && peerId < 0) messagesController.getChat(-peerId) else null
            if (chat == null || !ChatObject.isChannel(chat)) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.PluginCatalogChannelNotFound)).show()
            } else {
                addRepository(chat)
            }
        }
    }

    private fun removeRepository(repository: PluginRepositoryEntity) {
        val chat = messagesController.getChat(repository.repositoryId)
        controller.removeRepository(repository.repositoryId)
        val title = chat?.title ?: repository.title ?: repository.username ?: ""
        val text = LocaleController.formatString(R.string.PluginCatalogRepositoryRemoved, title)
        val bulletinFactory = BulletinFactory.of(this)
        val url = repository.url
        val bulletin = if (url != null) {
            bulletinFactory.createSimpleBulletin(R.raw.ic_delete, text, getString(R.string.Undo)) {
                PluginRepositoryLinks.addGit(this, url) { added -> syncRequested.add(added.repositoryId) }
            }
        } else if (chat != null) {
            bulletinFactory.createSimpleBulletin(R.raw.ic_delete, text, getString(R.string.Undo)) {
                controller.addRepository(chat) { added ->
                    if (added) {
                        syncRequested.add(chat.id)
                        controller.syncRepository(chat.id, force = true, loadMetadata = false)
                    }
                }
            }
        } else {
            bulletinFactory.createSimpleBulletin(R.raw.ic_delete, text)
        }
        bulletin.show()
    }

    private fun addGitRepository(url: String) {
        PluginRepositoryLinks.addGit(this, url) { repository ->
            syncRequested.add(repository.repositoryId)
            BulletinFactory.of(this).createSimpleBulletin(
                R.raw.contact_check,
                LocaleController.formatString(R.string.PluginCatalogRepositoryAdded, repository.title ?: url),
            ).show()
        }
    }

    private fun addRepository(chat: TLRPC.Chat) {
        controller.addRepository(chat) { added ->
            if (fragmentView == null) {
                return@addRepository
            }
            if (added) {
                syncRequested.add(chat.id)
                controller.syncRepository(chat.id, force = true, loadMetadata = false)
                BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.contact_check,
                    LocaleController.formatString(R.string.PluginCatalogRepositoryAdded, chat.title),
                ).show()
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.PluginCatalogRepositoryExists)).show()
            }
        }
    }

    private fun parseUsername(input: String?): String? {
        var value = input?.trim() ?: return null
        value = value.removePrefix("https://").removePrefix("http://")
        for (host in listOf("t.me/", "telegram.me/", "telegram.dog/")) {
            if (value.startsWith(host, ignoreCase = true)) {
                value = value.substring(host.length)
                break
            }
        }
        value = value.removePrefix("@").substringBefore('/').substringBefore('?')
        return value.takeIf { USERNAME_PATTERN.matches(it) }
    }

    companion object {
        private const val ADD_REPOSITORY_ID = 1
        private const val HEADER_EMOJI = "\uD83D\uDED2"
        private const val FLICKER_ID_START = 100
        private val USERNAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]{3,31}$")
    }
}
