/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

@file:JvmName("ExteraConfig")

package com.exteragram.messenger

import android.app.Activity
import android.content.SharedPreferences
import android.util.Pair
import androidx.core.content.edit
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.backup.PreferencesUtils.BackupItem
import com.exteragram.messenger.TabIconsMode.TITLES_ONLY
import com.exteragram.messenger.config.BooleanPref
import com.exteragram.messenger.config.EnumPref
import com.exteragram.messenger.config.FloatPref
import com.exteragram.messenger.config.IntegerPref
import com.exteragram.messenger.config.LongPref
import com.exteragram.messenger.config.NullableStringPref
import com.exteragram.messenger.config.PrefEnum
import com.exteragram.messenger.config.SHOW
import com.exteragram.messenger.config.SanitizedIntegerPref
import com.exteragram.messenger.config.StringPref
import com.exteragram.messenger.config.StringSetPref
import com.exteragram.messenger.config.allDelegates
import com.exteragram.messenger.config.registeredKeys
import com.exteragram.messenger.config.setMode
import com.exteragram.messenger.drawer.loadMainMenuLayout
import com.exteragram.messenger.export.ExportSettings
import com.exteragram.messenger.icons.loadIconPacksLayout
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.utils.chats.DoubleTapUtils
import com.exteragram.messenger.utils.network.RemoteUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLog
import org.telegram.messenger.SharedConfig
import org.telegram.ui.web.SearchEngine
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

val GSON: Gson = Gson()

const val PLACEHOLDERS = "exteraGramPlaceholders"

private const val DEFAULT_BOT_ID = 8083294286L
private const val DEFAULT_BOT_NAME = "exteraAuthBot"
private const val DEFAULT_SECTION_RADIUS_DP = 20
const val SECTION_RADIUS_MAX_DP = 28

enum class CameraType(override val prefId: Int) : PrefEnum {
    CAMERA_1(0),
    CAMERA_2(1),
    CAMERA_X(2)
}

enum class IconPackType(override val prefId: Int) : PrefEnum {
    DEFAULT(0),
    SOLAR(1),
    REMIX(2)
}

enum class TabIconsMode(override val prefId: Int) : PrefEnum {
    ICONS_AND_TITLES(0),
    TITLES_ONLY(1),
    ICONS_ONLY(2)
}

enum class TabCounterMode(override val prefId: Int) : PrefEnum {
    ALL(0),
    UNMUTED(1),
    HIDDEN(2)
}

enum class DividerStyle(override val prefId: Int) : PrefEnum {
    HIDDEN(0),
    LINE(1),
    SEGMENTS(2)
}

enum class AvatarCornerType {
    DEFAULT,
    FORUM,
    COMMUNITY
}

enum class GlassOutlineStyle(override val prefId: Int) : PrefEnum {
    GLARE(0),
    SOLID(1),
    HIDDEN(2)
}

enum class HeaderBlurStyle(override val prefId: Int) : PrefEnum {
    DEFAULT(0),
    PROGRESSIVE(1),
    FADE(2)
}

enum class TransitionAnimation(override val prefId: Int) : PrefEnum {
    DEFAULT(0),
    AOSP(1),
    SPRING(2)
}

enum class StickerTimeMode(override val prefId: Int) : PrefEnum {
    DEFAULT(0),
    SIDE(1),
    HIDDEN(2)
}

enum class VideoMessagesCamera(override val prefId: Int) : PrefEnum {
    FRONT(0),
    REAR(1),
    ASK(2)
}

enum class AngleUnit(override val prefId: Int) : PrefEnum {
    DEGREES(0),
    RADIANS(1)
}

enum class MeasureSystem(override val prefId: Int) : PrefEnum {
    AUTO(0),
    METRIC(1),
    IMPERIAL(2)
}

enum class DefaultProfileTab(override val prefId: Int) : PrefEnum {
    DEFAULT(0),
    MEDIA(1),
    COMMON_GROUPS(2)
}

