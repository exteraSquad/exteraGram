/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats

import android.view.View
import com.exteragram.messenger.AngleUnit
import com.exteragram.messenger.MeasureSystem
import com.exteragram.messenger.inline.InlineRates
import com.exteragram.messenger.inline.InlineResults
import com.exteragram.messenger.inline.docs.InlineExampleGroup
import com.exteragram.messenger.inline.docs.InlineExamples
import com.exteragram.messenger.inline.math.MathExpression
import com.exteragram.messenger.inline.math.MathOptions
import com.exteragram.messenger.inline.rates.Currencies
import com.exteragram.messenger.inlineAngleUnit
import com.exteragram.messenger.inlineCurrencyRates
import com.exteragram.messenger.inlineDigitGrouping
import com.exteragram.messenger.inlineMathResult
import com.exteragram.messenger.inlineMeasureSystem
import com.exteragram.messenger.inlineResults
import com.exteragram.messenger.inlineTargetCurrency
import com.exteragram.messenger.inlineTimeZones
import com.exteragram.messenger.inlineUnitConversion
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.chats.components.InlineDocBlock
import com.exteragram.messenger.preferences.chats.components.InlineDocExample
import com.exteragram.messenger.preferences.chats.components.InlineFeature
import com.exteragram.messenger.preferences.chats.components.InlineFeatureCell
import com.exteragram.messenger.utils.addIf
import com.exteragram.messenger.utils.network.ExchangeRates
import com.exteragram.messenger.utils.ui.PopupUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.formatString
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.text.Collator
import java.text.DecimalFormatSymbols
import java.util.ArrayList
import java.util.Currency
import java.util.Locale

class InlineResultsPreferencesActivity : BasePreferencesActivity() {

    enum class InlineItem {
        SHOW_RESULTS,
        CALCULATOR,
        CURRENCIES,
        UNITS,
        TIME_ZONES,
        DEFAULT_CURRENCY,
        MEASURE_SYSTEM,
        ANGLE_UNIT,
        DIGIT_GROUPING,
        ARITHMETIC,
        PERCENTAGES,
        CURRENCY_CONVERSION,
        UNIT_CONVERSION,
        TIME_CONVERSION;

        val id: Int = ordinal + 1
    }

    private enum class Section(
        val item: InlineItem,
        val icon: Int,
        val title: Int,
        val subtitle: Int,
        val enabled: () -> Boolean
    ) {
        ARITHMETIC(
            InlineItem.ARITHMETIC, R.drawable.msg_calculator,
            R.string.InlineDocArithmetic, R.string.InlineDocArithmeticInfo, { inlineMathResult }
        ),
        PERCENTAGES(
            InlineItem.PERCENTAGES, R.drawable.msg_percent,
            R.string.InlineDocPercent, R.string.InlineDocPercentInfo, { inlineMathResult }
        ),
        CURRENCIES(
            InlineItem.CURRENCY_CONVERSION, R.drawable.menu_feature_paid,
            R.string.InlineCurrencies, R.string.InlineDocCurrenciesInfo, { inlineCurrencyRates }
        ),
        UNITS(
            InlineItem.UNIT_CONVERSION, R.drawable.msg_ruler,
            R.string.InlineUnits, R.string.InlineDocUnitsInfo, { inlineUnitConversion }
        ),
        TIME(
            InlineItem.TIME_CONVERSION, R.drawable.msg_language,
            R.string.InlineTimeZones, R.string.InlineDocTimeInfo, { inlineTimeZones }
        )
    }

    private val cryptoNames = linkedMapOf(
        Currencies.GRAM to "Gram",
        ExchangeRates.BTC to "Bitcoin",
        ExchangeRates.ETH to "Ethereum",
        ExchangeRates.SOL to "Solana",
        ExchangeRates.USDT to "Tether"
    )
    private val expanded = HashSet<Section>()
    private val rates = InlineRates { if (fragmentView != null) listView.adapter?.update(true) }
    private val featureClick = View.OnClickListener { view ->
        when (val tag = view.tag) {
            is String -> if (AndroidUtilities.addToClipboard(tag)) {
                BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show()
            }
            is Int -> Section.entries.firstOrNull { it.item.id == tag }?.let { section ->
                if (!expanded.remove(section)) {
                    expanded.add(section)
                }
                listView.adapter?.update(true)
            }
        }
    }

    private lateinit var angleOptions: Array<String>
    private lateinit var measureOptions: Array<String>
    private lateinit var currencyCodes: List<String>
    private lateinit var currencyOptions: Array<String>

    override fun getTitle(): String = getString(R.string.InlineMathResult)

