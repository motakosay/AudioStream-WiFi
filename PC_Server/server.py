import pyaudio
import socket
import threading

# Audio settings
CHUNK = 1024
FORMAT = pyaudio.paInt16
CHANNELS = 2
RATE = 48000
PORT = 8765  # Must match Android app

# Initialize PyAudio
p = pyaudio.PyAudio()

# Find VB-Cable device
input_index = None
for i in range(p.get_device_count()):
    dev = p.get_device_info_by_index(i)
    if "CABLE Input" in dev["name"] or "VB-Audio" in dev["name"]:
        input_index = i
        print("✅ Using input device:", dev["name"])
        break

if input_index is None:
    raise RuntimeError("❌ VB-Cable not found! Make sure it's installed and set as default playback device.")

# Open audio stream
stream = p.open(format=FORMAT,
                channels=CHANNELS,
                rate=RATE,
                input=True,
                input_device_index=input_index,
                frames_per_buffer=CHUNK)

clients = []

def client_handler(conn, addr):
    print(f"Connected: {addr}")
    try:
        while True:
            data = stream.read(CHUNK, exception_on_overflow=False)
            conn.sendall(data)
    except:
        print(f"Disconnected: {addr}")
    finally:
        conn.close()
        clients.remove(conn)

# TCP server
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(("0.0.0.0", PORT))
server.listen(5)
print(f"🎧 Server listening on port {PORT}")

try:
    while True:
        conn, addr = server.accept()
        clients.append(conn)
        threading.Thread(target=client_handler, args=(conn, addr), daemon=True).start()
except KeyboardInterrupt:
    print("Shutting down server...")
finally:
    server.close()
    stream.stop_stream()
    stream.close()
    p.terminate()