enum class ProxyDisableCondition(val flag: Int) {
    VPN(1 shl 0),
    MOBILE_DATA(1 shl 1),
    WIFI(1 shl 2)
}

@Volatile
private var currentApiBot: Pair<Long, String>? = null
private var apiBotListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

private fun ensureApiBotListener() {
    if (apiBotListener != null) return
    try {
        RemoteUtils.initCached()
        val prefs = RemoteUtils.sharedPreferences ?: return
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (RemoteUtils.EXTERA_API_BOT == key)
                currentApiBot = null
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        apiBotListener = listener
    } catch (e: Exception) {
        FileLog.e(e)
    }
}

fun getApiBotInfo(): Pair<Long, String> {
    currentApiBot?.let { return it }
    synchronized(sync) {
        currentApiBot?.let { return it }
        ensureApiBotListener()
        val botInfo = RemoteUtils.getStringConfigValue(
            RemoteUtils.EXTERA_API_BOT,
            "$DEFAULT_BOT_ID:$DEFAULT_BOT_NAME"
        )
        try {
            if (botInfo != null) {
                val parts = botInfo.split(":", limit = 2)
                if (parts.size == 2) {
                    return Pair(parts[0].toLong(), parts[1]).also { currentApiBot = it }
                }
            }
        } catch (e: Exception) {
            FileLog.e(e)
        }
        return Pair(DEFAULT_BOT_ID, DEFAULT_BOT_NAME).also { currentApiBot = it }
    }
}

const val CONFIG_NAME = "exteraconfig"
val preferences: SharedPreferences = PreferencesUtils.getPreferences(CONFIG_NAME)
val editor: SharedPreferences.Editor = preferences.edit()

private val sync = Any()
private var configLoaded = false

val backupKeys: Array<BackupItem> get() = registeredKeys.toTypedArray()

// General
var translationProvider by IntegerPref(0)
var disableNumberRounding by BooleanPref(false)
var formatTimeWithSeconds by BooleanPref(false)
var relativeLastSeen by BooleanPref(false)
var inAppVibration by BooleanPref(true)
var disableNotificationDelay by BooleanPref(false)
var filterZalgo by BooleanPref(true)
var downloadSpeedBoost by IntegerPref(0)
var uploadSpeedBoost by BooleanPref(false)
var hidePhoneNumber by BooleanPref(false)
var showIdAndDc by IntegerPref(1)
var defaultProfileTab by EnumPref(DefaultProfileTab.DEFAULT)
var hideArchiveFolder by BooleanPref(false)
var archiveOnPull by BooleanPref(false)
var disableUnarchiveSwipe by BooleanPref(true)
var doNotUseProxy by IntegerPref(0)

fun isProxyDisabledOn(condition: ProxyDisableCondition) = (doNotUseProxy and condition.flag) != 0

fun setProxyDisabledOn(condition: ProxyDisableCondition, disable: Boolean) {
    doNotUseProxy = if (disable) doNotUseProxy or condition.flag else doNotUseProxy and condition.flag.inv()
}

var customSavePath by StringPref("exteraGram")

var doNotMarkAsNew = ArrayList<String>()
var newFeaturesShowedAt = HashMap<String, Long>()

// Icon Packs
var iconPack by EnumPref(IconPackType.DEFAULT)
var editingIconPackId by NullableStringPref(null)

// Appearance
var avatarCorners by FloatPref(28.0f)
var singleCornerRadius by BooleanPref(false)
var dividerStyle by EnumPref(DividerStyle.LINE)
var forceSnow by BooleanPref(false)
var hideActionBarStatus by BooleanPref(false)
var centerTitle by BooleanPref(false)
var hideStories by BooleanPref(false)
var hideFloatingButton by BooleanPref(false)
var hideDialogsSearchBar by BooleanPref(false)
var senderMiniAvatars by BooleanPref(true)
var titleText by IntegerPref(0)
var tabIcons by EnumPref(TITLES_ONLY)
var tabCounterMode by EnumPref(TabCounterMode.ALL)
var hideAllChats by BooleanPref(false)

