/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import android.content.Context;
import android.view.View;

import com.exteragram.messenger.messageexport.model.MessageExportSession;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.io.File;

public final class MessageExporter {

    public interface Callback {
        void onExported(File file);

        void onFailed(boolean noSpace);
    }

    private static final int OUTPUT_WIDTH = 1080;
    private static final long KEEP_EXPORTS_MS = 24L * 60 * 60 * 1000;

    private final MessageExportSession session;
    private final MessageExportRenderHost host;
    private final MessageExportWatermark watermark;

    private MessageExportLayout pendingLayout;
    private MessageExportJob job;
    private File exportedFile;
    private String exportedKey;

    public MessageExporter(Context context, MessageExportSession session) {
        this.session = session;
        this.host = new MessageExportRenderHost(context, session);
        this.watermark = new MessageExportWatermark(context);
        cleanupOldExports();
    }

    public View getHostView() {
        return host;
    }

    public boolean isBusy() {
        return job != null || pendingLayout != null;
    }

    public boolean isExporting() {
        return job != null;
    }

    public void export(int width, int backgroundPeriod, boolean transparent, Callback callback) {
        if (isBusy()) {
            return;
        }
        String key = session.document.getRevision() + ":" + session.getProviderGeneration() + ":" + width + ":" + MessageExportWatermark.isEnabled() + ":" + transparent;
        if (exportedFile != null && key.equals(exportedKey) && exportedFile.exists()) {
            callback.onExported(exportedFile);
            return;
        }
        host.clear();
        pendingLayout = MessageExportLayout.measure(session.document.blocks, host, width, layout -> {
            pendingLayout = null;
            File file = new File(getDirectory(), "messages_" + System.currentTimeMillis() + ".png");
            job = new MessageExportJob(layout, host, transparent ? null : session.appearance.getBackground(), watermark, session.appearance.getProvider(), backgroundPeriod, OUTPUT_WIDTH / (float) layout.width, file, new MessageExportJob.Callback() {
                @Override
                public void onDone(File file) {
                    job = null;
                    host.clear();
                    exportedFile = file;
                    exportedKey = key;
                    callback.onExported(file);
                }

                @Override
                public void onError(boolean noSpace) {
                    job = null;
                    host.clear();
                    callback.onFailed(noSpace);
                }
            });
            job.start();
        });
    }

    public void cancel() {
        if (job != null) {
            job.cancel();
            job = null;
        }
        if (pendingLayout != null) {
            pendingLayout.cancel();
            pendingLayout = null;
        }
        host.clear();
    }

    private static File getDirectory() {
        return new File(AndroidUtilities.getCacheDir(), "message_export");
    }

    private static void cleanupOldExports() {
        File dir = getDirectory();
        Utilities.globalQueue.postRunnable(() -> {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (File file : files) {
                if (now - file.lastModified() > KEEP_EXPORTS_MS) {
                    file.delete();
                }
            }
        });
    }
}
