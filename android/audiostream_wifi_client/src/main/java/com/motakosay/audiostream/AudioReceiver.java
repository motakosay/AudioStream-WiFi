package com.motakosay.audiostream;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

/**
 * Internal worker thread that connects to the server, optionally authenticates,
 * receives framed payloads, pushes them to a JitterBuffer, and consumes the buffer to play audio.
 *
 * Network framing: [2 bytes big-endian length][payload]
 *
 * If useOpus is true, an Opus decoder must be provided via setOpusDecoder().
 */
public class AudioReceiver extends Thread {
    private static final String TAG = "AudioReceiver";

    private final String host;
    private final int port;
    private final String password;
    private final boolean useOpus;
    private final int sampleRate;
    private final int channels;

    private volatile boolean running = true;
    private AudioStreamClient.ConnectionListener connectionListener;
    private AudioStreamClient.OpusDecoder opusDecoder = null;

    private final JitterBuffer jitterBuffer;
    private volatile boolean connected = false;

    public AudioReceiver(String host, int port, String password, boolean useOpus, int sampleRate, int channels) {
        super("AudioReceiverThread");
        this.host = host;
        this.port = port;
        this.password = password;
        this.useOpus = useOpus;
        this.sampleRate = sampleRate;
        this.channels = channels;
        // jitter buffer capacity in packets (tweakable)
        this.jitterBuffer = new JitterBuffer(12);
    }

    public boolean isRunning() {
        return running;
    }

    public void setConnectionListener(AudioStreamClient.ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void setOpusDecoder(AudioStreamClient.OpusDecoder decoder) {
        this.opusDecoder = decoder;
    }

    public void stopReceiver() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        Socket socket = null;
        try {
            socket = new Socket(host, port);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Authentication handshake (simple): send password, expect "OK" or "DENIED"
            if (password != null && !password.isEmpty()) {
                byte[] pw = password.getBytes("UTF-8");
                out.write(pw);
                out.flush();

                byte[] reply = new byte[16];
                int r = in.read(reply);
                if (r <= 0) throw new Exception("Auth reply missing");
                String res = new String(reply, 0, r, "UTF-8");
                if (!res.startsWith("OK")) {
                    throw new Exception("Authentication failed: " + res);
                }
            }

            // Notify connected
            connected = true;
            if (connectionListener != null) connectionListener.onConnected();

            // Setup AudioTrack
            int channelConfig = (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            // Use a playback buffer equal to a few times the minBuf to avoid underruns
            int trackBuf = Math.max(minBuf * 2, sampleRate * channels * 2 / 10); // ~200ms fallback

            AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig, audioFormat, trackBuf, AudioTrack.MODE_STREAM);
            track.play();

            // Network reader thread: reads framed packets and pushes them into jitter buffer
            final InputStream netIn = in;
            Thread netReader = new Thread(() -> {
                try {
                    byte[] sizeBuf = new byte[2];
                    byte[] tmpBuf = new byte[8192];
                    while (running) {
                        // read exactly 2 bytes length
                        int read = 0;
                        while (read < 2) {
                            int rr = netIn.read(sizeBuf, read, 2 - read);
                            if (rr <= 0) throw new Exception("Stream closed while reading size");
                            read += rr;
                        }
                        int size = ((sizeBuf[0] & 0xff) << 8) | (sizeBuf[1] & 0xff);
                        if (size <= 0) continue;
                        if (size > tmpBuf.length) tmpBuf = new byte[size];

                        int got = 0;
                        while (got < size) {
                            int rr = netIn.read(tmpBuf, got, size - got);
                            if (rr <= 0) throw new Exception("Stream closed while reading payload");
                            got += rr;
                        }
                        // copy exact-sized payload
                        byte[] payload = Arrays.copyOf(tmpBuf, size);
                        jitterBuffer.add(payload);
                    }
                } catch (Exception e) {
                    // if network error occurs, stop the whole receiver
                    Log.w(TAG, "Network reader stopped: " + e.getMessage());
                    running = false;
                }
            }, "NetReader");
            netReader.start();

            // Consumer loop: pop from jitter, decode (if needed), and write to AudioTrack
            while (running) {
                byte[] packet = jitterBuffer.get();
                if (packet == null) {
                    // interrupted / timeout
                    continue;
                }

                if (useOpus) {
                    if (opusDecoder == null) {
                        // No decoder provided; cannot decode Opus packets
                        Log.w(TAG, "useOpus=true but no OpusDecoder provided; skipping packet");
                        continue;
                    }
                    try {
                        short[] pcm = opusDecoder.decode(packet);
                        if (pcm == null || pcm.length == 0) continue;
                        // write short[] to AudioTrack as bytes
                        // convert short[] to byte[] (little-endian)
                        byte[] outBytes = new byte[pcm.length * 2];
                        int idx = 0;
                        for (short s : pcm) {
                            outBytes[idx++] = (byte) (s & 0xff);
                            outBytes[idx++] = (byte) ((s >> 8) & 0xff);
                        }
                        track.write(outBytes, 0, outBytes.length);
                    } catch (Exception e) {
                        Log.w(TAG, "Opus decode failed: " + e.getMessage());
                    }
                } else {
                    // raw PCM passthrough (expect 16-bit LE interleaved)
                    track.write(packet, 0, packet.length);
                }
            }

            // cleanup
            try { track.stop(); } catch (Exception ignored) {}
            try { track.release(); } catch (Exception ignored) {}

        } catch (Exception e) {
            Log.e(TAG, "AudioReceiver error", e);
            if (connectionListener != null) connectionListener.onError(e);
        } finally {
            connected = false;
            if (connectionListener != null) connectionListener.onDisconnected();
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
