#server.py

import pyaudio
import socket
import threading

# 🎚️ Audio settings
CHUNK = 1024
FORMAT = pyaudio.paInt16
RATE = 48000
PORT = 8765

p = pyaudio.PyAudio()

# 🔍 Find VB-Cable input device
def find_vb_cable():
    for i in range(p.get_device_count()):
        dev = p.get_device_info_by_index(i)
        if "CABLE Input" in dev["name"] or "VB-Audio" in dev["name"]:
            print(f"🎙️ Ritual begins with: {dev['name']}")
            return i
    raise RuntimeError("❌ VB-Cable not found! Ensure it's installed and set as default playback device.")

input_index = find_vb_cable()

clients = []

# 🧵 Handle each client
def client_handler(conn, addr):
    print(f"🔗 Connected: {addr}")
    try:
        # First message from client = channel request
        handshake = conn.recv(16).decode().strip()
        if handshake.startswith("CHANNELS:"):
            channels = int(handshake.split(":")[1])
        else:
            channels = 1  # default mono

        print(f"🎛️ Client {addr} requested {channels} channel(s)")

        # Open audio stream with requested channels
        stream = p.open(format=FORMAT,
                        channels=channels,
                        rate=RATE,
                        input=True,
                        input_device_index=input_index,
                        frames_per_buffer=CHUNK)

        while True:
            data = stream.read(CHUNK, exception_on_overflow=False)
            conn.sendall(data)

    except Exception as e:
        print(f"⚡ Disconnected: {addr} — {type(e).__name__}: {e}")
    finally:
        try:
            conn.close()
            clients.remove(conn)
        except ValueError:
            pass
        try:
            stream.stop_stream()
            stream.close()
        except Exception:
            pass

# 🌐 TCP server setup
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(("0.0.0.0", PORT))
server.listen(5)
print(f"🌀 Listening on port {PORT} — awaiting sonic pilgrims...")

# 🧘 Main loop
try:
    while True:
        conn, addr = server.accept()
        clients.append(conn)
        threading.Thread(target=client_handler, args=(conn, addr), daemon=True).start()
except KeyboardInterrupt:
    print("🕊️ Graceful shutdown initiated...")
finally:
    server.close()
    p.terminate()
    print("🔚 Audio stream closed.")
