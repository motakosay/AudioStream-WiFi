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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use identifier instead of R
        int layoutId = getResources().getIdentifier("activity_main", "layout", getPackageName());
        setContentView(layoutId);

        int ipInputId = getResources().getIdentifier("ipInput", "id", getPackageName());
        int connectBtnId = getResources().getIdentifier("connectBtn", "id", getPackageName());
        int statusViewId = getResources().getIdentifier("statusView", "id", getPackageName());

        final EditText ipInput = (EditText) findViewById(ipInputId);
        Button connectBtn = (Button) findViewById(connectBtnId);
        statusView = (TextView) findViewById(statusViewId);

        connectBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                final String ip = ipInput.getText().toString().trim();
                if (!ip.isEmpty()) {
                    startStreaming(ip);
                } else {
                    statusView.setText("Enter a valid IP!");
                }
            }
        });
    }

    private void startStreaming(final String ip) {
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(ip, 8765);
                    InputStream in = socket.getInputStream();
                    byte[] buffer = new byte[4096];

                    statusView.post(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Connected :) !");
                        }
                    });

                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        audioTrack.write(buffer, 0, read);
                    }

                    socket.close();
                } catch (final Exception e) {
                    statusView.post(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioTrack != null) {
            audioTrack.release();
        }
    }
}
