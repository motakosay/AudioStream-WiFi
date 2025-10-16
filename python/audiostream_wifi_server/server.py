"""Audio streaming server (simple, library-friendly).


while self._running:
try:
conn, addr = self._server_sock.accept()
print(f"Client connected: {addr}")
# simple auth handshake
if self.password:
# receive up to 128 bytes for password
client_pw = conn.recv(128).decode(errors="ignore").strip()
if client_pw != (self.password or ""):
try:
conn.sendall(b"DENIED")
except Exception:
pass
conn.close()
print("Client denied (bad password)")
continue
else:
try:
conn.sendall(b"OK")
except Exception:
pass
else:
# no password: the client may still send an empty auth; ignore
pass


# streaming loop: read from capture and send
with conn:
while self._running:
try:
data = stream.read(self.chunk, exception_on_overflow=False)
except Exception:
# if capture fails, wait a moment and retry
time.sleep(0.01)
continue


payload = data
if self.use_opus:
# encode PCM (16-bit little-endian) into opus bytes
# opuslib expects PCM as bytes; here we assume it accepts raw bytes
try:
payload = self._encoder.encode(payload, self._opus_frame_size)
except Exception as e:
# encoding failed; fall back to raw
payload = data


# application-level framing: 2-byte length + payload
try:
size = len(payload)
conn.sendall(size.to_bytes(2, 'big') + payload)
except BrokenPipeError:
print("Client disconnected")
break
except Exception:
# network errors -> break and wait for next client
break


except Exception as e:
# may be interrupted by stop(); continue loop
if not self._running:
break
time.sleep(0.1)


# cleanup
try:
stream.stop_stream()
stream.close()
p.terminate()
except Exception:
pass
print("WiFiAudioLink server stopped")