var squareFab by BooleanPref(true)
var sectionRadius by FloatPref(DEFAULT_SECTION_RADIUS_DP.toFloat())
private var sectionsSeparatedHeadersPreference by BooleanPref(true, "sectionsSeparatedHeaders")
var sectionsSeparatedHeaders: Boolean
    get() = dividerStyle == DividerStyle.SEGMENTS || sectionsSeparatedHeadersPreference
    set(value) {
        sectionsSeparatedHeadersPreference = if (dividerStyle == DividerStyle.SEGMENTS) true else value
    }
var newLoadingStyle by BooleanPref(true)
var newSliderStyle by BooleanPref(true)
var newSwitchStyle by BooleanPref(true)
var newChatHeaderStyle by BooleanPref(false)
var newNavigationBarStyle by BooleanPref(false)
var newFabStyle by BooleanPref(false)
var newProfileActionsStyle by BooleanPref(false)
var newPlayerStyle by BooleanPref(false)
var playerCoverColors by BooleanPref(false)
var playerMiniCoverColors by BooleanPref(false)
var lyricsOnline by BooleanPref(false)
var lyricsProviders by StringSetPref(setOf("lrclib", "lrcmux"))
var lyricsProgress by IntegerPref(1)
var lyricsSubtitle by IntegerPref(1)
var playerMotionCovers by BooleanPref(false)
var playerSpotifyCanvas by BooleanPref(false)
var playerYandexCanvas by BooleanPref(false)
var playerRhythmBackground by BooleanPref(true)
var playerParticles by BooleanPref(false)
var playerSpeedPitch by BooleanPref(false)
var playerMode by IntegerPref(0)

var tabletMode by IntegerPref(0)
var tabletLeftFraction by FloatPref(0.35f)
var useSystemFonts by BooleanPref(true)
var gooeyAvatarAnimation by BooleanPref(true)
var customThemes by BooleanPref(true)
var predictiveBackIntensity by FloatPref(1.0f)
var transitionAnimation by EnumPref(TransitionAnimation.SPRING)
val springAnimations get() = transitionAnimation == TransitionAnimation.SPRING
val aospTransitions get() = transitionAnimation == TransitionAnimation.AOSP
val springSwipeback get() = transitionAnimation != TransitionAnimation.DEFAULT

// Blur Preferences
var glassOutlineStyle by EnumPref(GlassOutlineStyle.GLARE)
var glassMessageMenu by BooleanPref(true)
var headerBlurStyle by EnumPref(HeaderBlurStyle.PROGRESSIVE)

// Event icon style
var eventType by IntegerPref(0)
var navigationDrawer by BooleanPref(false)
var immersiveDrawerAnimation by BooleanPref(false)
var hideNavigationBarLabels by BooleanPref(false)

var showFeedTab by BooleanPref(false)
var showFeedUnreadCounter by BooleanPref(true)

