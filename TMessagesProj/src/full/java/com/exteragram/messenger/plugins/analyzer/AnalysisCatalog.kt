/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R

class AnalysisCatalog private constructor(loadEntries: () -> Map<String, Entry>) {

    enum class Category(val iconRes: Int, val titleRes: Int) {
        CODE(R.drawable.plugin_analysis_code, R.string.PluginAnalysisCategoryCode),
        STEALTH(R.drawable.plugin_analysis_stealth, R.string.PluginAnalysisCategoryStealth),
        ACCOUNT(R.drawable.plugin_analysis_account, R.string.PluginAnalysisCategoryAccount),
        MESSAGES(R.drawable.plugin_analysis_messages, R.string.PluginAnalysisCategoryMessages),
        NETWORK(R.drawable.plugin_analysis_network, R.string.PluginAnalysisCategoryNetwork),
        FILES(R.drawable.plugin_analysis_files, R.string.PluginAnalysisCategoryFiles),
        DEVICE(R.drawable.plugin_analysis_device, R.string.PluginAnalysisCategoryDevice),
        APP(R.drawable.plugin_analysis_app, R.string.PluginAnalysisCategoryApp),
        PACKAGES(R.drawable.plugin_analysis_packages, R.string.PluginAnalysisCategoryPackages),
        OTHER(R.drawable.plugin_analysis_info, R.string.PluginAnalysisCategoryOther),
    }

    private class Entry(val category: Category, val texts: Map<String, Pair<String, String>>, val flows: Set<String>?)

    private val SYMBOL_VALUES = setOf(
        "telegram_api", "sensitive_requests", "intercepts_requests", "session_data", "java_hooks", "ui_hooks", "host_tampering", "traffic_interception",
    )

    private val ICONS = mapOf(
        "remote_code" to R.drawable.plugin_analysis_remote_code,
        "remote_control" to R.drawable.plugin_analysis_remote_control,
        "exfiltrate_messages" to R.drawable.plugin_analysis_exfiltrate_messages,
        "account_takeover" to R.drawable.plugin_analysis_account_takeover,
        "wallet_access" to R.drawable.plugin_analysis_wallet_access,
    )

    private val FALLBACK_LOCALES = mapOf("be" to "ru", "kk" to "ru")

    private val entries by lazy(loadEntries)

    companion object {
        const val LIMIT_SPEC_BUDGET = "spec_budget"
        const val LIMIT_UNVERIFIED_REQUIREMENT = "unverified_requirement"
        const val LIMIT_NATIVE_UNCHECKED = "native_unchecked"

        internal val bundled = AnalysisCatalog {
            runCatching {
                ApplicationLoader.applicationContext.assets.open("plugin_analyzer/texts.json").use {
                    parseEntries(it.readBytes().decodeToString())
                }
            }.getOrElse {
                FileLog.e(it)
                emptyMap()
            }
        }

        internal fun parse(text: String): AnalysisCatalog {
            val entries = parseEntries(text)
            return AnalysisCatalog { entries }
        }

        private fun parseEntries(text: String): Map<String, Entry> {
            val json = JSONObject(text)
            val locales = json.getJSONArray("locales").let { array -> List(array.length()) { array.getString(it) } }
            val statements = json.getJSONObject("statements")
            return statements.keys().asSequence().associateWith { id ->
                val entry = statements.getJSONObject(id)
                val category = Category.entries.firstOrNull { it.name.equals(entry.optString("category"), ignoreCase = true) } ?: Category.OTHER
                val texts = locales.filter(entry::has).associateWith { locale -> entry.getJSONArray(locale).let { it.getString(0) to it.getString(1) } }
                val flows = entry.optJSONArray("flows")?.let { array -> List(array.length()) { array.getString(it) }.toSet() }
                Entry(category, texts, flows)
            }
        }
    }

    private fun texts(statement: AnalyzerReport.Statement): Pair<String, String>? {
        val texts = entries[statement.id]?.texts ?: return null
        val language = LocaleController.getInstance().currentLocale?.language.orEmpty()
        return texts[language] ?: FALLBACK_LOCALES[language]?.let(texts::get) ?: texts["en"]
    }

    fun category(statement: AnalyzerReport.Statement): Category = entries[statement.id]?.category ?: Category.OTHER

    fun icon(statement: AnalyzerReport.Statement): Int = ICONS[statement.id] ?: category(statement).iconRes

    fun name(statement: AnalyzerReport.Statement): String = texts(statement)?.first ?: statement.title

    fun description(statement: AnalyzerReport.Statement): String? = texts(statement)?.second

    fun isCombination(statement: AnalyzerReport.Statement): Boolean = entries[statement.id]?.flows != null

