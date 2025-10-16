package com.motakosay.audiostream;

import java.util.LinkedList;

/**
 * Simple bounded jitter buffer (packet queue).
 * - add(byte[]) pushes newest packet into the queue (drops oldest when full).
 * - get() blocks until a packet is available (or returns null when interrupted).
 *
 * This is intentionally simple to be dependency-free and easy to tune.
 */
public class JitterBuffer {
    private final LinkedList<byte[]> queue = new LinkedList<>();
    private final int maxPackets;

    /**
     * @param maxPackets maximum packets to keep in buffer (e.g. 10-20)
     */
    public JitterBuffer(int maxPackets) {
        if (maxPackets <= 0) maxPackets = 10;
        this.maxPackets = maxPackets;
    }

    public synchronized void add(byte[] data) {
        if (data == null || data.length == 0) return;
        if (queue.size() >= maxPackets) {
            queue.removeFirst(); // drop oldest
        }
        queue.addLast(data);
        notifyAll();
    }

    /**
     * Blocking get. Waits until a packet is available or the thread is interrupted.
     * @return packet bytes or null if interrupted
     */
    public synchronized byte[] get() {
        while (queue.isEmpty()) {
            try {
                wait(50);
            } catch (InterruptedException e) {
                // thread interrupted -> return null to let caller handle termination
                return null;
            }
        }
        return queue.removeFirst();
    }

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized int size() {
        return queue.size();
    }
}
