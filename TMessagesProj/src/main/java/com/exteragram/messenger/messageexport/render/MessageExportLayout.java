/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.os.SystemClock;

import com.exteragram.messenger.messageexport.model.MessageExportDocument;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.List;

final class MessageExportLayout {

    interface Callback {
        void onMeasured(MessageExportLayout layout);
    }

    private static final int FRAME_BUDGET_MS = 8;

    final ArrayList<MessageExportDocument.Block> blocks;
    final int width;
    final int[] tops;
    final int[] heights;
    int totalHeight;
    int contentBottom;

    private final MessageExportRenderHost host;
    private final Callback callback;
    private int measured;
    private boolean cancelled;

    private MessageExportLayout(List<MessageExportDocument.Block> blocks, MessageExportRenderHost host, int width, Callback callback) {
        this.host = host;
        this.callback = callback;
        this.blocks = new ArrayList<>(blocks);
        this.width = width;
        this.tops = new int[this.blocks.size()];
        this.heights = new int[this.blocks.size()];
    }

    static MessageExportLayout measure(List<MessageExportDocument.Block> blocks, MessageExportRenderHost host, int width, Callback callback) {
        MessageExportLayout layout = new MessageExportLayout(blocks, host, width, callback);
        AndroidUtilities.runOnUIThread(layout::step);
        return layout;
    }

    void cancel() {
        cancelled = true;
    }

    private void step() {
        if (cancelled) {
            return;
        }
        long start = SystemClock.elapsedRealtime();
        while (measured < blocks.size() && SystemClock.elapsedRealtime() - start < FRAME_BUDGET_MS) {
            MessageExportListView view = host.obtain(blocks.get(measured), width);
            heights[measured] = view.getMeasuredHeight();
            measured++;
        }
        if (measured < blocks.size()) {
            AndroidUtilities.runOnUIThread(this::step);
            return;
        }
        int padding = dp(8);
        long y = padding;
        for (int i = 0; i < blocks.size(); i++) {
            tops[i] = (int) Math.min(Integer.MAX_VALUE, y);
            y += heights[i];
        }
        contentBottom = (int) Math.min(Integer.MAX_VALUE, y);
        totalHeight = (int) Math.min(Integer.MAX_VALUE, y + MessageExportWatermark.getBottomInset());
        callback.onMeasured(this);
    }
}