    fun flows(statement: AnalyzerReport.Statement): Set<String>? = entries[statement.id]?.flows?.takeIf { it.isNotEmpty() }

    fun values(report: AnalyzerReport, statement: AnalyzerReport.Statement): List<String> =
        values(statement, report.findingsOf(statement))

    fun values(statement: AnalyzerReport.Statement, findings: List<AnalyzerReport.Finding>): List<String> =
        findings.flatMap { finding ->
            val capabilities = finding.capabilities.filter { it in statement.capabilities }
            when {
                "network" in capabilities -> if (finding.rule.startsWith("net.") && (finding.rule.contains("endpoints") || finding.rule == "net.raw_ip")) {
                    finding.values.map(::hostOf)
                } else {
                    emptyList()
                }

                capabilities.any { it in SYMBOL_VALUES } -> finding.values.mapNotNull(::symbolName)
                "third_party_code" in capabilities || "verified_packages" in capabilities || "mvel_code" in capabilities || "unused_files" in capabilities -> finding.values
                else -> emptyList()
            }
        }.filter { it.isNotEmpty() }.distinct()

    fun notes(statement: AnalyzerReport.Statement): List<String> = listOfNotNull(
        if ("tl.unresolved_request" in statement.rules) LocaleController.getString(R.string.PluginAnalysisUnresolvedRequests) else null,
        if (statement.id == "java_hooks" && ("hooks.unresolved" in statement.rules || "hooks.external" in statement.rules)) {
            LocaleController.getString(R.string.PluginAnalysisUnresolvedHooks)
        } else {
            null
        },
    )

    class Limit(val iconRes: Int, val title: String, val subtitle: String)

    fun limits(report: AnalyzerReport): List<Limit> {
        val timedOut = report.limits.any { it.startsWith("timeout") || it.startsWith("unanalyzed") }
        val packages = LIMIT_UNVERIFIED_REQUIREMENT in report.limits
        val native = LIMIT_NATIVE_UNCHECKED in report.limits
        val incomplete = report.limits.any {
            !it.startsWith("timeout") && !it.startsWith("unanalyzed") && it != LIMIT_UNVERIFIED_REQUIREMENT && it != LIMIT_NATIVE_UNCHECKED
        }
        return listOfNotNull(
            if (packages) {
                Limit(
                    R.drawable.plugin_analysis_packages,
                    LocaleController.getString(R.string.PluginAnalysisLimitPackages),
                    LocaleController.getString(R.string.PluginAnalysisLimitPackagesInfo),
                )
            } else {
                null
            },
            if (native) {
                Limit(
                    R.drawable.plugin_analysis_code,
                    LocaleController.getString(R.string.PluginAnalysisLimitNative),
                    LocaleController.getString(R.string.PluginAnalysisLimitNativeInfo),
                )
            } else {
                null
            },
            if (timedOut) {
                Limit(
                    R.drawable.plugin_analysis_timer,
                    LocaleController.getString(R.string.PluginAnalysisLimitTime),
                    LocaleController.getString(R.string.PluginAnalysisLimitTimeInfo),
                )
            } else {
                null
            },
            if (incomplete) {
                Limit(
                    R.drawable.plugin_analysis_question,
                    LocaleController.getString(R.string.PluginAnalysisLimitIncomplete),
                    LocaleController.getString(R.string.PluginAnalysisLimitIncompleteInfo),
                )
            } else {
                null
            },
        )
    }

    internal fun symbolName(value: String): String? {
        val symbol = value.substringBefore(" (").substringAfter(':').trim()
        if (symbol.isEmpty() || symbol.startsWith("<") || symbol == "?") {
            return null
        }
        val className = symbol.substringBefore('#')
        val member = symbol.substringAfter('#', "")
        val parts = className.substringAfterLast('.').split('$')
        tlMethod(parts)?.let { return if (member.isEmpty() || member == "<init>") it else "$it.$member" }
        val simple = parts.joinToString(".")
        return when {
            member.isEmpty() -> simple
            member == "<init>" -> simple
            else -> "$simple.$member"
        }
    }

    private fun tlMethod(parts: List<String>): String? {
        val last = parts.last()
        val flat = when {
            last.startsWith("TL_") -> last.removePrefix("TL_")
            parts.size >= 2 && parts[parts.size - 2].startsWith("TL_") -> parts[parts.size - 2].removePrefix("TL_") + "_" + last
            else -> return null
        }
        val split = flat.indexOf('_')
        return if (split > 0 && split < flat.length - 1) flat.substring(0, split) + "." + flat.substring(split + 1) else flat
    }

    private fun hostOf(value: String): String =
        value.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').trim()

}
