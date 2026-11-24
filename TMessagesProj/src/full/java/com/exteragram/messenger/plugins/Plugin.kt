/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins

import org.telegram.messenger.BuildConfig
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R

class Plugin(
    private val id: String,
    private val name: String,
) {
    private var engine: String? = null
    private var appVersion: String? = ">=" + BuildConfig.BUILD_VERSION_STRING
    private var sdkVersion: String? = ">=" + PythonPluginsEngine.SDK_VERSION
    private var version: String? = "1.0"
    private var description: String? = LocaleController.getString(R.string.PluginNoDescription)
    private var author: String? = LocaleController.getString(R.string.PluginNoAuthor)
    @Volatile
    private var isEnabled = false
    private var pack: String? = null
    private var index = -1
    @Volatile
    private var error: Throwable? = null
    @Volatile
    private var isNotResponding = false
    private var requirements: List<String>? = arrayListOf()

    @Transient
    var cachedEngine: PluginsController.PluginsEngine? = null

    fun getId(): String = id

    fun getDescription(): String? = description

    fun setDescription(description: String?) {
        this.description = description
    }

    fun getEngine(): String? = engine

    fun setEngine(engine: String?) {
        this.engine = engine
    }

    fun getAuthor(): String? = author

    fun setAuthor(author: String?) {
        this.author = author
    }

    fun getName(): String = name

    fun isEnabled(): Boolean = !hasError() && isEnabled

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled && !hasError()
    }

    fun getError(): Throwable? = error

    fun setError(error: Throwable?) {
        this.error = error
        if (hasError()) {
            isEnabled = false
        }
    }

    fun hasError(): Boolean = error != null

    fun getPack(): String? = pack

    fun getIndex(): Int = index

    fun getVersion(): String? = version

    fun setVersion(version: String?) {
        this.version = version
    }

    fun getRequirements(): List<String>? = requirements

    fun getAppVersion(): String? = appVersion

    fun setAppVersion(appVersion: String?) {
        this.appVersion = appVersion
    }

    fun getSdkVersion(): String? = sdkVersion

    fun setSdkVersion(sdkVersion: String?) {
        this.sdkVersion = sdkVersion
    }

    fun setRequirements(requirements: List<String>?) {
        this.requirements = requirements
    }

    fun isNotResponding(): Boolean = isNotResponding

    fun setNotResponding(notResponding: Boolean) {
        isNotResponding = notResponding
    }

    fun getIcon(): String? {
        if (pack == null || index < 0) {
            return null
        }
        return "$pack/$index"
    }

    fun setIcon(link: String?) {
        if (!isIconValid(link)) {
            return
        }
        val iconIndex = link!!.lastIndexOf('/')
        if (iconIndex == -1) {
            return
        }
        pack = link.substring(0, iconIndex)
        index = link.substring(iconIndex + 1).toInt()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Plugin) {
            other.id == id
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        private fun isIconValid(input: String?): Boolean {
            return input != null && input.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*/\\d+$"))
        }
    }
}