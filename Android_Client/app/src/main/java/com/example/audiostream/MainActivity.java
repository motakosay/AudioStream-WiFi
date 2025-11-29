package com.example.audiostream;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends Activity {
    private TextView statusView;
    private AudioTrack audioTrack;
    private volatile boolean isStreaming = false;
    private Thread streamThread = null;
    private Socket socket = null;
    private String currentIp = "";
    private int currentChannelConfig = AudioFormat.CHANNEL_OUT_MONO; // default mono

    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int layoutId = getResources().getIdentifier("activity_main", "layout", getPackageName());
        setContentView(layoutId);

        int ipInputId = getResources().getIdentifier("ipInput", "id", getPackageName());
        int connectBtnId = getResources().getIdentifier("connectBtn", "id", getPackageName());
        int stopBtnId = getResources().getIdentifier("stopBtn", "id", getPackageName());
        int statusViewId = getResources().getIdentifier("statusView", "id", getPackageName());
        int monoBtnId = getResources().getIdentifier("monoBtn", "id", getPackageName());
        int stereoBtnId = getResources().getIdentifier("stereoBtn", "id", getPackageName());

        final EditText ipInput = findViewById(ipInputId);
        Button connectBtn = findViewById(connectBtnId);
        Button stopBtn = findViewById(stopBtnId);
        statusView = findViewById(statusViewId);
        final RadioButton monoBtn = findViewById(monoBtnId);
        final RadioButton stereoBtn = findViewById(stereoBtnId);

        // Default selection
        monoBtn.setChecked(true);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (ensureConnectivity()) {
                            final String ip = findServerIp();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (ip != null) {
                                        currentIp = ip;
                                        if (monoBtn.isChecked()) {
                                            currentChannelConfig = AudioFormat.CHANNEL_OUT_MONO;
                                        } else if (stereoBtn.isChecked()) {
                                            currentChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                                        }
                                        statusView.setText("Found server at " + ip + " — connecting...");
                                        startStreaming(ip);
                                    } else {
                                        statusView.setText("Know IP of your Device then type it");
                                    }
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusView.setText("X Wi-Fi or Bluetooth not connected");
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopStreaming();
            }
        });
    }

    private boolean isWifiConnected() {
        if (wifiManager == null) return false;
        return wifiManager.getConnectionInfo() != null &&
               wifiManager.getConnectionInfo().getNetworkId() != -1;
    }

    private boolean isBluetoothConnected() {
        if (bluetoothAdapter == null) return false;
        return bluetoothAdapter.isEnabled() &&
               bluetoothAdapter.getBondedDevices().size() > 0;
    }

    private boolean ensureConnectivity() {
        boolean wifiOk = isWifiConnected();
        boolean btOk = isBluetoothConnected();

        if (!wifiOk) {
            wifiManager.setWifiEnabled(true);
            sleep(3000); // wait for Wi-Fi to connect
            wifiOk = isWifiConnected();
        }

        if (!btOk && bluetoothAdapter != null) {
            bluetoothAdapter.enable();
            sleep(3000); // wait for Bluetooth to enable
            btOk = isBluetoothConnected();
        }

        return wifiOk && btOk;
    }

    private String findServerIp() {
        for (int i = 2; i < 255; i++) {
            String testIp = "192.168.1." + i;
            try (Socket testSocket = new Socket()) {
                testSocket.connect(new InetSocketAddress(testIp, 8765), 500);
                return testIp;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void startStreaming(final String ip) {
        if (isStreaming) return;
        isStreaming = true;

        streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isStreaming) {
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText("Connecting to " + ip + "...");
                            }
                        });

                        socket = new Socket(ip, 8765);

                        String handshake = "CHANNELS:" + (currentChannelConfig == AudioFormat.CHANNEL_OUT_MONO ? "1" : "2");
                        OutputStream out = socket.getOutputStream();
                        out.write(handshake.getBytes());
                        out.flush();

                        InputStream in = socket.getInputStream();

                        int minBuf = AudioTrack.getMinBufferSize(
                                48000,
                                currentChannelConfig,
                                AudioFormat.ENCODING_PCM_16BIT
                        );

                        audioTrack = new AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                48000,
                                currentChannelConfig,
                                AudioFormat.ENCODING_PCM_16BIT,
                                minBuf,
                                AudioTrack.MODE_STREAM
                        );
                        audioTrack.play();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText("Connected :) !");
                            }
                        });

                        byte[] buffer = new byte[minBuf];
                        int read;
                        while (isStreaming && (read = in.read(buffer)) != -1) {
                            int written = audioTrack.write(buffer, 0, read);
                            if (written < 0) {
                                resetAudioTrack();
                            }
                        }

                    } catch (Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText("Connection lost, retrying...");
                            }
                        });
                        cleanupAudioOnly();
                        sleep(2000);
                    } finally {
                        cleanupSocketOnly();
                    }
                }
                cleanup();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusView.setText("Stopped");
                    }
                });
            }
        });

        streamThread.start();
    }

    private void resetAudioTrack() {
        cleanupAudioOnly();
        int minBuf = AudioTrack.getMinBufferSize(
                48000,
                currentChannelConfig,
                AudioFormat.ENCODING_PCM_16BIT
        );
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                48000,
                currentChannelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf,
                AudioTrack.MODE_STREAM
        );
        audioTrack.play();
    }

    private void stopStreaming() {
        isStreaming = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setText("Stopping...");
            }
        });
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
