/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import android.graphics.Paint;
import org.telegram.messenger.AndroidUtilities;

// small hack to fix system font in places where TextPaint is used without setTypeface
public class TextPaint extends android.text.TextPaint {

    public TextPaint() {
        super();
        setTypeface(AndroidUtilities.regular());
    }

    public TextPaint(int flags) {
        super(flags);
        setTypeface(AndroidUtilities.regular());
    }

    public TextPaint(Paint p) {
        super(p);
        setTypeface(AndroidUtilities.regular());
    }
}