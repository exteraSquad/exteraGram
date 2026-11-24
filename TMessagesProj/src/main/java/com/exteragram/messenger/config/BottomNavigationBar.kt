/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

@file:JvmName("BottomNavigationBar")

package com.exteragram.messenger.config

const val SHOW = 0
const val HIDE = 1
const val FLOATING = 2

private var mode = SHOW

fun getMode(): Int = mode.coerceIn(SHOW, FLOATING).also { mode = it }
fun setMode(value: Int) { mode = value; getMode() }
fun hidden(): Boolean = getMode() == HIDE
fun visible(): Boolean = getMode() != HIDE
fun floating(): Boolean = getMode() == FLOATING