// Chats
var stickerSize by FloatPref(12.0f)
var stickerTimeMode by EnumPref(StickerTimeMode.DEFAULT)
var replyColors by BooleanPref(true)
var replyEmoji by BooleanPref(true)
var replyBackground by BooleanPref(true)
var stickerShape by IntegerPref(1)
var unlimitedRecentStickers by BooleanPref(false)
var hideReactionsInPrivateChats by BooleanPref(
    false
)
var hideReactionsInChannels by BooleanPref(false)
var hideReactionsInGroups by BooleanPref(false)
var doubleTapAction by SanitizedIntegerPref(1, sanitize = DoubleTapUtils::sanitizeSetting)
var doubleTapActionOutOwner by SanitizedIntegerPref(1, sanitize = DoubleTapUtils::sanitizeSetting)
var swipeActions by StringPref(DoubleTapUtils.ACTION_REPLY.toString())
var swipeActionsLoop by BooleanPref(false)
var swipeActionsReversed by BooleanPref(false)
var bottomButton by IntegerPref(2)
var widePostsInFeed by BooleanPref(true)
var widePostsInChannels by BooleanPref(false)
var telegramAiEditor by BooleanPref(true)
var telegramAiSummaries by BooleanPref(true)
var telegramAiInstantViewSummaries by BooleanPref(true)
var quickAdminShortcuts by BooleanPref(true)
var quickTransitionForChannels by BooleanPref(
    true
)
var quickTransitionForTopics by BooleanPref(true)
var disableGreetingSticker by BooleanPref(false)
var autoRunWidgets by BooleanPref(false)
var hideKeyboardOnScroll by BooleanPref(true)
var addCommaAfterMention by BooleanPref(true)
var inlineResults by BooleanPref(true)
var inlineMathResult by BooleanPref(true)
var inlineCurrencyRates by BooleanPref(true)
var inlineUnitConversion by BooleanPref(true)
var inlineTimeZones by BooleanPref(false)
var inlineTargetCurrency by StringPref("")
var inlineAngleUnit by EnumPref(AngleUnit.DEGREES)
var inlineMeasureSystem by EnumPref(MeasureSystem.AUTO)
var inlineDigitGrouping by BooleanPref(true)
var disableMarkdown by BooleanPref(false)
var hideSendAsPeer by BooleanPref(false)
var removeMessageTail by BooleanPref(true)
var replaceEditedWithIcon by BooleanPref(true)
var showOnlineStatus by BooleanPref(false)
var showForwardsCount by BooleanPref(false)
var hideShareButton by BooleanPref(true)
var showResultsBeforeVoting by BooleanPref(false)
var showCopyPhotoButton by BooleanPref(true)
var showSaveMessageButton by BooleanPref(false)
var showRepeatMessageButton by BooleanPref(false)
var showClearButton by BooleanPref(true)
var showHistoryButton by BooleanPref(false)
var showReportButton by BooleanPref(true)
var showGenerateButton by BooleanPref(true)
var showDetailsButton by BooleanPref(false)
var groupMessageMenu by BooleanPref(true)
var recognitionLanguage by StringPref("none")
var postprocessingWithAi by BooleanPref(false)

var cameraType by EnumPref(if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH) CameraType.CAMERA_X else CameraType.CAMERA_1)
var extendedFramesPerSecond by BooleanPref(false)
var cameraStabilization by BooleanPref(false)
var cameraMirrorMode by BooleanPref(true)
var videoMessagesCamera by EnumPref(
    VideoMessagesCamera.FRONT
)
var rememberLastUsedCamera by BooleanPref(false)
var startWithWideAngleCamera by BooleanPref(false)
var zoomSlider by BooleanPref(true)
var staticZoom by BooleanPref(false)
var alwaysSendInHD by BooleanPref(true)
var hideCameraTile by BooleanPref(false)

var doubleTapSeekDuration by IntegerPref(1)
var preferOriginalQuality by BooleanPref(false)
var swipeToPip by BooleanPref(false)
var unmuteWithVolumeButtons by BooleanPref(false)
var pauseOnMinimizeVideo by BooleanPref(true)
var pauseOnMinimizeVoice by BooleanPref(false)
var pauseOnMinimizeRound by BooleanPref(false)

var messageExportWatermark by BooleanPref(true)
var messageExportSource by BooleanPref(true)
var messageExportAuthors by BooleanPref(false)
var messageExportTime by BooleanPref(true)

// Other
var useGoogleCrashlytics by BooleanPref(BuildVars.isBetaApp())
var useGoogleAnalytics by BooleanPref(false)
var enableAdBlock by BooleanPref(true)

// Data export
var exportTypes by IntegerPref(ExportSettings.DefaultTypes)
var exportFullChats by IntegerPref(ExportSettings.DefaultFullChats)
var exportMediaTypes by IntegerPref(ExportSettings.MediaSettings.Photo)
var exportSizeLimit by LongPref(8L * 1024 * 1024)
var exportFormat by EnumPref(ExportSettings.Format.Html)
var exportFolder by StringPref("")

