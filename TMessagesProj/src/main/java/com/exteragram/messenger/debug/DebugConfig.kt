/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

@file:JvmName("DebugConfig")
package com.exteragram.messenger.debug

import com.exteragram.messenger.config.BooleanPref
import com.exteragram.messenger.config.IntegerPref

var debugCameraMetrics by BooleanPref(false)
var forceCompactSavedMusic by BooleanPref(false)
var disableApiRequests by BooleanPref(false)
var disableChatFadeWallpaperBlend by BooleanPref(false)
var chatFadeUseWhiteBackground by BooleanPref(false)
var profileColorDesign by BooleanPref(false)
var heapMonitorEnabled by BooleanPref(false)
var heapMonitorLimitMb by IntegerPref(256)
var loadMonitorEnabled by BooleanPref(false)
var loadMonitorCpuPercent by IntegerPref(5)
var freezeMonitorEnabled by BooleanPref(false)
var freezeMonitorThresholdMs by IntegerPref(700)
