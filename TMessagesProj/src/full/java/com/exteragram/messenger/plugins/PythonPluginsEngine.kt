/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.exteragram.messenger.plugins.hooks.PluginsHooks
import com.exteragram.messenger.plugins.models.CustomSetting
import com.exteragram.messenger.plugins.models.DividerSetting
import com.exteragram.messenger.plugins.models.EditTextSetting
import com.exteragram.messenger.plugins.models.HeaderSetting
import com.exteragram.messenger.plugins.models.InputSetting
import com.exteragram.messenger.plugins.models.SelectorSetting
import com.exteragram.messenger.plugins.models.SettingItem
import com.exteragram.messenger.plugins.models.SwitchSetting
import com.exteragram.messenger.plugins.models.TextSetting
import com.exteragram.messenger.plugins.pip.PipController
import com.exteragram.messenger.plugins.ui.PluginSettingsActivity
import com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet
import com.exteragram.messenger.plugins.ui.components.PluginFileViewer
import com.exteragram.messenger.plugins.ui.components.PythonSdkUpdateAlert
import com.exteragram.messenger.plugins.updates.SignedArtifact
import com.exteragram.messenger.plugins.updates.TelegramUpdateSource
import com.exteragram.messenger.plugins.utils.PyObjectUtils
import com.exteragram.messenger.plugins.utils.SdkSignature
import com.exteragram.messenger.pluginsDevMode
import com.exteragram.messenger.pluginsEngine
import com.exteragram.messenger.pluginsPySdkAutoUpdate
import com.exteragram.messenger.pluginsPySdkBetaVersions
import com.exteragram.messenger.pluginsSafeMode
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.sdkUpdateScheduleTimestamp
import com.exteragram.messenger.utils.AppUtils
import com.exteragram.messenger.utils.text.LocaleUtils
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.ArchiveExtractionOptions
import org.simplifiles.archive.ExtractionTargetPolicy
import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.files.OverwritePolicy
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.LaunchActivity
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class PythonPluginsEngine : PluginsController.PluginsEngine {

    companion object {
        private val VERSION_PATTERN = Pattern.compile("^(>=|<=|==|>|<)(.+)$")
        private val SDK_REQUIRED_MODULES = arrayOf(
            "_sdk_version",
            "base_plugin",
            "plugin_settings"
        )
        private val SDK_ARCHIVE_POLICY = SecurityPolicy.builder()
            .maxEntries(100_000)
            .maxTotalUncompressedSize(2L * 1024L * 1024L * 1024L)
            .maxSingleFileSize(512L * 1024L * 1024L)
            .maxCompressionRatio(500.0)
            .build()
        private val SDK_ARCHIVE_OPTIONS = ArchiveExtractionOptions.builder()
            .targetPolicy(ExtractionTargetPolicy.REPLACE)
            .build()
        private const val MAX_SDK_VERSION_BYTES = 64L * 1024L
        private const val SAFE_MODE_ENABLE_ERROR =
            "Plugins cannot be enabled while safe mode is active"

        var SDK_VERSION: String? = null

        var SDK_BETA = false

        private var SDK_DIR: File? = null

        @Volatile
        private var sdkInitialized = false

        @Volatile
        private var sdkState = SdkState.READY

        private fun setSdkState(state: SdkState) {
            sdkState = state
            Updater.notifySdkStateChanged()
        }

        private fun sdkModuleExists(sdkDir: File, moduleName: String): Boolean {
            return File(sdkDir, "$moduleName.so").exists() || File(
                sdkDir,
                "$moduleName.pyc"
            ).exists()
        }

        private fun deleteFileIfExists(file: File?) {
            if (file == null || !file.exists()) {
                return
            }
            runCatching {
                SimpliFiles.file(file).delete()
            }.onFailure {
                FileLog.e(it)
            }
        }

        private fun isSdkDirValid(sdkDir: File?): Boolean {
            if (sdkDir == null || !sdkDir.isDirectory) {
                return false
            }
            for (moduleName in SDK_REQUIRED_MODULES) {
                if (!sdkModuleExists(sdkDir, moduleName)) {
                    return false
                }
            }
            return true
        }

        private fun canonicalPathOrNull(path: String?): String? {
            if (path.isNullOrEmpty()) {
                return null
            }
            return runCatching { File(path).canonicalPath }.getOrNull()
        }

        private fun topLevelModuleNames(dir: File?): Set<String> {
            val files = dir?.listFiles() ?: return emptySet()
            val names = HashSet<String>()
            for (file in files) {
                val name = file.name
                if (file.isDirectory) {
                    if (name == "__pycache__" || name.endsWith(".dist-info") || name.endsWith(".data")) {
                        continue
                    }
                    names.add(name)
                } else if (name.endsWith(".py") || name.endsWith(".pyc") || name.endsWith(".so")) {
                    names.add(name.substringBefore('.'))
                }
            }
            return names
        }
    }

    val pluginInstances = ConcurrentHashMap<String, PyObject>()

    private val settingsCache = ConcurrentHashMap<String, ConcurrentHashMap<String, Any?>>()

    private val dependencyPaths = ConcurrentHashMap.newKeySet<String>()
    private val lifecycleGeneration = AtomicInteger()

    @Volatile
    private var python: Python? = null

    private var devServerClass: PyObject? = null

    var debuggerListener: PyObject? = null

    @Volatile
    var basePluginClass: PyObject? = null

    private fun getPluginsController(): PluginsController {
        return PluginsController.getInstance()
    }

    @Synchronized
    private fun getPython(): Python? {
        if (python == null) {
            initPython()
            if (python == null) {
                FileLog.e("Python initialization failed, unable to proceed.")
                return null
            }
        }
        return python
    }

    private fun initPython() {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(ApplicationLoader.applicationContext))
            }
            python = Python.getInstance()
        } catch (e: Throwable) {
            FileLog.e("Failed to initialize Python", e)
        }
    }

    override fun isPlugin(file: File?, messageObject: MessageObject?): Boolean {
        return file != null && file.name.lowercase().endsWith(PluginsConstants.PLUGINS_EXT)
    }

    override fun isEngineAvailable(): Boolean {
        return python != null && sdkInitialized && basePluginClass != null
    }

    @Synchronized
    @Throws(Exception::class)
    private fun requireBasePluginClass(): PyObject {
        if (pluginsSafeMode) {
            throw Exception(SAFE_MODE_ENABLE_ERROR)
        }
        if (!sdkInitialized) {
            throw Exception("Python plugin SDK is not initialized")
        }
        basePluginClass?.let { return it }

        val currentPython = python ?: throw Exception("Python interpreter is not initialized")
        return try {
            currentPython.getModule("base_plugin")["BasePlugin"]
                ?.also { basePluginClass = it }
                ?: throw Exception("BasePlugin class is missing from the Python SDK")
        } catch (t: Throwable) {
            throw Exception("Failed to initialize Python plugin runtime", t)
        }
    }

    @Throws(IOException::class)
    private fun installSdkArchive(archiveFile: File, fromApk: Boolean) {
        val sdkDir = requireNotNull(SDK_DIR)
        val tempSdkDir = File(sdkDir.parentFile, PluginsConstants.PLUGINS_SDK + ".tmp")

        try {
            SimpliFiles.archive(archiveFile)
                .withPolicy(SDK_ARCHIVE_POLICY)
                .extractToDirectory(tempSdkDir, SDK_ARCHIVE_OPTIONS)
            if (!isSdkDirValid(tempSdkDir)) {
                throw IOException("Python SDK archive unpacked with missing required files")
            }

            SimpliFiles.directory(tempSdkDir).moveTo(sdkDir, OverwritePolicy.REPLACE)
            Updater.setBuildFromApk(fromApk)
        } finally {
            if (tempSdkDir.exists()) {
                runCatching {
                    SimpliFiles.directory(tempSdkDir).deleteRecursively()
                }.onFailure {
                    FileLog.e(it)
                }
            }
            if (!sdkDir.exists()) {
                SimpliFiles.directory(sdkDir).create()
            }
        }
    }

    private fun initSdk(): Boolean {
        sdkInitialized = false
        val currentPython = python ?: return false

        if (SDK_DIR == null) {
            SDK_DIR = Updater.sdkDirFile()
            if (!requireNotNull(SDK_DIR).exists()) {
                SimpliFiles.directory(requireNotNull(SDK_DIR)).create()
            }
        }

        val sdkDir = requireNotNull(SDK_DIR)
        val restoreRequestFile = Updater.requestSdkFromApkFile()
        val sdkFileUpdate = Updater.getPythonSdkUpdateFile()
        val currentSdkFile = Updater.getPythonCurrentSdkFile()

        var shouldRestoreFromApk = restoreRequestFile.exists() || pluginsSafeMode
        if (shouldRestoreFromApk) {
            SimpliFiles.directory(sdkDir).clean()
            deleteFileIfExists(restoreRequestFile)
        }

        if (!pluginsSafeMode) synchronized(Updater.pendingUpdateLock) {
            if (!shouldRestoreFromApk && sdkFileUpdate.exists()) {
                if (!Updater.isPendingUpdateVerified(sdkFileUpdate)) {
                    FileLog.e("Discarding Python SDK update that failed signature verification")
                } else {
                    try {
                        SimpliFiles.file(sdkFileUpdate).copyTo(currentSdkFile, OverwritePolicy.REPLACE)
                        installSdkArchive(currentSdkFile, false)
                    } catch (e: IOException) {
                        FileLog.e("Failed to install updated Python SDK archive", e)
                        shouldRestoreFromApk = true
                    }
                }
            }
            Updater.deleteSdkUpdateFile()
        }

        val versionFile = File(sdkDir, "v.txt")
        if (versionFile.exists()) {
            var currentVersion = SimpliFiles.file(versionFile).readText(MAX_SDK_VERSION_BYTES).trim()
            val isBeta = currentVersion.endsWith("|1")
            currentVersion = currentVersion.substringBefore('|')

            try {
                ApplicationLoader.applicationContext.assets.open("plugins_pysdk/v.txt")
                    .use { versionAsset ->
                        BufferedReader(
                            InputStreamReader(
                                versionAsset,
                                StandardCharsets.UTF_8
                            )
                        ).use { reader ->
                            val sb = StringBuilder()
                            while (true) {
                                val line = reader.readLine() ?: break
                                sb.append(line).append('\n')
                            }
                            val apkVersion = sb.toString().trim()
                            try {
                                if (AppUtils.compareVersions(
                                        if (!Updater.isSdkFromCurrentApk() && (isBeta || Updater.isSdkFromApk())) ">=" else ">",
                                        apkVersion,
                                        currentVersion
                                    )
                                ) {
                                    shouldRestoreFromApk = true
                                }
                            } catch (e: NumberFormatException) {
                                FileLog.e("Invalid Python SDK version file. Restoring SDK from APK.", e)
                                shouldRestoreFromApk = true
                            }
                        }
                    }
            } catch (e: IOException) {
                throw RuntimeException(
                    "Failed to read Python SDK version (v.txt) from APK assets",
                    e
                )
            }
        }

        if (shouldRestoreFromApk || !isSdkDirValid(sdkDir)) {
            if (!shouldRestoreFromApk) {
                FileLog.w("Python SDK directory is missing required files. Restoring SDK from APK.")
            }
            SimpliFiles.directory(sdkDir).clean()
            try {
                Updater.sdkFromApk().use { packed ->
                    SimpliFiles.file(currentSdkFile).writeFromAtomic(packed)
                }
                installSdkArchive(currentSdkFile, true)
            } catch (e: IOException) {
                throw RuntimeException("Failed to install Python SDK from APK", e)
            }
        }
        val sys = currentPython.getModule("sys")
        sys["path"]?.callAttr("append", sdkDir.absolutePath)

        try {
            val sdkInfo = currentPython.getModule("_sdk_version")
            sdkInitialized = sdkInfo.callAttr("__start__")?.toBoolean() == true
            val sdkVersion = sdkInfo["__version__"]
            SDK_VERSION = sdkVersion?.toJava(String::class.java) ?: SDK_VERSION
            val sdkIsBeta = sdkInfo["__beta__"]
            SDK_BETA = sdkIsBeta?.toJava(Boolean::class.java) ?: SDK_BETA
        } catch (e: Throwable) {
            FileLog.e("Failed to initialize Python SDK bootstrap", e)
            try {
                Updater.restoreSdkFromApk()
            } catch (restoreError: Throwable) {
                FileLog.e("Failed to schedule Python SDK restore from APK", restoreError)
            }
            return false
        }

        if (basePluginClass == null && sdkInitialized && !pluginsSafeMode) {
            try {
                requireBasePluginClass()
            } catch (e: Exception) {
                FileLog.e("Failed to load BasePlugin class", e)
            }
        }

        return sdkInitialized
    }

    private fun stopAndUnloadSdk() {
        val currentPython = python ?: return
        basePluginClass = null
        val sys = currentPython.getModule("sys")
        val sysModules = sys["modules"]
        try {
            sysModules?.callAttr("get", "_sdk_version")?.callAttr("__stop__")
        } catch (e: Throwable) {
            FileLog.e("Failed to stop Python SDK bootstrap", e)
        }

        sdkInitialized = false

        val sdkDir = SDK_DIR
        val sysPath = sys["path"]
        if (sdkDir != null && sysPath != null && sysPath.callAttr(
                "__contains__",
                sdkDir.absolutePath
            ).toBoolean()
        ) {
            sysPath.callAttr("remove", sdkDir.absolutePath)
        }

        if (sdkDir != null && sysModules != null) {
            removeModulesRecursive(sysModules, sdkDir, "")
        }
    }

    private fun removeModulesRecursive(sysModules: PyObject, file: File, prefix: String) {
        var effectivePrefix = prefix
        if (effectivePrefix == PluginsConstants.PLUGINS_SDK + ".") {
            effectivePrefix = ""
        }
        if (file.isDirectory && sysModules.callAttr("__contains__", effectivePrefix + file.name)
                .toBoolean()
        ) {
            sysModules.callAttr("pop", effectivePrefix + file.name)
        }

        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                removeModulesRecursive(sysModules, child, file.name + ".")
            }
        }

        val moduleName = file.name.substringBefore('.')
        if (!file.isDirectory && sysModules.callAttr("__contains__", effectivePrefix + moduleName)
                .toBoolean()
        ) {
            sysModules.callAttr("pop", effectivePrefix + moduleName)
        }
    }

    override fun init(callback: Runnable) {
        lifecycleGeneration.incrementAndGet()
        setSdkState(SdkState.STARTING)
        if (getPython() == null) {
            setSdkState(SdkState.PYTHON_FAILED)
            callback.run()
            return
        }
        try {
            if (!initSdk()) {
                setSdkState(SdkState.START_FAILED)
                callback.run()
                return
            }
        } catch (e: Throwable) {
            FileLog.e("Failed to initialize Python plugins engine", e)
            setSdkState(SdkState.INSTALL_FAILED)
            callback.run()
            return
        }
        setSdkState(SdkState.READY)
        if (!pluginsSafeMode) {
            AndroidUtilities.runOnUIThread(Updater::checkUpdates, 5000)
        }

        val currentPython = python ?: run {
            callback.run()
            return
        }

        if (!pluginsSafeMode) {
            try {
                val pySettingsModule = currentPython.getModule("plugin_settings")
                val allPrefs = getPluginsController().preferences.all
                val keysToDeletePy = pySettingsModule.callAttr(
                    "init",
                    getPluginsController().pluginsDir.absolutePath,
                    allPrefs
                )
                val keysToDelete = keysToDeletePy.toJava(Array<String>::class.java)
                if (keysToDelete.isNotEmpty()) {
                    getPluginsController().preferences.edit {
                        for (key in keysToDelete) {
                            remove(key)
                        }
                    }
                    FileLog.d("Migrated ${keysToDelete.size} plugin settings from SharedPreferences to JSON.")
                }
            } catch (e: PyException) {
                FileLog.e("Failed to initialize plugin_settings module", e)
            }
        }

        PipController.cleanup()
        loadPlugins(callback)
        checkDevServer()
    }

    override fun checkDevServer() {
        if (pluginsSafeMode) return

        if (pluginsDevMode) {
            runDevServer()
        } else {
            stopDevServer()
        }
    }

    private fun runDevServer() {
        if (pluginsSafeMode) return

        val currentPython = getPython() ?: return
        if (devServerClass != null) {
            stopDevServer()
        }

        try {
            devServerClass =
                currentPython.getModule(PluginsConstants.DevServer.MODULE)[PluginsConstants.DevServer.CLASS]
            if (devServerClass == null) {
                return
            }
            devServerClass?.callAttrThrows(PluginsConstants.DevServer.START_SERVER)
            FileLog.d("Dev server started successfully.")
        } catch (e: Throwable) {
            FileLog.e("Failed to initialize dev server", e)
            devServerClass = null
        }
    }

    private fun stopDevServer() {
        val currentDevServerClass = devServerClass ?: return
        try {
            currentDevServerClass.callAttrThrows(PluginsConstants.DevServer.STOP_SERVER)
            FileLog.d("Dev server stopped successfully.")
        } catch (e: Throwable) {
            FileLog.e("Failed to stop dev server", e)
        } finally {
            devServerClass = null
        }
    }

    internal fun stopPluginTasks() {
        lifecycleGeneration.incrementAndGet()
        runCatching {
            PyObjectUtils.closeSafely(basePluginClass?.get("_stop_all_lifecycles")?.call())
        }.onFailure { FileLog.e("Failed to stop plugin tasks", it) }
    }

    override fun shutdown(callback: Runnable) {
        try {
            stopPluginTasks()
            runCatching { stopDevServer() }.onFailure { FileLog.e(it) }
            for (pluginId in ArrayList(pluginInstances.keys)) {
                runCatching { unloadPlugin(pluginId) }
                    .onFailure { FileLog.e("Failed to unload plugin $pluginId", it) }
            }
            runCatching { debuggerListener?.close() }.onFailure { FileLog.e(it) }
            debuggerListener = null
            pluginInstances.clear()
            synchronized(this) {
                runCatching { removePluginPathsFromSysPath() }.onFailure { FileLog.e(it) }
                runCatching { stopAndUnloadSdk() }.onFailure { FileLog.e(it) }
                python = null
                sdkInitialized = false
            }
            setSdkState(SdkState.READY)
            FileLog.d("Python plugin engine shut down.")
        } finally {
            callback.run()
        }
    }

    fun loadPlugins(callback: Runnable?) {
        val generation = lifecycleGeneration.get()
        PluginsController.runOnPluginsQueue {
            val currentPython = python
            if (currentPython == null || generation != lifecycleGeneration.get()) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback)
                }
                return@runOnPluginsQueue
            }

            try {
                val sys = currentPython.getModule("sys")
                val sysPath = sys["path"]
                val pluginsPath = getPluginsController().pluginsDir.absolutePath
                if (!pluginsSafeMode && sysPath != null &&
                    !sysPath.callAttr("__contains__", pluginsPath).toBoolean()
                ) {
                    sysPath.callAttr("append", pluginsPath)
                }
            } catch (e: PyException) {
                FileLog.e("Failed to setup Python environment for plugins", e)
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback)
                }
                return@runOnPluginsQueue
            }

            val pluginFiles = getPluginsController().pluginsDir.listFiles { _, name ->
                name.lowercase().endsWith(".py")
            }
            if (pluginFiles == null) {
                getPluginsController().notifyPluginsChanged()
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback)
                }
                return@runOnPluginsQueue
            }

            for (file in pluginFiles) {
                val pluginId = file.name.substring(0, file.name.length - 3)
                var validationResult: PluginsController.PluginValidationResult? = null
                try {
                    validationResult = validatePluginFromFile(file.absolutePath)
                    if (validationResult.error != null) {
                        throw Exception(validationResult.error)
                    }
                    loadPlugin(pluginId, file.absolutePath, validationResult.plugin)
                } catch (e: Throwable) {
                    FileLog.e("Failed to load plugin ${file.name}. Reason: ${e.message}", e)

                    val metadata = validationResult?.plugin
                    val plugin = if (metadata != null) {
                        Plugin(pluginId, metadata.getName()).apply {
                            setAuthor(metadata.getAuthor())
                            setDescription(metadata.getDescription())
                            setIcon(metadata.getIcon())
                            setVersion(metadata.getVersion())
                            setAppVersion(metadata.getAppVersion())
                            setSdkVersion(metadata.getSdkVersion())
                            setRequirements(metadata.getRequirements())
                            setEngine(metadata.getEngine())
                        }
                    } else {
                        Plugin(pluginId, pluginId).apply {
                            setAuthor(LocaleController.getString(R.string.PluginNoAuthor))
                            setVersion("1.0")
                            setEngine(PluginsConstants.PYTHON)
                        }
                    }

                    plugin.setError(e)
                    plugin.setEnabled(false)
                    getPluginsController().plugins[pluginId] = plugin
                }
            }

            getPluginsController().notifyPluginsChanged()
            val enabledCount =
                getPluginsController().plugins.values.count { it.isEnabled() }
            FileLog.d("Python plugin system initialized. Total: ${getPluginsController().plugins.size}, Enabled: $enabledCount")
            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback)
            }
        }
    }

    @Throws(Exception::class)
    fun loadPlugin(pluginId: String, filePath: String) {
        loadPlugin(pluginId, filePath, null, null)
    }

    @Throws(Exception::class)
    fun loadPlugin(pluginId: String, filePath: String, metadata: Plugin?) {
        loadPlugin(pluginId, filePath, metadata, null)
    }

    @Throws(Exception::class)
    fun loadPlugin(
        pluginId: String,
        filePath: String,
        metadata: Plugin?,
        delegate: PipController.InstallerDelegate?
    ) {
        val wasEnabled = getPluginsController().preferences.getBoolean(
            PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId,
            false
        )
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw Exception("Plugin file not found: $filePath")
        }

        var resolvedMetadata = metadata
        if (resolvedMetadata == null) {
            val validation = validatePluginFromFile(filePath)
            if (validation.error != null) {
                throw Exception(validation.error)
            }
            resolvedMetadata = validation.plugin
        }

        val pluginMetadata = requireNotNull(resolvedMetadata)
        if (pluginId != pluginMetadata.getId()) {
            throw Exception("Plugin ID mismatch. Expected: $pluginId, but found: ${pluginMetadata.getId()} in metadata.")
        }

        if (pluginInstances.containsKey(pluginId)) {
            unloadPlugin(pluginId)
        }

        pluginMetadata.setEnabled(false)
        pluginMetadata.setError(null)
        getPluginsController().plugins[pluginId] = pluginMetadata

        if (pluginsSafeMode) {
            return
        }

        if (!wasEnabled) {
            if (delegate != null) {
                installPluginDependencies(pluginId, pluginMetadata, delegate)
            }
            return
        }

        try {
            createPluginInstance(pluginId, filePath, pluginMetadata, delegate)
        } catch (t: Throwable) {
            getPluginsController().preferences.edit {
                putBoolean(PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId, false)
            }
            unloadPlugin(pluginId)
            throw t
        }
        setPluginEnabled(pluginId, true, null)
    }

    @Throws(Exception::class)
    private fun installPluginDependencies(
        pluginId: String,
        pluginMetadata: Plugin,
        delegate: PipController.InstallerDelegate?
    ) {
        val requirements = pluginMetadata.getRequirements()
        if (requirements.isNullOrEmpty()) {
            return
        }
        val paths = PipController.installDependencies(requirements, pluginId, delegate)

        val providedModules = paths.associateWith { topLevelModuleNames(File(it)) }
        val sdkModules = topLevelModuleNames(SDK_DIR)
        for ((path, modules) in providedModules) {
            val sdkCollisions = modules.intersect(sdkModules)
            if (sdkCollisions.isNotEmpty()) {
                throw Exception(
                    "Dependency ${File(path).name} of $pluginId provides ${sdkCollisions.joinToString()}, " +
                            "which would replace the plugins SDK module of the same name."
                )
            }
        }
        for ((path, modules) in providedModules) {
            disableShadowedPlugins(pluginId, File(path).name, modules)
        }

        val sysPath = getPython()?.getModule("sys")?.get("path")
        if (sysPath != null) {
            for (i in paths.size - 1 downTo 0) {
                val path = paths[i]
                removeFromSysPath(sysPath, path)
                sysPath.callAttr("insert", 0, path)
                dependencyPaths.add(path)
            }
        }
    }

    private fun disableShadowedPlugins(
        pluginId: String,
        dependencyName: String,
        providedModules: Set<String>
    ) {
        for (moduleName in providedModules) {
            if (moduleName == pluginId ||
                !File(getPluginsController().pluginsDir, "$moduleName.py").exists()
            ) {
                continue
            }

            FileLog.w("Disabling plugin '$moduleName': dependency $dependencyName of $pluginId now owns that module name")
            unloadPlugin(moduleName)
            getPluginsController().preferences.edit {
                putBoolean(PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + moduleName, false)
            }
            getPluginsController().plugins[moduleName]?.setError(
                Exception(
                    "Plugin id '$moduleName' is now provided by $dependencyName, a dependency of '$pluginId'. " +
                            "Rename or delete one of them."
                )
            )
        }
    }

    private fun removePluginDependencies(pluginId: String) {
        PipController.uninstallDependencies(pluginId)
        pruneDependencyPaths()
    }

    private fun pruneDependencyPaths() {
        if (dependencyPaths.isEmpty()) {
            return
        }
        val activePaths = PipController.activeLibraryPaths()
        val stalePaths = dependencyPaths.filter { !activePaths.contains(it) }
        if (stalePaths.isEmpty()) {
            return
        }

        try {
            val sys = python?.getModule("sys")
            val sysPath = sys?.get("path")
            val sysModules = sys?.get("modules")
            val pathImporterCache = sys?.get("path_importer_cache")
            for (path in stalePaths) {
                if (sysPath != null) {
                    removeFromSysPath(sysPath, path)
                }
                pathImporterCache?.callAttr("pop", path, null)
                if (sysModules != null) {
                    removeModulesUnderPath(sysModules, path)
                }
                dependencyPaths.remove(path)
            }
            python?.getModule("importlib")?.callAttr("invalidate_caches")
        } catch (e: PyException) {
            FileLog.e("Failed to drop unused dependency paths from the Python environment", e)
        }
    }

    private fun removeModulesUnderPath(sysModules: PyObject, path: String) {
        val currentPython = python ?: return
        val prefix = path + File.separator
        val canonicalPrefix = canonicalPathOrNull(path)?.plus(File.separator) ?: prefix
        val staleNames = ArrayList<String>()
        for (nameObject in currentPython.builtins.callAttr("list", sysModules).asList()) {
            val name = nameObject?.toString() ?: continue
            val module = sysModules.callAttr("get", name) ?: continue
            val moduleFile = module["__file__"]?.toString()
                ?: runCatching { module["__path__"]?.asList()?.firstOrNull()?.toString() }.getOrNull()
                ?: continue
            if (moduleFile.startsWith(prefix) ||
                canonicalPathOrNull(moduleFile)?.startsWith(canonicalPrefix) == true
            ) {
                staleNames.add(name)
            }
        }
        for (name in staleNames) {
            sysModules.callAttr("pop", name, null)
        }
        if (staleNames.isNotEmpty()) {
            FileLog.d("Unloaded ${staleNames.size} module(s) belonging to removed dependency ${File(path).name}")
        }
    }

    private fun removePluginPathsFromSysPath() {
        val sysPath = python?.getModule("sys")?.get("path") ?: return
        val paths = ArrayList(dependencyPaths)
        paths.add(getPluginsController().pluginsDir.absolutePath)
        for (path in paths) {
            removeFromSysPath(sysPath, path)
        }
        dependencyPaths.clear()
    }

    private fun removeFromSysPath(sysPath: PyObject, path: String) {
        while (sysPath.callAttr("__contains__", path).toBoolean()) {
            sysPath.callAttr("remove", path)
        }
    }

    @Throws(Exception::class)
    private fun createPluginInstance(
        pluginId: String,
        filePath: String,
        pluginMetadata: Plugin,
        delegate: PipController.InstallerDelegate?
    ) {
        val currentBasePluginClass = requireBasePluginClass()

        installPluginDependencies(pluginId, pluginMetadata, delegate)

        refreshImportCaches(pluginId, File(filePath).parentFile)

        PyObjectUtils.closeSafely(currentBasePluginClass["_begin_lifecycle"]?.call(pluginId))
        getPluginsController().watchdog.onPluginExecutionStarted(pluginId)
        val module = try {
            getPython()?.getModule(pluginId)
                ?: throw Exception("Failed to import plugin module: $pluginId")
        } catch (e: PyException) {
            throw Exception("Failed to import plugin module: ${e.message}", e)
        } finally {
            getPluginsController().watchdog.onPluginExecutionFinished(pluginId)
        }

        val moduleFile = module["__file__"]?.toString()
        if (canonicalPathOrNull(moduleFile) != canonicalPathOrNull(filePath)) {
            throw Exception(
                "Plugin id '$pluginId' is already taken by the Python module ${moduleFile ?: "built into the runtime"}. " +
                        "Change '__id__' to a name that no other module uses."
            )
        }

        val pluginClass = currentBasePluginClass.callAttr("_findPluginClass", module)
            ?: throw Exception("Could not find a class inheriting from BasePlugin in $pluginId.py. Make sure your main plugin class extends BasePlugin.")

        val pluginInstance = pluginClass.call()
        pluginInstance.put("id", pluginMetadata.getId())
        pluginInstance.put("name", pluginMetadata.getName())
        pluginInstance.put("description", pluginMetadata.getDescription())
        pluginInstance.put("author", pluginMetadata.getAuthor())
        pluginInstance.put("version", pluginMetadata.getVersion())
        pluginInstance.put("icon", pluginMetadata.getIcon())
        pluginInstance.put("app_version", pluginMetadata.getAppVersion())
        pluginInstance.put("sdk_version", pluginMetadata.getSdkVersion())
        pluginInstance.put("requirements", pluginMetadata.getRequirements())
        pluginInstance.put("enabled", false)
        pluginInstance.put("initialized", false)
        pluginInstance["error_message"] = null

        pluginInstances[pluginId] = pluginInstance
    }

    fun unloadPlugin(pluginId: String) {
        settingsCache.remove(pluginId)
        val pluginInstance = pluginInstances.remove(pluginId)
        getPluginsController().plugins[pluginId]?.setEnabled(false)
        try {
            PyObjectUtils.closeSafely(basePluginClass?.get("_stop_lifecycle")?.call(pluginId))
        } catch (t: Throwable) {
            FileLog.e("Failed to stop plugin tasks for $pluginId", t)
        }
        try {
            val wasInitialized = pluginInstance != null && PyObjectUtils.getBoolean(pluginInstance, "initialized", false)
            pluginInstance?.put("initialized", false)
            pluginInstance?.put("enabled", false)
            if (pluginInstance != null && wasInitialized) {
                getPluginsController().watchdog.onPluginExecutionStarted(pluginId)
                try {
                    if (pluginInstance["_invoke_plugin_unload"] != null) {
                        PyObjectUtils.closeSafely(pluginInstance.callAttr("_invoke_plugin_unload"))
                    } else {
                        PyObjectUtils.closeSafely(pluginInstance.callAttr(PluginsConstants.ON_PLUGIN_UNLOAD))
                    }
                } catch (e: Throwable) {
                    FileLog.e("Error during on_plugin_unload for $pluginId", e)
                } finally {
                    getPluginsController().watchdog.onPluginExecutionFinished(pluginId)
                }
            }
            releasePluginObjects(pluginId)
            File(getPluginsController().pluginsDir, "__pycache__").let { dir ->
                        if (!dir.exists() || !dir.isDirectory) return@let null else dir.listFiles()?.find { it.name.startsWith("$pluginId.cpython-") }
                    }?.let { deleteFileIfExists(it) }
            refreshImportCaches(pluginId, getPluginsController().pluginsDir)
        } catch (e: PyException) {
            FileLog.e("Failed to unload plugin $pluginId", e)
        } finally {
            runCatching {
                PyObjectUtils.closeSafely(basePluginClass?.get("_cleanup")?.call(pluginId))
            }.onFailure { FileLog.e("Failed to clean up SDK resources for $pluginId", it) }
            runCatching { getPluginsController().cleanupPlugin(pluginId) }
                .onFailure { FileLog.e("Failed to clean up plugin $pluginId", it) }
            runCatching { pluginInstance?.close() }
                .onFailure { FileLog.e("Failed to close plugin instance $pluginId", it) }
        }
    }

    private fun releasePluginObjects(pluginId: String) {
        val currentPython = python ?: return
        try {
            val release = currentPython.getModule("extera_utils.classes")["release_plugin"] ?: return
            release.call(pluginId)
        } catch (e: PyException) {
            FileLog.e("Failed to release objects of plugin $pluginId", e)
        }
    }

    private fun refreshImportCaches(pluginId: String, moduleDir: File?) {
        val currentPython = python ?: return
        try {
            val sys = currentPython.getModule("sys")
            val sysModules = sys["modules"]
            if (sysModules != null) {
                evictPluginModule(sysModules, pluginId, moduleDir)
            }

            val pathImporterCache = sys["path_importer_cache"]
            if (moduleDir != null && pathImporterCache != null) {
                pathImporterCache.callAttr("pop", moduleDir.absolutePath, null)
            }

            currentPython.getModule("importlib").callAttr("invalidate_caches")
        } catch (e: PyException) {
            FileLog.e("Failed to refresh import caches for $pluginId", e)
        }
    }

    private fun evictPluginModule(sysModules: PyObject, pluginId: String, moduleDir: File?) {
        val module = sysModules.callAttr("get", pluginId) ?: return
        val moduleFile = module["__file__"]?.toString()
        val pluginPath = if (moduleDir != null) {
            canonicalPathOrNull(File(moduleDir, "$pluginId.py").absolutePath)
        } else {
            null
        }
        if (pluginPath == null || canonicalPathOrNull(moduleFile) != pluginPath) {
            FileLog.w("Keeping module '$pluginId' loaded: it belongs to ${moduleFile ?: "the Python runtime"}, not to a plugin")
            return
        }
        sysModules.callAttr("pop", pluginId, null)
    }

    override fun setPluginEnabled(
        pluginId: String,
        enabled: Boolean,
        callback: Utilities.Callback<String>?
    ) {
        try {
            val plugin = getPluginsController().plugins[pluginId]
                ?: throw Exception("Plugin not found: $pluginId")
            if (enabled && pluginsSafeMode) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread {
                        callback.run(SAFE_MODE_ENABLE_ERROR)
                    }
                }
                return
            }

            var pluginInstance = pluginInstances[pluginId]
            if (enabled && pluginInstance == null) {
                createPluginInstance(pluginId, getPluginPath(pluginId), plugin, null)
                pluginInstance = pluginInstances[pluginId]
                    ?: throw Exception("Failed to create plugin instance: $pluginId")
            }

            val isInitialized = pluginInstance != null &&
                    PyObjectUtils.getBoolean(pluginInstance, "initialized", false)
            if (isInitialized == enabled && !plugin.hasError()) {
                callback?.run(null)
                return
            }

            if (enabled) {
                val activeInstance = requireNotNull(pluginInstance)
                getPluginsController().cleanupPlugin(pluginId)
                getPluginsController().watchdog.onPluginExecutionStarted(pluginId)
                try {
                    activeInstance.callAttr(PluginsConstants.ON_PLUGIN_LOAD)
                } finally {
                    getPluginsController().watchdog.onPluginExecutionFinished(pluginId)
                }
                activeInstance.put("initialized", true)
                activeInstance["error_message"] = null
                plugin.setError(null)
                activeInstance.put("enabled", true)
                plugin.setEnabled(true)
                getPluginsController().preferences.edit {
                    putBoolean(PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId, true)
                }
                getPluginsController().loadPluginSettings(pluginId)
            } else {
                getPluginsController().preferences.edit {
                    putBoolean(PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId, false)
                }
                unloadPlugin(pluginId)
            }

            getPluginsController().notifyPluginsChanged()
            if (callback != null) {
                AndroidUtilities.runOnUIThread { callback.run(null) }
            }
        } catch (t: Throwable) {
            FileLog.e("Unexpected error setting enabled state for $pluginId", t)
            if (enabled) {
                getPluginsController().plugins[pluginId]?.setError(t)
            }
            getPluginsController().preferences.edit {
                putBoolean(PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId, false)
            }
            unloadPlugin(pluginId)
            if (callback != null) {
                AndroidUtilities.runOnUIThread { callback.run(AppUtils.stackTraceToString(t)) }
            }
        }
    }

    override fun deletePlugin(pluginId: String, callback: Utilities.Callback<String>?) {
        unloadPlugin(pluginId)

        try {
            removePluginDependencies(pluginId)
        } catch (e: Exception) {
            FileLog.e("Failed to uninstall dependencies for $pluginId", e)
        }

        val pluginFile = File(getPluginsController().pluginsDir, "$pluginId.py")
        deleteFileIfExists(pluginFile)

        if (PluginsController.isPluginPinned(pluginId)) {
            PluginsController.setPluginPinned(pluginId, false)
        }
        getPluginsController().clearPluginSettingsPreferences(pluginId, true)
        getPluginsController().plugins.remove(pluginId)
        getPluginsController().notifyPluginsChanged()

        if (callback != null) {
            AndroidUtilities.runOnUIThread { callback.run(null) }
        }
    }

    override fun getPluginPath(id: String): String {
        return getPluginsController().pluginsDir.absolutePath + File.separator + id + ".py"
    }

    override fun canOpenInExternalApp(): Boolean = true

    override fun openInExternalApp(id: String) {
        val fragment = LaunchActivity.getSafeLastFragment() ?: return
        val sourceFilePath = getPluginPath(id)
        val file = File(sourceFilePath)
        if (file.exists()) {
            PluginFileViewer.open(fragment, file, id + PluginsConstants.PLUGINS_EXT)
        }
    }

    override fun sharePlugin(id: String) {
        val fragment = LaunchActivity.getSafeLastFragment() ?: return
        val context = fragment.parentActivity ?: return
        val sourceFilePath = getPluginPath(id)
        val tempDir = File(ApplicationLoader.getFilesDirFixed(), "temp")
        SimpliFiles.directory(tempDir).create()

        val tempFile = File(tempDir, id + PluginsConstants.PLUGINS_EXT)
        val uri: Uri = try {
            SimpliFiles.file(sourceFilePath).copyTo(tempFile, OverwritePolicy.REPLACE)
            FileProvider.getUriForFile(
                context,
                ApplicationLoader.getApplicationId() + ".provider",
                tempFile
            )
        } catch (e: IOException) {
            FileLog.e(e)
            return
        } catch (e: IllegalArgumentException) {
            FileLog.e(e)
            return
        } catch (e: Exception) {
            FileLog.e(e)
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/x-plugin"
        }
        fragment.startActivityForResult(
            Intent.createChooser(
                shareIntent,
                LocaleController.getString(R.string.ShareFile)
            ), 500
        )
        tempFile.deleteOnExit()
    }

    fun loadPluginFromFile(
        filePath: String,
        pluginMetadata: Plugin?,
        callback: Utilities.Callback<String>?
    ) {
        loadPluginFromFile(filePath, pluginMetadata, callback, null)
    }

    override fun loadPluginFromFile(
        filePath: String,
        pluginMetadata: Plugin?,
        callback: Utilities.Callback<String>?,
        delegate: PipController.InstallerDelegate?
    ) {
        PluginsController.runOnPluginsQueue {
            var pluginId: String? = null
            var pluginFile: File? = null
            var backupFile: File? = null
            var isUpdate = false
            var wasEnabledBeforeUpdate = false

            try {
                val metadata = if (pluginMetadata == null) {
                    val validation = validatePluginFromFile(filePath)
                    if (validation.error != null) {
                        throw Exception(validation.error)
                    }
                    requireNotNull(validation.plugin)
                } else {
                    pluginMetadata
                }

                pluginId = metadata.getId()
                wasEnabledBeforeUpdate = getPluginsController().preferences.getBoolean(
                    PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId,
                    false
                )
                pluginFile = File(getPluginsController().pluginsDir, "$pluginId.py")
                val currentPluginFile = pluginFile
                isUpdate = currentPluginFile.exists()

                if (isUpdate) {
                    unloadPlugin(pluginId)
                    backupFile = File(getPluginsController().pluginsDir, "$pluginId.py.bak")
                    val currentBackupFile = backupFile
                    deleteFileIfExists(currentBackupFile)
                    SimpliFiles.file(currentPluginFile).moveTo(currentBackupFile, OverwritePolicy.ERROR)
                    removePluginDependencies(pluginId)
                }

                if (!File(filePath).exists()) {
                    throw Exception("Plugin file is no longer available. It may have been moved or deleted.")
                }

                SimpliFiles.file(filePath).copyTo(currentPluginFile, OverwritePolicy.REPLACE)

                loadPlugin(pluginId, currentPluginFile.absolutePath, metadata, delegate)

                val currentBackupFile = backupFile
                deleteFileIfExists(currentBackupFile)

                getPluginsController().notifyPluginsChanged()
                if (callback != null) {
                    AndroidUtilities.runOnUIThread { callback.run(null) }
                }
            } catch (t: Throwable) {
                FileLog.e("Unexpected error loading plugin from file: $filePath", t)
                if (pluginId != null) {
                    val currentPluginFile = pluginFile
                    val currentBackupFile = backupFile
                    deleteFileIfExists(currentPluginFile)

                    var fileRestored = false
                    if (isUpdate && currentBackupFile != null && currentBackupFile.exists() && currentPluginFile != null) {
                        try {
                            SimpliFiles.file(currentBackupFile).moveTo(currentPluginFile, OverwritePolicy.REPLACE)
                            fileRestored = true
                            getPluginsController().preferences.edit {
                                putBoolean(
                                    PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId,
                                    wasEnabledBeforeUpdate
                                )
                            }
                            try {
                                loadPlugin(pluginId, currentPluginFile.absolutePath)
                            } catch (reloadEx: Exception) {
                                FileLog.e(
                                    "Failed to reload original plugin after update failure for $pluginId",
                                    reloadEx
                                )
                                getPluginsController().plugins[pluginId]?.setError(reloadEx)
                            }
                        } catch (e: Exception) {
                            FileLog.e("Failed to restore backup for plugin $pluginId", e)
                        }
                    }

                    if (!fileRestored) {
                        getPluginsController().cleanupPlugin(pluginId)
                        try {
                            removePluginDependencies(pluginId)
                        } catch (e: Exception) {
                            FileLog.e(e)
                        }
                        val removed = pluginInstances.remove(pluginId)
                        removed?.close()
                        getPluginsController().clearPluginSettingsPreferences(pluginId, true)
                        getPluginsController().plugins.remove(pluginId)
                        deleteFileIfExists(currentPluginFile)
                    }
                }
                getPluginsController().notifyPluginsChanged()
                if (callback != null) {
                    AndroidUtilities.runOnUIThread { callback.run(AppUtils.stackTraceToString(t)) }
                }
            }
        }
    }

    private fun findModuleNameOwner(pluginId: String): String? {
        if (topLevelModuleNames(SDK_DIR).contains(pluginId)) {
            return "the plugins SDK"
        }

        for (path in PipController.activeLibraryPaths()) {
            val dependencyDir = File(path)
            if (topLevelModuleNames(dependencyDir).contains(pluginId)) {
                return "the installed dependency ${dependencyDir.name}"
            }
        }

        val currentPython = getPython() ?: return null
        return try {
            val spec = currentPython.getModule("importlib.util").callAttr("find_spec", pluginId)
                ?: return null
            val origin = spec["origin"]?.toString()
            if (origin != null) {
                val pluginPath = canonicalPathOrNull(
                    File(getPluginsController().pluginsDir, "$pluginId.py").absolutePath
                )
                return if (canonicalPathOrNull(origin) == pluginPath) null else origin
            }

            val locations = spec["submodule_search_locations"] ?: return "another Python module"
            val dataDir = canonicalPathOrNull(
                File(getPluginsController().pluginsDir, pluginId).absolutePath
            )
            currentPython.builtins.callAttr("list", locations).asList()
                .map { it.toString() }
                .firstOrNull { canonicalPathOrNull(it) != dataDir }
        } catch (e: PyException) {
            FileLog.w("Treating plugin id '$pluginId' as taken: ${e.message}")
            "another Python module"
        } catch (e: Throwable) {
            FileLog.e("Failed to check whether plugin id '$pluginId' is free", e)
            null
        }
    }

    fun validatePluginFromFile(filePath: String): PluginsController.PluginValidationResult {
        val pluginFile = File(filePath)
        if (!pluginFile.exists()) {
            return PluginsController.PluginValidationResult(null, "Plugin file not found.")
        }

        try {
            val metadataMap = parsePluginMetadata(filePath)
            val id = metadataMap["id"]
            val name = metadataMap["name"]

            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) {
                return PluginsController.PluginValidationResult(
                    null,
                    "Plugin metadata must contain non-empty '__id__' and '__name__'."
                )
            }

            if (!requireNotNull(id).matches(Regex("^[a-zA-Z][a-zA-Z0-9_-]{1,31}$"))) {
                return PluginsController.PluginValidationResult(
                    null,
                    "Plugin '__id__' must be 2-32 characters long, start with a letter, and contain only latin letters, numbers, dashes and underscores."
                )
            }

            val moduleOwner = findModuleNameOwner(id)
            if (moduleOwner != null) {
                return PluginsController.PluginValidationResult(
                    null,
                    "Plugin '__id__' is already used as a Python module name by $moduleOwner. Choose a different '__id__'."
                )
            }

            val appVersion = metadataMap["app_version"]
            if (appVersion != null) {
                val matcher = VERSION_PATTERN.matcher(appVersion)
                if (!matcher.matches()) {
                    return PluginsController.PluginValidationResult(
                        null,
                        "Invalid appVersion: $appVersion"
                    )
                }
                val operator = matcher.group(1) ?: return PluginsController.PluginValidationResult(
                    null,
                    "Invalid appVersion: $appVersion"
                )
                val targetVersion = matcher.group(2)?.trim()
                    ?: return PluginsController.PluginValidationResult(
                        null,
                        "Invalid appVersion: $appVersion"
                    )
                if (!AppUtils.compareVersions(
                        operator,
                        BuildVars.BUILD_VERSION_STRING,
                        targetVersion
                    )
                ) {
                    return PluginsController.PluginValidationResult(
                        null,
                        "Plugin requires app version $appVersion, but current is ${BuildVars.BUILD_VERSION_STRING}"
                    )
                }
            }

            val sdkVersion = metadataMap["sdk_version"]
            if (sdkVersion != null) {
                val matcher = VERSION_PATTERN.matcher(sdkVersion)
                if (!matcher.matches()) {
                    return PluginsController.PluginValidationResult(
                        null,
                        "Invalid sdkVersion: $sdkVersion"
                    )
                }
                val operator = matcher.group(1) ?: return PluginsController.PluginValidationResult(
                    null,
                    "Invalid sdkVersion: $sdkVersion"
                )
                val targetVersion = matcher.group(2)?.trim()
                    ?: return PluginsController.PluginValidationResult(
                        null,
                        "Invalid sdkVersion: $sdkVersion"
                    )
                if (!AppUtils.compareVersions(operator, SDK_VERSION, targetVersion)) {
                    return PluginsController.PluginValidationResult(
                        null,
                        "Plugin requires sdk version $sdkVersion, but current is $SDK_VERSION"
                    )
                }
            }

            val metadata = Plugin(id, requireNotNull(name))
            metadata.setEngine(PluginsConstants.PYTHON)
            metadata.setAuthor(
                metadataMap["author"] ?: LocaleController.getString(R.string.PluginNoAuthor)
            )
            metadata.setDescription(
                metadataMap["description"]
                    ?: LocaleController.getString(R.string.PluginNoDescription)
            )
            metadata.setIcon(metadataMap["icon"])
            metadata.setVersion(metadataMap["version"] ?: "1.0")
            metadata.setAppVersion(appVersion)
            metadata.setSdkVersion(sdkVersion)

            val requirements = metadataMap["requirements"]
            if (!requirements.isNullOrEmpty()) {
                metadata.setRequirements(
                    requirements.split(Regex(",(?!\\s*[<>=!~(])"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                )
            }
            metadata.setEnabled(
                getPluginsController().preferences.getBoolean(
                    PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + id,
                    false
                )
            )
            return PluginsController.PluginValidationResult(metadata, null)
        } catch (e: PyException) {
            FileLog.e("Failed to parse metadata from $filePath. Error: ${e.message}", e)
            return PluginsController.PluginValidationResult(null, e.message)
        } catch (e: Throwable) {
            FileLog.e("Unexpected error validating plugin $filePath", e)
            return PluginsController.PluginValidationResult(null, e.message)
        }
    }

    fun parsePySettingDefinitions(pyDefinitionsList: List<PyObject>): List<SettingItem> {
        val javaSettings = ArrayList<SettingItem>(pyDefinitionsList.size)
        for (defMap in pyDefinitionsList) {
            val itemType = PyObjectUtils.getString(defMap, PluginsConstants.Settings.TYPE, null)
            if (itemType == null) {
                FileLog.w("A setting item in a plugin is missing its 'type'. Skipping.")
                continue
            }

            val itemKey = PyObjectUtils.getString(defMap, PluginsConstants.Settings.KEY, null)
            val itemText = PyObjectUtils.getString(defMap, PluginsConstants.Settings.TEXT, null)
            val itemSubtext =
                PyObjectUtils.getString(defMap, PluginsConstants.Settings.SUBTEXT, null)
            val itemIcon = PyObjectUtils.getString(defMap, PluginsConstants.Settings.ICON, null)
            val itemOnChange = defMap[PluginsConstants.Settings.ON_CHANGE]
            val itemOnLongClick = defMap[PluginsConstants.Settings.ON_LONG_CLICK]
            val itemLinkAlias =
                PyObjectUtils.getString(defMap, PluginsConstants.Settings.LINK_ALIAS, null)
            val itemDefault = defMap[PluginsConstants.Settings.DEFAULT]
            val itemOnClick = defMap[PluginsConstants.Settings.ON_CLICK]
            val createSubFragment = defMap[PluginsConstants.Settings.CREATE_SUB_FRAGMENT]

            val currentJavaItem = when (itemType) {
                PluginsConstants.Settings.TYPE_HEADER -> {
                    if (itemText != null) HeaderSetting(itemText) else null
                }

                PluginsConstants.Settings.TYPE_SWITCH -> {
                    if (itemKey != null && itemText != null && itemDefault != null) {
                        SwitchSetting(
                            itemKey,
                            itemText,
                            itemDefault.toBoolean(),
                            itemSubtext,
                            itemIcon,
                            itemOnChange,
                            itemOnLongClick,
                            itemLinkAlias
                        )
                    } else {
                        null
                    }
                }

                PluginsConstants.Settings.TYPE_SELECTOR -> {
                    val itemsList =
                        PyObjectUtils.getStringArray(defMap, PluginsConstants.Settings.ITEMS, null)
                    if (itemKey != null && itemText != null && !itemsList.isNullOrEmpty() && itemDefault != null) {
                        SelectorSetting(
                            itemKey,
                            itemText,
                            itemDefault.toInt(),
                            itemsList,
                            itemIcon,
                            itemOnChange,
                            itemOnLongClick,
                            itemLinkAlias
                        )
                    } else {
                        null
                    }
                }

                PluginsConstants.Settings.TYPE_INPUT -> {
                    if (itemKey != null && itemText != null) {
                        InputSetting(
                            itemKey,
                            itemText,
                            itemDefault?.toString() ?: "",
                            itemSubtext,
                            itemIcon,
                            itemOnChange,
                            itemOnLongClick,
                            itemLinkAlias
                        )
                    } else {
                        null
                    }
                }

                PluginsConstants.Settings.TYPE_EDIT_TEXT -> {
                    val hint = PyObjectUtils.getString(defMap, PluginsConstants.Settings.HINT, null)
                    val multiline =
                        PyObjectUtils.getBoolean(defMap, PluginsConstants.Settings.MULTILINE, false)
                    val maxLength =
                        PyObjectUtils.getInt(defMap, PluginsConstants.Settings.MAX_LENGTH, 256)
                    val mask = PyObjectUtils.getString(defMap, PluginsConstants.Settings.MASK, null)
                    if (itemKey != null && hint != null) {
                        EditTextSetting(
                            itemKey,
                            hint,
                            itemDefault?.toString() ?: "",
                            multiline,
                            maxLength,
                            mask,
                            itemOnChange
                        )
                    } else {
                        null
                    }
                }

                PluginsConstants.Settings.TYPE_TEXT -> {
                    val accent =
                        PyObjectUtils.getBoolean(defMap, PluginsConstants.Settings.ACCENT, false)
                    val red = PyObjectUtils.getBoolean(defMap, PluginsConstants.Settings.RED, false)
                    if (itemText != null) {
                        TextSetting(
                            itemText,
                            itemSubtext,
                            itemIcon,
                            accent,
                            red,
                            itemOnClick,
                            createSubFragment,
                            itemOnLongClick,
                            itemLinkAlias
                        )
                    } else {
                        null
                    }
                }

                PluginsConstants.Settings.TYPE_DIVIDER -> DividerSetting(itemText)
                PluginsConstants.Settings.TYPE_CUSTOM -> {
                    val view = defMap[PluginsConstants.Settings.VIEW]
                    val item = defMap[PluginsConstants.Settings.ITEM]
                    val factory = defMap[PluginsConstants.Settings.FACTORY]
                    val args = defMap[PluginsConstants.Settings.FACTORY_ARGS]
                    when {
                        factory != null -> {
                            val javaFactory = PyObjectUtils.toJavaCompat(
                                factory,
                                CustomSetting.Factory::class.java
                            )
                            if (javaFactory == null) {
                                null
                            } else if (args == null) {
                                CustomSetting(
                                    javaFactory,
                                    itemOnClick,
                                    createSubFragment,
                                    itemOnLongClick,
                                    itemLinkAlias
                                )
                            } else {
                                CustomSetting(
                                    javaFactory,
                                    args,
                                    itemOnClick,
                                    createSubFragment,
                                    itemOnLongClick,
                                    itemLinkAlias
                                )
                            }
                        }

                        item != null -> {
                            val javaItem = PyObjectUtils.toJavaCompat(item, UItem::class.java)
                            if (javaItem != null) {
                                CustomSetting(
                                    javaItem,
                                    itemOnClick,
                                    createSubFragment,
                                    itemOnLongClick,
                                    itemLinkAlias
                                )
                            } else {
                                null
                            }
                        }

                        view != null -> {
                            val javaView = PyObjectUtils.toJavaCompat(view, View::class.java)
                            if (javaView != null) {
                                CustomSetting(
                                    javaView,
                                    itemOnClick,
                                    createSubFragment,
                                    itemOnLongClick,
                                    itemLinkAlias
                                )
                            } else {
                                null
                            }
                        }

                        else -> null
                    }
                }

                else -> null
            }

            if (currentJavaItem != null) {
                javaSettings.add(currentJavaItem)
            }
        }
        return javaSettings
    }

    override fun loadPluginSettings(id: String): List<SettingItem>? {
        try {
            val currentPluginState = getPluginsController().plugins[id]
            val pluginInstance = pluginInstances[id]
            if (!getPluginsController().isPluginActive(currentPluginState) || pluginInstance == null) {
                getPluginsController().invalidatePluginSettings(id)
                return null
            }

            val pyDefinitions =
                pluginInstance.callAttr(PluginsConstants.CREATE_SETTINGS) ?: return null
            val pyDefinitionsList = pyDefinitions.asList()
            if (pyDefinitionsList.isEmpty()) {
                return null
            }
            return parsePySettingDefinitions(pyDefinitionsList)
        } catch (e: Exception) {
            FileLog.e("Failed to load plugin settings", e)
            return null
        }
    }

    override fun executeOnAppEvent(eventType: String) {
        if (!sdkInitialized || pluginsSafeMode) {
            return
        }
        val appEventClass = getPython()?.getModule("base_plugin")?.get("AppEvent") ?: return
        val appEvent = appEventClass.call(eventType)
        try {
            val currentDebuggerListener = debuggerListener
            if (currentDebuggerListener != null) {
                try {
                    currentDebuggerListener.callAttr(PluginsConstants.ON_APP_EVENT, appEvent)
                } catch (e: PyException) {
                    FileLog.e("Failed to execute app event for debugger listener", e)
                }
            }

            for ((pluginId, instance) in pluginInstances.entries) {
                if (getPluginsController().isPluginActive(pluginId)) {
                    getPluginsController().watchdog.onPluginExecutionStarted(pluginId)
                    try {
                        instance.callAttr(PluginsConstants.ON_APP_EVENT, appEvent)
                    } catch (e: PyException) {
                        FileLog.e("Failed to execute app $eventType for $pluginId", e)
                    } finally {
                        getPluginsController().watchdog.onPluginExecutionFinished(pluginId)
                    }
                }
            }
        } finally {
            PyObjectUtils.closeSafely(appEvent)
        }
    }

    private fun interface PyMethodCaller<T> {
        @Throws(PyException::class)
        fun call(instance: PyObject, value: T?): PyObject?
    }

    private fun <T> executeHook(
        pluginInstance: PyObject?,
        initialValue: T?,
        valueClass: Class<T>,
        pyResultKey: String,
        caller: PyMethodCaller<T>,
        errorLogger: Utilities.Callback<Throwable>
    ): PluginsController.HookResult<T> {
        var modifiedValue = initialValue
        if (pluginInstance != null) {
            try {
                val pyResult = caller.call(pluginInstance, modifiedValue)
                if (pyResult != null) {
                    try {
                        val strategy = PyObjectUtils.getString(
                            pyResult,
                            PluginsConstants.STRATEGY,
                            PluginsConstants.Strategy.DEFAULT
                        )
                        if (strategy != null && strategy.endsWith(PluginsConstants.Strategy.CANCEL)) {
                            return PluginsController.HookResult(null, cancel = true, isFinal = false)
                        } else if (strategy != null && (strategy.endsWith(PluginsConstants.Strategy.MODIFY) || strategy.endsWith(
                                PluginsConstants.Strategy.MODIFY_FINAL
                            ))
                        ) {
                            val modifiedObj = pyResult[pyResultKey]
                            if (modifiedObj != null) {
                                try {
                                    modifiedValue = modifiedObj.toJava(valueClass)
                                } catch (e: ClassCastException) {
                                    errorLogger.run(e)
                                }
                            }
                            if (strategy.endsWith(PluginsConstants.Strategy.MODIFY_FINAL)) {
                                return PluginsController.HookResult(
                                    modifiedValue,
                                    cancel = false,
                                    isFinal = true
                                )
                            }
                        }
                    } finally {
                        PyObjectUtils.closeSafely(pyResult)
                    }
                }
            } catch (e: Throwable) {
                errorLogger.run(e)
            }
        }
        return PluginsController.HookResult(modifiedValue, cancel = false, isFinal = false)
    }

    private fun <T> executeHook(
        pluginId: String,
        initialValue: T?,
        valueClass: Class<T>,
        pyResultKey: String,
        caller: PyMethodCaller<T>,
        errorLogger: Utilities.Callback<Throwable>
    ): PluginsController.HookResult<T> {
        return executeHook(
            pluginInstances[pluginId],
            initialValue,
            valueClass,
            pyResultKey,
            caller,
            errorLogger
        )
    }

    override fun executePreRequestHook(
        requestName: String,
        account: Int,
        request: TLObject?,
        pluginId: String
    ): PluginsController.HookResult<TLObject> {
        return executeHook(
            pluginId,
            request,
            TLObject::class.java,
            PluginsConstants.REQUEST,
            { instance, value ->
                instance.callAttr(
                    "pre_request_hook",
                    requestName,
                    account,
                    value
                )
            },
            { e ->
                FileLog.e(
                    "Failed to execute pre_request_hook in $pluginId for $requestName",
                    e
                )
            }
        )
    }

    fun executePostRequestHook(
        requestName: String,
        account: Int,
        response: TLObject?,
        error: TLRPC.TL_error?,
        pluginInstance: PyObject?
    ): PluginsController.HookResult<PluginsHooks.PostRequestResult> {
        var modifiedResponse = response
        var modifiedError = error

        if (pluginInstance != null) {
            try {
                val pyResult = pluginInstance.callAttr(
                    "post_request_hook",
                    requestName,
                    account,
                    modifiedResponse,
                    modifiedError
                )
                if (pyResult != null) {
                    try {
                        val strategy = PyObjectUtils.getString(pyResult, PluginsConstants.STRATEGY, "")
                        if (strategy != null && (strategy.endsWith(PluginsConstants.Strategy.MODIFY) || strategy.endsWith(
                                PluginsConstants.Strategy.MODIFY_FINAL
                            ))
                        ) {
                            val modifiedResponseObj = pyResult[PluginsConstants.RESPONSE]
                            if (modifiedResponseObj != null) {
                                try {
                                    modifiedResponse = modifiedResponseObj.toJava(TLObject::class.java)
                                } catch (e: ClassCastException) {
                                    FileLog.e("Bad response type from post_request_hook for $requestName", e)
                                }
                            }
                            val modifiedErrorObj = pyResult[PluginsConstants.ERROR]
                            if (modifiedErrorObj != null) {
                                try {
                                    modifiedError = modifiedErrorObj.toJava(TLRPC.TL_error::class.java)
                                } catch (e: ClassCastException) {
                                    FileLog.e("Bad error type from post_request_hook for $requestName", e)
                                }
                            }
                            if (strategy.endsWith(PluginsConstants.Strategy.MODIFY_FINAL)) {
                                return PluginsController.HookResult(
                                    PluginsHooks.PostRequestResult(
                                        modifiedResponse,
                                        modifiedError
                                    ), cancel = false, isFinal = true
                                )
                            }
                        }
                    } finally {
                        PyObjectUtils.closeSafely(pyResult)
                    }
                }
            } catch (e: Throwable) {
                FileLog.e("Failed to execute post_request_hook for $requestName", e)
            }
        }

        return PluginsController.HookResult(
            PluginsHooks.PostRequestResult(
                modifiedResponse,
                modifiedError
            ), false, isFinal = false
        )
    }

    override fun executePostRequestHook(
        requestName: String,
        account: Int,
        response: TLObject?,
        error: TLRPC.TL_error?,
        pluginId: String
    ): PluginsController.HookResult<PluginsHooks.PostRequestResult> {
        return executePostRequestHook(requestName, account, response, error, pluginInstances[pluginId])
    }

    override fun executeUpdateHook(
        updateName: String,
        account: Int,
        update: TLRPC.Update?,
        pluginId: String
    ): PluginsController.HookResult<TLRPC.Update> {
        return executeHook(
            pluginId,
            update,
            TLRPC.Update::class.java,
            PluginsConstants.UPDATE,
            { instance, value ->
                instance.callAttr(
                    "on_update_hook",
                    updateName,
                    account,
                    value
                )
            },
            { e ->
                FileLog.e(
                    "Failed to execute on_update_hook for $updateName",
                    e
                )
            }
        )
    }

    override fun executeUpdatesHook(
        containerName: String,
        account: Int,
        updates: TLRPC.Updates?,
        pluginId: String
    ): PluginsController.HookResult<TLRPC.Updates> {
        return executeHook(
            pluginId,
            updates,
            TLRPC.Updates::class.java,
            PluginsConstants.UPDATES,
            { instance, value ->
                instance.callAttr(
                    "on_updates_hook",
                    containerName,
                    account,
                    value
                )
            },
            { e ->
                FileLog.e(
                    "Failed to execute on_updates_hook for $containerName",
                    e
                )
            }
        )
    }

    override fun executeSendMessageHook(
        account: Int,
        params: SendMessagesHelper.SendMessageParams?,
        pluginId: String
    ): PluginsController.HookResult<SendMessagesHelper.SendMessageParams> {
        return executeHook(
            pluginId,
            params,
            SendMessagesHelper.SendMessageParams::class.java,
            PluginsConstants.PARAMS,
            { instance, value ->
                instance.callAttr(
                    "on_send_message_hook",
                    account,
                    value
                )
            },
            { e ->
                FileLog.e(
                    "Failed to execute on_send_message_hook for $pluginId",
                    e
                )
            }
        )
    }

    fun fetchParameterValue(filePath: String?, parameterName: String): String? {
        if (filePath == null) {
            return null
        }
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                return null
            }
            parsePluginMetadata(filePath)[parameterName]
        } catch (_: Exception) {
            null
        }
    }

    override fun parsePluginMetadata(filePath: String?): Map<String, String> {
        val metadata = HashMap<String, String>()
        if (filePath == null) {
            return metadata
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return metadata
        }

        val currentPython = getPython()
        if (currentPython == null) {
            FileLog.e("Python engine not initialized, cannot parse metadata for $filePath")
            return metadata
        }

        try {
            val metadataParser = currentPython.getModule("extera_utils.metadata_parser")
            val pyMetadataDict = metadataParser.callAttr("get_metadata", filePath)
            if (pyMetadataDict != null) {
                for ((key, value) in pyMetadataDict.asMap()) {
                    metadata[key.toString()] = value.toString()
                }
            }
        } catch (e: PyException) {
            FileLog.e("Failed to parse metadata from $filePath. Error: ${e.message}", e)
            throw e
        }
        return metadata
    }

    override fun getPluginSetting(pluginId: String, key: String, defaultValue: Any?): Any? {
        val pluginCache = settingsCache[pluginId]
        if (pluginCache != null && pluginCache.containsKey(key)) {
            return pluginCache[key]
        }

        val currentPython = getPython() ?: return defaultValue
        try {
            val pySettingsModule = currentPython.getModule("plugin_settings")
            val result = pySettingsModule.callAttr("get_setting", pluginId, key, defaultValue)
                ?: return defaultValue
            val javaResult: Any = when (defaultValue) {
                is Boolean -> result.toBoolean()
                is Int -> result.toInt()
                is String -> result.toString()
                is Float -> result.toFloat()
                is Long -> result.toLong()
                null -> result.toJava(Any::class.java)
                else -> result.toJava(defaultValue.javaClass)
            }
            settingsCache.computeIfAbsent(pluginId) { ConcurrentHashMap() }[key] = javaResult
            return javaResult
        } catch (e: PyException) {
            FileLog.e("Failed to get plugin setting $pluginId/$key", e)
            return defaultValue
        }
    }

    override fun setPluginSetting(pluginId: String, key: String, value: Any?) {
        settingsCache.computeIfAbsent(pluginId) { ConcurrentHashMap() }[key] = value
        val currentPython = getPython() ?: return
        try {
            val pySettingsModule = currentPython.getModule("plugin_settings")
            pySettingsModule.callAttr("set_setting", pluginId, key, value)
        } catch (e: PyException) {
            FileLog.e("Failed to set plugin setting $pluginId/$key", e)
        }
    }

    override fun clearPluginSettings(pluginId: String) {
        settingsCache.remove(pluginId)
        val currentPython = getPython() ?: return
        try {
            val pySettingsModule = currentPython.getModule("plugin_settings")
            pySettingsModule.callAttr("clear_settings", pluginId)
        } catch (e: PyException) {
            FileLog.e("Failed to clear plugin settings for $pluginId", e)
        }
    }

    override fun getAllPluginSettings(pluginId: String): Map<String, *>? {
        val currentPython = getPython() ?: return null
        try {
            val pySettingsModule = currentPython.getModule("plugin_settings")
            val result = pySettingsModule.callAttr("get_all_settings", pluginId)
            if (result != null) {
                val javaMap = HashMap<String, Any?>()
                for ((key, value) in result.asMap()) {
                    if (key != null) {
                        javaMap[key.toString()] = value?.toJava(Any::class.java)
                    }
                }
                settingsCache[pluginId] = ConcurrentHashMap(javaMap)
                return javaMap
            }
        } catch (e: PyException) {
            FileLog.e("Failed to get all plugin settings for $pluginId", e)
        }
        return null
    }

    override fun showInstallDialog(
        fragment: BaseFragment,
        params: InstallPluginBottomSheet.PluginInstallParams
    ) {
        val activity = fragment.parentActivity ?: return
        val account = fragment.currentAccount
        val filePath = params.filePath
        PluginsController.runOnPluginsQueue {
            val validationResult = validatePluginFromFile(filePath)
            val fileName = File(filePath).name
            AndroidUtilities.runOnUIThread {
                if (fragment.isFinished || fragment.isPaused || fragment.parentActivity !== activity ||
                    fragment.currentAccount != account || !AndroidUtilities.isActivityRunning(activity)) {
                    return@runOnUIThread
                }
                if (validationResult.plugin != null) {
                    InstallPluginBottomSheet(fragment, validationResult, params).show()
                } else {
                    BulletinFactory.of(fragment).createSimpleBulletin(
                        R.raw.error,
                        LocaleController.formatString(R.string.PluginInstallError, fileName),
                        LocaleUtils.createCopySpan(fragment)
                    ) {
                        if (AndroidUtilities.addToClipboard(validationResult.error)) {
                            BulletinFactory.of(fragment)
                                .createCopyBulletin(LocaleController.getString(R.string.TextCopied))
                                .show()
                        }
                    }.show()
                }
            }
        }
    }

    override fun openPluginSettings(id: String, fragment: BaseFragment) {
        val plugin = getPluginsController().plugins[id]
        if (plugin != null) {
            openPluginSettings(plugin, fragment)
        }
    }

    override fun openPluginSettings(plugin: Plugin, fragment: BaseFragment) {
        AndroidUtilities.runOnUIThread {
            fragment.presentFragment(PluginSettingsActivity(plugin))
        }
    }

    override fun openPluginSetting(plugin: Plugin, linkAlias: String, fragment: BaseFragment) {
        PluginsController.runOnPluginsQueue {
            if (!getPluginsController().isPluginActive(plugin)) {
                return@runOnPluginsQueue
            }
            val newFragment: PluginSettingsActivity
            FileLog.d("Opening plugin setting: ${plugin.getId()}/$linkAlias")
            if (!linkAlias.contains(":")) {
                newFragment = PluginSettingsActivity(plugin, linkAlias)
            } else {
                var settings =
                    getPluginsController().settings[plugin.getId()] ?: return@runOnPluginsQueue
                var targetSetting: TextSetting? = null
                val parts = linkAlias.split(":").toTypedArray()
                for (i in 0 until parts.size - 1) {
                    val settingName = parts[i]
                    for (setting in settings) {
                        if (setting is TextSetting && settingName == setting.linkAlias) {
                            targetSetting = setting
                            try {
                                if (!getPluginsController().isPluginActive(plugin)) {
                                    return@runOnPluginsQueue
                                }
                                val pySubDefinitions = setting.createSubFragmentCallback?.call()
                                if (pySubDefinitions != null) {
                                    settings = parsePySettingDefinitions(pySubDefinitions.asList())
                                }
                            } catch (_: Exception) {
                            }
                            break
                        }
                    }
                    if (targetSetting == null && settings.isEmpty()) {
                        SettingsRegistry.getInstance().onSettingNotFound(fragment)
                        return@runOnPluginsQueue
                    }
                }
                val resolvedTargetSetting = targetSetting ?: return@runOnPluginsQueue
                newFragment = PluginSettingsActivity(
                    plugin,
                    resolvedTargetSetting.text,
                    settings,
                    resolvedTargetSetting.createSubFragmentCallback,
                    parts[parts.size - 1]
                ).setSettingsLinkPrefix(parts.copyOf(parts.size - 1).joinToString(":"))
            }
            AndroidUtilities.runOnUIThread { fragment.presentFragment(newFragment) }
        }
    }

    override fun openPluginSetting(pluginId: String, linkAlias: String, fragment: BaseFragment) {
        val plugin = getPluginsController().plugins[pluginId]
        if (plugin != null) {
            openPluginSetting(plugin, linkAlias, fragment)
        }
    }

    private enum class SdkState {
        READY,
        STARTING,
        PYTHON_FAILED,
        INSTALL_FAILED,
        START_FAILED
    }

    class Updater private constructor() {
        companion object {
            private val PYTHON_SDK_APP_VERSION_PATTERN =
                Pattern.compile("^app_version(>=|<=|==)(.+)$")
            private val PYTHON_SDK_APP_VERSION_CODE_PATTERN =
                Pattern.compile("^app_version_code(>=|<=|==)(.+)$")
            private val notifyRunnable = Runnable {
                NotificationCenter.getGlobalInstance()
                    .postNotificationName(NotificationCenter.pluginsPySdkInfoChanged)
            }

            @Volatile
            private var isLoading = false

            var status = 0

            var notifyWhenChangeStatus = false

            internal val pendingUpdateLock = Any()
            private val download = TelegramUpdateSource.Download()

            @JvmStatic
            fun getVersion(): CharSequence {
                var version = if (pluginsEngine && sdkInitialized) SDK_VERSION else null
                var isBeta = SDK_BETA
                if (version == null) {
                    val versionFile = File(SDK_DIR ?: sdkDirFile(), "v.txt")
                    if (versionFile.exists()) {
                        val versionText = SimpliFiles.file(versionFile).readText(MAX_SDK_VERSION_BYTES).trim()
                        isBeta = versionText.endsWith("|1")
                        version = versionText.substringBefore('|')
                    }
                }
                val versionText = version?.let { "v" + it + if (isBeta) "-beta" else "" }
                val stateText = if (pluginsEngine) getSdkStateText() else null
                return when {
                    stateText != null && versionText != null -> "$stateText ($versionText)"
                    stateText != null -> stateText
                    versionText != null -> versionText
                    !pluginsEngine -> LocaleController.getString(R.string.PluginsPySdkEngineDisabled)
                    else -> LocaleController.getString(R.string.PluginsPySdkNotInstalled)
                }
            }

            private fun getSdkStateText(): String? {
                return when (sdkState) {
                    SdkState.READY -> null
                    SdkState.STARTING -> LocaleController.getString(R.string.PluginsPySdkStarting)
                    SdkState.PYTHON_FAILED -> LocaleController.getString(R.string.PluginsPySdkPythonFailed)
                    SdkState.INSTALL_FAILED -> LocaleController.getString(R.string.PluginsPySdkInstallFailed)
                    SdkState.START_FAILED -> LocaleController.getString(R.string.PluginsPySdkStartFailed)
                }
            }

            fun notifySdkStateChanged() {
                if (notifyWhenChangeStatus) {
                    AndroidUtilities.runOnUIThread(notifyRunnable)
                }
            }

            @JvmStatic
            fun getStateString(): CharSequence? {
                return when (status) {
                    0 -> getVersion()
                    1 -> LocaleController.getString(R.string.CheckingForUpdates)
                    2 -> LocaleController.getString(R.string.LatestVersionInstalled) + " (${getVersion()})"
                    3 -> LocaleController.getString(R.string.LoadingUpdate)
                    4 -> LocaleController.getString(R.string.RestartPluginSystemToApplyUpdate)
                    5 -> LocaleController.getString(R.string.PluginComponentUpdateFailed)
                    else -> null
                }
            }

            class PythonSdkUpdateInfo : TLRPC.TL_help_appUpdate() {
                var message: TLRPC.Message? = null
                internal var account = UserConfig.selectedAccount
                var available = false
                var channel: String? = null
                var appVersionOperator: String? = null
                var appVersion: String? = null
                var appVersionCodeOperator: String? = null
                var appVersionCode: String? = null
                var abi: String? = null
                var signature: String? = null

                init {
                    clear()
                }

                fun clear() {
                    message = null
                    available = false
                    can_not_skip = false
                    channel = null
                    version = null
                    appVersion = null
                    appVersionOperator = null
                    appVersionCode = null
                    appVersionCodeOperator = null
                    document = null
                    abi = null
                    signature = null
                }

                fun canInstall(): Boolean {
                    val currentAppVersion = appVersion
                    val currentAppVersionOperator = appVersionOperator
                    val currentAppVersionCode = appVersionCode
                    val currentAppVersionCodeOperator = appVersionCodeOperator
                    val currentVersion = version
                    return (currentAppVersion == null || currentAppVersionOperator == null || isAppVersionCompatible(
                        currentAppVersionOperator,
                        currentAppVersion
                    ))
                            && (currentAppVersionCode == null || currentAppVersionCodeOperator == null || isAppVersionCodeCompatible(
                        currentAppVersionCodeOperator,
                        currentAppVersionCode
                    ))
                            && !currentVersion.isNullOrEmpty()
                            && isSdkVersionNewer(currentVersion, channel == "beta")
                            && document != null
                            && abi != null && abi == sdkAbi
                            && !signature.isNullOrEmpty()
                }
            }

            private val sdkAbi: String? by lazy {
                val assets = ApplicationLoader.applicationContext.assets
                Build.SUPPORTED_ABIS.firstOrNull { abi ->
                    runCatching { assets.open("plugins_pysdk/sdk-$abi.zip").close() }.isSuccess
                }
            }

            @JvmStatic
            @Throws(IOException::class)
            fun sdkFromApk(): InputStream {
                val abi = sdkAbi
                    ?: throw IOException("APK has no Python SDK for ${Build.SUPPORTED_ABIS.joinToString()}")
                return ApplicationLoader.applicationContext.assets.open("plugins_pysdk/sdk-$abi.zip")
            }

            fun sdkDirFile(): File {
                return File(File(ApplicationLoader.getFilesDirFixed(), "chaquopy"), PluginsConstants.PLUGINS_SDK)
            }

            @JvmStatic
            fun isSdkFromApk(): Boolean {
                return sdkFromApkMarker().exists() || requestSdkFromApkFile().exists()
            }

            fun isSdkFromCurrentApk(): Boolean {
                val marker = sdkFromApkMarker()
                return marker.exists() && runCatching {
                    SimpliFiles.file(marker).readText(MAX_SDK_VERSION_BYTES)
                }.getOrNull() == currentApkStamp()
            }

            @JvmStatic
            fun setBuildFromApk(fromApk: Boolean) {
                val file = sdkFromApkMarker()
                if (!fromApk) {
                    deleteFileIfExists(file)
                    return
                }
                runCatching {
                    SimpliFiles.file(file).writeTextAtomic(currentApkStamp())
                }.onFailure {
                    FileLog.e(it)
                }
            }

            private fun sdkFromApkMarker(): File {
                return File(File(ApplicationLoader.getFilesDirFixed(), "chaquopy"), ".currentSdkFromApk")
            }

            private fun currentApkStamp(): String {
                val apk = ApplicationLoader.applicationContext.applicationInfo.sourceDir
                return apk + ":" + File(apk).lastModified()
            }

            @JvmStatic
            fun hashBytes(inputStream: InputStream): String {
                try {
                    inputStream.use { stream ->
                        val digest = MessageDigest.getInstance("SHA-1")
                        val buf = ByteArray(1024 * 1024)
                        while (true) {
                            val len = stream.read(buf)
                            if (len <= 0) {
                                break
                            }
                            digest.update(buf, 0, len)
                        }

                        val hashBytes = digest.digest()
                        val sb = StringBuilder(hashBytes.size * 2)
                        for (b in hashBytes) {
                            sb.append(String.format("%02x", b))
                        }
                        return sb.toString()
                    }
                } catch (e: IOException) {
                    throw RuntimeException(e)
                } catch (e: NoSuchAlgorithmException) {
                    throw RuntimeException(e)
                }
            }

            @JvmStatic
            fun getPythonSdkUpdateFile(): File {
                return File(File(ApplicationLoader.getFilesDirFixed(), "chaquopy"), "newSdk")
            }

            @JvmStatic
            fun getPythonCurrentSdkFile(): File {
                return File(File(ApplicationLoader.getFilesDirFixed(), "chaquopy"), "currentSdk")
            }

            @JvmStatic
            fun getPythonSdkUpdateMetaFile(): File {
                return File(File(ApplicationLoader.getFilesDirFixed(), "chaquopy"), "newSdk.meta")
            }

            @JvmStatic
            fun isPendingUpdateVerified(archive: File): Boolean {
                val metaFile = getPythonSdkUpdateMetaFile()
                if (!metaFile.exists()) {
                    FileLog.e("Pending Python SDK update has no signature")
                    return false
                }
                val meta = try {
                    SimpliFiles.file(metaFile).readText(MAX_SDK_VERSION_BYTES).trim().split("|")
                } catch (e: IOException) {
                    FileLog.e("Failed to read pending Python SDK update signature", e)
                    return false
                }
                if (meta.size != 3) {
                    FileLog.e("Malformed signature of a pending Python SDK update")
                    return false
                }
                if (meta[1] != sdkAbi) {
                    FileLog.e("Pending Python SDK update is built for ${meta[1]}, but the app runs $sdkAbi")
                    return false
                }
                return SdkSignature.verify(archive, meta[0], meta[1], meta[2])
            }

            @JvmStatic
            fun requestSdkFromApkFile(): File {
                return File(File(ApplicationLoader.getFilesDirFixed(), "chaquopy"), ".restoreSdk")
            }

            @JvmStatic
            fun deleteSdkUpdateFile() {
                deleteFileIfExists(getPythonSdkUpdateMetaFile())
                val file = getPythonSdkUpdateFile()
                if (file.exists()) {
                    deleteFileIfExists(file)
                    updateStatus(0)
                }
            }

            @JvmStatic
            fun enableAutoUpdate() {
                pluginsPySdkAutoUpdate = true
                if (notifyWhenChangeStatus) {
                    notifyRunnable.run()
                }
            }

            @JvmStatic
            fun checkUpdates() {
                checkUpdates(false)
            }

            @JvmStatic
            fun checkUpdates(force: Boolean) {
                checkUpdates(force, null)
            }

            internal fun checkUpdates(force: Boolean, source: BaseFragment?) {
                if (status == 1 || status == 3 || status == 4) return
                val now = System.currentTimeMillis()
                if (!force) {
                    val checkedRecently = kotlin.math.abs(now - sdkUpdateScheduleTimestamp) <
                            TimeUnit.HOURS.toMillis(1)
                    if (!pluginsEngine || pluginsSafeMode || checkedRecently) {
                        return
                    }
                }
                sdkUpdateScheduleTimestamp = now

                updateStatus(1)
                val account = UserConfig.selectedAccount
                TelegramUpdateSource.search(account, "python_sdk") { resp, error ->
                    if (account != UserConfig.selectedAccount) {
                        updateStatus(0)
                        return@search
                    }
                    if (error != null || resp == null) {
                        if (error != null) FileLog.e("Failed to search messages with sdk updates: ${error.text}")
                        updateStatus(if (force) 5 else 0)
                        return@search
                    }
                    val update = try {
                        parsePythonSdkUpdateResponse(resp)
                    } catch (e: Exception) {
                        FileLog.e(e)
                        updateStatus(if (force) 5 else 0)
                        return@search
                    }
                    if (update == null) {
                        updateStatus(2)
                        return@search
                    }

                    update.account = account
                    if (force || !pluginsPySdkAutoUpdate) {
                        val fragment = source ?: LaunchActivity.getSafeLastFragment()
                        val activity = fragment?.parentActivity
                        if (fragment != null && !fragment.isPaused && fragment.visibleDialog?.isShowing != true &&
                            activity != null && !activity.isFinishing && !activity.isDestroyed
                        ) {
                            fragment.showDialog(PythonSdkUpdateAlert(fragment, update))
                        }
                        updateStatus(0)
                    } else {
                        try {
                            savePythonSdkArchive(update)
                        } catch (e: IOException) {
                            FileLog.e(e)
                            updateStatus(5)
                        }
                    }
                }
            }

            @JvmStatic
            fun restoreSdkFromApk() {
                touchFile(requestSdkFromApkFile())
            }

            private fun touchFile(file: File) {
                runCatching {
                    SimpliFiles.file(file).touch()
                }.onFailure {
                    FileLog.e(it)
                }
            }

            private fun updateStatus(newStatus: Int) {
                status = newStatus
                if (notifyWhenChangeStatus) {
                    AndroidUtilities.cancelRunOnUIThread(notifyRunnable)
                    AndroidUtilities.runOnUIThread(notifyRunnable, if (newStatus == 1) 0 else 600)
                }
            }

            @JvmStatic
            fun parsePythonSdkUpdateResponse(res: TLRPC.messages_Messages): PythonSdkUpdateInfo? {
                val update = PythonSdkUpdateInfo()
                for (msg in res.messages) {
                    if (msg !is TLRPC.TL_message || TextUtils.isEmpty(msg.message) || msg.media !is TLRPC.TL_messageMediaDocument) {
                        continue
                    }

                    val isStableMessage = msg.message.contains("python_sdk_stable")
                    val isBetaMessage = msg.message.contains("python_sdk_beta")
                    if (!isStableMessage && !isBetaMessage) {
                        continue
                    }
                    if (isBetaMessage && !pluginsPySdkBetaVersions) {
                        continue
                    }

                    val text = StringBuilder()
                    var textEnd = false
                    val lines = msg.message.split("\n")
                    for (rawLine in lines) {
                        val line = rawLine.trim()
                        if (TextUtils.isEmpty(line) && textEnd) {
                            continue
                        }
                        if (line.startsWith("python_sdk_")) {
                            textEnd = true
                            update.channel = if (isBetaMessage) "beta" else "stable"
                            continue
                        } else if (!textEnd) {
                            text.append(line).append("\n")
                            continue
                        }

                        val appVersionMatcher = PYTHON_SDK_APP_VERSION_PATTERN.matcher(line)
                        if (appVersionMatcher.matches()) {
                            update.appVersionOperator = appVersionMatcher.group(1)
                            update.appVersion = appVersionMatcher.group(2)?.trim()
                            continue
                        }

                        val appVersionCodeMatcher =
                            PYTHON_SDK_APP_VERSION_CODE_PATTERN.matcher(line)
                        if (appVersionCodeMatcher.matches()) {
                            update.appVersionCodeOperator = appVersionCodeMatcher.group(1)
                            update.appVersionCode = appVersionCodeMatcher.group(2)?.trim()
                            continue
                        }

                        val keyValue = line.split("=", limit = 2)
                        if (keyValue.size != 2) {
                            continue
                        }
                        val key = keyValue[0].trim()
                        val value = keyValue[1].trim()
                        when (key) {
                            "can_not_skip" -> update.can_not_skip = value.toBoolean()
                            "version" -> update.version = value
                            "abi" -> update.abi = value
                            "signature" -> update.signature = value
                        }
                    }

                    update.document = msg.media.document
                    if (!update.canInstall()) {
                        update.clear()
                        continue
                    }

                    update.text = text.toString()
                    val entities = ArrayList<TLRPC.MessageEntity>()
                    for (ent in msg.entities) {
                        if (ent !is TLRPC.TL_messageEntityPre) {
                            entities.add(ent)
                        }
                    }
                    update.entities = entities
                    update.message = msg
                    break
                }

                if (update.message == null) {
                    return null
                }
                update.available = update.document != null && !TextUtils.isEmpty(update.version)
                return update
            }

            @JvmStatic
            fun isSdkVersionNewer(remoteVersion: String, isBeta: Boolean): Boolean {
                if (!pluginsPySdkBetaVersions && SDK_BETA && !isSdkFromCurrentApk()) {
                    return !isBeta
                }
                return if (SDK_VERSION != null) AppUtils.compareVersions(">", remoteVersion, SDK_VERSION) else false
            }

            @JvmStatic
            fun isAppVersionCompatible(operator: String, targetVersion: String): Boolean {
                return AppUtils.compareVersions(
                    operator,
                    BuildVars.BUILD_VERSION_STRING,
                    targetVersion
                )
            }

            @JvmStatic
            fun isAppVersionCodeCompatible(operator: String, targetVersion: String): Boolean {
                return AppUtils.compareVersions(
                    operator,
                    BuildVars.BUILD_VERSION,
                    targetVersion.toInt()
                )
            }

            @JvmStatic
            @Throws(IOException::class)
            fun zipFolder(sourceDir: File, zipFile: File) {
                SimpliFiles.directory(sourceDir).zipTo(zipFile, OverwritePolicy.REPLACE)
            }

            @JvmStatic
            @Throws(IOException::class)
            fun savePythonSdkArchive(
                update: PythonSdkUpdateInfo,
                autoRestartEngine: Boolean = false
            ) {
                val message = update.message ?: return
                val document = update.document ?: return
                if (isLoading) return
                isLoading = true
                updateStatus(3)
                download.start(update.account, message, document) { downloaded ->
                    if (downloaded == null) {
                        isLoading = false
                        updateStatus(5)
                        return@start
                    }
                    Utilities.globalQueue.postRunnable {
                        val ready = runCatching {
                            synchronized(pendingUpdateLock) {
                                val staged = SignedArtifact.stage(downloaded, getPythonSdkUpdateFile(), Long.MAX_VALUE) {
                                    SdkSignature.verify(it, update.version, update.abi, update.signature)
                                }
                                if (staged) {
                                    SimpliFiles.file(getPythonSdkUpdateMetaFile())
                                        .writeTextAtomic("${update.version}|${update.abi}|${update.signature}")
                                }
                                staged
                            }
                        }.onFailure { FileLog.e(it) }.getOrDefault(false)
                        AndroidUtilities.runOnUIThread {
                            isLoading = false
                            if (!ready) {
                                updateStatus(5)
                            } else if (autoRestartEngine) {
                                PluginsController.getInstance().restart()
                            } else {
                                updateStatus(4)
                            }
                        }
                    }
                }
            }
        }
    }
}
