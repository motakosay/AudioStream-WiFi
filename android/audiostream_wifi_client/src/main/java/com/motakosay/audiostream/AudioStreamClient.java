package com.motakosay.audiostream;

import androidx.annotation.Nullable;

public class AudioStreamClient {
    private final String host;
    private final int port;

    private String password = null;
    private boolean useOpus = false;
    private int sampleRate = 48000;
    private int channels = 2;

    private AudioReceiver receiver;
    private ConnectionListener connectionListener;

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(Exception e);
    }

    public AudioStreamClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public void setUseOpus(boolean useOpus) {
        this.useOpus = useOpus;
    }

    /**
     * Set preferred sample rate (must match server).
     */
    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /**
     * Set number of channels (1 = mono, 2 = stereo).
     */
    public void setChannels(int channels) {
        this.channels = channels;
    }

    public void setConnectionListener(@Nullable ConnectionListener listener) {
        this.connectionListener = listener;
        if (receiver != null) receiver.setConnectionListener(listener);
    }

    /**
     * Start receiving audio (non-blocking).
     */
    public synchronized void start() {
        if (receiver != null && receiver.isRunning()) return;
        receiver = new AudioReceiver(host, port, password, useOpus, sampleRate, channels);
        receiver.setConnectionListener(connectionListener);
        receiver.start();
    }

    /**
     * Stop receiving audio.
     */
    public synchronized void stop() {
        if (receiver == null) return;
        receiver.stopReceiver();
        receiver = null;
    }

    /**
     * Optional: supply an Opus decoder implementation if useOpus==true.
     * The OpusDecoder must implement decode(byte[] input) -> short[] PCM samples.
     */
    public interface OpusDecoder {
        /**
         * Decode an Opus packet to 16-bit PCM samples (interleaved shorts).
         * Return null or empty on decode failure.
         */
        short[] decode(byte[] opusPacket);
    }

    /**
     * Provide a decoder implementation to the running receiver (if any).
     */
    public void setOpusDecoder(@Nullable OpusDecoder decoder) {
        if (receiver != null) receiver.setOpusDecoder(decoder);
    }
}
