/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.exteragram.messenger.plugins.analyzer.AnalysisCatalog
import com.exteragram.messenger.plugins.analyzer.AnalyzerDiff
import com.exteragram.messenger.plugins.analyzer.AnalyzerReport
import com.exteragram.messenger.sectionsSeparatedHeaders
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BottomSheetWithRecyclerListView
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Stories.recorder.ButtonWithCounterView

class PluginAnalysisReportSheet(
    fragment: BaseFragment,
    private val report: AnalyzerReport,
    private val diff: AnalyzerDiff?,
    private val diffUnavailable: Boolean = false,
    confirmTitle: CharSequence? = null,
    onConfirm: (() -> Unit)? = null,
) : BottomSheetWithRecyclerListView(
    fragment.parentActivity,
    fragment,
    false,
    false,
    false,
    true,
    ActionBarType.FADING,
    fragment.resourceProvider,
) {
    private var listAdapter: UniversalAdapter? = null
    private var sheetContainer: FrameLayout? = null
    private var contentReady = false
    private val expanded = HashSet<String>()
    private val rows = HashMap<String, AnalysisStatementRow>()
    private val toggle = View.OnClickListener { view ->
        val key = view.tag as? String ?: return@OnClickListener
        if (!expanded.remove(key)) {
            expanded.add(key)
        }
        listAdapter?.update(true)
    }

    init {
        topPadding = 0.2f
        if (!sectionsSeparatedHeaders) {
            headerPaddingBottom = maxOf(0, dp(SECTION_GAP) - (dp(SHEET_TOP_INSET) - backgroundPaddingTop))
        }
        val sheetColor = getThemedColor(Theme.key_windowBackgroundGray)
        setBackgroundColor(sheetColor)
        fixNavigationBar(sheetColor)
        actionBar.apply {
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray))
            setCastShadows(false)
        }
        showShadow = false
        takeTranslationIntoAccount = true
        recyclerListView.apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)
            setSections()
            itemAnimator = object : DefaultItemAnimator() {
                override fun onMoveAnimationUpdate(holder: RecyclerView.ViewHolder) {
                    super.onMoveAnimationUpdate(holder)
                    containerView.invalidate()
                }
            }.apply {
                supportsChangeAnimations = false
                setDelayAnimations(false)
                setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                setDurations(350)
            }
        }
        if (onConfirm != null) {
            val button = ButtonWithCounterView(context, true, resourcesProvider).apply {
                setRound()
                setDangerColors(resourcesProvider)
                setText(confirmTitle, false)
                setOnClickListener {
                    dismiss()
                    onConfirm()
                }
            }
            val bottom = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = sheetBottomBackdrop(getThemedColor(Theme.key_windowBackgroundGray))
                addView(
                    button,
                    LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, dp(48f)).apply {
                        val horizontal = backgroundPaddingLeft + dp(SECTION_GAP)
                        setMargins(horizontal, dp(12f), horizontal, dp(SECTION_GAP))
                    }
                )
                addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                    val height = bottom - top
                    if (recyclerListView.paddingBottom != height) {
                        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, height)
                    }
                }
            }
            sheetContainer?.addView(bottom, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM))
        }
        contentReady = true
        listAdapter?.update(false)
        layoutManager.scrollToPositionWithOffset(0, 0)
    }

    private fun copy(text: CharSequence) {
        val container = sheetContainer
        if (container != null && AndroidUtilities.addToClipboard(text)) {
            BulletinFactory.of(container, resourcesProvider)
                .createCopyBulletin(LocaleController.getString(R.string.TextCopied))
                .show()
        }
    }

    override fun onViewCreated(containerView: FrameLayout) {
        super.onViewCreated(containerView)
        sheetContainer = containerView
    }

    override fun getTitle(): CharSequence = LocaleController.getString(R.string.PluginAnalysisReport)

    override fun createAdapter(listView: RecyclerListView): RecyclerListView.SelectionAdapter =
        UniversalAdapter(
            listView,
            context,
            currentAccount,
            baseFragment?.classGuid ?: 0,
            true,
            this::fillItems,
            resourcesProvider,
        ).also { listAdapter = it }

    private fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        if (!contentReady) {
            return
        }
        val newIds = diff?.newStatements?.map { it.id }?.toSet().orEmpty()
        val newStatements = report.statements.filter { it.id in newIds }
        val changed = diff?.changed(report).orEmpty()
        if (newStatements.isNotEmpty() || changed.isNotEmpty()) {
            addHeader(items, LocaleController.getString(R.string.PluginAnalysisNewInVersion))
            addRows(items, newStatements, "new")
            addRows(items, changed, "changed") { diff?.changedFindings(report, it).orEmpty() }
        }
        val rest = report.statements.filter { it.id !in newIds }
        for (severity in AnalyzerReport.SEVERITY_CRITICAL downTo AnalyzerReport.SEVERITY_INFO) {
            val group = rest.filter { it.severity == severity }
            if (group.isNotEmpty()) {
                addGroup(items, groupTitle(severity), group, "all")
            }
        }
        val limits = report.catalog.limits(report) + listOfNotNull(
            if (diffUnavailable) {
                AnalysisCatalog.Limit(
                    R.drawable.plugin_analysis_question,
                    LocaleController.getString(R.string.PluginAnalysisLimitCompare),
                    LocaleController.getString(R.string.PluginAnalysisLimitCompareInfo),
                )
            } else {
                null
            },
        )
        if (limits.isNotEmpty()) {
            addHeader(items, LocaleController.getString(R.string.PluginAnalysisGroupLimits))
            for (limit in limits) {
                val row = rows.getOrPut("limit:${limit.title}") {
                    AnalysisStatementRow(
                        key = "limit:${limit.title}",
                        iconRes = limit.iconRes,
                        color = analysisSeverityColor(AnalyzerReport.SEVERITY_CAUTION, resourcesProvider),
                        title = limit.title,
                        subtitle = limit.subtitle,
                        values = emptyList(),
                        evidence = null,
                    )
                }
                items.add(AnalysisStatementCell.Factory.of(row, false, null))
            }
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.PluginAnalysisLimitations)))
    }

    private fun addGroup(items: ArrayList<UItem>, title: String, statements: List<AnalyzerReport.Statement>, prefix: String) {
        addHeader(items, title)
        addRows(items, statements, prefix)
    }

    private fun addRows(
        items: ArrayList<UItem>,
        statements: List<AnalyzerReport.Statement>,
        prefix: String,
        findingsOf: (AnalyzerReport.Statement) -> List<AnalyzerReport.Finding> = report::findingsOf,
    ) {
        for (statement in statements) {
            val key = "$prefix:${statement.id}"
            val row = rows.getOrPut(key) {
                val findings = findingsOf(statement)
                val values = report.catalog.values(statement, findings)
                val hidden = values.size - MAX_VALUES
                AnalysisStatementRow(
                    key = key,
                    iconRes = report.catalog.icon(statement),
                    color = analysisSeverityColor(statement.severity, resourcesProvider),
                    title = report.catalog.name(statement),
                    subtitle = report.catalog.description(statement),
                    values = values.take(MAX_VALUES),
                    evidence = evidenceOf(statement, findings).takeIf { it.lines.isNotEmpty() },
                    notes = listOfNotNull(
                        if (hidden > 0) LocaleController.formatString(R.string.PluginAnalysisMoreValues, hidden) else null,
                    ) + report.catalog.notes(statement),
                )
            }
            items.add(AnalysisStatementCell.Factory.of(row, key in expanded, toggle, ::copy))
        }
    }

    private fun addHeader(items: ArrayList<UItem>, title: String) {
        if (items.isNotEmpty()) {
            items.add(UItem.asSpace(dp(SECTION_GAP)).also { it.transparent = true })
        }
        items.add(pluginSheetHeader(title))
    }

    private fun evidenceOf(statement: AnalyzerReport.Statement, source: List<AnalyzerReport.Finding>): AnalysisEvidence {
        val findings = source.let { all ->
            if (report.catalog.isCombination(statement) && all.any { it.severity > AnalyzerReport.SEVERITY_INFO }) {
                all.filter { it.severity > AnalyzerReport.SEVERITY_INFO }
            } else {
                all
            }
        }
        val flow = report.catalog.flows(statement)
        val carriesFlow = { item: AnalyzerReport.Evidence -> flow != null && item.traits.any { it in flow } }
        val onlyFlow = flow != null && findings.any { finding -> finding.evidence.any(carriesFlow) }
        val seen = HashMap<String, Candidate>()
        val all = ArrayList<Candidate>()
        for (finding in findings) {
            if (onlyFlow && finding.evidence.none(carriesFlow)) {
                continue
            }
            for (item in finding.evidence) {
                if (onlyFlow && !carriesFlow(item)) {
                    continue
                }
                val code = item.excerpt
                    ?: item.instruction?.let { insn -> item.scope?.let { "$it: $insn" } ?: insn }
                    ?: item.symbol.takeIf { it.startsWith("native:") }?.removePrefix("native:")
                    ?: continue
                val address = item.symbol.startsWith("net:")
                val key = "${item.artifact}:${item.line}:${if (item.excerpt == null) code else ""}"
                val existing = seen[key]
                if (existing != null) {
                    if (!address) {
                        existing.address = false
                    }
                    continue
                }
                val payload = item.payload
                val label = when {
                    payload?.inComment == true && item.endLine > item.line ->
                        LocaleController.formatString(R.string.PluginAnalysisHiddenInComment, item.line, item.endLine)
                    report.isRoot(item.artifact) -> null
                    report.kindOf(item.artifact) == "dex" -> LocaleController.getString(R.string.PluginAnalysisCompiledCode)
                    report.kindOf(item.artifact) == "elf" -> LocaleController.getString(R.string.PluginAnalysisNativeLibrary)
                    else -> LocaleController.getString(R.string.PluginAnalysisDecodedCode)
                }
                val shown = payload?.takeIf { it.dataLine }?.let(::describePayload) ?: code.trim().take(MAX_EXCERPT)
                val candidate = Candidate(AnalysisEvidence.Line(label, item.line, shown, code.trim()), finding.severity, finding.capabilities, address)
                seen[key] = candidate
                all.add(candidate)
            }
        }
        val order = compareBy<AnalysisEvidence.Line>({ it.label != null }, { it.line })
        val bySeverity = compareByDescending<Candidate> { it.severity }.thenBy { it.address }
        val rank = (if (statement.id == "network") compareBy<Candidate> { it.address }.then(bySeverity) else bySeverity)
            .thenBy(order) { it.line }
        val ranked = all.sortedWith(rank)
        val picked = LinkedHashSet<Candidate>()
        if (report.catalog.isCombination(statement) && !onlyFlow) {
            for (capability in statement.capabilities) {
                val part = if (capability == "network") ranked.sortedBy { it.address } else ranked
                part.firstOrNull { capability in it.capabilities && it !in picked }?.let(picked::add)
            }
        }
        for (candidate in ranked) {
            if (picked.size >= MAX_LINES) {
                break
            }
            picked.add(candidate)
        }
        val shown = picked.take(MAX_LINES).map { it.line }.sortedWith(order)
        val total = if (onlyFlow) all.size else maxOf(all.size, findings.sumOf { it.evidenceTotal })
        return AnalysisEvidence(shown, total - shown.size)
    }

    private class Candidate(
        val line: AnalysisEvidence.Line,
        val severity: Int,
        val capabilities: List<String>,
        var address: Boolean,
    )

    private fun describePayload(payload: AnalyzerReport.Payload): CharSequence {
        val size = AndroidUtilities.formatFileSize(payload.size)
        if (payload.kind.isEmpty()) {
            return LocaleController.formatString(R.string.PluginAnalysisEncodedData, size)
        }
        val kind = if (payload.kind == "python") "Python" else payload.kind.uppercase()
        val steps = payload.transforms.joinToString(" + ")
        return if (steps.isEmpty()) "$kind · $size" else LocaleUtils.replaceArrows(context, "$steps -> $kind · $size", R.drawable.msg_mini_arrow_mediathin)
    }

    private fun groupTitle(severity: Int): String = LocaleController.getString(
        when (severity) {
            AnalyzerReport.SEVERITY_CRITICAL -> R.string.PluginAnalysisGroupCritical
            AnalyzerReport.SEVERITY_DANGER -> R.string.PluginAnalysisGroupDanger
            AnalyzerReport.SEVERITY_CAUTION -> R.string.PluginAnalysisGroupCaution
            else -> R.string.PluginAnalysisGroupInfo
        }
    )

    companion object {
        private const val SECTION_GAP = 12f
        private const val SHEET_TOP_INSET = 16f
        private const val BOTTOM_GAP = 12f
        private const val MAX_LINES = 4
        private const val MAX_VALUES = 12
        private const val MAX_EXCERPT = 200
    }
}
