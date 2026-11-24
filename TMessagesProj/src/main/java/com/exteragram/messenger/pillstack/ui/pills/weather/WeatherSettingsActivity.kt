/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.exteragram.messenger.GSON
import com.exteragram.messenger.pillstack.core.PillType
import com.exteragram.messenger.pillstack.core.activePills
import com.exteragram.messenger.pillstack.core.customWeatherAddress
import com.exteragram.messenger.pillstack.core.customWeatherLocation
import com.exteragram.messenger.pillstack.core.notifySettingsChanged
import com.exteragram.messenger.pillstack.core.useCurrentLocation
import com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity
import com.exteragram.messenger.preferences.BasePreferencesActivity
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.messenger.WebFile
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.ChatAttachAlertLocationLayout
import org.telegram.ui.Components.ClipRoundedDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.LocationActivity
import org.telegram.ui.Stories.recorder.Weather
import java.util.ArrayList
import kotlin.math.ceil
import kotlin.math.min

class WeatherSettingsActivity : BasePreferencesActivity() {

    companion object {
        private const val ITEM_SHOW_WEATHER = 1
        private const val ITEM_USE_CURRENT_LOCATION = 2
        private const val ITEM_USE_CUSTOM_LOCATION = 3
        private const val ITEM_ENABLE_LOCATION = 4
        private const val ITEM_MAP_PREVIEW = 5
        private const val ITEM_ADDRESS = 6
    }

    private lateinit var mapPreviewContainer: FrameLayout
    private lateinit var mapMarker: View
    private var mapLoadingDrawable: ClipRoundedDrawable? = null
    private lateinit var mapPreview: BackupImageView
    private var currentGeo: TLRPC.GeoPoint? = null
    private lateinit var addressContainer: FrameLayout
    private lateinit var addressText: TextView

    override fun getTitle(): String = getString(R.string.WeatherPill)

    override fun needHideTitle(): Boolean = true

