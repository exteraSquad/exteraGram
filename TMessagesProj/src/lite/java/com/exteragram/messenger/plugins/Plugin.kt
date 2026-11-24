/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins

class Plugin(
    private val id: String,
    private val name: String,
) {
    private var version: String = "1.0"
    private var description: String = ""
    private var author: String = ""
    private var isEnabled = false
    private var pack: String? = null
    private var index = -1
    private var error: Throwable? = null

    @Transient
    var cachedEngine: PluginsController.PluginsEngine? = null

    fun getId(): String = id

    fun getName(): String = name

    fun getDescription(): String = description

    fun getAuthor(): String = author

    fun getVersion(): String = version

    fun isEnabled(): Boolean = false

    fun setEnabled(enabled: Boolean) {
    }

    fun getError(): Throwable? = error

    fun setError(error: Throwable?) {
        this.error = error
    }

    fun getPack(): String? = pack

    fun getIndex(): Int = index

    fun getIcon(): String? = null

    override fun equals(other: Any?): Boolean {
        return if (other is Plugin) {
            other.id == id
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int = id.hashCode()
}