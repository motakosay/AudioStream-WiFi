WiFiAudioLink Android client library


Usage (in an Android app):


1. Add the AAR to your app (or publish to your Maven repo).
2. Request `android.permission.INTERNET` in manifest.


Example:


```java
AudioStreamClient client = new AudioStreamClient("192.168.1.10", 8765);
client.setPassword("secret"); // optional
client.setUseOpus(false); // // optional
client.setConnectionListener(listener); // optional
client.start();
...
client.stop();
