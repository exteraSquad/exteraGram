/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.exteragram.messenger.export.ExportController
import com.exteragram.messenger.export.ExportSettings
import com.exteragram.messenger.export.ExportState
import com.exteragram.messenger.export.output.ExportFiles
import com.exteragram.messenger.exportFolder
import com.exteragram.messenger.exportFormat
import com.exteragram.messenger.exportFullChats
import com.exteragram.messenger.exportMediaTypes
import com.exteragram.messenger.exportSizeLimit
import com.exteragram.messenger.exportTypes
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.SwitchGroup
import com.exteragram.messenger.utils.ui.UIUtil
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.formatString
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.text.HtmlCompat
import org.telegram.messenger.FileLog
import org.simplifiles.SimpliFiles
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.BasePermissionsActivity
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Stories.recorder.ButtonWithCounterView
import java.io.File
import java.util.Calendar
import java.util.function.BooleanSupplier

class ExportActivity @JvmOverloads constructor(
    private val peer: TLRPC.InputPeer? = null,
    private val topicId: Int = 0,
    private val topicTitle: String? = null,
) : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    private enum class ExportItem {
        ACCOUNT,
        CHATS,
        MEDIA,
        OTHER,
        INFO,
        USERPICS,
        STORIES,
        PROFILE_MUSIC,
        CONTACTS,
        PERSONAL_CHATS,
        BOT_CHATS,
        PRIVATE_GROUPS,
        PRIVATE_CHANNELS,
        PUBLIC_GROUPS,
        PUBLIC_CHANNELS,
        ONLY_MY_MESSAGES,
        DATE_FROM,
        DATE_TILL,
        PHOTOS,
        VIDEOS,
        VOICE_MESSAGES,
        VIDEO_MESSAGES,
        STICKERS,
        GIFS,
        FILES,
        SIZE_LIMIT,
        SESSIONS,
        OTHER_DATA,
        FORMAT,
        LOCATION;

        val id: Int = ordinal + 1
    }

    private val settings = createSettings()

    private val accountGroup = SwitchGroup(this, ExportItem.ACCOUNT, R.string.ExportSectionAccount, searchable = false).apply {
        onChanged { exportTypes = settings.types }
        addType(ExportItem.INFO, R.string.ExportOptionInfo, ExportSettings.PersonalInfo)
        addType(ExportItem.USERPICS, R.string.ExportOptionUserpics, ExportSettings.Userpics)
        addType(ExportItem.STORIES, R.string.ExportOptionStories, ExportSettings.Stories)
        addType(ExportItem.PROFILE_MUSIC, R.string.ExportOptionProfileMusic, ExportSettings.ProfileMusic)
        addType(ExportItem.CONTACTS, R.string.ExportOptionContacts, ExportSettings.Contacts)
    }

    private val chatsGroup = SwitchGroup(this, ExportItem.CHATS, R.string.ExportSectionChats, searchable = false).apply {
        onChanged { exportTypes = settings.types }
        addType(ExportItem.PERSONAL_CHATS, R.string.ExportOptionPersonalChats, ExportSettings.PersonalChats)
        addType(ExportItem.BOT_CHATS, R.string.ExportOptionBotChats, ExportSettings.BotChats)
        addType(ExportItem.PRIVATE_GROUPS, R.string.ExportOptionPrivateGroups, ExportSettings.PrivateGroups)
        addType(ExportItem.PRIVATE_CHANNELS, R.string.ExportOptionPrivateChannels, ExportSettings.PrivateChannels)
        addType(ExportItem.PUBLIC_GROUPS, R.string.ExportOptionPublicGroups, ExportSettings.PublicGroups)
        addType(ExportItem.PUBLIC_CHANNELS, R.string.ExportOptionPublicChannels, ExportSettings.PublicChannels)
    }

    private val mediaGroup = SwitchGroup(this, ExportItem.MEDIA, R.string.ExportSectionMedia, searchable = false).apply {
        onChanged { exportMediaTypes = settings.media.types }
        addMedia(ExportItem.PHOTOS, R.string.ExportOptionPhotos, ExportSettings.MediaSettings.Photo)
        addMedia(ExportItem.VIDEOS, R.string.ExportOptionVideos, ExportSettings.MediaSettings.Video)
        addMedia(ExportItem.VOICE_MESSAGES, R.string.ExportOptionVoiceMessages, ExportSettings.MediaSettings.VoiceMessage)
        addMedia(ExportItem.VIDEO_MESSAGES, R.string.ExportOptionVideoMessages, ExportSettings.MediaSettings.VideoMessage)
        addMedia(ExportItem.STICKERS, R.string.ExportOptionStickers, ExportSettings.MediaSettings.Sticker)
        addMedia(ExportItem.GIFS, R.string.ExportOptionGifs, ExportSettings.MediaSettings.GIF)
        addMedia(ExportItem.FILES, R.string.ExportOptionFiles, ExportSettings.MediaSettings.File)
    }

    private val otherGroup = SwitchGroup(this, ExportItem.OTHER, R.string.ExportSectionOther, searchable = false).apply {
        onChanged { exportTypes = settings.types }
        addType(ExportItem.SESSIONS, R.string.ExportOptionSessions, ExportSettings.Sessions)
        addType(ExportItem.OTHER_DATA, R.string.ExportOptionOther, ExportSettings.OtherData)
    }

    private var observers: NotificationCenter.ObserversGroup? = null
    private var button: ButtonWithCounterView? = null
    private var buttonContainer: FrameLayout? = null
    private var pendingStart = false
    private var wasRunning = false
    private var freeSpace = -1L
    private var exports: List<ExportEntry> = emptyList()

    private val controller: ExportController
        get() = ExportController.getInstance(currentAccount)

    private fun createSettings(): ExportSettings {
        val result = if (peer != null) {
            ExportSettings.forSinglePeer(peer, topicId, topicTitle)
        } else {
            ExportSettings().apply {
                types = exportTypes
                fullChats = exportFullChats
            }
        }
        result.media.types = exportMediaTypes
        result.media.sizeLimit = sizeLimitByIndex(sizeLimitIndex(exportSizeLimit))
        result.format = exportFormat
        result.outputFolder = exportFolder
        return result
    }

    override fun getTitle(): String = getString(
        when {
            topicId != 0 -> R.string.ExportTopicHistory
            peer != null -> R.string.ExportChatHistory
            else -> R.string.ExportData
        }
    )

    override fun needHideTitle(): Boolean = true

    override fun createView(context: Context): View {
        val view = super.createView(context)
        val button = ButtonWithCounterView(context, true, resourceProvider).apply {
            setRound()
            setOnClickListener { onButtonClick() }
        }
        this.button = button
        buttonContainer = FrameLayout(context).apply {
            background = UIUtil.createBottomFade(getThemedColor(Theme.key_windowBackgroundGray))
            addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 16f, 12f, 16f, 12f))
        }
        (view as FrameLayout).addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM))
        listView.setPadding(0, listView.paddingTop, 0, dp(BUTTON_AREA_DP))
        updateButton(false)
        return view
    }

    override fun onInsets(left: Int, top: Int, right: Int, bottom: Int) {
        super.onInsets(left, top, right, bottom)
        buttonContainer?.setPadding(0, 0, 0, bottom)
        listView.setPadding(0, listView.paddingTop, 0, bottom + dp(BUTTON_AREA_DP))
    }

    override fun onFragmentCreate(): Boolean {
        observers = notificationCenter.createObserversGroup(this).add(NotificationCenter.exportStateChanged)
        wasRunning = controller.isRunning
        if (cachedFolder == ExportFiles.baseFolder(settings.outputFolder).absolutePath) {
            exports = cachedExports
        }
        loadStorageInfo()
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        observers?.removeAllObservers()
        observers = null
        button = null
        buttonContainer = null
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.exportStateChanged) {
            val running = controller.isRunning
            if (wasRunning != running) {
                wasRunning = running
                if (!running) {
                    loadStorageInfo()
                }
                if (fragmentView != null) {
                    listView.adapter?.update(true)
                }
            }
            updateButton(true)
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val delayUntil = controller.delayUntil
        if (delayUntil > 0) {
            items.add(UItem.asTopView(getTitle(), formatString(R.string.ExportDelayUntil, ExportUiHelper.delayDate(delayUntil)), "RestrictedEmoji", "\u23F3"))
        } else {
            items.add(UItem.asTopView(getTitle(), getString(if (peer != null) R.string.ExportIntroChat else R.string.ExportIntro), "RestrictedEmoji", "\uD83D\uDCC1"))
        }
        items.add(UItem.asShadow())

        if (peer != null) {
            items.add(UItem.asHeader(getString(R.string.ExportSectionPeriod)))
            items.add(uButton(ExportItem.DATE_FROM, getString(R.string.ExportDateFrom), value = dateText(settings.singlePeerFrom, true), searchable = false))
            items.add(uButton(ExportItem.DATE_TILL, getString(R.string.ExportDateTill), value = dateText(settings.singlePeerTill, false), searchable = false))
            items.add(UItem.asShadow())
        }

        val showMedia = showMedia()
        items.add(UItem.asHeader(getString(R.string.ExportSectionData)))
        if (peer == null) {
            accountGroup.fill(items)
            chatsGroup.fill(items)
            if (chatsGroup.isExpanded && settings.has(ExportSettings.GroupsChannelsMask)) {
                items.add(UItem.asRoundCheckbox(ExportItem.ONLY_MY_MESSAGES.id, getString(R.string.ExportOptionOnlyMy)).apply {
                    setChecked(onlyMyMessages()).pad()
                })
            }
        }
        if (showMedia) {
            mediaGroup.fill(items)
        }
        if (peer == null) {
            otherGroup.fill(items)
        }
        items.add(UItem.asShadow(if (peer == null) getString(R.string.ExportDataInfo) else null))

        if (showMedia) {
            items.add(UItem.asHeader(getString(R.string.ExportSizeLimit)))
            items.add(UItem.asIntSlideView(1, 0, sizeLimitIndex(settings.media.sizeLimit), SIZE_STEPS - 1, { formatSizeLimit(it) }) { index ->
                settings.media.sizeLimit = sizeLimitByIndex(index)
                exportSizeLimit = settings.media.sizeLimit
            }.apply { id = ExportItem.SIZE_LIMIT.id })
            items.add(UItem.asShadow(getString(R.string.ExportSizeLimitInfo)))
        }

        items.add(UItem.asHeader(getString(R.string.ExportSectionSaving)))
        items.add(uButton(ExportItem.FORMAT, getString(R.string.ExportFormat), value = formats()[settings.format.ordinal], searchable = false))
        items.add(uButton(ExportItem.LOCATION, getString(R.string.ExportFolder), subtext = folderSubtext(), searchable = false))
        items.add(UItem.asShadow(getString(R.string.ExportInfo)))

        if (exports.isNotEmpty()) {
            items.add(UItem.asHeader(getString(R.string.ExportListTitle)))
            val context = context
            if (context != null) {
                exports.forEachIndexed { index, export ->
                    items.add(ExportRowCell.Factory.of(EXPORT_ROW_BASE + index, ExportRowCell.Row(
                        export.title ?: getString(if (export.chat) R.string.ExportChatHistory else R.string.ExportData),
                        LocaleController.formatDateTime(export.time / 1000, true),
                        if (export.bytes >= 0) AndroidUtilities.formatFileSize(export.bytes) else "",
                        export.chat,
                        export.dialogId,
                    )))
                }
            }
            items.add(UItem.asShadow())
        }

        updateButton(true)
    }

    private fun showMedia(): Boolean = peer != null || settings.has(ExportSettings.AnyChatsMask or ExportSettings.ProfileMusic)

    private fun folderSubtext(): CharSequence {
        if (freeSpace in 0 until MIN_FREE_SPACE) {
            val text = SpannableString(formatString(R.string.ExportFolderLowSpace, AndroidUtilities.formatFileSize(freeSpace)))
            text.setSpan(ForegroundColorSpan(getThemedColor(Theme.key_text_RedRegular)), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return text
        }
        return ExportUiHelper.displayPath(ExportFiles.baseFolder(settings.outputFolder).absolutePath)
    }

    private fun loadStorageInfo() {
        val folder = ExportFiles.baseFolder(settings.outputFolder)
        Utilities.globalQueue.postRunnable {
            var existing: File? = folder
            while (existing != null && !existing.exists()) {
                existing = existing.parentFile
            }
            val free = existing?.usableSpace ?: -1L
            val sizes = ApplicationLoader.applicationContext.getSharedPreferences(SIZE_CACHE, Context.MODE_PRIVATE)
            val peers = ApplicationLoader.applicationContext.getSharedPreferences(ExportController.PEERS_PREFERENCES, Context.MODE_PRIVATE)
            val found = folder.listFiles { file -> file.isDirectory && EXPORT_PREFIXES.any { file.name.startsWith(it) } }
                ?.map { directory ->
                    val time = directory.lastModified()
                    val cached = sizes.getString(directory.absolutePath, null)?.split(':')
                    ExportEntry(
                        directory.absolutePath,
                        time,
                        cached?.takeIf { it.size == 2 && it[0].toLongOrNull() == time }?.get(1)?.toLongOrNull() ?: -1L,
                        directory.name.startsWith(EXPORT_PREFIXES[0]),
                        readExportTitle(directory),
                        peers.getLong(directory.absolutePath, 0L),
                    )
                }
                ?.sortedByDescending { it.time }
                ?: emptyList()
            publishStorageInfo(folder, free, found)
            if (found.any { it.bytes < 0 }) {
                val sized = found.map { export ->
                    if (export.bytes >= 0) export else export.copy(bytes = SimpliFiles.directory(File(export.path)).walkFiles().sumOf { it.size })
                }
                sizes.edit().apply {
                    sized.forEach { putString(it.path, "${it.time}:${it.bytes}") }
                }.apply()
                publishStorageInfo(folder, free, sized)
            }
        }
    }

    private fun publishStorageInfo(folder: File, free: Long, found: List<ExportEntry>) {
        AndroidUtilities.runOnUIThread {
            cachedFolder = folder.absolutePath
            cachedExports = found
            if (observers == null || ExportFiles.baseFolder(settings.outputFolder) != folder) {
                return@runOnUIThread
            }
            val changed = freeSpace != free || exports != found
            freeSpace = free
            exports = found
            if (changed && fragmentView != null) {
                listView.adapter?.update(true)
            }
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        exports.getOrNull(item.id - EXPORT_ROW_BASE)?.let { export ->
            openExport(export)
            return
        }
        val clicked = ExportItem.entries.getOrNull(item.id - 1) ?: return
        when (clicked) {
            ExportItem.ACCOUNT, ExportItem.INFO, ExportItem.USERPICS, ExportItem.STORIES,
            ExportItem.PROFILE_MUSIC, ExportItem.CONTACTS -> accountGroup.onClick(item)
            ExportItem.CHATS, ExportItem.PERSONAL_CHATS, ExportItem.BOT_CHATS, ExportItem.PRIVATE_GROUPS,
            ExportItem.PRIVATE_CHANNELS, ExportItem.PUBLIC_GROUPS, ExportItem.PUBLIC_CHANNELS -> chatsGroup.onClick(item)
            ExportItem.MEDIA, ExportItem.PHOTOS, ExportItem.VIDEOS, ExportItem.VOICE_MESSAGES, ExportItem.VIDEO_MESSAGES,
            ExportItem.STICKERS, ExportItem.GIFS, ExportItem.FILES -> mediaGroup.onClick(item)
            ExportItem.OTHER, ExportItem.SESSIONS, ExportItem.OTHER_DATA -> otherGroup.onClick(item)
            ExportItem.ONLY_MY_MESSAGES -> toggleBooleanSettingAndRefresh(item) { checked ->
                settings.fullChats = if (checked) ExportSettings.DefaultFullChats else ExportSettings.AnyChatsMask
                exportFullChats = settings.fullChats
            }
            ExportItem.DATE_FROM -> pickDate(true)
            ExportItem.DATE_TILL -> pickDate(false)
            ExportItem.FORMAT -> showListDialog(item, formats(), getString(R.string.ExportFormat), settings.format.ordinal) { i ->
                settings.format = ExportSettings.Format.entries[i]
                exportFormat = settings.format
            }
            ExportItem.LOCATION -> chooseLocation()
            else -> {}
        }
    }

    private fun SwitchGroup.addType(item: ExportItem, textRes: Int, type: Int) = add(
        item, textRes,
        BooleanSupplier { settings.has(type) },
        SwitchGroup.Setter { checked -> settings.types = if (checked) settings.types or type else settings.types and type.inv() },
    )

    private fun SwitchGroup.addMedia(item: ExportItem, textRes: Int, type: Int) = add(
        item, textRes,
        BooleanSupplier { (settings.media.types and type) != 0 },
        SwitchGroup.Setter { checked -> settings.media.types = if (checked) settings.media.types or type else settings.media.types and type.inv() },
    )

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        exports.getOrNull(item.id - EXPORT_ROW_BASE)?.let { export ->
            showExportOptions(export, view)
            return true
        }
        return super.onLongClick(item, view, position, x, y)
    }

    private fun openExport(export: ExportEntry) {
        val activity = parentActivity ?: return
        if (!ExportBrowser.canOpen(export.path) || !ExportBrowser.open(activity, export.path)) {
            showExportFolder(export)
        }
    }

    private fun showExportOptions(export: ExportEntry, view: View) {
        ItemOptions.makeOptions(this, view).apply {
            if (ExportBrowser.canOpen(export.path)) {
                add(R.drawable.msg_openin, getString(R.string.Open)) { openExport(export) }
            }
            add(R.drawable.msg_folders, getString(R.string.ExportShowInFolder)) { showExportFolder(export) }
            add(R.drawable.msg_delete, getString(R.string.Delete), true) { confirmDeleteExport(export) }
            setScrimViewBackground(listView.getClipBackground(view))
            setDismissOnMoveOutside(true)
            show()
        }
    }

    private fun readExportTitle(directory: File): String? {
        if (!directory.name.startsWith(EXPORT_PREFIXES[0])) {
            return null
        }
        val page = File(directory, "messages.html")
        if (!page.isFile) {
            return null
        }
        return try {
            val head = page.inputStream().use { input ->
                val buffer = ByteArray(TITLE_SCAN_BYTES)
                val read = input.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
            }
            TITLE_PATTERN.find(head)?.groupValues?.get(1)
                ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
    }

    private fun showExportFolder(export: ExportEntry) {
        val activity = parentActivity ?: return
        if (!ExportUiHelper.openFolder(activity, export.path)) {
            AndroidUtilities.addToClipboard(export.path)
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.ExportPathCopied)).show()
        }
    }

    private fun confirmDeleteExport(export: ExportEntry) {
        val context = parentActivity ?: return
        val dialog = AlertDialog.Builder(context, resourceProvider)
            .setTitle(getString(R.string.ExportDeleteTitle))
            .setMessage(formatString(R.string.ExportDeleteConfirm, ExportUiHelper.displayPath(export.path)))
            .setPositiveButton(getString(R.string.Delete)) { _, _ -> deleteExport(export) }
            .setNegativeButton(getString(R.string.Cancel), null)
            .create()
        showDialog(dialog)
        dialog.redPositive()
    }

    private fun deleteExport(export: ExportEntry) {
        val directory = File(export.path)
        if (controller.isRunning || EXPORT_PREFIXES.none { directory.name.startsWith(it) }) {
            return
        }
        val progress = AlertDialog(parentActivity ?: return, AlertDialog.ALERT_TYPE_SPINNER, resourceProvider).apply {
            setCanCancel(false)
            showDelayed(DELETE_SPINNER_DELAY)
        }
        Utilities.globalQueue.postRunnable {
            val deleted = try {
                SimpliFiles.directory(directory).deleteRecursively()
                !directory.exists()
            } catch (e: Exception) {
                FileLog.e(e)
                false
            }
            ApplicationLoader.applicationContext.getSharedPreferences(SIZE_CACHE, Context.MODE_PRIVATE).edit().remove(export.path).apply()
            ApplicationLoader.applicationContext.getSharedPreferences(ExportController.PEERS_PREFERENCES, Context.MODE_PRIVATE).edit().remove(export.path).apply()
            AndroidUtilities.runOnUIThread {
                progress.dismiss()
                if (observers == null) {
                    return@runOnUIThread
                }
                if (deleted) {
                    exports = exports - export
                    cachedExports = exports
                    listView.adapter?.update(true)
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.ExportDeleted)).show()
                } else {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.ExportDeleteFailed)).show()
                }
                loadStorageInfo()
            }
        }
    }

    private fun onlyMyMessages(): Boolean = (settings.fullChats and ExportSettings.GroupsChannelsMask) == 0

    private fun canStart(): Boolean = peer != null || settings.types != 0

    private fun updateButton(animated: Boolean) {
        val button = button ?: return
        when (val state = controller.state) {
            is ExportState.Processing -> {
                button.setText(getString(R.string.ExportShowProgress), animated)
                button.setSubText(ExportUiHelper.percent(state).toString() + "%", animated)
                button.isEnabled = true
            }
            is ExportState.Finished -> {
                button.setText(getString(R.string.ExportFinished), animated)
                button.setSubText(null, animated)
                button.isEnabled = true
            }
            is ExportState.ApiError, is ExportState.IoError -> {
                button.setText(getString(R.string.ExportFailed), animated)
                button.setSubText(null, animated)
                button.isEnabled = true
            }
            else -> {
                val delayUntil = controller.delayUntil
                button.setText(getString(R.string.ExportStart), animated)
                button.setSubText(
                    if (canStart() && delayUntil > 0) formatString(R.string.ExportAvailableFrom, ExportUiHelper.delayDate(delayUntil)) else null,
                    animated,
                )
                button.isEnabled = canStart()
            }
        }
    }

    private fun onButtonClick() {
        if (ExportUiHelper.hasVisibleState(currentAccount)) {
            ExportProgressSheet.show(this, currentAccount)
        } else if (canStart()) {
            startExport()
        }
    }

    private fun dateText(date: Int, from: Boolean): String = when {
        date <= 0 -> getString(if (from) R.string.ExportDateBeginning else R.string.ExportDatePresent)
        else -> LocaleController.getInstance().chatFullDate.format((if (from) date else date - DAY) * 1000L)
    }

    private fun pickDate(from: Boolean) {
        val current = if (from) settings.singlePeerFrom else settings.singlePeerTill - DAY
        val calendar = Calendar.getInstance()
        if (current > 0) {
            calendar.timeInMillis = current * 1000L
        }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val builder = AlertsCreator.createDatePickerDialog(
            context, FIRST_YEAR - currentYear, 0, 0,
            calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH), calendar.get(Calendar.YEAR),
            getString(if (from) R.string.ExportDateFrom else R.string.ExportDateTill), false
        ) { year, month, day ->
            val selected = Calendar.getInstance().apply {
                clear()
                set(year, month, day)
            }
            val start = (selected.timeInMillis / 1000).toInt()
            if (from) {
                settings.singlePeerFrom = start
                if (settings.singlePeerTill in 1..start) {
                    settings.singlePeerTill = start + DAY
                }
            } else {
                settings.singlePeerTill = start + DAY
                if (settings.singlePeerFrom > start) {
                    settings.singlePeerFrom = start
                }
            }
            listView.adapter?.update(true)
        } ?: return
        builder.setNeutralButton(getString(R.string.ExportDateReset)) { _, _ ->
            if (from) settings.singlePeerFrom = 0 else settings.singlePeerTill = 0
            listView.adapter?.update(true)
        }
        showDialog(builder.create())
    }

    private fun chooseLocation() {
        if (settings.outputFolder.isEmpty()) {
            openFolderPicker()
            return
        }
        val dialog = AlertDialog.Builder(context, resourceProvider)
            .setTitle(getString(R.string.ExportFolder))
            .setItems(arrayOf(getString(R.string.ExportFolderChoose), getString(R.string.ExportFolderDefault))) { _, which ->
                if (which == 0) {
                    openFolderPicker()
                } else {
                    setOutputFolder("")
                }
            }
            .create()
        showDialog(dialog)
    }

    private fun openFolderPicker() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), FOLDER_PICKER)
        } catch (e: Exception) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ExportFolderUnavailable)).show()
        }
    }

    override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResultFragment(requestCode, resultCode, data)
        if (requestCode != FOLDER_PICKER || resultCode != Activity.RESULT_OK) {
            return
        }
        val path = data?.data?.let { ExportUiHelper.resolveTreePath(it) }
        if (path == null) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ExportFolderUnavailable)).show()
            return
        }
        Utilities.globalQueue.postRunnable {
            val writable = ExportFiles.isWritable(File(path))
            AndroidUtilities.runOnUIThread {
                if (fragmentView == null) {
                    return@runOnUIThread
                }
                if (writable) {
                    setOutputFolder(path)
                } else {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.ExportFolderUnavailable)).show()
                }
            }
        }
    }

    private fun setOutputFolder(path: String) {
        settings.outputFolder = path
        exportFolder = path
        freeSpace = -1
        listView.adapter?.update(true)
        loadStorageInfo()
    }

    private fun formats(): Array<CharSequence> = arrayOf(
        getString(R.string.ExportFormatHtml),
        getString(R.string.ExportFormatJson),
        getString(R.string.ExportFormatBoth),
    )

    private fun formatSizeLimit(index: Int): String = AndroidUtilities.formatFileSize(sizeLimitByIndex(index), true, true)

    private fun needsStoragePermission(): Boolean {
        val activity = parentActivity ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    }

    private fun startExport() {
        if (needsStoragePermission()) {
            pendingStart = true
            parentActivity?.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE)
            return
        }
        launchExport()
    }

    private fun launchExport() {
        if (freeSpace in 0 until MIN_FREE_SPACE) {
            BulletinFactory.of(this).createErrorBulletin(formatString(R.string.ExportNoSpace, AndroidUtilities.formatFileSize(freeSpace))).show()
            return
        }
        controller.start(settings)
        val previous = parentLayout?.fragmentStack?.let { it.getOrNull(it.size - 2) }
        if (previous != null) {
            ExportProgressSheet.show(previous, currentAccount)
            finishFragment()
        } else {
            ExportProgressSheet.show(this, currentAccount)
        }
    }

    override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        super.onRequestPermissionsResultFragment(requestCode, permissions, grantResults)
        if (requestCode != BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE || !pendingStart) {
            return
        }
        pendingStart = false
        if (grantResults != null && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchExport()
        } else {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ExportStoragePermission)).show()
        }
    }

    private data class ExportEntry(val path: String, val time: Long, val bytes: Long, val chat: Boolean, val title: String?, val dialogId: Long)

    companion object {
        private const val EXPORT_ROW_BASE = 1000
        private val EXPORT_PREFIXES = arrayOf("ChatExport_", "DataExport_")
        private const val SIZE_CACHE = "export_sizes"
        private const val DELETE_SPINNER_DELAY = 300L
        private var cachedFolder: String? = null
        private var cachedExports: List<ExportEntry> = emptyList()
        private const val TITLE_SCAN_BYTES = 16 * 1024
        private val TITLE_PATTERN = Regex("class=\"text bold\">\\s*([^<]+?)\\s*<")

        @JvmStatic
        @JvmOverloads
        fun open(fragment: BaseFragment, peer: TLRPC.InputPeer? = null, topicId: Int = 0, topicTitle: String? = null) {
            if (ExportUiHelper.hasVisibleState(fragment.currentAccount)) {
                ExportProgressSheet.show(fragment, fragment.currentAccount)
            } else {
                fragment.presentFragment(ExportActivity(peer, topicId, topicTitle))
            }
        }

        private const val FOLDER_PICKER = 0x6578
        private const val DAY = 86400
        private const val FIRST_YEAR = 2013
        private const val BUTTON_AREA_DP = 12f + 48f + 12f
        private const val MEGABYTE = 1024L * 1024
        private const val MIN_FREE_SPACE = 64 * MEGABYTE
        private const val SIZE_STEPS = 100

        private fun sizeLimitByIndex(index: Int): Long {
            val step = index.coerceIn(0, SIZE_STEPS - 1) + 1
            val megabytes = when {
                step <= 10 -> step
                step <= 30 -> 10 + (step - 10) * 2
                step <= 40 -> 50 + (step - 30) * 5
                step <= 60 -> 100 + (step - 40) * 10
                step <= 70 -> 300 + (step - 60) * 20
                step <= 80 -> 500 + (step - 70) * 50
                step <= 90 -> 1000 + (step - 80) * 100
                else -> 2000 + (step - 90) * 200
            }
            return megabytes * MEGABYTE
        }

        private fun sizeLimitIndex(limit: Long): Int =
            (0 until SIZE_STEPS).firstOrNull { sizeLimitByIndex(it) >= limit } ?: (SIZE_STEPS - 1)
    }
}
