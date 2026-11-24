/*
 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.
*/

package com.exteragram.messenger.pillstack.ui

import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.exteragram.messenger.pillstack.core.PillRegistry
import com.exteragram.messenger.pillstack.core.PillType
import com.exteragram.messenger.pillstack.core.activePills
import com.exteragram.messenger.pillstack.core.getDefaultActivePills
import com.exteragram.messenger.pillstack.core.hiddenPills
import com.exteragram.messenger.pillstack.core.infiniteScrolling
import com.exteragram.messenger.pillstack.core.savePillsLayout
import com.exteragram.messenger.pillstack.custom.CustomPillIO
import com.exteragram.messenger.pillstack.custom.CustomPillSpec
import com.exteragram.messenger.pillstack.custom.CustomPillStore
import com.exteragram.messenger.pillstack.custom.CustomPillTemplates
import com.exteragram.messenger.pillstack.custom.ui.CustomPillEditorActivity
import com.exteragram.messenger.pillstack.custom.ui.PillImportSheet
import com.exteragram.messenger.pillstack.custom.ui.PillTemplatesSheet
import com.exteragram.messenger.pillstack.ui.pills.weather.WeatherSettingsActivity
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.utils.ui.AddButtonDrawable
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class PillStackPreferencesActivity : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    companion object {
        private const val ITEM_INFINITE_SCROLLING = 1000
        private const val ITEM_CREATE = 1001
    }

    private var activeSectionId = -1
    private var hiddenSectionId = -1

    private var createDrawable: AddButtonDrawable? = null
    private var observers: NotificationCenter.ObserversGroup? = null
    private var resetItem: ActionBarMenuItem? = null

    override fun getTitle(): String = getString(R.string.PillStackPills)

    override fun onFragmentCreate(): Boolean {
        observers = NotificationCenter.getGlobalInstance().createObserversGroup(this).add(NotificationCenter.pillStackLayoutChanged)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        observers?.removeAllObservers()
        observers = null
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.pillStackLayoutChanged && fragmentView != null) {
            listView.adapter?.update(true)
            updateResetButtonVisibility()
        }
    }

    override fun createView(context: Context): View {
        val view = super.createView(context)
        resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset).apply {
            contentDescription = getString(R.string.Reset)
            setOnClickListener { resetToDefault() }
        }
        updateResetButtonVisibility(false)

        listView.apply {
            setReorderHandleOnly(true)
            allowReorder(true)
            listenReorder(this@PillStackPreferencesActivity::updateConfigFromReorder)
        }

        return view
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.Settings)))
        items.add(uCheck(ITEM_INFINITE_SCROLLING, getString(R.string.PillStackInfiniteScrolling), infiniteScrolling, linkAlias = "pillStackInfiniteScrolling"))
        items.add(UItem.asShadow(null))

        adapter.whiteSectionStart()
        items.add(UItem.asHeader(getString(R.string.PillStackActivePills)))
        activeSectionId = adapter.reorderSectionStart()
        activePills.forEach { id -> createPillItem(id)?.let(items::add) }
        adapter.reorderSectionEnd()
        items.add(UItem.asButton(ITEM_CREATE, getCreateDrawable(), getString(R.string.CustomPillCreate)).accent().apply {
            pad = 65
            bind = Utilities.Callback { view -> (view as? TextCell)?.setImageLeft(18) }
            setSearchable(this@PillStackPreferencesActivity)
            setLinkAlias("pillStackCustom", this@PillStackPreferencesActivity)
        })
        adapter.whiteSectionEnd()
        items.add(UItem.asShadow(getString(R.string.PillStackPillsSettingsInfo)))

        hiddenSectionId = -1
        if (hiddenPills.isNotEmpty()) {
            adapter.whiteSectionStart()
            items.add(UItem.asHeader(getString(R.string.PillStackHiddenPills)))
            hiddenSectionId = adapter.reorderSectionStart()
            hiddenPills.forEach { id -> createPillItem(id)?.let(items::add) }
            adapter.reorderSectionEnd()
            adapter.whiteSectionEnd()
            items.add(UItem.asShadow(null))
        }
    }

    private fun getCreateDrawable(): AddButtonDrawable {
        val drawable = createDrawable ?: AddButtonDrawable(context).also { createDrawable = it }
        drawable.updateColors(getResourceProvider())
        return drawable
    }

    private fun createPillItem(id: Int): UItem? {
        val info = PillRegistry.getPillInfo(id) ?: return null
        val spec = CustomPillStore.get(id)
        val subtitle = when {
            spec != null -> PillImportSheet.getHost(spec)?.let { "$it · ${CustomPillEditorActivity.getRefreshName(spec.refresh)}" }
                ?: getString(R.string.CustomPillSourceNone)
            PillType.getById(id) != null -> null
            else -> getString(R.string.PillStackFromPlugin)
        }
        val row = PillStackPillCell.Row(info.name(), subtitle, info.iconRes(), spec?.icon, info.iconColorTop(), info.iconColorBottom())
        return PillStackPillCell.Factory.of(id, row)
    }

    private fun updateConfigFromReorder(sectionId: Int, uItems: ArrayList<UItem>) {
        val newIds = uItems.map { it.id }

        if (sectionId == activeSectionId) {
            activePills.clear()
            activePills.addAll(newIds)
        } else if (sectionId == hiddenSectionId) {
            hiddenPills.clear()
            hiddenPills.addAll(newIds)
        }

        saveAndNotify()
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            ITEM_INFINITE_SCROLLING -> toggleBooleanSettingAndRefresh(item) { infiniteScrolling = it }
            ITEM_CREATE -> createPill()
            else -> showPillOptions(item.id, view)
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        if (item.`object` is PillStackPillCell.Row) {
            showPillOptions(item.id, view)
            return true
        }
        return super.onLongClick(item, view, position, x, y)
    }

    private fun togglePill(id: Int) {
        if (activePills.contains(id)) {
            activePills.remove(Integer.valueOf(id))
            if (!hiddenPills.contains(id)) {
                hiddenPills.add(0, id)
            }
        } else if (hiddenPills.contains(id)) {
            hiddenPills.remove(Integer.valueOf(id))
            activePills.add(id)
        } else {
            return
        }

        saveAndNotify()
    }

    private fun showPillOptions(id: Int, anchor: View) {
        val spec = CustomPillStore.get(id)
        val options = ItemOptions.makeOptions(this, anchor)
        var toggle = false
        if (activePills.contains(id)) {
            options.add(R.drawable.menu_hide_gift, getString(R.string.Hide)) { toggle = true }
        } else if (hiddenPills.contains(id)) {
            options.add(R.drawable.msg_view_file, getString(R.string.Show)) { toggle = true }
        }
        options.setOnDismiss {
            if (toggle) {
                AndroidUtilities.runOnUIThread({ if (fragmentView != null) togglePill(id) }, 150)
            }
        }
        if (id == PillType.WEATHER.id) {
            options.add(R.drawable.msg_settings, getString(R.string.Settings)) { presentFragment(WeatherSettingsActivity()) }
        }
        if (spec != null) {
            options.add(R.drawable.msg_edit, getString(R.string.Edit)) { presentFragment(CustomPillEditorActivity(id)) }
                .add(R.drawable.msg_link, getString(R.string.CustomPillDuplicate)) { duplicate(spec) }
                .add(R.drawable.msg_share, getString(R.string.ShareFile)) { CustomPillIO.share(this, spec) }
                .add(R.drawable.msg_copy, getString(R.string.Copy)) { CustomPillIO.copy(this, spec) }
                .add(R.drawable.msg_delete, getString(R.string.Delete), true) { confirmDelete(spec) }
        }
        if (LocaleController.isRTL) {
            options.setGravity(Gravity.LEFT)
        }
        options.setScrimViewBackground(listView.getClipBackground(anchor))
            .show()
    }

    private fun createPill() {
        if (!CustomPillStore.canCreate()) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString(R.string.CustomPillLimitReached, CustomPillStore.MAX_PILLS)).show()
            return
        }
        showDialog(PillTemplatesSheet(this,
            { template -> presentFragment(CustomPillEditorActivity(CustomPillTemplates.create(template.id()))) },
            { spec -> CustomPillIO.showImport(this, spec) }))
    }

    private fun duplicate(spec: CustomPillSpec) {
        val id = CustomPillStore.allocateId()
        if (id == 0) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString(R.string.CustomPillLimitReached, CustomPillStore.MAX_PILLS)).show()
            return
        }
        val copy = spec.copy()
        copy.id = id
        val suffix = " " + getString(R.string.CustomPillCopySuffix)
        if (copy.name.length + suffix.length <= CustomPillSpec.MAX_NAME) {
            copy.name += suffix
        }
        CustomPillStore.save(copy, false, CustomPillStore.getSecrets(spec.id), CustomPillStore.getResponse(spec.id))
    }

    private fun confirmDelete(spec: CustomPillSpec) {
        val activity = parentActivity ?: return
        val dialog = AlertDialog.Builder(activity, getResourceProvider())
            .setTitle(getString(R.string.CustomPillDeleteTitle))
            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CustomPillDeleteText, spec.name)))
            .setPositiveButton(getString(R.string.Delete)) { _, _ -> CustomPillStore.delete(spec.id) }
            .setNegativeButton(getString(R.string.Cancel), null)
            .create()
        showDialog(dialog)
        (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)?.setTextColor(getThemedColor(Theme.key_text_RedBold))
    }

    private fun saveAndNotify() {
        savePillsLayout()
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged)
    }

    private fun updateResetButtonVisibility(animated: Boolean = true) {
        resetItem?.let { item ->
            val isModified = activePills != getDefaultActivePills()
            AndroidUtilities.updateViewVisibilityAnimated(item, isModified, 0.5f, animated)
        }
    }

    private fun resetToDefault() {
        activePills.clear()
        activePills.addAll(getDefaultActivePills())

        hiddenPills.clear()
        PillRegistry.getRegisteredPills().forEach { item ->
            if (!activePills.contains(item.id()))
                hiddenPills.add(item.id())
        }

        saveAndNotify()
    }
}
