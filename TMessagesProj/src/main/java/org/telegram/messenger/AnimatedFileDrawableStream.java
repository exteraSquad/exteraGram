package org.telegram.messenger;

import androidx.media3.exoplayer.ExoPlayer;


import org.telegram.tgnet.TLRPC;

import java.util.concurrent.CountDownLatch;

public class AnimatedFileDrawableStream implements FileLoadOperationStream {

    private FileLoadOperation loadOperation;
    private volatile CountDownLatch countDownLatch;
    private TLRPC.Document document;
    private ImageLocation location;
    private Object parentObject;
    private int currentAccount;
    private volatile boolean canceled;
    private final Object sync = new Object();
    private long lastOffset;
    private boolean waitingForLoad;
    private boolean preview;
    private boolean finishedLoadingFile;
    private String finishedFilePath;
    private int loadingPriority;

    private int debugCanceledCount;
    private boolean debugReportSend;

    public AnimatedFileDrawableStream(TLRPC.Document d, ImageLocation l, Object p, int a, boolean prev, int loadingPriority, int cacheType) {
        document = d;
        location = l;
        parentObject = p;
        currentAccount = a;
        preview = prev;
        this.loadingPriority = loadingPriority;
        loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, 0, preview, loadingPriority, cacheType);
    }

    public boolean isFinishedLoadingFile() {
        return finishedLoadingFile;
    }

    public String getFinishedFilePath() {
        return finishedFilePath;
    }

    public int read(int offset, int readLength) {
        synchronized (sync) {
            if (canceled) {
                debugCanceledCount++;
                if (!debugReportSend && debugCanceledCount > 200) {
                    debugReportSend = true;
                    FileLog.e(new RuntimeException("infinity stream reading!!!"));
                }
                return 0;
            }
        }
        if (readLength == 0) {
            return 0;
        } else {
            long availableLength = 0;
            try {
                while (availableLength == 0) {
                    CountDownLatch waitLatch = new CountDownLatch(1);
                    countDownLatch = waitLatch;
                    long[] result = loadOperation.getDownloadedLengthFromOffset(offset, readLength);
                    availableLength = result[0];
                    boolean operationFinished = result[1] > 0;
                    boolean operationCanceled = result[1] < 0;
                    if (!finishedLoadingFile && operationFinished) {
                        finishedLoadingFile = true;
                        finishedFilePath = loadOperation.getCacheFileFinal().getAbsolutePath();
                    }
                    if (availableLength == 0) {
                        synchronized (sync) {
                            if (canceled) {
                                if (countDownLatch == waitLatch) {
                                    countDownLatch = null;
                                }
                                cancelLoadingInternal();
                                return 0;
                            }
                        }
                        if (loadOperation.isPaused() || operationCanceled || lastOffset != offset || preview) {
                            FileLoadOperation loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, offset, preview, loadingPriority);
                            if (this.loadOperation != loadOperation) {
                                this.loadOperation.removeStreamListener(this);
                                this.loadOperation = loadOperation;
                            }
                            lastOffset = offset + availableLength;
                        }
                        synchronized (sync) {
                            if (canceled) {
                                if (countDownLatch == waitLatch) {
                                    countDownLatch = null;
                                }
                                cancelLoadingInternal();
                                return 0;
                            }
                        }
                        if (!preview) {
                            FileLoader.getInstance(currentAccount).setLoadingVideo(document, false, true);
                        }
                        if (waitLatch.getCount() != 0) {
                            waitingForLoad = true;
                            waitLatch.await();
                            waitingForLoad = false;
                        }
                    }
                    if (countDownLatch == waitLatch) {
                        countDownLatch = null;
                    }
                }
                lastOffset = offset + availableLength;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return (int) availableLength;
        }
    }

    public void cancel() {
        cancel(true);
    }

    public void cancel(boolean removeLoading) {
        if (canceled) {
            return;
        }
        synchronized (sync) {
            CountDownLatch latch = countDownLatch;
            if (latch != null) {
                latch.countDown();
                if (removeLoading && !canceled && !preview) {
                    FileLoader.getInstance(currentAccount).removeLoadingVideo(document, false, true);
                }
            }
            if (parentObject instanceof MessageObject) {
                MessageObject messageObject = (MessageObject) parentObject;
                if (DownloadController.getInstance(messageObject.currentAccount).isDownloading(messageObject.getId()))  {
                    removeLoading = false;
                }
            }
            if (removeLoading) {
                cancelLoadingInternal();
            }
            canceled = true;
        }
    }

    private void cancelLoadingInternal() {
        FileLoader.getInstance(currentAccount).cancelLoadFile(document);
        if (location != null) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(location.location, "mp4");
        }
    }

    public void reset() {
        synchronized (sync) {
            canceled = false;
        }
    }

    public TLRPC.Document getDocument() {
        return document;
    }

    public ImageLocation getLocation() {
        return location;
    }

    public Object getParentObject() {
        return document;
    }

    public boolean isPreview() {
        return preview;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public boolean isWaitingForLoad() {
        return waitingForLoad;
    }

    @Override
    public void newDataAvailable() {
        CountDownLatch latch = countDownLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    public boolean isCanceled() {
        return canceled;
    }
}
