/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.ui.activities

import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.exteragram.messenger.PLACEHOLDERS
import com.exteragram.messenger.ai.AiController
import com.exteragram.messenger.ai.data.Role
import com.exteragram.messenger.ai.setSelectedAiRole
import com.exteragram.messenger.ai.ui.components.RoleCell
import com.exteragram.messenger.preferences.BasePreferencesActivity
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AIEditorAlert
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class RolesActivity : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    companion object {
        private const val NAME_LIMIT = 64
        private const val PROMPT_LIMIT = 1024
    }

    override fun onFragmentCreate(): Boolean {
        notificationCenter.addObserver(this, NotificationCenter.rolesUpdated)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        notificationCenter.removeObserver(this, NotificationCenter.rolesUpdated)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.rolesUpdated) {
            listView.adapter?.update(true)
        }
    }

    override fun createView(context: Context): View {
        val view = super.createView(context)
        actionBar.createMenu().addItem(0, R.drawable.msg_add)
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                when (id) {
                    -1 -> finishFragment()
                    0 -> showRoleAlert(null)
                }
            }
        })
        return view
    }

    override fun getTitle(): String = getString(R.string.Roles)

    override fun needHideTitle(): Boolean = true

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asTopView(getTitle(), getString(R.string.RolesInfo), PLACEHOLDERS, "\uD83C\uDFAD"))
        items.add(UItem.asHeader(getString(R.string.Suggestions)))
        AiController.getInstance().suggestedRoles.forEach { role ->
            items.add(RoleCell.Factory.asRoleCell(role) { selectRole(role) })
        }
        items.add(UItem.asShadow(null))

        val roles = AiController.getInstance().roles
        if (roles.isNotEmpty()) {
            items.add(UItem.asHeader(getString(R.string.Roles)))
            roles.forEach { role ->
                items.add(RoleCell.Factory.asRoleCell(role) { selectRole(role) })
            }
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val role = item.`object` as? Role ?: return
        if (role.isSuggestion) {
            showRolePreview(role)
        } else {
            showRoleAlert(role)
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val role = item.`object` as? Role
        if (role == null || role.isSuggestion) return false

        ItemOptions.makeOptions(this, view).apply {
            add(R.drawable.msg_edit, getString(R.string.Edit)) { showRoleAlert(role) }
            add(R.drawable.msg_copy, getString(R.string.Copy)) {
                if (AndroidUtilities.addToClipboard("${role.name}\n${role.prompt}"))
                    BulletinFactory.of(this@RolesActivity).createCopyBulletin(getString(R.string.TextCopied)).show()
            }
            add(R.drawable.msg_delete, getString(R.string.Delete), true) { confirmDeleteRole(role) }
            setScrimViewBackground(listView.getClipBackground(view))
            setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)
            setDismissOnMoveOutside(true)
            show()
        }
        return true
    }

    private fun confirmDeleteRole(role: Role) {
        val activity = parentActivity ?: return
        val dialog = AlertDialog.Builder(activity)
            .setTitle(getString(R.string.Delete))
            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeleteRoleInfo, role.name)))
            .setPositiveButton(getString(R.string.Delete)) { _, _ -> deleteRole(role) }
            .setNegativeButton(getString(R.string.Cancel), null)
            .create()
        showDialog(dialog)
        (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)?.setTextColor(Theme.getColor(Theme.key_text_RedBold))
    }

    private fun deleteRole(role: Role) {
        val wasSelected = role.isSelected
        if (!AiController.getInstance().removeRole(role)) return

        if (wasSelected) {
            setSelectedAiRole(AiController.getInstance().suggestedRoles[0])
        }
        listView.adapter?.update(true)
    }

    private fun selectRole(role: Role) {
        if (role.isSelected) return
        setSelectedAiRole(role)
        listView.adapter?.update(true)
    }

    private fun showRoleAlert(role: Role?) {
        val activity = parentActivity ?: return

        AIEditorAlert.CreateAiStyleAlert(activity, resourceProvider)
            .setLocalStyle(
                role?.name ?: "",
                role?.prompt ?: "",
                role?.emojiId ?: 0L,
                role != null && !role.isSuggestion,
                NAME_LIMIT,
                PROMPT_LIMIT
            ) { name, prompt, emojiId ->
                val newRole = Role(name, prompt).setEmojiId(emojiId ?: 0L)
                val wasSelected = role != null && role.isSelected
                val updated = if (role != null && !role.isSuggestion) {
                    AiController.getInstance().updateRole(role, newRole)
                } else {
                    AiController.getInstance().addRole(newRole)
                }

                if (updated) {
                    if (wasSelected) setSelectedAiRole(newRole)
                    notificationCenter.postNotificationName(NotificationCenter.rolesUpdated)
                }
                updated
            }
            .show()
    }

    private fun showRolePreview(role: Role) {
        val activity = parentActivity ?: return

        AIEditorAlert.CreateAiStyleAlert(activity, resourceProvider)
            .setLocalStylePreview(role.name, role.prompt, role.emojiId, NAME_LIMIT, PROMPT_LIMIT)
            .show()
    }
}
