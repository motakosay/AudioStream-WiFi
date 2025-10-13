package com.example.audiostream;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.InputStream;
import java.net.Socket;

public class MainActivity extends Activity {
    private TextView statusView;
    private AudioTrack audioTrack;
    private volatile boolean isStreaming = false;
    private Thread streamThread = null;
    private Socket socket = null;
    private String currentIp = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int layoutId = getResources().getIdentifier("activity_main", "layout", getPackageName());
        setContentView(layoutId);

        int ipInputId = getResources().getIdentifier("ipInput", "id", getPackageName());
        int connectBtnId = getResources().getIdentifier("connectBtn", "id", getPackageName());
        int stopBtnId = getResources().getIdentifier("stopBtn", "id", getPackageName());
        int statusViewId = getResources().getIdentifier("statusView", "id", getPackageName());

        final EditText ipInput = findViewById(ipInputId);
        Button connectBtn = findViewById(connectBtnId);
        Button stopBtn = findViewById(stopBtnId);
        statusView = findViewById(statusViewId);

        connectBtn.setOnClickListener(v -> {
            final String ip = ipInput.getText().toString().trim();
            if (!ip.isEmpty()) {
                currentIp = ip;
                startStreaming(ip);
            } else {
                statusView.setText("Enter a valid IP!");
            }
        });

        stopBtn.setOnClickListener(v -> stopStreaming());
    }

    private void startStreaming(final String ip) {
        if (isStreaming) return;
        isStreaming = true;

        streamThread = new Thread(() -> {
            while (isStreaming) {
                try {
                    runOnUiThread(() -> statusView.setText("Connecting to " + ip + "..."));
                    socket = new Socket(ip, 8765);
                    InputStream in = socket.getInputStream();

                    int minBuf = AudioTrack.getMinBufferSize(
                            48000,
                            AudioFormat.CHANNEL_OUT_STEREO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

                    audioTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            48000,
                            AudioFormat.CHANNEL_OUT_STEREO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            minBuf,
                            AudioTrack.MODE_STREAM
                    );
                    audioTrack.play();

                    runOnUiThread(() -> statusView.setText("Connected ✔️"));

                    byte[] buffer = new byte[4096];
                    int read;
                    while (isStreaming && (read = in.read(buffer)) != -1) {
                        audioTrack.write(buffer, 0, read);
                    }

                } catch (Exception e) {
                    runOnUiThread(() -> statusView.setText("Connection lost, retrying..."));
                    cleanupAudioOnly(); // don't stop the loop, just reset audio
                    sleep(2000); // wait before retry
                } finally {
                    cleanupSocketOnly();
                }
            }
            cleanup(); // final cleanup when stopped
            runOnUiThread(() -> statusView.setText("Stopped"));
        });

        streamThread.start();
    }

    private void stopStreaming() {
        isStreaming = false;
        runOnUiThread(() -> statusView.setText("Stopping..."));
        cleanup();
    }

    private void cleanupAudioOnly() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
            audioTrack = null;
        }
    }

    private void cleanupSocketOnly() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {}
            socket = null;
        }
    }

    private void cleanup() {
        cleanupAudioOnly();
        cleanupSocketOnly();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }
}
