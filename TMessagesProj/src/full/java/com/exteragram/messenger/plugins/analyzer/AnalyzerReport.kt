/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import org.json.JSONArray
import org.json.JSONObject

class AnalyzerReport(
    val version: String,
    val maxSeverity: Int,
    val statements: List<Statement>,
    val findings: Map<String, Finding>,
    val limits: List<String>,
    val artifacts: Map<String, Artifact>,
    val elapsedMs: Long,
) {
    internal var catalog = AnalysisCatalog.bundled
        private set

    class Artifact(
        val name: String,
        val kind: String,
    )

    class Statement(
        val id: String,
        val severity: Int,
        val title: String,
        val values: List<String>,
        val rules: List<String>,
        val capabilities: List<String>,
    )

    class Finding(
        val rule: String,
        val severity: Int,
        val title: String,
        val values: List<String>,
        val capabilities: List<String>,
        val evidence: List<Evidence>,
        val evidenceTotal: Int,
    )

    class Evidence(
        val artifact: String,
        val line: Int,
        val excerpt: String?,
        val scope: String?,
        val instruction: String?,
        val traits: List<String>,
        val symbol: String,
        val endLine: Int,
        val payload: Payload?,
    )

    class Payload(
        val kind: String,
        val size: Long,
        val transforms: List<String>,
        val inComment: Boolean,
        val dataLine: Boolean,
    )

    fun isRoot(artifact: String): Boolean = artifact == ROOT_ARTIFACT

    fun kindOf(artifact: String): String? = artifacts[artifact]?.kind

    fun count(severity: Int): Int = statements.count { it.severity == severity }

    fun findingsOf(statement: Statement): List<Finding> =
        statement.rules.mapNotNull { findings[it] }.sortedByDescending { it.severity }

    companion object {
        const val SEVERITY_NONE = -1
        const val SEVERITY_INFO = 0
        const val SEVERITY_CAUTION = 1
        const val SEVERITY_DANGER = 2
        const val SEVERITY_CRITICAL = 3

        private const val ROOT_ARTIFACT = "0"

        fun severityOf(label: String?): Int = when (label) {
            "info" -> SEVERITY_INFO
            "caution" -> SEVERITY_CAUTION
            "danger" -> SEVERITY_DANGER
            "critical" -> SEVERITY_CRITICAL
            else -> SEVERITY_NONE
        }

        fun parse(json: String): AnalyzerReport = parse(json, AnalysisCatalog.bundled)

        internal fun parse(json: String, catalog: AnalysisCatalog): AnalyzerReport {
            val root = JSONObject(json)
            val findings = LinkedHashMap<String, Finding>()
            root.optJSONArray("findings").forEachObject { item ->
                val finding = parseFinding(item)
                findings[finding.rule] = finding
            }
            val artifacts = HashMap<String, Artifact>()
            root.optJSONArray("artifacts").forEachObject { item ->
                artifacts[item.optString("id")] = Artifact(item.optString("name"), item.optString("kind"))
            }
            val statements = ArrayList<Statement>()
            root.optJSONArray("statements").forEachObject { statements.add(parseStatement(it)) }
            statements.sortByDescending { it.severity }
            val covered = statements.flatMapTo(HashSet()) { it.rules }
            val shown = withoutSecondaryDuplicates(statements, findings) + findings.values
                .filter { it.severity >= SEVERITY_CAUTION && it.rule !in covered }
                .map { Statement(it.rule, it.severity, it.title, it.values, listOf(it.rule), it.capabilities) }
            return AnalyzerReport(
                version = root.optJSONObject("analyzer")?.optString("version").orEmpty(),
                maxSeverity = severityOf(root.optString("max_severity")),
                statements = shown.sortedByDescending { it.severity },
                findings = findings,
                limits = root.optJSONArray("limits").strings(),
                artifacts = artifacts,
                elapsedMs = root.optJSONObject("stats")?.optLong("elapsed_ms", 0L) ?: 0L,
            ).also { it.catalog = catalog }
        }

        private fun withoutSecondaryDuplicates(statements: List<Statement>, findings: Map<String, Finding>): List<Statement> {
            val byId = statements.associateBy { it.id }
            return statements.mapNotNull { statement ->
                val primaries = ArrayList<Statement>()
                val rules = statement.rules.filter { rule ->
                    val capabilities = findings[rule]?.capabilities.orEmpty()
                    val primary = capabilities.firstOrNull()?.takeIf { it != statement.id }?.let { byId[it] }
                    if (primary != null && statement.id in capabilities) {
                        primaries.add(primary)
                        false
                    } else {
                        true
                    }
                }
                when {
                    rules.size == statement.rules.size -> statement
                    rules.isNotEmpty() -> Statement(statement.id, statement.severity, statement.title, statement.values, rules, statement.capabilities)
                    primaries.all { it.severity >= statement.severity } -> null
                    else -> statement
                }
            }
        }

        internal fun parseStatement(item: JSONObject) = Statement(
            id = item.optString("id"),
            severity = severityOf(item.optString("severity")),
            title = item.optString("title"),
            values = item.optJSONArray("values").strings(),
            rules = item.optJSONArray("rules").strings(),
            capabilities = item.optJSONArray("capabilities").strings(),
        )

        private fun parsePayload(symbol: String, detail: JSONObject?): Payload? {
            detail ?: return null
            return when {
                symbol.startsWith("artifact:") -> Payload(
                    kind = symbol.substringAfter(':'),
                    size = detail.optLong("size", 0L),
                    transforms = detail.optJSONArray("transforms").strings().filter { it != "comment" },
                    inComment = detail.optString("via") == "comment",
                    dataLine = detail.optString("via") in setOf("comment", "auto-unwrap"),
                )

                symbol.startsWith("data:opaque") -> Payload(
                    kind = "",
                    size = detail.optLong("length", 0L),
                    transforms = emptyList(),
                    inComment = symbol == "data:opaque-comment",
                    dataLine = true,
                )

                else -> null
            }
        }

        internal fun parseFinding(item: JSONObject): Finding {
            val evidence = ArrayList<Evidence>()
            item.optJSONArray("evidence").forEachObject { entry ->
                val location = entry.optJSONObject("location")
                evidence.add(
                    Evidence(
                        artifact = location?.optString("artifact").orEmpty(),
                        line = location?.optInt("line", 0) ?: 0,
                        excerpt = entry.optString("excerpt").takeIf { it.isNotEmpty() },
                        scope = location?.optString("scope")?.takeIf { it.isNotEmpty() },
                        instruction = entry.optJSONObject("detail")?.optString("insn")?.takeIf { it.isNotEmpty() },
                        traits = entry.optJSONArray("traits").strings(),
                        symbol = entry.optString("symbol"),
                        endLine = location?.optInt("end_line", 0) ?: 0,
                        payload = parsePayload(entry.optString("symbol"), entry.optJSONObject("detail")),
                    )
                )
            }
            return Finding(
                rule = item.optString("rule"),
                severity = severityOf(item.optString("severity")),
                title = item.optString("title"),
                values = item.optJSONArray("values").strings(),
                capabilities = item.optJSONArray("capabilities").strings(),
                evidence = evidence,
                evidenceTotal = item.optInt("evidence_total", evidence.size),
            )
        }
    }
}