    override fun initializeOptionStrings() {
        angleOptions = arrayOf(getString(R.string.InlineAngleDegrees), getString(R.string.InlineAngleRadians))
        measureOptions = arrayOf(getString(R.string.QualityAuto), getString(R.string.InlineMeasureMetric), getString(R.string.InlineMeasureImperial))
        val locale = LocaleController.getInstance().currentLocale ?: Locale.getDefault()
        val supported = ExchangeRates.getSupportedCurrencies().map { InlineRates.normalize(it) }
        val fiat = ArrayList<Pair<String, String>>()
        for (code in supported) {
            if (code in cryptoNames) {
                continue
            }
            val name = try {
                Currency.getInstance(code).getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
            } catch (e: IllegalArgumentException) {
                code
            }
            fiat.add(code to name)
        }
        val collator = Collator.getInstance(locale)
        fiat.sortWith { a, b -> collator.compare(a.second, b.second) }
        val crypto = cryptoNames.entries.filter { it.key in supported }.map { it.key to it.value }
        val currencies = fiat + crypto
        currencyCodes = listOf("") + currencies.map { it.first }
        currencyOptions = (listOf(getString(R.string.QualityAuto)) + currencies.map { "${it.second} — ${it.first}" }).toTypedArray()
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(
            UItem.asRippleCheck(InlineItem.SHOW_RESULTS.id, getString(R.string.InlineShowResults))
                .setChecked(inlineResults)
                .setSearchable(this)
        )
        items.add(UItem.asShadow(getString(R.string.InlineMathResultHint)))

        if (inlineResults) {
            items.add(UItem.asHeader(getString(R.string.InlineFeatures)))
            items.add(uCheck(InlineItem.CALCULATOR, getString(R.string.InlineCalculator), inlineMathResult, icon = R.drawable.msg_calculator, linkAlias = "inlineCalculator"))
            items.add(uCheck(InlineItem.CURRENCIES, getString(R.string.InlineCurrencies), inlineCurrencyRates, icon = R.drawable.menu_feature_paid, linkAlias = "inlineCurrencyRates"))
            items.add(uCheck(InlineItem.UNITS, getString(R.string.InlineUnits), inlineUnitConversion, icon = R.drawable.msg_ruler, linkAlias = "inlineUnitConversion"))
            items.add(uCheck(InlineItem.TIME_ZONES, getString(R.string.InlineTimeZones), inlineTimeZones, icon = R.drawable.msg_language, linkAlias = "inlineTimeZones"))
            items.add(UItem.asShadow())

            items.add(UItem.asHeader(getString(R.string.Settings)))
            items.add(uCheck(InlineItem.DIGIT_GROUPING, getString(R.string.InlineDigitGrouping), inlineDigitGrouping, linkAlias = "inlineDigitGrouping"))
            items.addIf(inlineMathResult, uButton(InlineItem.ANGLE_UNIT, getString(R.string.InlineAngleUnit), value = angleOptions[inlineAngleUnit.ordinal], linkAlias = "inlineAngleUnit"))
            items.addIf(inlineCurrencyRates, uButton(InlineItem.DEFAULT_CURRENCY, getString(R.string.InlineDefaultCurrency), value = defaultCurrencyValue(), linkAlias = "inlineTargetCurrency"))
            items.addIf(inlineUnitConversion, uButton(InlineItem.MEASURE_SYSTEM, getString(R.string.InlineMeasureSystem), value = measureOptions[inlineMeasureSystem.ordinal], linkAlias = "inlineMeasureSystem"))
            items.add(UItem.asShadow(settingsInfo()))
        }

        items.add(UItem.asHeader(getString(R.string.InlineCapabilities)))
        val options = if (expanded.isEmpty()) null else InlineResults.options(rates, decimalSeparator(), true)
        for (section in Section.entries) {
            val badge = if (inlineResults && !section.enabled()) getString(R.string.InlineFeatureOff) else null
            val docs = if (options != null && section in expanded) blocks(section, options) else null
            val feature = InlineFeature(section.icon, getString(section.title), getString(section.subtitle), badge, docs)
            items.add(InlineFeatureCell.Factory.of(section.item.id, feature, featureClick))
        }
        items.add(UItem.asShadow(getString(R.string.InlineTapToCopy)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (InlineItem.entries.getOrNull(item.id - 1)) {
            InlineItem.SHOW_RESULTS -> {
                toggleBooleanSettingAndRefresh(item) { inlineResults = it }
                (view as? TextCheckCell)?.setBackgroundColorAnimated(
                    inlineResults, getThemedColor(if (inlineResults) Theme.key_windowBackgroundChecked else Theme.key_windowBackgroundUnchecked)
                )
            }
            InlineItem.CALCULATOR -> toggleBooleanSettingAndRefresh(item) { inlineMathResult = it }
            InlineItem.CURRENCIES -> toggleBooleanSettingAndRefresh(item) { inlineCurrencyRates = it }
            InlineItem.UNITS -> toggleBooleanSettingAndRefresh(item) { inlineUnitConversion = it }
            InlineItem.TIME_ZONES -> toggleBooleanSettingAndRefresh(item) { inlineTimeZones = it }
            InlineItem.DEFAULT_CURRENCY -> {
                val current = currencyCodes.indexOf(InlineRates.normalize(inlineTargetCurrency)).coerceAtLeast(0)
                PopupUtils.showDialog(currencyOptions, null, getString(R.string.InlineDefaultCurrency), current, context, { i ->
                    if (i == current) return@showDialog
                    inlineTargetCurrency = currencyCodes[i]
                    (listView.findViewByItemId(item.id) as? TextCell)?.setValue(defaultCurrencyValue(), true)
                    listView.adapter?.update(true)
                }, resourceProvider)
            }
            InlineItem.ANGLE_UNIT ->
                showListDialog(item, angleOptions, getString(R.string.InlineAngleUnit), inlineAngleUnit.ordinal) { i ->
                    inlineAngleUnit = AngleUnit.entries[i]
                }
            InlineItem.MEASURE_SYSTEM ->
                showListDialog(item, measureOptions, getString(R.string.InlineMeasureSystem), inlineMeasureSystem.ordinal) { i ->
                    inlineMeasureSystem = MeasureSystem.entries[i]
                }
            InlineItem.DIGIT_GROUPING -> toggleBooleanSettingAndRefresh(item) { inlineDigitGrouping = it }
            else -> Unit
        }
    }

    private fun defaultCurrencyValue(): String {
        val code = InlineRates.normalize(inlineTargetCurrency)
        return if (rates.supports(code)) code else getString(R.string.QualityAuto)
    }

    private fun settingsInfo(): String? {
        if (!inlineCurrencyRates) {
            return null
        }
        val info = getString(R.string.InlineAutoCurrencyInfo)
        val state = ExchangeRates.peekCached() ?: return info
        return info + " " + formatString(R.string.InlineRatesUpdated, LocaleController.formatDateAudio(state.time() / 1000, true))
    }

    private fun decimalSeparator(): Char {
        val locale = LocaleController.getInstance().currentLocale ?: Locale.US
        return DecimalFormatSymbols.getInstance(locale).decimalSeparator
    }

    private fun blocks(section: Section, options: MathOptions): List<InlineDocBlock> = when (section) {
        Section.ARITHMETIC -> listOf(
            examples(InlineExamples.arithmetic, options),
            text(R.string.InlineDocFunctionsText),
            InlineDocBlock.Codes(InlineExamples.functions),
            examples(InlineExamples.arithmeticFunctions, options),
            text(R.string.InlineDocBasesText),
            examples(InlineExamples.bases, options)
        )
        Section.PERCENTAGES -> listOf(
            text(R.string.InlineDocPercentText),
            examples(InlineExamples.percentages, options),
            text(R.string.InlineDocShorthandsText),
            examples(InlineExamples.shorthands, options)
        )
        Section.CURRENCIES -> listOf(
            text(R.string.InlineDocCurrencyText),
            examples(InlineExamples.currencyDefault, options),
            examples(InlineExamples.currencyTarget, options)
        )
        Section.UNITS -> listOf(
            text(R.string.InlineDocUnitsText),
            examples(InlineExamples.unitsDefault, options),
            examples(InlineExamples.unitsTarget, options),
            text(R.string.InlineDocDurationsText),
            examples(InlineExamples.durations, options)
        )
        Section.TIME -> listOf(
            text(R.string.InlineDocTimeText),
            examples(InlineExamples.timeZones, options),
            text(R.string.InlineDocCurrentTimeText),
            examples(InlineExamples.currentTime, options)
        )
    }

    private fun text(res: Int): InlineDocBlock = InlineDocBlock.Text(getString(res))

    private fun examples(group: InlineExampleGroup, options: MathOptions): InlineDocBlock {
        val language = LocaleController.getInstance().currentLocale?.language
        val russian = language == "ru" || language == "be"
        return InlineDocBlock.Examples(group.examples(russian).map { example ->
            InlineDocExample(example, MathExpression.suggestionAt(example, example.length, options)?.value)
        })
    }
}
