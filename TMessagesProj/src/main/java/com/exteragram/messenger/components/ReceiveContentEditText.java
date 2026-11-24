/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import static androidx.core.view.ContentInfoCompat.FLAG_CONVERT_TO_PLAIN_TEXT;
import static androidx.core.view.ContentInfoCompat.SOURCE_CLIPBOARD;
import static androidx.core.view.ContentInfoCompat.SOURCE_DRAG_AND_DROP;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.os.Build;
import android.text.Editable;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.OnReceiveContentViewBehavior;
import androidx.core.view.ViewCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.widget.TextViewOnReceiveContentListener;

import com.exteragram.messenger.inline.InlineMathController;

@SuppressLint({"RestrictedApi", "AppCompatCustomView"})
public class ReceiveContentEditText extends EditText implements OnReceiveContentViewBehavior {

    private final TextViewOnReceiveContentListener defaultOnReceiveContentListener;

    private InlineMathController inlineMath;

    public ReceiveContentEditText(@NonNull Context context) {
        super(context);
        defaultOnReceiveContentListener = new TextViewOnReceiveContentListener();
    }

    public ReceiveContentEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        defaultOnReceiveContentListener = new TextViewOnReceiveContentListener();
    }

    public ReceiveContentEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        defaultOnReceiveContentListener = new TextViewOnReceiveContentListener();
    }

    public ReceiveContentEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        defaultOnReceiveContentListener = new TextViewOnReceiveContentListener();
    }

    @Override
    @Nullable
    public Editable getText() {
        if (Build.VERSION.SDK_INT >= 28) {
            return super.getText();
        }
        return super.getEditableText();
    }

    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo outAttrs) {
        InputConnection ic = super.onCreateInputConnection(outAttrs);
        if (ic != null && Build.VERSION.SDK_INT <= 30) {
            String[] mimeTypes = ViewCompat.getOnReceiveContentMimeTypes(this);
            if (mimeTypes != null) {
                EditorInfoCompat.setContentMimeTypes(outAttrs, mimeTypes);
                ic = InputConnectionCompat.createWrapper(this, ic, outAttrs);
            }
        }
        if (ic != null && inlineMath != null) {
            ic = inlineMath.wrap(ic);
        }
        return ic;
    }

    public void setInlineMath(InlineMathController controller) {
        inlineMath = controller;
        requestLayout();
    }

    protected InlineMathController getInlineMath() {
        return inlineMath;
    }

    @Override
    public int getCompoundPaddingBottom() {
        return super.getCompoundPaddingBottom() + (inlineMath != null ? inlineMath.getExtraBottom() : 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (inlineMath != null && inlineMath.updateOnMeasure()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (inlineMath != null) {
            inlineMath.onTextChanged();
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (inlineMath != null) {
            inlineMath.invalidateState();
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (inlineMath != null) {
            inlineMath.onFocusChanged(focused);
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        if (inlineMath != null) {
            inlineMath.cancel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (inlineMath != null) {
            inlineMath.cancel();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (inlineMath != null && event.getAction() == MotionEvent.ACTION_DOWN) {
            inlineMath.onTouchDown();
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (inlineMath != null && inlineMath.onKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        if (handleDragEventViaReceiveContent(event)) {
            return true;
        }
        return super.onDragEvent(event);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (handleMenuActionViaReceiveContent(id)) {
            return true;
        }
        return super.onTextContextMenuItem(id);
    }

    @Nullable
    @Override
    public ContentInfoCompat onReceiveContent(@NonNull ContentInfoCompat payload) {
        return defaultOnReceiveContentListener.onReceiveContent(this, payload);
    }

    private boolean handleMenuActionViaReceiveContent(int actionId) {
        if (ViewCompat.getOnReceiveContentMimeTypes(this) == null
                || !(actionId == android.R.id.paste || actionId == android.R.id.pasteAsPlainText)) {
            return false;
        }
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboardManager == null ? null : clipboardManager.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            ContentInfoCompat payload = new ContentInfoCompat.Builder(clip, SOURCE_CLIPBOARD)
                    .setFlags(actionId == android.R.id.paste ? 0 : FLAG_CONVERT_TO_PLAIN_TEXT)
                    .build();
            ViewCompat.performReceiveContent(this, payload);
        }
        return true;
    }

    private boolean handleDragEventViaReceiveContent(@NonNull DragEvent event) {
        if (Build.VERSION.SDK_INT >= 31
                || event.getLocalState() != null
                || ViewCompat.getOnReceiveContentMimeTypes(this) == null) {
            return false;
        }
        Activity activity = findActivity();
        if (activity == null) {
            return false;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            return false;
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            return OnDropApi24Impl.onDropForTextView(event, this, activity);
        }
        return false;
    }

    @Nullable
    private Activity findActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static final class OnDropApi24Impl {

        private OnDropApi24Impl() {
        }

        @DoNotInline
        static boolean onDropForTextView(@NonNull DragEvent event, @NonNull ReceiveContentEditText view, @NonNull Activity activity) {
            activity.requestDragAndDropPermissions(event);
            int offset = view.getOffsetForPosition(event.getX(), event.getY());
            view.beginBatchEdit();
            try {
                Selection.setSelection(view.getText(), offset);
                ContentInfoCompat payload = new ContentInfoCompat.Builder(event.getClipData(), SOURCE_DRAG_AND_DROP).build();
                ViewCompat.performReceiveContent(view, payload);
            } finally {
                view.endBatchEdit();
            }
            return true;
        }
    }
}
