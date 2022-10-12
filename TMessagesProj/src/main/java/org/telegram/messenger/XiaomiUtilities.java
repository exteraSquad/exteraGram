package org.telegram.messenger;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.lang.reflect.Method;

// MIUI. Redefining Android.
// (not in the very best way I'd say)
public class XiaomiUtilities {

	// custom permissions
	public static final int OP_AUTO_START = 10008;
	public static final int OP_DELETE_CONTACTS = 10012;
	public static final int OP_SHOW_WHEN_LOCKED = 10020;

	public static boolean isMIUI() {
		return !TextUtils.isEmpty(AndroidUtilities.getSystemProperty("ro.miui.ui.version.name"));
	}

	@SuppressWarnings("JavaReflectionMemberAccess")
	public static boolean isCustomPermissionGranted(int permission) {
		try {
			AppOpsManager mgr = (AppOpsManager) ApplicationLoader.applicationContext.getSystemService(Context.APP_OPS_SERVICE);
			Method m = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class, String.class);
			int result = (int) m.invoke(mgr, permission, android.os.Process.myUid(), ApplicationLoader.applicationContext.getPackageName());
			return result == AppOpsManager.MODE_ALLOWED;
		} catch (Exception x) {
			FileLog.e(x);
		}
		return true;
	}

	public static Intent getPermissionManagerIntent() {
		Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
		intent.putExtra("extra_package_uid", android.os.Process.myUid());
		intent.putExtra("extra_pkgname", ApplicationLoader.applicationContext.getPackageName());
		return intent;
	}
}
