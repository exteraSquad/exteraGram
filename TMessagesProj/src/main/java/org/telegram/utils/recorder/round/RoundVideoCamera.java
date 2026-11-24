package org.telegram.utils.recorder.round;

import androidx.annotation.NonNull;

abstract class RoundVideoCamera {

    abstract void startSegment(
            @NonNull RoundVideoMp4Writer writer,
            long timelineOffsetUs,
            @NonNull RoundVideoSession.CameraFacing facing
    );

    abstract boolean setCameraFacing(@NonNull RoundVideoSession.CameraFacing facing);

    abstract boolean setZoom(float progress);

    abstract boolean setZoomRatio(float ratio);

    abstract boolean setFocusPoint(float x, float y);

    abstract boolean setTorchEnabled(boolean enabled);

    abstract boolean stopSegment();

    abstract void release();
}
