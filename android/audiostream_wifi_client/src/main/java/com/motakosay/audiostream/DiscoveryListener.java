package com.motakosay.audiostream;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashSet;

/**
 * Listens for UDP broadcast discovery messages from the server.
 * Message format expected: name;tcp_port;password
 *
 * Usage:
 *   DiscoveryListener listener = new DiscoveryListener(info -> { ... });
 *   listener.start();  // starts thread
 *   listener.stopListener(); // stops
 */
public class DiscoveryListener extends Thread {
    public interface Callback {
        void onServerFound(ServerInfo info);
    }

    private final int port;
    private volatile boolean running = true;
    private final Callback callback;
    private final HashSet<String> seen = new HashSet<>();

    public DiscoveryListener(Callback callback) {
        this(callback, 8766);
    }

    public DiscoveryListener(Callback callback, int port) {
        this.callback = callback;
        this.port = port;
        setName("DiscoveryListener");
    }

    public void stopListener() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"))) {
            socket.setBroadcast(true);
            byte[] buf = new byte[512];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                String[] parts = msg.split(";", 3);
                if (parts.length >= 2) {
                    String name = parts[0];
                    int tcpPort;
                    try {
                        tcpPort = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                    String password = parts.length > 2 ? parts[2] : "";
                    String key = packet.getAddress().getHostAddress() + ":" + tcpPort;
                    if (!seen.contains(key)) {
                        seen.add(key);
                        ServerInfo info = new ServerInfo(packet.getAddress().getHostAddress(), tcpPort, name, password);
                        if (callback != null) callback.onServerFound(info);
                    }
                }
            }
        } catch (Exception e) {
            // socket closed or interrupted - end quietly
        }
    }

    public static class ServerInfo {
        public final String ip;
        public final int port;
        public final String name;
        public final String password;

        public ServerInfo(String ip, int port, String name, String password) {
            this.ip = ip;
            this.port = port;
            this.name = name;
            this.password = password;
        }
    }
}
