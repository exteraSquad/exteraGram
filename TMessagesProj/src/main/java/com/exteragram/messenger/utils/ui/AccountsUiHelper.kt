/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.exteragram.messenger.getAvatarCorners
import com.exteragram.messenger.utils.ui.tabs.MainTabsUiHelper
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.Emoji
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import org.telegram.ui.LaunchActivity
import org.telegram.ui.LoginActivity
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.IntPredicate

object AccountsUiHelper {

    private const val ROW_WIDTH_DP = 230
    private const val ROW_HEIGHT_DP = 48
    private const val ROW_AVATAR_SIZE_DP = 34
    private const val ROW_AVATAR_INSET_DP = 1
    private const val ROW_AVATAR_INNER_SIZE_DP = ROW_AVATAR_SIZE_DP - ROW_AVATAR_INSET_DP * 2
    private const val SELECTED_AVATAR_SCALE = 0.833f
    private const val SELECTED_STROKE_WIDTH_DP = 1.33f

    @JvmStatic
    @JvmOverloads
    fun activated(filter: IntPredicate? = null): List<Int> =
        (0 until UserConfig.MAX_ACCOUNT_COUNT)
            .filter { UserConfig.getInstance(it).isClientActivated && filter?.test(it) != false }
            .sortedBy { UserConfig.getInstance(it).loginTime }

    @JvmStatic
    fun switchTo(account: Int) {
        LaunchActivity.instance?.switchToAccount(account, true)
    }

    @JvmStatic
    fun hasFreeSlot(): Boolean = UserConfig.getActivatedAccountsCount() < UserConfig.MAX_ACCOUNT_COUNT

    @JvmStatic
    fun freeSlotWithinLimit(): Int? {
        var freeAccounts = 0
        var availableAccount: Int? = null
        for (account in UserConfig.MAX_ACCOUNT_COUNT - 1 downTo 0) {
            if (!UserConfig.getInstance(account).isClientActivated) {
                freeAccounts++
                if (availableAccount == null) {
                    availableAccount = account
                }
            }
        }
        if (!UserConfig.hasPremiumOnAccounts()) {
            freeAccounts -= UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT
        }
        return if (freeAccounts > 0) availableAccount else null
    }

    @JvmStatic
    @JvmOverloads
    fun add(fragment: BaseFragment? = null) {
        val target = fragment ?: LaunchActivity.getSafeLastFragment()
        val slot = freeSlotWithinLimit()
        if (slot != null) {
            if (target != null) {
                target.presentFragment(LoginActivity(slot))
            } else {
                LaunchActivity.instance?.presentFragment(LoginActivity(slot))
            }
            return
        }
        if (UserConfig.hasPremiumOnAccounts()) {
            return
        }
        val context = target?.context ?: return
        target.showDialog(
            LimitReachedBottomSheet(target, context, LimitReachedBottomSheet.TYPE_ACCOUNTS, target.currentAccount, null)
        )
    }

    @JvmStatic
    fun row(
        context: Context,
        resourcesProvider: Theme.ResourcesProvider?,
        account: Int,
        selected: Boolean,
        top: Boolean,
        bottom: Boolean
    ): LinearLayout {
        val btn = LinearLayout(context)
        btn.orientation = LinearLayout.HORIZONTAL
        btn.background = Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 0)
        UIUtil.applyScaleStateListAnimator(btn, 12f, top, bottom, 3, 0.04f, 1.5f)

        val user = UserConfig.getInstance(account).currentUser

        val avatarDrawable = AvatarDrawable()
        avatarDrawable.setInfo(user)

