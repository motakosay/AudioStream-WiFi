import socket
import threading
import time


class DiscoveryBroadcaster:
"""Broadcast simple server discovery messages over UDP.


Message format: name;tcp_port;password
"""
def __init__(self, port=8766, name="WiFiAudioLink", tcp_port=8765, password=None, interval=2.0):
self.port = port
self.name = name
self.tcp_port = tcp_port
self.password = password
self.interval = interval
self.running = False
self._sock = None


def start(self):
if self.running:
return
self.running = True
t = threading.Thread(target=self._broadcast_loop, daemon=True)
t.start()


def stop(self):
self.running = False
if self._sock:
try:
self._sock.close()
except Exception:
pass


def _broadcast_loop(self):
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
self._sock = s
msg = f"{self.name};{self.tcp_port};{self.password or ''}".encode()
try:
while self.running:
try:
s.sendto(msg, ("<broadcast>", self.port))
except Exception:
# on some networks broadcast may fail; ignore
pass
time.sleep(self.interval)
finally:
try:
s.close()
except Exception:
pass
