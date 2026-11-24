/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui

import com.exteragram.messenger.DividerStyle
import com.exteragram.messenger.getApiBotInfo
import com.exteragram.messenger.dividerStyle
import com.exteragram.messenger.getSectionRadiusDp
import com.exteragram.messenger.sectionsSeparatedHeaders
import org.json.JSONObject
import org.telegram.tgnet.TLRPC

object WebAppSectionStyle {

    @JvmStatic
    fun apply(themeParams: JSONObject, botUser: TLRPC.User?) {
        if (botUser == null || botUser.id != getApiBotInfo().first) return
        themeParams.put("extera_divider_style", when (dividerStyle) {
            DividerStyle.HIDDEN -> "hidden"
            DividerStyle.LINE -> "line"
            DividerStyle.SEGMENTS -> "segments"
        })
        themeParams.put("extera_section_radius", getSectionRadiusDp())
        themeParams.put("extera_separated_headers", sectionsSeparatedHeaders)
    }
}