// Updates
var updateScheduleTimestamp by LongPref(0L)
var sdkUpdateScheduleTimestamp by LongPref(0L)

// Misc
var targetLang by StringPref(TranslatorUtils.TARGET_LANG_FOLLOW_APP)
var flashWarmth by FloatPref(0.5f)
var flashIntensity by FloatPref(1f)

val yandexSearchEngine = SearchEngine(
    "Yandex",
    "https://mini.ya.ru/",
    "https://ya.ru/search/?text=",
    "https://suggestqueries.google.com/complete/search?client=chrome&q=", // Yandex suggestions are paid, so Google
    "https://yandex.ru/legal/confidential"
)

var pluginsEngine = false
var pluginsDevMode by BooleanPref(false)
var pluginsSafeMode by BooleanPref(false)
var pluginsCompactView by BooleanPref(false)
var pluginsPySdkAutoUpdate by BooleanPref(false)
var pluginsPySdkBetaVersions by BooleanPref(false)
var pluginsDisableArtOpts by BooleanPref(false)
var pluginsUnknownSources by BooleanPref(false)
var pluginsStaticAnalysis by BooleanPref(true)
var pinnedPlugins by StringSetPref(emptySet())
var useSystemIconShape by BooleanPref(true)

fun init() {
    loadConfig()
    startServices()
}

// todo: remove after 2-3 updates
private fun migrateProxyConditions() {
    if (!preferences.contains("doNotUseProxyWithVpn")) return
    if (preferences.getBoolean("doNotUseProxyWithVpn", false)) {
        setProxyDisabledOn(ProxyDisableCondition.VPN, true)
    }
    editor.remove("doNotUseProxyWithVpn").apply()
}

private fun migrateTelegramAiInstantViewSummaries() {
    if (preferences.contains("telegramAiInstantViewSummaries")) return
    telegramAiInstantViewSummaries = telegramAiSummaries
}

private fun migrateInlineCurrencyRates() {
    if (preferences.contains("inlineCurrencyRates")) return
    inlineCurrencyRates = inlineMathResult
}

private fun migrateInlineResults() {
    if (preferences.contains("inlineResults")) return
    inlineResults = inlineMathResult || inlineCurrencyRates
    if (!inlineResults) {
        inlineMathResult = true
        inlineCurrencyRates = true
    }
}

private fun migrateTransitionAnimation() {
    if (!preferences.contains("springAnimations") && !preferences.contains("aospTransitions")) return
    transitionAnimation = when {
        preferences.getBoolean("aospTransitions", false) -> TransitionAnimation.AOSP
        preferences.getBoolean("springAnimations", true) -> TransitionAnimation.SPRING
        else -> TransitionAnimation.DEFAULT
    }
    editor.remove("springAnimations").remove("aospTransitions").apply()
}

private fun migrateStickerTimeMode() {
    if (!preferences.contains("hideStickerTime")) return
    if (preferences.getBoolean("hideStickerTime", false))
        stickerTimeMode = StickerTimeMode.HIDDEN
    editor.remove("hideStickerTime").apply()
}

private fun migrateTabCounterMode() {
    if (!preferences.contains("tabCounter")) return
    if (!preferences.getBoolean("tabCounter", true))
        tabCounterMode = TabCounterMode.HIDDEN
    editor.remove("tabCounter").apply()
}

