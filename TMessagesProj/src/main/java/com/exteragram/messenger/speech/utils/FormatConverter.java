/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.speech.utils;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class FormatConverter {
    public static int getSampleRate(String path) {
        MediaExtractor extractor = new MediaExtractor();
        int sampleRate = -1;

        try {
            extractor.setDataSource(path);
            int numTracks = extractor.getTrackCount();

            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if (mime != null && mime.startsWith("audio/")) {
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            FileLog.e("Error detecting sample rate", e);
        } finally {
            extractor.release();
        }

        return sampleRate != -1 ? sampleRate : 48000;
    }

    public static InputStream extractAndConvertToPcm(String oggFilePath, boolean limitToOneMinute) throws IOException {
        return new LazyPcmInputStream(oggFilePath, limitToOneMinute);
    }

    public static ByteArrayOutputStream resamplePcm(InputStream inputStream, int inputSampleRate, int outputSampleRate) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] inputBytes = new byte[2 * inputSampleRate]; // 1 second of audio
        short[] inputShorts = new short[inputSampleRate];
        short[] outputShorts = new short[outputSampleRate];

        int bytesRead;
        while ((bytesRead = inputStream.read(inputBytes)) > 0) {
            ByteBuffer.wrap(inputBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inputShorts);

            for (int i = 0; i < outputSampleRate; i++) {
                int inputIndex = i * inputSampleRate / outputSampleRate;
                outputShorts[i] = inputShorts[inputIndex];
            }

            ByteBuffer outputBuffer = ByteBuffer.allocate(outputShorts.length * 2);
            outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer shortBuffer = outputBuffer.asShortBuffer();
            shortBuffer.put(outputShorts);

            outputStream.write(outputBuffer.array());
        }

        return outputStream;
    }

    public static float[] convertPCM16BitToFloat(byte[] pcmData) {
        float[] floatData = new float[pcmData.length / 2];
        for (int i = 0; i < floatData.length; i++) {
            short shortVal = (short) ((pcmData[i * 2] & 0xFF) | (pcmData[i * 2 + 1] << 8));
            floatData[i] = shortVal / 32768.0f;
        }
        return floatData;
    }

    private static class LazyPcmInputStream extends InputStream {
        private final MediaExtractor extractor;
        private final MediaCodec codec;
        private final MediaCodec.BufferInfo bufferInfo;
        private final boolean limitToOneMinute;
        private final long oneMinuteUs = 60_000_000;
        private ByteBuffer currentOutputBuffer;
        private boolean isEOS;
        private long totalDecodedDurationUs;

        public LazyPcmInputStream(String oggFilePath, boolean limitToOneMinute) throws IOException {
            this.limitToOneMinute = limitToOneMinute;
            this.bufferInfo = new MediaCodec.BufferInfo();

            extractor = new MediaExtractor();
            extractor.setDataSource(oggFilePath);

            MediaFormat format = extractor.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!mime.startsWith("audio/")) {
                throw new IOException("Not an audio file");
            }

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            extractor.selectTrack(0);
        }

        @Override
        public int read() throws IOException {
            byte[] singleByte = new byte[1];
            int bytesRead = read(singleByte, 0, 1);
            return bytesRead == -1 ? -1 : singleByte[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (isEOS) {
                return -1;
            }

            int totalBytesRead = 0;

            while (totalBytesRead < len && !isEOS) {
                if (currentOutputBuffer == null || !currentOutputBuffer.hasRemaining()) {
                    currentOutputBuffer = getNextOutputBuffer();
                    if (currentOutputBuffer == null) {
                        break;
                    }
                }

                int bytesToRead = Math.min(len - totalBytesRead, currentOutputBuffer.remaining());
                currentOutputBuffer.get(b, off + totalBytesRead, bytesToRead);
                totalBytesRead += bytesToRead;
            }

            return totalBytesRead > 0 ? totalBytesRead : -1;
        }

        private ByteBuffer getNextOutputBuffer() throws IOException {
            while (!isEOS) {
                int inIndex = codec.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = codec.getInputBuffer(inIndex);
                    int sampleSize = extractor.readSampleData(buffer, 0);

                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outIndex >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(outIndex);
                    ByteBuffer copyBuffer = ByteBuffer.allocate(bufferInfo.size);
                    copyBuffer.put(buffer);
                    copyBuffer.flip();
                    codec.releaseOutputBuffer(outIndex, false);

                    totalDecodedDurationUs += bufferInfo.presentationTimeUs - totalDecodedDurationUs;
                    if (limitToOneMinute && totalDecodedDurationUs >= oneMinuteUs) {
                        isEOS = true;
                    }

                    return copyBuffer;
                }
            }

            return null;
        }

        @Override
        public void close() throws IOException {
            codec.stop();
            codec.release();
            extractor.release();
            super.close();
        }
    }
}