class AnalyzerDiff(
    val newStatements: List<AnalyzerReport.Statement>,
    val changes: Map<String, Change>,
) {
    class Change(
        val newValues: List<String>,
        val evidence: List<AnalyzerReport.Evidence>,
    )

    val isEmpty: Boolean
        get() = newStatements.isEmpty() && changes.isEmpty()

    fun changed(report: AnalyzerReport): List<AnalyzerReport.Statement> {
        val new = newStatements.mapTo(HashSet()) { it.id }
        return report.statements.filter { it.id !in new && it.rules.any(changes::containsKey) }
    }

    fun changedFindings(report: AnalyzerReport, statement: AnalyzerReport.Statement): List<AnalyzerReport.Finding> =
        report.findingsOf(statement).mapNotNull { finding ->
            val change = changes[finding.rule] ?: return@mapNotNull null
            AnalyzerReport.Finding(
                rule = finding.rule,
                severity = finding.severity,
                title = finding.title,
                values = change.newValues,
                capabilities = finding.capabilities,
                evidence = change.evidence,
                evidenceTotal = change.evidence.size,
            )
        }

    companion object {
        fun parse(json: String): AnalyzerDiff {
            val root = JSONObject(json)
            val statements = ArrayList<AnalyzerReport.Statement>()
            root.optJSONArray("new_statements").forEachObject { statements.add(AnalyzerReport.parseStatement(it)) }
            statements.sortByDescending { it.severity }
            val changes = LinkedHashMap<String, Change>()
            root.optJSONArray("new_findings").forEachObject { item ->
                val finding = AnalyzerReport.parseFinding(item)
                changes[finding.rule] = Change(item.optJSONArray("new_values").strings(), finding.evidence)
            }
            return AnalyzerDiff(statements, changes)
        }
    }
}

private inline fun JSONArray?.forEachObject(action: (JSONObject) -> Unit) {
    if (this == null) {
        return
    }
    for (i in 0 until length()) {
        optJSONObject(i)?.let(action)
    }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) {
        return emptyList()
    }
    return (0 until length()).mapNotNull { optString(it).takeIf { value -> value.isNotEmpty() } }
}