        val avatarContainer = object : FrameLayout(context) {
            private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun dispatchDraw(canvas: Canvas) {
                if (selected) {
                    selectedPaint.style = Paint.Style.STROKE
                    selectedPaint.strokeWidth = dp(SELECTED_STROKE_WIDTH_DP).toFloat()
                    selectedPaint.color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)
                    val radius = getAvatarCorners(ROW_AVATAR_SIZE_DP.toFloat()).toFloat()
                    val inset = dp(1f).toFloat()
                    canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, selectedPaint)
                }
                super.dispatchDraw(canvas)
            }
        }
        btn.addView(
            avatarContainer,
            LayoutHelper.createLinear(ROW_AVATAR_SIZE_DP, ROW_AVATAR_SIZE_DP, Gravity.CENTER_VERTICAL, 12, 0, 0, 0)
        )

        val avatarView = BackupImageView(context)
        if (selected) {
            avatarView.scaleX = SELECTED_AVATAR_SCALE
            avatarView.scaleY = SELECTED_AVATAR_SCALE
        }
        avatarView.setRoundRadius(getAvatarCorners(ROW_AVATAR_INNER_SIZE_DP.toFloat()))
        avatarView.imageReceiver.currentAccount = account
        avatarView.setForUserOrChat(user, avatarDrawable)
        avatarContainer.addView(
            avatarView,
            LayoutHelper.createLinear(
                ROW_AVATAR_INNER_SIZE_DP, ROW_AVATAR_INNER_SIZE_DP, Gravity.CENTER,
                ROW_AVATAR_INSET_DP, ROW_AVATAR_INSET_DP, ROW_AVATAR_INSET_DP, ROW_AVATAR_INSET_DP
            )
        )

        val textView = TextView(context)
        NotificationCenter.listenEmojiLoading(textView)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider))
        textView.text = Emoji.replaceEmoji(UserObject.getUserName(user), textView.paint.fontMetricsInt, false)
        textView.maxLines = 2
        textView.ellipsize = TextUtils.TruncateAt.END
        btn.addView(
            textView,
            LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 13, 0, 14, 0)
        )

        return btn
    }

    @JvmStatic
    fun menu(fragment: BaseFragment, anchor: View): Builder = Builder(fragment, anchor)

    class Builder internal constructor(private val fragment: BaseFragment, private val anchor: View) {

        private var fromBottom = false
        private var withAddAccount = true
        private var touchRelayView: View? = null
        private var onSelected = IntConsumer { switchTo(it) }
        private var extraItems: Consumer<ItemOptions>? = null
        private var container: ViewGroup? = null
        private var filter: IntPredicate? = null
        private var gravity: Int? = null

        fun fromBottom(fromBottom: Boolean) = apply { this.fromBottom = fromBottom }

        fun withAddAccount(withAddAccount: Boolean) = apply { this.withAddAccount = withAddAccount }

        fun touchRelay(touchRelayView: View?) = apply { this.touchRelayView = touchRelayView }

        fun onSelected(onSelected: IntConsumer) = apply { this.onSelected = onSelected }

        fun extraItems(extraItems: Consumer<ItemOptions>?) = apply { this.extraItems = extraItems }

        fun container(container: ViewGroup?) = apply { this.container = container }

        fun filter(filter: IntPredicate?) = apply { this.filter = filter }

        fun gravity(gravity: Int) = apply { this.gravity = gravity }

        @SuppressLint("ClickableViewAccessibility")
        fun show(): Boolean {
            val context = fragment.context ?: return false
            val resourcesProvider = fragment.resourceProvider
            val currentAccount = fragment.currentAccount
            val accounts = activated(filter).let { if (fromBottom) it.asReversed() else it }

            val container = container
            val options = if (container != null) {
                ItemOptions.makeOptions(container, resourcesProvider, anchor, false, fromBottom)
            } else {
                ItemOptions.makeOptions(fragment, anchor)
            }
            val addAccount = withAddAccount && hasFreeSlot()
            if (addAccount && !fromBottom) {
                addAccountItem(options)
            }
            extraItems?.accept(options)

            if (accounts.isNotEmpty()) {
                touchRelayView?.setOnTouchListener { view, event ->
                    if (options.isShown) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        options.dispatchCapturedTouchEvent(event)
                    }
                    false
                }
                val hasItemsAbove = options.itemsCount > 0
                if (hasItemsAbove) {
                    options.addGap()
                }
                val hasItemsBelow = addAccount && fromBottom
                accounts.forEachIndexed { index, account ->
                    val row = row(
                        context, resourcesProvider, account, currentAccount == account,
                        index == 0 && !hasItemsAbove, index == accounts.size - 1 && !hasItemsBelow
                    )
                    row.setOnClickListener {
                        if (currentAccount != account) {
                            options.dismiss()
                            onSelected.accept(account)
                        }
                    }
                    options.addView(row, LayoutHelper.createLinear(ROW_WIDTH_DP, ROW_HEIGHT_DP))
                }
            }

            if (addAccount && fromBottom) {
                if (options.itemsCount > 0) {
                    options.addGap()
                }
                addAccountItem(options)
            }

            options.setBlur(true)
                .translate(0f, -dp(4f).toFloat())
                .setDismissOnMoveOutside(true)
            gravity?.let { options.setGravity(it) }
            if (container == null) {
                options.setScrimViewBackground(MainTabsUiHelper.createMainTabsScrimBackground(resourcesProvider, fromBottom))
            }
            options.show()
            return true
        }

        private fun addAccountItem(options: ItemOptions) {
            options.add(R.drawable.msg_addbot, getString(R.string.AddAccount)) { add(fragment) }
        }
    }
}