    override fun createView(context: Context): View {
        addressText = TextView(context)
        addressText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        addressText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        addressText.gravity = Gravity.CENTER
        addressText.setPadding(0, 0, 0, 0)

        addressContainer = FrameLayout(context)
        addressContainer.addView(addressText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP, 21f, 15f, 21f, 15f))
        addressContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite))

        mapPreview = object : BackupImageView(context) {
            override fun createImageReciever(): ImageReceiver = object : ImageReceiver(this) {
                override fun setImageBitmapByKey(drawable: Drawable?, key: String?, type: Int, memCache: Boolean, guid: Int): Boolean {
                    if (drawable != null && type != TYPE_THUMB) {
                        mapMarker.animate().alpha(1f).translationY(0f).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).setDuration(250).start()
                    }
                    return super.setImageBitmapByKey(drawable, key, type, memCache, guid)
                }
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(240f), MeasureSpec.EXACTLY))
            }

            override fun verifyDrawable(who: Drawable): Boolean = who === mapLoadingDrawable || super.verifyDrawable(who)
        }

        val svgThumb = DocumentObject.getSvgThumb(R.raw.map_placeholder, Theme.key_chat_outLocationIcon, 0.2f)
        svgThumb.setColorKey(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)
        svgThumb.setAspectCenter(true)
        svgThumb.setParent(mapPreview.getImageReceiver())
        val loadingDrawable = ClipRoundedDrawable(svgThumb)
        mapLoadingDrawable = loadingDrawable
        loadingDrawable.callback = mapPreview
        mapPreview.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite))

        mapMarker = object : View(context) {
            private val pin = requireNotNull(ContextCompat.getDrawable(context, R.drawable.map_pin_photo)).mutate()
            private val avatarDrawable = AvatarDrawable()
            private val avatarImage = ImageReceiver(this)

            init {
                avatarDrawable.setInfo(userConfig.currentUser)
                avatarImage.setForUserOrChat(userConfig.currentUser, avatarDrawable)
            }

            override fun dispatchDraw(canvas: Canvas) {
                pin.setBounds(0, 0, dp(62f), dp(85f))
                pin.draw(canvas)
                avatarImage.setRoundRadius(dp(62f))
                avatarImage.setImageCoords(dp(6f).toFloat(), dp(6f).toFloat(), dp(50f).toFloat(), dp(50f).toFloat())
                avatarImage.draw(canvas)
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(dp(62f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(85f), MeasureSpec.EXACTLY))
            }
        }

        mapPreviewContainer = FrameLayout(context)
        mapPreviewContainer.addView(mapPreview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
        mapPreviewContainer.addView(mapMarker, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, -31f, 0f, 0f))
        mapPreviewContainer.setOnClickListener { openMapPicker() }

        customWeatherLocation?.let { json ->
            try {
                currentGeo = GSON.fromJson(json, TLRPC.TL_geoPoint::class.java)
            } catch (_: Exception) {
            }
        }
        updateMapPreview()

        return super.createView(context)
    }

    private fun updateMapPreview() {
        val geo = currentGeo
        if (geo == null) {
            mapPreview.setImageBitmap(null)
            return
        }

        mapMarker.alpha = 0f
        mapMarker.translationY = -dp(12f).toFloat()
        val w = ((if (mapPreview.measuredWidth <= 0) AndroidUtilities.displaySize.x else mapPreview.measuredWidth) / AndroidUtilities.density).toInt()
        val h = 240
        val scale = min(2, ceil(AndroidUtilities.density).toInt())
        mapPreview.setImage(ImageLocation.getForWebFile(WebFile.createWithGeoPoint(geo.lat, geo._long, 0L, scale * w, scale * h, 15, scale)), "${w}_$h", mapLoadingDrawable, 0, null)
        addressText.text = customWeatherAddress
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asTopView(getTitle(), getString(R.string.WeatherPillTopInfo), "RestrictedEmoji", "\uD83C\uDF24"))

        items.add(UItem.asButton(ITEM_SHOW_WEATHER, R.drawable.msg_settings_old, getString(R.string.PillStackPills)))
        items.add(UItem.asShadow())

        if (!activePills.contains(PillType.WEATHER.id)) return

        items.add(UItem.asHeader(getString(R.string.WeatherLocation)))
        items.add(UItem.asRadio(ITEM_USE_CURRENT_LOCATION, getString(R.string.CurrentLocation)).setChecked(useCurrentLocation))
        items.add(UItem.asRadio(ITEM_USE_CUSTOM_LOCATION, getString(R.string.SelectLocation)).setChecked(!useCurrentLocation))

        if (useCurrentLocation) {
            items.add(UItem.asShadow(getString(R.string.WeatherSettingsInfo)))
            if (!Weather.isLocationPermissionGranted()) {
                items.add(UItem.asButton(ITEM_USE_CURRENT_LOCATION, R.drawable.report, getString(R.string.WeatherLocationPermissionGrant)).red())
                items.add(UItem.asShadow(getString(R.string.WeatherLocationPermissionNo)))
            } else if (!Weather.isLocationEnabled()) {
                items.add(UItem.asButton(ITEM_ENABLE_LOCATION, R.drawable.filled_location, getString(R.string.WeatherLocationServicesEnable)).accent())
                items.add(UItem.asShadow(getString(R.string.GpsDisabledAlertText)))
            }
        } else {
            items.add(UItem.asCustom(ITEM_MAP_PREVIEW, mapPreviewContainer))
            if (!customWeatherAddress.isNullOrEmpty()) {
                items.add(UItem.asCustom(ITEM_ADDRESS, addressContainer))
            }
            items.add(UItem.asShadow(getString(R.string.WeatherSettingsInfo)))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            ITEM_SHOW_WEATHER -> presentFragment(PillStackPreferencesActivity())
            ITEM_USE_CURRENT_LOCATION -> {
                if (!useCurrentLocation || !Weather.isLocationPermissionGranted()) {
                    Weather.getUserLocation(true) { location ->
                        if (location != null) {
                            updateLocationSetting(true)
                        }
                    }
                }
            }
            ITEM_ENABLE_LOCATION -> Weather.getUserLocation(true) { location ->
                if (location != null && fragmentView != null) {
                    listView.adapter?.update(true)
                }
            }
            ITEM_USE_CUSTOM_LOCATION -> {
                if (useCurrentLocation) {
                    if (currentGeo == null) {
                        currentGeo = TLRPC.TL_geoPoint().apply { // default is Moscow
                            lat = 55.7558
                            _long = 37.6173
                        }
                        customWeatherLocation = GSON.toJson(currentGeo)
                    }
                    updateLocationSetting(false)
                }
                openMapPicker()
            }
            ITEM_MAP_PREVIEW, ITEM_ADDRESS -> openMapPicker()
        }
    }

    private fun updateLocationSetting(useCurrent: Boolean) {
        useCurrentLocation = useCurrent
        notifySettingsChanged(PillType.WEATHER.id)
        if (fragmentView != null) {
            updateMapPreview()
            listView.adapter?.update(true)
        }
    }

    private fun openMapPicker() {
        val locationFragment = LocationActivity(ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ)
        currentGeo?.let { geo ->
            val initialLocation = TLRPC.TL_channelLocation()
            initialLocation.geo_point = geo
            initialLocation.address = customWeatherAddress
            locationFragment.setInitialLocation(initialLocation)
        }
        locationFragment.setDelegate { location, _, _, _, _ ->
            currentGeo = location.geo
            val address = locationFragment.addressName ?: ""

            customWeatherLocation = GSON.toJson(currentGeo)
            customWeatherAddress = address

            updateLocationSetting(false)
        }
        presentFragment(locationFragment)
    }
}
