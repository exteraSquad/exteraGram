/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.exteragram.messenger.editingIconPackId
import com.exteragram.messenger.icons.iconPacksHidden
import com.exteragram.messenger.icons.iconPacksLayout
import com.exteragram.messenger.icons.BaseIconPacks
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.icons.IconPack
import com.exteragram.messenger.icons.ui.components.IconPackCell
import com.exteragram.messenger.icons.ui.components.NewIconPackBottomSheet
import com.exteragram.messenger.icons.ui.picker.IconPickerController
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.icons.saveIconPacksLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.FragmentFloatingButton
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.LaunchActivity
import java.util.ArrayList

class IconPacksActivity : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    private lateinit var floatingButton: FragmentFloatingButton
    private var scrollUpdated = false
    private var reorderRunnable: Runnable? = null

    override fun onFragmentCreate(): Boolean {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.iconPackUpdated)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        super.onFragmentDestroy()
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.iconPackUpdated)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.iconPackUpdated && fragmentView != null) {
            listView.adapter?.update(true)
        }
    }

    override fun onInsets(left: Int, top: Int, right: Int, bottom: Int) {
        floatingButton.translationY = -bottom.toFloat()
        super.onInsets(left, top, right, bottom)
    }

    override fun createView(context: Context): View {
        val view = super.createView(context)

        floatingButton = FragmentFloatingButton(context, resourceProvider)
        floatingButton.imageView.setImageResource(R.drawable.msg_add)
        floatingButton.contentDescription = getString(R.string.Add)
        floatingButton.setOnClickListener { NewIconPackBottomSheet(this, context).show() }
        (view as? FrameLayout)?.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams())

        listView.allowReorder(true)
        listView.setReorderHandleOnly(true)
        listView.listenReorder(::updateConfigFromReorder)
        listView.setOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 && scrollUpdated) {
                    floatingButton.setButtonVisible(dy < 0, true)
                }
                scrollUpdated = true
            }
        })
        return view
    }

    private fun updateConfigFromReorder(sectionId: Int, uItems: ArrayList<UItem>) {
        val newIds = uItems.mapNotNull { (it.`object` as? IconPack)?.id }
        val isEnabledSection = countCustomPacks(iconPacksLayout) > 1 && sectionId == 0

        if (isEnabledSection) {
            val activeBaseId = iconPacksLayout.firstOrNull { it.startsWith(IconPack.PREFIX_BASE) }
            iconPacksLayout.clear()
            iconPacksLayout.addAll(newIds)
            if (activeBaseId != null) {
                iconPacksLayout.add(activeBaseId)
            }
        } else {
            iconPacksHidden.clear()
            iconPacksHidden.addAll(newIds)
        }

        saveIconPacksLayout()
        if (isEnabledSection) {
            reorderRunnable?.let { AndroidUtilities.cancelRunOnUIThread(it) }
            val runnable = Runnable { IconManager.initialize(true) }
            reorderRunnable = runnable
            AndroidUtilities.runOnUIThread(runnable, 500)
        }
        if (fragmentView != null) {
            listView.adapter?.update(true)
        }
    }

    override fun getTitle(): String = getString(R.string.IconPacks)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val activeBaseId = iconPacksLayout.firstOrNull { it.startsWith(IconPack.PREFIX_BASE) } ?: IconPack.ID_DEFAULT

        adapter.whiteSectionStart()
        items.add(UItem.asHeader(getString(R.string.BasePacks)))
        for (id in arrayOf(IconPack.ID_DEFAULT, IconPack.ID_SOLAR, IconPack.ID_REMIX)) {
            val pack = getPackById(id) ?: continue
            val item = IconPackCell.Factory.asIconPackCell(pack)
            item.checked = id == activeBaseId
            items.add(item)
        }
        adapter.whiteSectionEnd()
        items.add(UItem.asShadow(getString(R.string.BaseIconPackInfo)))

        val enabledCustomCount = countCustomPacks(iconPacksLayout)
        if (enabledCustomCount > 0) {
            addCustomPacksSection(items, adapter, getString(R.string.EnabledPacks), iconPacksLayout, enabledCustomCount > 1)
        }

        val hiddenCustomCount = countCustomPacks(iconPacksHidden)
        if (hiddenCustomCount > 0) {
            if (enabledCustomCount > 0) {
                items.add(UItem.asShadow())
            }
            addCustomPacksSection(items, adapter, getString(R.string.AllPacks), iconPacksHidden, hiddenCustomCount > 1)
        }

        if (enabledCustomCount > 0 || hiddenCustomCount > 0) {
            items.add(UItem.asShadow(getString(R.string.IconPacksHint)))
        }
    }

    private fun addCustomPacksSection(items: ArrayList<UItem>, adapter: UniversalAdapter, header: String, ids: List<String>, reorderable: Boolean) {
        adapter.whiteSectionStart()
        items.add(UItem.asHeader(header))
        if (reorderable) adapter.reorderSectionStart()
        for (id in ids) {
            if (id.startsWith(IconPack.PREFIX_BASE)) continue
            val pack = getPackById(id) ?: continue
            items.add(IconPackCell.Factory.asIconPackCell(pack).setReordering(reorderable))
        }
        if (reorderable) adapter.reorderSectionEnd()
        adapter.whiteSectionEnd()
    }

    private fun countCustomPacks(ids: List<String>): Int =
        ids.count { !it.startsWith(IconPack.PREFIX_BASE) && getPackById(it) != null }

    private fun getPackById(id: String): IconPack? =
        if (id.startsWith(IconPack.PREFIX_BASE)) BaseIconPacks.getBasePack(id) else IconManager.findPackById(id)

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val id = (item.`object` as? IconPack)?.id ?: return

        if (id.startsWith(IconPack.PREFIX_BASE)) {
            iconPacksLayout.removeAll { it.startsWith(IconPack.PREFIX_BASE) }
            iconPacksLayout.add(id)
        } else if (iconPacksLayout.contains(id)) {
            iconPacksLayout.remove(id)
            if (!iconPacksHidden.contains(id)) {
                iconPacksHidden.add(0, id)
            }
        } else if (iconPacksHidden.contains(id)) {
            iconPacksHidden.remove(id)
            iconPacksLayout.add(0, id)
        }
        saveIconPacksLayout()
        IconManager.initialize(true)
        listView.adapter?.update(true)
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val pack = item.`object` as? IconPack ?: return super.onLongClick(item, view, position, x, y)
        if (pack.id.startsWith(IconPack.PREFIX_BASE)) return false

        ItemOptions.makeOptions(this, view).apply {
            addIf(iconPacksLayout.contains(pack.id), R.drawable.msg_edit, getString(R.string.Edit)) { openEditor(pack) }
            add(R.drawable.msg_share, getString(R.string.ShareFile)) { sharePack(pack) }
            add(R.drawable.msg_delete, getString(R.string.Delete), true) { confirmDeletePack(pack) }
            setScrimViewBackground(listView.getClipBackground(view))
            setDismissOnMoveOutside(true)
            show()
        }
        return true
    }

    private fun openEditor(pack: IconPack) {
        editingIconPackId = pack.id
        IconPickerController.setActive(parentActivity as LaunchActivity, true)
        presentFragment(IconPacksEditorActivity(pack))
    }

    private fun sharePack(pack: IconPack) {
        Utilities.globalQueue.postRunnable {
            val bundle = IconManager.bundlePackBlocking(pack.id) ?: return@postRunnable
            AndroidUtilities.runOnUIThread {
                val activity = parentActivity ?: return@runOnUIThread
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "application/zip"
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", bundle))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.startActivity(Intent.createChooser(intent, getString(R.string.ShareFile)))
            }
        }
    }

    private fun confirmDeletePack(pack: IconPack) {
        val dialog = AlertDialog.Builder(parentActivity, resourceProvider)
            .setTitle(getString(R.string.DeletePack))
            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeletePackInfo, pack.name)))
            .setPositiveButton(getString(R.string.Delete)) { _, _ -> IconManager.deletePack(pack.id) }
            .setNegativeButton(getString(R.string.Cancel), null)
            .create()
        dialog.show()
        (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)?.setTextColor(Theme.getColor(Theme.key_text_RedBold))
    }
}
