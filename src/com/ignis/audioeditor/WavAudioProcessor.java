package com.ignis.audioeditor;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * WavAudioProcessor - Lower-level utility to process WAV headers,
 * manipulate 16-bit signed PCM data (trim, merge, fade-in/out),
 * and mix multiple tracks into a single WAV output.
 */
public final class WavAudioProcessor {

    public static class WavData {
        public AudioFormat format;
        public byte[] pcmData;
        public double duration; // in seconds

        public WavData(AudioFormat format, byte[] pcmData, double duration) {
            this.format = format;
            this.pcmData = pcmData;
            this.duration = duration;
        }
    }

    private WavAudioProcessor() {}

    /**
     * Reads a WAV file and returns its format and PCM data.
     * Guaranteed to convert to standard 44100Hz 16-bit Stereo PCM Little Endian.
     */
    public static WavData readWav(File file) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat format = ais.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100.0f,
                16,
                2,
                4,
                44100.0f,
                false
            );

            // 1. Try native AudioSystem conversion first
            try {
                if (AudioSystem.isConversionSupported(targetFormat, format)) {
                    try (AudioInputStream converted = AudioSystem.getAudioInputStream(targetFormat, ais)) {
                        byte[] pcmData = converted.readAllBytes();
                        double duration = (double) pcmData.length / targetFormat.getFrameSize() / targetFormat.getSampleRate();
                        return new WavData(targetFormat, pcmData, duration);
                    }
                }
            } catch (Exception e) {
                // Fallback to manual converter/resampler
            }

            // 2. Manual Resampling/Conversion Fallback
            // First convert to PCM signed 16-bit, keeping original sample rate and channels
            AudioFormat pcm16Format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.getSampleRate(),
                16,
                format.getChannels(),
                format.getChannels() * 2,
                format.getSampleRate(),
                false
            );

            byte[] srcBytes;
            if (AudioSystem.isConversionSupported(pcm16Format, format)) {
                try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm16Format, ais)) {
                    srcBytes = converted.readAllBytes();
                }
            } else {
                srcBytes = ais.readAllBytes();
            }

            byte[] standardBytes = resampleTo44100Stereo16Bit(srcBytes, pcm16Format);
            double duration = (double) standardBytes.length / targetFormat.getFrameSize() / targetFormat.getSampleRate();
            return new WavData(targetFormat, standardBytes, duration);
        }
    }

    private static byte[] resampleTo44100Stereo16Bit(byte[] srcBytes, AudioFormat srcFormat) {
        float srcRate = srcFormat.getSampleRate();
        int srcChannels = srcFormat.getChannels();
        boolean isBigEndian = srcFormat.isBigEndian();

        int srcSampleCount = srcBytes.length / (srcChannels * 2);
        if (srcSampleCount == 0) return new byte[0];

        short[][] srcSamples = new short[srcChannels][srcSampleCount];
        ByteBuffer wrap = ByteBuffer.wrap(srcBytes).order(isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < srcSampleCount; i++) {
            for (int c = 0; c < srcChannels; c++) {
                if (wrap.remaining() >= 2) {
                    srcSamples[c][i] = wrap.getShort();
                }
            }
        }

        float ratio = 44100.0f / srcRate;
        int dstSampleCount = Math.round(srcSampleCount * ratio);
        if (dstSampleCount <= 0) return new byte[0];

        short[][] dstSamples = new short[2][dstSampleCount];

        for (int i = 0; i < dstSampleCount; i++) {
            float srcPos = i / ratio;
            int idx1 = (int) srcPos;
            int idx2 = Math.min(srcSampleCount - 1, idx1 + 1);
            float frac = srcPos - idx1;

            if (idx1 >= srcSampleCount) {
                idx1 = srcSampleCount - 1;
                idx2 = srcSampleCount - 1;
                frac = 0;
            }

            float val0 = (1 - frac) * srcSamples[0][idx1] + frac * srcSamples[0][idx2];
            dstSamples[0][i] = (short) Math.max(-32768, Math.min(32767, val0));

            float val1;
            if (srcChannels > 1) {
                val1 = (1 - frac) * srcSamples[1][idx1] + frac * srcSamples[1][idx2];
            } else {
                val1 = val0;
            }
            dstSamples[1][i] = (short) Math.max(-32768, Math.min(32767, val1));
        }

        byte[] dstBytes = new byte[dstSampleCount * 4];
        ByteBuffer outBuffer = ByteBuffer.wrap(dstBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dstSampleCount; i++) {
            outBuffer.putShort(dstSamples[0][i]);
            outBuffer.putShort(dstSamples[1][i]);
        }
        return dstBytes;
    }

    /**
     * Writes raw PCM data back to a standard WAV file.
     */
    public static void writeWav(byte[] pcmData, AudioFormat format, File outputFile) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
             AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
        }
    }

    /**
     * Trims a PCM byte array to a specific start and end time.
     */
    public static byte[] trimPcm(byte[] pcm, AudioFormat format, double startSec, double endSec) {
        int frameSize = format.getFrameSize();
        float sampleRate = format.getSampleRate();

        int startByte = (int) (startSec * sampleRate) * frameSize;
        int endByte = (int) (endSec * sampleRate) * frameSize;

        // Clamp
        startByte = Math.max(0, Math.min(pcm.length, startByte));
        endByte = Math.max(startByte, Math.min(pcm.length, endByte));

        // Align to frame boundaries
        startByte = (startByte / frameSize) * frameSize;
        endByte = (endByte / frameSize) * frameSize;

        int length = endByte - startByte;
        byte[] trimmed = new byte[length];
        System.arraycopy(pcm, startByte, trimmed, 0, length);
        return trimmed;
    }

    /**
     * Applies linear fade-in and/or fade-out to a PCM 16-bit byte array.
     */
    public static byte[] applyFades(byte[] pcm, AudioFormat format, double fadeInSec, double fadeOutSec) {
        if (fadeInSec <= 0 && fadeOutSec <= 0) {
            return pcm.clone();
        }

        byte[] result = pcm.clone();
        int frameSize = format.getFrameSize();
        int channels = format.getChannels();
        float sampleRate = format.getSampleRate();
        boolean bigEndian = format.isBigEndian();

        int totalFrames = result.length / frameSize;
        int fadeInFrames = (int) (fadeInSec * sampleRate);
        int fadeOutFrames = (int) (fadeOutSec * sampleRate);

        fadeInFrames = Math.min(fadeInFrames, totalFrames);
        fadeOutFrames = Math.min(fadeOutFrames, totalFrames - fadeInFrames);

        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        // Apply Fade In
        for (int f = 0; f < fadeInFrames; f++) {
            double factor = (double) f / fadeInFrames;
            int frameOffset = f * frameSize;
            for (int c = 0; c < channels; c++) {
                int sampleOffset = frameOffset + c * 2;
                short sample = buffer.getShort(sampleOffset);
                buffer.putShort(sampleOffset, (short) (sample * factor));
            }
        }

        // Apply Fade Out
        for (int f = 0; f < fadeOutFrames; f++) {
            double factor = 1.0 - ((double) f / fadeOutFrames);
            int targetFrame = totalFrames - fadeOutFrames + f;
            int frameOffset = targetFrame * frameSize;
            for (int c = 0; c < channels; c++) {
                int sampleOffset = frameOffset + c * 2;
                short sample = buffer.getShort(sampleOffset);
                buffer.putShort(sampleOffset, (short) (sample * factor));
            }
        }

        return result;
    }

    /**
     * Mixes multiple PCM 16-bit streams into a single master stream with clipping protection.
     */
    public static byte[] mixTracks(java.util.List<byte[]> streams, java.util.List<Double> startTimes, AudioFormat format) {
        if (streams.isEmpty()) {
            return new byte[0];
        }

        int frameSize = format.getFrameSize();
        float sampleRate = format.getSampleRate();
        int channels = format.getChannels();
        boolean bigEndian = format.isBigEndian();
        ByteOrder order = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;

        // Find max duration in bytes
        int maxBytes = 0;
        for (int i = 0; i < streams.size(); i++) {
            int startByte = (int) (startTimes.get(i) * sampleRate) * frameSize;
            startByte = (startByte / frameSize) * frameSize;
            int totalBytes = startByte + streams.get(i).length;
            if (totalBytes > maxBytes) {
                maxBytes = totalBytes;
            }
        }

        byte[] masterPcm = new byte[maxBytes];
        ByteBuffer masterBuffer = ByteBuffer.wrap(masterPcm);
        masterBuffer.order(order);

        for (int i = 0; i < streams.size(); i++) {
            byte[] stream = streams.get(i);
            int startByte = (int) (startTimes.get(i) * sampleRate) * frameSize;
            startByte = (startByte / frameSize) * frameSize;

            ByteBuffer trackBuffer = ByteBuffer.wrap(stream);
            trackBuffer.order(order);

            int trackSamples = stream.length / 2;
            for (int s = 0; s < trackSamples; s++) {
                int masterSampleOffset = startByte + s * 2;
                if (masterSampleOffset + 1 >= maxBytes) break;

                short masterSample = masterBuffer.getShort(masterSampleOffset);
                short trackSample = trackBuffer.getShort(s * 2);

                // Mix and clamp to prevent overflow/clipping
                int mixed = masterSample + trackSample;
                mixed = Math.max(-32768, Math.min(32767, mixed));

                masterBuffer.putShort(masterSampleOffset, (short) mixed);
            }
        }

        return masterPcm;
    }
}
