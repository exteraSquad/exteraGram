/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.nowplaying.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.exteragram.messenger.api.model.NowPlayingServiceType
import com.exteragram.messenger.nowplaying.NowPlayingController
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BotWebViewVibrationEffect
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DialogRadioCell
import org.telegram.ui.Cells.EditTextCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.CircularProgressDrawable
import org.telegram.ui.Components.CrossfadeDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView

class SetupNowPlayingActivity : BaseFragment() {
    private lateinit var nowPlayingEdit: EditTextCell
    private lateinit var listView: UniversalRecyclerView

    private data class NowPlayingState(
        val serviceType: NowPlayingServiceType,
        val username: String?
    )

    private var initialState: NowPlayingState? = null
    private var currentState: NowPlayingState = NowPlayingState(NowPlayingServiceType.NONE, null)

    private var doneButtonDrawable: CrossfadeDrawable? = null
    private var doneButton: ActionBarMenuItem? = null

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(getString(R.string.NowPlaying))
        actionBar.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment()
                    }
                } else if (id == 0) {
                    processDone(true)
                }
            }
        })

        val contentView = FrameLayout(context)
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))

        listView = UniversalRecyclerView(
            this,
            { items: ArrayList<UItem>, adapter: UniversalAdapter ->
                this.fillItems(
                    items,
                    adapter
                )
            },
            { item: UItem, view: View, position: Int, x: Float, y: Float ->
                this.onClick(
                    item,
                    view,
                    position,
                    x,
                    y
                )
            }, null
        )
        listView.setSections()
        actionBar.setAdaptiveBackground(listView)
        listView.adapter.setApplyBackground(false)
        contentView.addView(
            listView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat())
        )

        nowPlayingEdit = object : EditTextCell(
            context,
            getString(R.string.Username),
            false,
            false,
            50,
            resourceProvider
        ) {
            override fun onTextChanged(newText: CharSequence) {
                super.onTextChanged(newText)
                currentState = currentState.copy(username = newText.toString())
                checkDone(true)
            }
        }
        nowPlayingEdit.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite))
        nowPlayingEdit.hideKeyboardOnEnter()

        NowPlayingController.getNowPlayingInfo {
            AndroidUtilities.runOnUIThread {
                if (it != null) {
                    NowPlayingState(it.serviceType, it.username ?: "")
                } else {
                    NowPlayingState(NowPlayingServiceType.NONE, "")
                }.also {
                    currentState = it
                    initialState = it
                }
                nowPlayingEdit.text = currentState.username
                listView.adapter.update(true)
                checkDone(false)
            }
        }

        val checkmark = ContextCompat.getDrawable(context, R.drawable.ic_ab_done)?.mutate()
        checkmark?.colorFilter = PorterDuffColorFilter(
            Theme.getColor(Theme.key_actionBarDefaultIcon),
            PorterDuff.Mode.MULTIPLY
        )
        doneButtonDrawable = CrossfadeDrawable(
            checkmark, CircularProgressDrawable(
                Theme.getColor(
                    Theme.key_actionBarDefaultIcon
                )
            )
        )
        doneButton = actionBar.createMenu().addItemWithWidth(
            0,
            doneButtonDrawable,
            AndroidUtilities.dp(56f),
            getString(R.string.Done)
        )
        checkDone(false)

        return contentView.also { fragmentView = it }
    }

    private fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.SelectService)))
        for (s in NowPlayingServiceType.entries) {
            items.add(
                UItem.asRadio(s.ordinal, s.displayName).setChecked(s == currentState.serviceType && initialState != null)
            )
        }
        items.add(UItem.asShadow(getString(R.string.SelectServiceInfo)))
        if (currentState.serviceType != NowPlayingServiceType.NONE) {
            items.add(UItem.asHeader(getString(R.string.EnterUsername)))
            items.add(UItem.asCustom(nowPlayingEdit))
            val nowPlayingInfo: CharSequence = AndroidUtilities.replaceSingleTag(
                getString(R.string.EnterUsernameInfo)
            ) {
                Browser.openUrl(parentActivity, if (currentState.serviceType == NowPlayingServiceType.LAST_FM) "https://www.last.fm/" else "https://stats.fm/")
            }
            items.add(UItem.asShadow(nowPlayingInfo))
        }
    }

    private fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (view is DialogRadioCell) {
            val newService = NowPlayingServiceType.entries.toTypedArray()[item.id]
            currentState = currentState.copy(serviceType = newService)
            listView.adapter.update(true)
            checkDone(true)
        }
    }

    private var shiftDp = -4
    private fun processDone(error: Boolean) {
        if (doneButtonDrawable!!.progress > 0f) return

        if (error && currentState.serviceType != NowPlayingServiceType.NONE && nowPlayingEdit.text.isNullOrEmpty()) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate()
            AndroidUtilities.shakeViewSpring(
                nowPlayingEdit,
                (-shiftDp).also { shiftDp = it }.toFloat()
            )
            return
        }

        doneButtonDrawable!!.animateToProgress(1f)

        val nowPlayingInfoDTO = NowPlayingInfoDTO(
            currentState.serviceType,
            if (currentState.serviceType == NowPlayingServiceType.NONE) null else currentState.username
        )
        NowPlayingController.updateNowPlayingInfo(nowPlayingInfoDTO) {
            AndroidUtilities.runOnUIThread {
                if (!it) {
                    doneButtonDrawable!!.animateToProgress(0f)
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.UnknownError))
                        .show()
                } else {
                    notificationCenter.postNotificationName(NotificationCenter.nowPlayingUpdated, nowPlayingInfoDTO.serviceType)
                    finishFragment()
                }
            }
        }
    }

    private fun checkDone(animated: Boolean) {
        if (doneButton == null) return
        val hasChanges = initialState != currentState
        doneButton!!.isEnabled = hasChanges
        if (animated) {
            doneButton!!.animate()
                .alpha(if (hasChanges) 1.0f else 0.0f)
                .scaleX(if (hasChanges) 1.0f else 0.0f)
                .scaleY(if (hasChanges) 1.0f else 0.0f)
                .setDuration(180).start()
        } else {
            doneButton?.apply {
                alpha = if (hasChanges) 1.0f else 0.0f
                scaleX = if (hasChanges) 1.0f else 0.0f
                scaleY = if (hasChanges) 1.0f else 0.0f
            }
        }
    }
}