fun loadConfig() {
    synchronized(sync) {
        if (configLoaded) return

        setMode(preferences.getInt("bottomNavigationBarMode", SHOW))

        loadIconPacksLayout()

        doNotMarkAsNew = preferences.getString("doNotMarkAsNew", null)
            ?.let { GSON.fromJson(it, object : TypeToken<ArrayList<String>>() {}.type) }
            ?: ArrayList()

        newFeaturesShowedAt = preferences.getString("newFeaturesShowedAt", null)
            ?.let { GSON.fromJson(it, object : TypeToken<HashMap<String, Long>>() {}.type) }
            ?: HashMap()

        loadMainMenuLayout()

        if (!PluginsController.isPluginEngineSupported()) {
            pluginsEngine = false
            if (preferences.getBoolean("pluginsEngine", false))
                editor.putBoolean("pluginsEngine", false).apply()
        } else {
            pluginsEngine = preferences.getBoolean("pluginsEngine", false)
        }

        migrateProxyConditions()
        migrateTelegramAiInstantViewSummaries()
        migrateStickerTimeMode()
        migrateTransitionAnimation()
        migrateTabCounterMode()
        migrateInlineCurrencyRates()
        migrateInlineResults()

        TranslatorUtils.ensureTargetLanguageCompatibleWithProvider()

        configLoaded = true
    }
}

fun reloadConfig() {
    synchronized(sync) {
        configLoaded = false
        allDelegates.forEach { it.invalidate() }
        loadConfig()
    }
}

@JvmOverloads
fun getAvatarCorners(size: Float, px: Boolean = false, forum: Boolean = false): Int =
    getAvatarCorners(size, px, if (forum) AvatarCornerType.FORUM else AvatarCornerType.DEFAULT)

@JvmOverloads
fun getAvatarCorners(size: Float, px: Boolean, type: AvatarCornerType, allowSingleRadius: Boolean = true): Int {
    if (avatarCorners == 0f) return 0
    var radius = avatarCorners * size / 56.0f
    if (!px) radius = dp(radius).toFloat()
    if (!singleCornerRadius || !allowSingleRadius) {
        radius = when (type) {
            AvatarCornerType.DEFAULT -> radius
            AvatarCornerType.FORUM -> ((radius.toInt() * 42) shr 6).toFloat()
            AvatarCornerType.COMMUNITY -> radius * 40.0f / 72.0f
        }
    }
    return ceil(radius.toDouble()).toInt().coerceAtLeast(0)
}

fun getAvatarSquareness(): Float = (1.0f - avatarCorners / 28.0f).coerceIn(0.0f, 1.0f)

fun getOnlineDotOuterRadius(): Int = dp(7.0f + 2.0f * getAvatarSquareness())

fun getOnlineDotInnerRadius(): Int = dp(5.0f + getAvatarSquareness())

fun getOnlineDotOffset(baseOffset: Float, outerRadius: Float): Float {
    val target = (outerRadius / sqrt(2.0)).toFloat()
    return baseOffset + (target - baseOffset) * getAvatarSquareness()
}

fun getSectionRadiusDp(): Int = sanitizeSectionRadius(sectionRadius).roundToInt()

private fun sanitizeSectionRadius(value: Float): Float
    = if (value.isFinite()) value.coerceIn(0f, SECTION_RADIUS_MAX_DP.toFloat()) else DEFAULT_SECTION_RADIUS_DP.toFloat()

private fun systemConfigPrefs(): SharedPreferences =
    ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Activity.MODE_PRIVATE)

fun toggleLogging() {
    BuildVars.LOGS_ENABLED = !BuildVars.LOGS_ENABLED
    systemConfigPrefs().edit { putBoolean("logsEnabled", BuildVars.LOGS_ENABLED) }
    if (!BuildVars.LOGS_ENABLED) FileLog.cleanupLogs()
}

fun getLogging(): Boolean = systemConfigPrefs().getBoolean("logsEnabled", false)

fun setLogging(enabled: Boolean) {
    BuildVars.LOGS_ENABLED = enabled
    systemConfigPrefs().edit { putBoolean("logsEnabled", enabled) }
    if (!enabled) FileLog.cleanupLogs()
}

fun getCurrentLangName(): String = TranslatorUtils.getTargetLanguageTitle()

fun getDoubleTapSeekDurationMillis(): Int = when (doubleTapSeekDuration) {
    0, 1, 2 -> (doubleTapSeekDuration + 1) * 5000
    else -> 30000
}
