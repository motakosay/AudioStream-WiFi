from audiostream_wifi_server import AudioStreamer

if __name__ == '__main__':
# example: starts server, discovery, no opus
s = AudioStreamer(port=8765, password="secret", use_opus=False)
s.start()
print("Server started. Press Ctrl+C to stop.")
try:
while True:
import time
time.sleep(1)
except KeyboardInterrupt:
s.stop()
print("Stopped")
