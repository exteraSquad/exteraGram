/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import com.exteragram.messenger.plugins.analyzer.AnalysisCatalog
import com.exteragram.messenger.plugins.analyzer.AnalyzerDiff
import com.exteragram.messenger.plugins.analyzer.AnalyzerReport
import com.exteragram.messenger.plugins.analyzer.PluginAnalyzer
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.UItem

class PluginAnalysisSection(
    private val resourcesProvider: Theme.ResourcesProvider?,
    private val onChanged: () -> Unit,
    private val onOpenReport: () -> Unit,
) {
    private val summaryClick = View.OnClickListener { onOpenReport() }

    private var state = STATE_RUNNING
    private var report: AnalyzerReport? = null
    private var diff: AnalyzerDiff? = null
    private var diffUnavailable = false
    private var summary = runningSummary()

    val maxSeverity: Int
        get() = if (state == STATE_REPORT) report?.maxSeverity ?: AnalyzerReport.SEVERITY_NONE else AnalyzerReport.SEVERITY_NONE

    val isVisible: Boolean
        get() = state != STATE_DISABLED && state != STATE_UNAVAILABLE

    val isIncomplete: Boolean
        get() = when (state) {
            STATE_FAILED, STATE_UNAVAILABLE -> true
            STATE_REPORT -> report?.limits?.any { it != AnalysisCatalog.LIMIT_SPEC_BUDGET } != false
            else -> false
        }

    fun setDisabled() {
        state = STATE_DISABLED
        onChanged()
    }

    fun setRunning() {
        state = STATE_RUNNING
        report = null
        diff = null
        diffUnavailable = false
        summary = runningSummary()
        onChanged()
    }

    fun setResult(result: PluginAnalyzer.Result) {
        val report = result.report
        when {
            result.status == PluginAnalyzer.STATUS_DONE && report != null -> {
                state = STATE_REPORT
                this.report = report
                diff = result.diff?.takeIf { !it.isEmpty }
                diffUnavailable = result.diffUnavailable
                summary = reportSummary(report)
            }

            result.status == PluginAnalyzer.STATUS_UNAVAILABLE -> state = STATE_UNAVAILABLE

            else -> {
                state = STATE_FAILED
                summary = failureSummary(result.status)
            }
        }
        onChanged()
    }

    fun fill(items: ArrayList<UItem>) {
        if (!isVisible) {
            return
        }
        items.add(AnalysisSummaryCell.Factory.of(ID_SUMMARY, summary, if (hasReport) summaryClick else null))
    }

    val hasReport: Boolean
        get() {
            val report = report ?: return false
            return state == STATE_REPORT && (report.statements.isNotEmpty() || report.limits.isNotEmpty() || diffUnavailable)
        }

    fun openReport(fragment: BaseFragment, confirmTitle: CharSequence?, onConfirm: (() -> Unit)?) {
        val report = report ?: return
        PluginAnalysisReportSheet(fragment, report, diff, diffUnavailable, confirmTitle, onConfirm).show()
    }

    private fun color(key: Int) = Theme.getColor(key, resourcesProvider)

    private fun runningSummary() = AnalysisSummary(
        iconRes = 0,
        color = color(Theme.key_featuredStickers_addButton),
        title = LocaleController.getString(R.string.PluginAnalysisRunning),
        subtitle = LocaleController.getString(R.string.PluginAnalysisRunningInfo),
        loading = true,
    )

    private fun failureSummary(status: Int) = AnalysisSummary(
        iconRes = R.drawable.plugin_analysis_warning,
        color = analysisSeverityColor(AnalyzerReport.SEVERITY_DANGER, resourcesProvider),
        title = LocaleController.getString(R.string.PluginAnalysisFailed),
        subtitle = LocaleController.getString(
            when (status) {
                PluginAnalyzer.STATUS_TIMEOUT -> R.string.PluginAnalysisFailedTimeout
                PluginAnalyzer.STATUS_CRASHED -> R.string.PluginAnalysisFailedCrash
                PluginAnalyzer.STATUS_TOO_LARGE -> R.string.PluginAnalysisFailedTooLarge
                PluginAnalyzer.STATUS_MEMORY -> R.string.PluginAnalysisFailedMemory
                else -> R.string.PluginAnalysisFailedError
            }
        ),
    )

    private fun reportSummary(report: AnalyzerReport): AnalysisSummary {
        val severity = report.maxSeverity
        val threats = severity >= AnalyzerReport.SEVERITY_DANGER
        val warnings = severity == AnalyzerReport.SEVERITY_CAUTION
        val title = LocaleController.getString(
            when {
                threats -> R.string.PluginAnalysisThreats
                warnings -> R.string.PluginAnalysisCaution
                else -> R.string.PluginAnalysisClean
            }
        )
        val subtitle = SpannableStringBuilder()
        if (threats) {
            appendCount(subtitle, "PluginAnalysisCriticalCount", report.count(AnalyzerReport.SEVERITY_CRITICAL))
            appendCount(subtitle, "PluginAnalysisDangerCount", report.count(AnalyzerReport.SEVERITY_DANGER))
        } else {
            subtitle.append(LocaleController.getString(if (warnings) R.string.PluginAnalysisCautionInfo else R.string.PluginAnalysisCleanInfo))
        }

        val chips = run {
            val categories = LinkedHashMap<AnalysisCatalog.Category, Int>()
            for (statement in report.statements) {
                val category = report.catalog.category(statement)
                categories[category] = maxOf(categories[category] ?: AnalyzerReport.SEVERITY_NONE, statement.severity)
            }
            categories.entries
                .sortedWith(compareByDescending<Map.Entry<AnalysisCatalog.Category, Int>> { it.value }.thenBy { it.key.ordinal })
                .map { (category, level) ->
                    AnalysisSummary.Chip(
                        iconRes = category.iconRes,
                        text = LocaleController.getString(category.titleRes),
                        color = analysisSeverityColor(level, resourcesProvider),
                    )
                }
        }

        return AnalysisSummary(
            iconRes = if (severity >= AnalyzerReport.SEVERITY_CAUTION) R.drawable.plugin_analysis_warning else R.drawable.plugin_analysis_shield,
            color = if (severity >= AnalyzerReport.SEVERITY_CAUTION) {
                analysisSeverityColor(severity, resourcesProvider)
            } else {
                analysisSafeColor(resourcesProvider)
            },
            title = title,
            subtitle = subtitle,
            chips = chips,
            badge = newCount(report)?.let { LocaleController.formatPluralString("PluginAnalysisNewBadge", it) },
            badgeColor = color(Theme.key_featuredStickers_addButton),
        )
    }

    private fun newCount(report: AnalyzerReport): Int? {
        val diff = diff ?: return null
        val ids = diff.newStatements.mapTo(HashSet()) { it.id }
        return (report.statements.count { it.id in ids } + diff.changed(report).size).takeIf { it > 0 }
    }

    private fun appendCount(builder: SpannableStringBuilder, key: String, count: Int) {
        if (count <= 0) {
            return
        }
        if (builder.length > 0) {
            builder.append(SEPARATOR)
        }
        val text = LocaleController.formatPluralString(key, count)
        val from = builder.length
        builder.append(text)
        val number = count.toString()
        if (text.startsWith(number)) {
            builder.setSpan(
                ForegroundColorSpan(color(Theme.key_windowBackgroundWhiteBlackText)),
                from, from + number.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    companion object {
        const val ID_SUMMARY = 0x41A0

        private const val SEPARATOR = " · "

        private const val STATE_RUNNING = 0
        private const val STATE_REPORT = 1
        private const val STATE_FAILED = 2
        private const val STATE_UNAVAILABLE = 3
        private const val STATE_DISABLED = 4
    }
}
