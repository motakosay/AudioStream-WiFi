# 🎧 WiFiAudioLink — Low-Latency Audio Streaming over Wi-Fi

WiFiAudioLink is a lightweight, cross-platform library designed to stream live audio over local Wi-Fi between a Python server and an Android client — ideal for projects like wireless monitoring, live performance tools, remote microphones, or multi-room audio systems.

The library focuses on simplicity, low latency, and extensibility, offering a plug-and-play foundation for custom audio applications without external dependencies.

---

## 📦 Notes & Next steps
- These files intentionally do not include an Opus implementation. The AudioStreamClient.OpusDecoder interface is the required hook: you (or a consuming app) can provide a concrete implementation that calls a native Opus library (JNI) or a Java wrapper. When useOpus==true, the receiver requires a decoder to be set via setOpusDecoder().

- The authentication is intentionally minimal (password sent as plain bytes). If you need stronger security, we should upgrade to TLS/DTLS or at least HMAC-signed tokens.

- The framing is [2 bytes length][payload]. The server scaffold already uses this pattern.

- Tweak JitterBuffer capacity and AudioTrack buffer sizes after testing on real networks/devices.
