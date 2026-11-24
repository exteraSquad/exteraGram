/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog

import android.content.Context
import android.content.SharedPreferences
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.PythonPluginsEngine
import com.exteragram.messenger.plugins.catalog.db.CatalogEntryEntity
import com.exteragram.messenger.plugins.catalog.db.PluginCatalogDao
import com.exteragram.messenger.plugins.catalog.db.PluginCatalogDatabase
import com.exteragram.messenger.plugins.catalog.db.PluginMetadataEntity
import com.exteragram.messenger.plugins.catalog.db.PluginRepositoryEntity
import com.exteragram.messenger.pluginsEngine
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_chatlists
import java.io.File
import java.util.concurrent.Executors
import androidx.core.content.edit

class PluginCatalogController private constructor(private val account: Int) :
    NotificationCenter.NotificationCenterDelegate {

    private class PendingDownload(val document: TLRPC.Document, val callbacks: ArrayList<(File?) -> Unit>)

    private class MetadataJob(val repositoryId: Long, val entry: CatalogEntryEntity, val maxSize: Long)

    private val dao: PluginCatalogDao
        get() = PluginCatalogDatabase.getInstance().catalogDao()

    private val messagesController: MessagesController
        get() = MessagesController.getInstance(account)

    private val syncingRepositories = HashSet<Long>()
    private val syncCallbacks = HashMap<Long, ArrayList<(Int?) -> Unit>>()
    private val pendingForcedSyncs = HashSet<Long>()
    private val metadataRequests = HashSet<Long>()
    private var resolvingFolder = false

    private val downloads = HashMap<String, PendingDownload>()
    private var observingDownloads = false

    private val metadataQueue = ArrayDeque<MetadataJob>()
    private val queuedDocuments = HashSet<Long>()
    private var activeMetadataJobs = 0
    private var metadataUnavailable = false

    private val pendingUpdates = HashSet<Long>()
    private val postUpdatesRunnable = Runnable {
        val repositories = ArrayList(pendingUpdates)
        pendingUpdates.clear()
        val notificationCenter = NotificationCenter.getInstance(account)
        for (repositoryId in repositories) {
            notificationCenter.postNotificationName(NotificationCenter.pluginCatalogUpdated, repositoryId)
        }
    }

    fun loadRepositories(callback: (List<PluginRepositoryEntity>) -> Unit) {
        catalogQueue.postRunnable {
            val repositories = dao.getRepositories()
            val missing = repositories.filter { !it.isGit }.map { it.repositoryId }.filter { messagesController.getChat(it) == null }
            if (missing.isEmpty()) {
                AndroidUtilities.runOnUIThread { callback(repositories) }
                return@postRunnable
            }
            val storage = MessagesStorage.getInstance(account)
            storage.storageQueue.postRunnable {
                val chats = storage.getChats(ArrayList(missing))
                AndroidUtilities.runOnUIThread {
                    messagesController.putChats(chats, true)
                    callback(repositories)
                }
            }
        }
    }

    fun loadCatalog(repositoryId: Long, callback: (List<CatalogPlugin>) -> Unit) {
        catalogQueue.postRunnable {
            val plugins = getVisiblePlugins(repositoryId)
            val repository = dao.getRepository(repositoryId)
            val countChanged = repository != null && repository.syncedAt != 0L && repository.pluginsCount != plugins.size
            if (countChanged) {
                dao.updatePluginsCount(repositoryId, plugins.size)
            }
            AndroidUtilities.runOnUIThread {
                callback(plugins)
                if (countChanged) {
                    postUpdate(ALL_REPOSITORIES)
                }
            }
        }
    }

    private fun getVisiblePlugins(repositoryId: Long): List<CatalogPlugin> {
        val plugins = dao.getEntries(repositoryId)
            .filter { isSupported(it.entry.fileName) }
            .map(CatalogPlugin::from)
        return CatalogPlugin.latestVersions(plugins).filter { !it.isFailed }
    }

    fun isSyncing(repositoryId: Long): Boolean = syncingRepositories.contains(repositoryId)

    fun isResolvingFolder(): Boolean = resolvingFolder

    fun isMetadataAvailable(): Boolean = pluginsEngine && !metadataUnavailable

    fun refreshFolderRepositories(force: Boolean) {
        if (resolvingFolder) {
            return
        }
        if (!force && System.currentTimeMillis() - preferences.getLong(KEY_FOLDER_RESOLVED_AT, 0) < FOLDER_REFRESH_INTERVAL) {
            return
        }
        resolvingFolder = true
        postUpdate(ALL_REPOSITORIES)

        val req = TL_chatlists.TL_chatlists_checkChatlistInvite()
        req.slug = TRUSTED_FOLDER_SLUG
        ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
            AndroidUtilities.runOnUIThread {
                val peers: List<TLRPC.Peer>
                val chats: ArrayList<TLRPC.Chat>
                val users: ArrayList<TLRPC.User>
                when (response) {
                    is TL_chatlists.TL_chatlists_chatlistInvite -> {
                        peers = response.peers
                        chats = response.chats
                        users = response.users
                    }

                    is TL_chatlists.TL_chatlists_chatlistInviteAlready -> {
                        peers = response.already_peers + response.missing_peers
                        chats = response.chats
                        users = response.users
                    }

                    else -> {
                        FileLog.e("PluginCatalog: failed to resolve folder: ${error?.text}")
                        resolvingFolder = false
                        postUpdate(ALL_REPOSITORIES)
                        return@runOnUIThread
                    }
                }

                messagesController.putChats(chats, false)
                messagesController.putUsers(users, false)
                MessagesStorage.getInstance(account).putUsersAndChats(users, chats, true, true)

                val chatsById = chats.associateBy { it.id }
                val folderChats = peers.mapNotNull { peer ->
                    chatsById[peer.channel_id]?.takeIf { peer.channel_id != 0L && ChatObject.isChannel(it) }
                }
                catalogQueue.postRunnable {
                    val existing = dao.getRepositories().associateBy { it.repositoryId }
                    val now = System.currentTimeMillis()
                    val repositories = folderChats.mapIndexedNotNull { index, chat ->
                        val current = existing[chat.id]
                        if (current?.source == PluginRepositoryEntity.SOURCE_USER) {
                            return@mapIndexedNotNull null
                        }
                        PluginRepositoryEntity(
                            repositoryId = chat.id,
                            username = ChatObject.getPublicUsername(chat),
                            title = chat.title,
                            source = PluginRepositoryEntity.SOURCE_FOLDER,
                            sortOrder = index,
                            addedAt = current?.addedAt ?: now,
                            syncedAt = current?.syncedAt ?: 0,
                            pluginsCount = current?.pluginsCount ?: 0,
                        )
                    }
                    dao.replaceFolderRepositories(repositories)
                    preferences.edit { putLong(KEY_FOLDER_RESOLVED_AT, now) }
                    AndroidUtilities.runOnUIThread {
                        resolvingFolder = false
                        postUpdate(ALL_REPOSITORIES)
                    }
                }
            }
        }
    }

    fun addRepository(chat: TLRPC.Chat, callback: ((Boolean) -> Unit)? = null) {
        if (!ChatObject.isChannel(chat)) {
            callback?.invoke(false)
            return
        }
        MessagesStorage.getInstance(account).putUsersAndChats(null, arrayListOf(chat), true, true)
        val username = ChatObject.getPublicUsername(chat)
        val title = chat.title
        catalogQueue.postRunnable {
            if (dao.getRepository(chat.id) != null) {
                AndroidUtilities.runOnUIThread { callback?.invoke(false) }
                return@postRunnable
            }
            val sortOrder = dao.getRepositories().count { it.source == PluginRepositoryEntity.SOURCE_USER }
            dao.upsertRepositories(
                listOf(
                    PluginRepositoryEntity(
                        repositoryId = chat.id,
                        username = username,
                        title = title,
                        source = PluginRepositoryEntity.SOURCE_USER,
                        sortOrder = sortOrder,
                        addedAt = System.currentTimeMillis(),
                    )
                )
            )
            AndroidUtilities.runOnUIThread {
                callback?.invoke(true)
                postUpdate(ALL_REPOSITORIES)
            }
        }
    }

    fun addGitRepository(url: String, callback: (Int, PluginRepositoryEntity?) -> Unit) {
        catalogQueue.postRunnable {
            if (dao.getRepositoryByUrl(url) != null) {
                AndroidUtilities.runOnUIThread { callback(ADD_EXISTS, null) }
                return@postRunnable
            }
            networkExecutor.execute {
                val (result, source) = loadGitIndex(url, null, null)
                if (result !is GitPluginRepository.IndexResult.Loaded) {
                    val status = if (result == GitPluginRepository.IndexResult.Unsupported) ADD_UNSUPPORTED else ADD_FAILED
                    AndroidUtilities.runOnUIThread { callback(status, null) }
                    return@execute
                }
                catalogQueue.postRunnable {
                    if (dao.getRepositoryByUrl(url) != null) {
                        AndroidUtilities.runOnUIThread { callback(ADD_EXISTS, null) }
                        return@postRunnable
                    }
                    val repository = PluginRepositoryEntity(
                        repositoryId = nextGitRepositoryId(),
                        username = null,
                        title = result.index.name,
                        source = PluginRepositoryEntity.SOURCE_USER,
                        sortOrder = dao.getRepositories().count { it.source == PluginRepositoryEntity.SOURCE_USER },
                        addedAt = System.currentTimeMillis(),
                        url = url,
                        homepage = result.index.homepage,
                    )
                    dao.upsertRepositories(listOf(repository))
                    saveEtag(repository.repositoryId, source, result.etag)
                    val (entries, metadata) = buildGitEntries(repository, result.index)
                    AndroidUtilities.runOnUIThread {
                        syncingRepositories.add(repository.repositoryId)
                        finishSync(repository.repositoryId, entries, metadata, result.index)
                        callback(ADD_OK, repository)
                        postUpdate(ALL_REPOSITORIES)
                    }
                }
            }
        }
    }

    fun removeRepository(repositoryId: Long) {
        catalogQueue.postRunnable {
            if (dao.getRepository(repositoryId)?.source != PluginRepositoryEntity.SOURCE_USER) {
                return@postRunnable
            }
            dao.removeRepository(repositoryId)
            preferences.edit {
                remove(etagKey(repositoryId))
                remove(etagUrlKey(repositoryId))
            }
            AndroidUtilities.runOnUIThread {
                metadataQueue.removeAll { it.repositoryId == repositoryId }
                postUpdate(ALL_REPOSITORIES)
            }
        }
    }

    fun syncRepository(repositoryId: Long, force: Boolean, loadMetadata: Boolean, callback: ((Int?) -> Unit)? = null) {
        if (loadMetadata) {
            metadataRequests.add(repositoryId)
        }
        if (callback != null) {
            syncCallbacks.getOrPut(repositoryId) { ArrayList() }.add(callback)
        }
        if (!syncingRepositories.add(repositoryId)) {
            if (force) {
                pendingForcedSyncs.add(repositoryId)
            }
            return
        }
        postUpdate(repositoryId)
        val topMessage = getTopMessage(repositoryId)
        catalogQueue.postRunnable {
            val repository = dao.getRepository(repositoryId)
            val hasNewPosts = topMessage > preferences.getInt(syncedTopMessageKey(repositoryId), 0)
            if (repository == null || !force && !hasNewPosts && System.currentTimeMillis() - repository.syncedAt < SYNC_INTERVAL) {
                AndroidUtilities.runOnUIThread {
                    syncingRepositories.remove(repositoryId)
                    postUpdate(repositoryId)
                    completeSync(repositoryId, if (repository != null) 0 else null)
                    if (metadataRequests.remove(repositoryId) && repository != null) {
                        loadMissingMetadata(repositoryId)
                    }
                }
                return@postRunnable
            }
            if (repository.isGit) {
                syncGitRepository(repository)
                return@postRunnable
            }
            val cachedChat = messagesController.getChat(repositoryId)
                ?: MessagesStorage.getInstance(account).getChatSync(repositoryId)
            AndroidUtilities.runOnUIThread {
                resolveInputPeer(repository, cachedChat) { peer ->
                    if (peer == null) {
                        FileLog.e("PluginCatalog: cannot resolve repository $repositoryId")
                        finishSync(repositoryId, null)
                    } else {
                        loadHistoryPage(repositoryId, peer, 0, 0, ArrayList())
                    }
                }
            }
        }
    }

    private fun syncGitRepository(repository: PluginRepositoryEntity) {
        val url = repository.url ?: return
        val repositoryId = repository.repositoryId
        val etag = preferences.getString(etagKey(repositoryId), null)
        val etagUrl = preferences.getString(etagUrlKey(repositoryId), null)
        networkExecutor.execute {
            val (result, source) = loadGitIndex(url, etagUrl, etag)
            when (result) {
                is GitPluginRepository.IndexResult.Loaded -> {
                    val (entries, metadata) = buildGitEntries(repository, result.index)
                    catalogQueue.postRunnable {
                        if (dao.getRepository(repositoryId) != null) {
                            saveEtag(repositoryId, source, result.etag)
                        }
                    }
                    AndroidUtilities.runOnUIThread { finishSync(repositoryId, entries, metadata, result.index) }
                }

                GitPluginRepository.IndexResult.NotModified -> catalogQueue.postRunnable {
                    dao.updateSyncState(repositoryId, System.currentTimeMillis(), getVisiblePlugins(repositoryId).size)
                    AndroidUtilities.runOnUIThread {
                        syncingRepositories.remove(repositoryId)
                        postUpdate(repositoryId)
                        postUpdate(ALL_REPOSITORIES)
                        completeSync(repositoryId, 0)
                    }
                }

                else -> AndroidUtilities.runOnUIThread { finishSync(repositoryId, null) }
            }
        }
    }

    private fun loadGitIndex(url: String, etagUrl: String?, etag: String?): Pair<GitPluginRepository.IndexResult, String?> {
        var result: GitPluginRepository.IndexResult = GitPluginRepository.IndexResult.Failed
        for (source in GitPluginRepository.getIndexUrls(url)) {
            result = GitPluginRepository.loadIndex(source, etag.takeIf { source == etagUrl })
            if (result != GitPluginRepository.IndexResult.Failed) {
                return result to source
            }
        }
        return result to null
    }

    private fun buildGitEntries(
        repository: PluginRepositoryEntity,
        index: GitPluginRepository.Index,
    ): Pair<List<CatalogEntryEntity>, List<PluginMetadataEntity>> {
        val now = (System.currentTimeMillis() / 1000).toInt()
        val entryIds = HashSet<Int>()
        val entries = ArrayList<CatalogEntryEntity>()
        val metadata = ArrayList<PluginMetadataEntity>()
        for (plugin in index.plugins) {
            var entryId = plugin.id.hashCode()
            while (!entryIds.add(entryId)) {
                entryId++
            }
            val documentId = GitPluginRepository.documentId(repository.repositoryId, plugin.id, plugin.sha256)
            val updatedAt = plugin.updatedAt ?: now
            entries.add(
                CatalogEntryEntity(
                    repositoryId = repository.repositoryId,
                    entryId = entryId,
                    date = if (repository.syncedAt == 0L) updatedAt else now,
                    documentId = documentId,
                    fileName = plugin.fileName,
                    size = plugin.size,
                    caption = null,
                    message = null,
                    updatedAt = updatedAt,
                    fileUrl = plugin.fileUrl,
                    sha256 = plugin.sha256,
                )
            )
            metadata.add(
                PluginMetadataEntity(
                    documentId = documentId,
                    pluginId = plugin.id,
                    name = plugin.name,
                    author = plugin.author,
                    description = plugin.description,
                    version = plugin.version,
                    icon = plugin.icon,
                    appVersion = plugin.appVersion,
                    sdkVersion = plugin.sdkVersion,
                    requirements = plugin.requirements,
                    categories = plugin.categories,
                    error = null,
                    parsedAt = System.currentTimeMillis(),
                    parsedWithSdk = null,
                )
            )
        }
        return entries to metadata
    }

    private fun nextGitRepositoryId(): Long {
        val id = minOf(dao.getMinRepositoryId() ?: 0, preferences.getLong(KEY_LAST_GIT_REPOSITORY_ID, 0), 0) - 1
        preferences.edit { putLong(KEY_LAST_GIT_REPOSITORY_ID, id) }
        return id
    }

    private fun saveEtag(repositoryId: Long, source: String?, etag: String?) {
        preferences.edit {
            if (source != null && etag != null) {
                putString(etagKey(repositoryId), etag)
                putString(etagUrlKey(repositoryId), source)
            } else {
                remove(etagKey(repositoryId))
                remove(etagUrlKey(repositoryId))
            }
        }
    }

    private fun resolveInputPeer(
        repository: PluginRepositoryEntity,
        cachedChat: TLRPC.Chat?,
        callback: (TLRPC.InputPeer?) -> Unit,
    ) {
        val chat = messagesController.getChat(repository.repositoryId) ?: cachedChat?.also {
            messagesController.putChat(it, true)
        }
        if (chat != null && chat.access_hash != 0L) {
            callback(MessagesController.getInputPeer(chat))
            return
        }
        val username = repository.username
        if (username.isNullOrEmpty()) {
            callback(null)
            return
        }
        messagesController.userNameResolver.resolve(username) { peerId ->
            val resolved = if (peerId == -repository.repositoryId) messagesController.getChat(repository.repositoryId) else null
            callback(resolved?.let(MessagesController::getInputPeer))
        }
    }

    private fun loadHistoryPage(
        repositoryId: Long,
        peer: TLRPC.InputPeer,
        offsetId: Int,
        page: Int,
        collected: ArrayList<CatalogEntryEntity>,
    ) {
        val req = TLRPC.TL_messages_search()
        req.peer = peer
        req.q = ""
        req.filter = TLRPC.TL_inputMessagesFilterDocument()
        req.offset_id = offsetId
        req.limit = PAGE_SIZE
        ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
            if (response !is TLRPC.messages_Messages) {
                FileLog.e("PluginCatalog: history request failed for $repositoryId: ${error?.text}")
                AndroidUtilities.runOnUIThread { finishSync(repositoryId, null) }
                return@sendRequest
            }
            catalogQueue.postRunnable {
                for (message in response.messages) {
                    toCatalogEntry(repositoryId, message)?.let(collected::add)
                }
                val lastId = response.messages.lastOrNull()?.id ?: 0
                val hasMore = response.messages.size >= PAGE_SIZE && lastId != 0 && page + 1 < MAX_PAGES
                AndroidUtilities.runOnUIThread {
                    messagesController.putChats(response.chats, false)
                    messagesController.putUsers(response.users, false)
                    if (hasMore) {
                        loadHistoryPage(repositoryId, peer, lastId, page + 1, collected)
                    } else {
                        finishSync(repositoryId, collected)
                    }
                }
            }
        }
    }

    private fun finishSync(
        repositoryId: Long,
        entries: List<CatalogEntryEntity>?,
        metadata: List<PluginMetadataEntity> = emptyList(),
        index: GitPluginRepository.Index? = null,
    ) {
        if (entries == null) {
            syncingRepositories.remove(repositoryId)
            postUpdate(repositoryId)
            completeSync(repositoryId, null)
            if (metadataRequests.remove(repositoryId)) {
                loadMissingMetadata(repositoryId)
            }
            return
        }
        val topMessage = getTopMessage(repositoryId)
        catalogQueue.postRunnable {
            var changed = 0
            val repository = dao.getRepository(repositoryId)
            if (repository != null) {
                val previous = dao.getEntryVersions(repositoryId).associateBy { it.entryId }
                for (entry in entries) {
                    val old = previous[entry.entryId]
                    if (old != null) {
                        entry.date = old.date
                    }
                    val isNew = when {
                        old == null -> {
                            if (!repository.isGit) {
                                entry.updatedAt = entry.date
                            }
                            entry.date * 1000L > repository.syncedAt
                        }
                        old.documentId == entry.documentId -> {
                            entry.updatedAt = old.updatedAt
                            false
                        }
                        else -> true
                    }
                    if (isNew && isSupported(entry.fileName)) {
                        changed++
                    }
                }
                dao.replaceEntries(repositoryId, entries, metadata)
                if (index != null) {
                    dao.updateGitInfo(repositoryId, index.name, index.homepage)
                }
                dao.updateSyncState(repositoryId, System.currentTimeMillis(), getVisiblePlugins(repositoryId).size)
                if (!repository.isGit) {
                    preferences.edit { putInt(syncedTopMessageKey(repositoryId), topMessage) }
                }
            }
            AndroidUtilities.runOnUIThread {
                syncingRepositories.remove(repositoryId)
                postUpdate(repositoryId)
                postUpdate(ALL_REPOSITORIES)
                completeSync(repositoryId, changed)
                if (metadataRequests.remove(repositoryId)) {
                    loadMissingMetadata(repositoryId)
                }
            }
        }
    }

    private fun completeSync(repositoryId: Long, changed: Int?) {
        if (pendingForcedSyncs.remove(repositoryId)) {
            syncRepository(repositoryId, force = true, loadMetadata = false)
            return
        }
        syncCallbacks.remove(repositoryId)?.forEach { it(changed) }
    }

    private fun getTopMessage(repositoryId: Long): Int =
        if (PluginRepositoryEntity.isGitId(repositoryId)) 0 else messagesController.dialogs_dict[-repositoryId]?.top_message ?: 0

    fun loadMissingMetadata(repositoryId: Long) {
        if (!pluginsEngine) {
            return
        }
        metadataUnavailable = false
        catalogQueue.postRunnable {
            if (dao.getRepository(repositoryId)?.isGit != false) {
                return@postRunnable
            }
            val tooLarge = dao.getDocumentIdsWithError(ERROR_TOO_LARGE).toHashSet()
            val jobs = dao.getEntriesWithoutMetadata(repositoryId, PythonPluginsEngine.SDK_VERSION, ERROR_TOO_LARGE).mapNotNull { entry ->
                val maxSize = getMetadataSizeLimit(entry.fileName)
                if (maxSize == null || entry.size > maxSize && entry.documentId in tooLarge) null else MetadataJob(repositoryId, entry, maxSize)
            }
            AndroidUtilities.runOnUIThread {
                for (job in jobs) {
                    if (queuedDocuments.add(job.entry.documentId)) {
                        metadataQueue.addLast(job)
                    }
                }
                pumpMetadataQueue()
            }
        }
    }

    private fun pumpMetadataQueue() {
        while (activeMetadataJobs < MAX_METADATA_JOBS) {
            val job = metadataQueue.removeFirstOrNull() ?: return
            if (job.entry.size > job.maxSize) {
                storeMetadata(job, failedMetadata(job.entry.documentId, ERROR_TOO_LARGE))
                continue
            }
            val messageObject = createMessageObject(job.repositoryId, job.entry)
            if (messageObject?.document == null) {
                storeMetadata(job, failedMetadata(job.entry.documentId, ERROR_INVALID_MESSAGE))
                continue
            }
            activeMetadataJobs++
            downloadDocument(messageObject, FileLoader.PRIORITY_LOW) { file ->
                if (file == null) {
                    activeMetadataJobs--
                    queuedDocuments.remove(job.entry.documentId)
                    pumpMetadataQueue()
                } else {
                    parseMetadata(job, file)
                }
            }
        }
    }

    private fun parseMetadata(job: MetadataJob, file: File) {
        val path = file.absolutePath
        PluginsController.runOnPluginsQueue {
            val engine = PluginsController.getPluginEngine(probeFile(job.entry.fileName))
            val map = try {
                engine?.parsePluginMetadata(path)
            } catch (e: Throwable) {
                FileLog.e("PluginCatalog: failed to parse ${job.entry.fileName}", e)
                null
            }
            if (engine == null) {
                AndroidUtilities.runOnUIThread {
                    activeMetadataJobs--
                    queuedDocuments.remove(job.entry.documentId)
                    pumpMetadataQueue()
                }
                return@runOnPluginsQueue
            }
            if (map?.isEmpty() == true) {
                AndroidUtilities.runOnUIThread {
                    activeMetadataJobs--
                    metadataUnavailable = true
                    metadataQueue.clear()
                    queuedDocuments.clear()
                    postUpdate(job.repositoryId)
                }
                return@runOnPluginsQueue
            }
            val metadata = run {
                if (map == null || map["id"].isNullOrEmpty() || map["name"].isNullOrEmpty()) {
                    failedMetadata(job.entry.documentId, ERROR_INVALID_METADATA)
                } else {
                    PluginMetadataEntity(
                        documentId = job.entry.documentId,
                        pluginId = map["id"],
                        name = map["name"],
                        author = map["author"],
                        description = map["description"],
                        version = map["version"],
                        icon = map["icon"],
                        appVersion = map["app_version"],
                        sdkVersion = map["sdk_version"],
                        requirements = map["requirements"],
                        categories = map["category"],
                        error = null,
                        parsedAt = System.currentTimeMillis(),
                        parsedWithSdk = PythonPluginsEngine.SDK_VERSION,
                    )
                }
            }
            AndroidUtilities.runOnUIThread {
                activeMetadataJobs--
                storeMetadata(job, metadata)
            }
        }
    }

    private fun storeMetadata(job: MetadataJob, metadata: PluginMetadataEntity) {
        catalogQueue.postRunnable {
            dao.upsertMetadata(metadata)
            AndroidUtilities.runOnUIThread {
                queuedDocuments.remove(job.entry.documentId)
                postUpdate(job.repositoryId)
                pumpMetadataQueue()
            }
        }
    }

    fun downloadPlugin(plugin: CatalogPlugin, callback: (File?, MessageObject?) -> Unit) {
        catalogQueue.postRunnable {
            val entry = dao.getEntry(plugin.repositoryId, plugin.entryId)
            val fileUrl = entry?.fileUrl
            val sha256 = entry?.sha256
            if (fileUrl != null && sha256 != null) {
                downloadGitFile(entry, fileUrl, sha256, plugin.metadata) { file -> callback(file, null) }
                return@postRunnable
            }
            AndroidUtilities.runOnUIThread {
                val messageObject = entry?.let { createMessageObject(plugin.repositoryId, it) }
                if (messageObject?.document == null) {
                    callback(null, null)
                } else {
                    downloadDocument(messageObject, FileLoader.PRIORITY_HIGH) { file -> callback(file, messageObject) }
                }
            }
        }
    }

    private fun downloadGitFile(
        entry: CatalogEntryEntity,
        fileUrl: String,
        sha256: String,
        metadata: PluginMetadataEntity?,
        callback: (File?) -> Unit,
    ) {
        networkExecutor.execute {
            val file = GitPluginRepository.downloadFile(fileUrl, entry.fileName, sha256, entry.size)
            if (file == null || metadata == null) {
                AndroidUtilities.runOnUIThread { callback(null) }
                return@execute
            }
            PluginsController.runOnPluginsQueue {
                val parsed = try {
                    PluginsController.getPluginEngine(file)?.parsePluginMetadata(file.absolutePath)
                } catch (e: Throwable) {
                    FileLog.e("PluginCatalog: failed to parse ${entry.fileName}", e)
                    null
                }
                val matches = parsed?.get("id") == metadata.pluginId && parsed?.get("version")?.trim() == metadata.version
                if (!matches) {
                    FileLog.e("PluginCatalog: ${entry.fileName} does not match its index entry")
                }
                AndroidUtilities.runOnUIThread { callback(file.takeIf { matches }) }
            }
        }
    }

    private fun downloadDocument(messageObject: MessageObject, priority: Int, callback: (File?) -> Unit) {
        val document = messageObject.document
        findDownloadedFile(document)?.let {
            callback(it)
            return
        }
        val fileName = FileLoader.getAttachFileName(document)
        val pending = downloads[fileName]
        if (pending != null) {
            pending.callbacks.add(callback)
            if (priority != FileLoader.PRIORITY_LOW) {
                FileLoader.getInstance(account).loadFile(document, messageObject, priority, 1)
            }
            return
        }
        downloads[fileName] = PendingDownload(document, arrayListOf(callback))
        if (!observingDownloads) {
            observingDownloads = true
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoaded)
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadFailed)
        }
        FileLoader.getInstance(account).loadFile(document, messageObject, priority, 1)
    }

    private fun findDownloadedFile(document: TLRPC.Document): File? {
        val fileLoader = FileLoader.getInstance(account)
        return fileLoader.getPathToAttach(document, true).takeIf { it.exists() }
            ?: fileLoader.getPathToAttach(document, false).takeIf { it.exists() }
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id != NotificationCenter.fileLoaded && id != NotificationCenter.fileLoadFailed) {
            return
        }
        val pending = downloads.remove(args[0] as? String ?: return) ?: return
        val file = if (id == NotificationCenter.fileLoaded) {
            (args.getOrNull(1) as? File)?.takeIf { it.exists() } ?: findDownloadedFile(pending.document)
        } else {
            null
        }
        if (downloads.isEmpty() && observingDownloads) {
            observingDownloads = false
            NotificationCenter.getInstance(this.account).removeObserver(this, NotificationCenter.fileLoaded)
            NotificationCenter.getInstance(this.account).removeObserver(this, NotificationCenter.fileLoadFailed)
        }
        for (callback in pending.callbacks) {
            callback(file)
        }
    }

    private fun createMessageObject(repositoryId: Long, entry: CatalogEntryEntity): MessageObject? {
        val data = SerializedData(entry.message ?: return null)
        return try {
            val message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false) ?: return null
            message.dialog_id = -repositoryId
            MessageObject(account, message, false, false)
        } catch (e: Exception) {
            FileLog.e(e)
            null
        } finally {
            data.cleanup()
        }
    }

    private fun toCatalogEntry(repositoryId: Long, message: TLRPC.Message): CatalogEntryEntity? {
        val document = message.media?.document ?: return null
        val fileName = FileLoader.getDocumentFileName(document) ?: return null
        val data = SerializedData(message.objectSize)
        return try {
            message.serializeToStream(data)
            CatalogEntryEntity(
                repositoryId = repositoryId,
                entryId = message.id,
                date = message.date,
                documentId = document.id,
                fileName = fileName,
                size = document.size,
                caption = message.message?.takeIf { it.isNotEmpty() },
                message = data.toByteArray(),
                views = message.views,
                updatedAt = maxOf(message.date, message.edit_date),
            )
        } catch (e: Exception) {
            FileLog.e(e)
            null
        } finally {
            data.cleanup()
        }
    }

    private fun failedMetadata(documentId: Long, error: String) = PluginMetadataEntity(
        documentId = documentId,
        pluginId = null,
        name = null,
        author = null,
        description = null,
        version = null,
        icon = null,
        appVersion = null,
        sdkVersion = null,
        requirements = null,
        categories = null,
        error = error,
        parsedAt = System.currentTimeMillis(),
        parsedWithSdk = PythonPluginsEngine.SDK_VERSION,
    )

    private fun postUpdate(repositoryId: Long) {
        pendingUpdates.add(repositoryId)
        AndroidUtilities.cancelRunOnUIThread(postUpdatesRunnable)
        AndroidUtilities.runOnUIThread(postUpdatesRunnable, UPDATE_DEBOUNCE_MS)
    }

    companion object {
        const val TRUSTED_FOLDER_SLUG = "pPhOtEq00KhjYTc6"
        const val ALL_REPOSITORIES = 0L

        const val ADD_OK = 0
        const val ADD_EXISTS = 1
        const val ADD_FAILED = 2
        const val ADD_UNSUPPORTED = 3

        const val ERROR_TOO_LARGE = "FILE_TOO_LARGE"
        const val ERROR_INVALID_MESSAGE = "INVALID_MESSAGE"
        const val ERROR_INVALID_METADATA = "INVALID_METADATA"

        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 20
        private const val MAX_METADATA_JOBS = 3
        private const val MAX_METADATA_FILE_SIZE = 2L * 1024 * 1024
        private const val MAX_ARCHIVE_METADATA_FILE_SIZE = 10L * 1024 * 1024
        private const val SYNC_INTERVAL = 10 * 60 * 1000L
        private const val FOLDER_REFRESH_INTERVAL = 5 * 60 * 1000L
        private const val UPDATE_DEBOUNCE_MS = 100L
        private const val KEY_FOLDER_RESOLVED_AT = "folderResolvedAt"
        private const val KEY_SORT_ORDER = "sortOrder"
        private const val KEY_LAST_GIT_REPOSITORY_ID = "lastGitRepositoryId"
        private const val NETWORK_THREADS = 3

        var sortOrder: Int
            get() = preferences.getInt(KEY_SORT_ORDER, 0)
            set(value) = preferences.edit { putInt(KEY_SORT_ORDER, value) }

        private fun syncedTopMessageKey(repositoryId: Long) = "syncedTopMessage_$repositoryId"
        private fun etagKey(repositoryId: Long) = "etag_$repositoryId"
        private fun etagUrlKey(repositoryId: Long) = "etagUrl_$repositoryId"

        private val catalogQueue by lazy { DispatchQueue("pluginCatalogQueue") }
        private val networkExecutor by lazy { Executors.newFixedThreadPool(NETWORK_THREADS) }

        private val extensionEngines = HashMap<String, PluginsController.PluginsEngine?>()
        private var extensionEnginesKey = 0

        private fun getEngine(fileName: String): PluginsController.PluginsEngine? {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) {
                return null
            }
            val enginesKey = PluginsController.getEngines().keys.hashCode()
            if (enginesKey != extensionEnginesKey) {
                extensionEngines.clear()
                extensionEnginesKey = enginesKey
            }
            if (!extensionEngines.containsKey(extension)) {
                extensionEngines[extension] = PluginsController.getPluginEngine(probeFile(fileName))
            }
            return extensionEngines[extension]
        }

        private fun isSupported(fileName: String): Boolean = getEngine(fileName) != null

        private fun getMetadataSizeLimit(fileName: String): Long? = when (getEngine(fileName)) {
            null -> null
            is PythonPluginsEngine -> MAX_METADATA_FILE_SIZE
            else -> MAX_ARCHIVE_METADATA_FILE_SIZE
        }

        private fun probeFile(fileName: String) = File("plugin." + fileName.substringAfterLast('.', "").lowercase())

        private val preferences: SharedPreferences by lazy {
            ApplicationLoader.applicationContext.getSharedPreferences("plugin_catalog", Context.MODE_PRIVATE)
        }

        private val instances = arrayOfNulls<PluginCatalogController>(UserConfig.MAX_ACCOUNT_COUNT)

        @JvmStatic
        fun getInstance(account: Int): PluginCatalogController {
            return instances[account] ?: synchronized(instances) {
                instances[account] ?: PluginCatalogController(account).also { instances[account] = it }
            }
        }
    }
}
