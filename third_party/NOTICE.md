# Optional wake-word dependency

The Android app depends on `ai.picovoice:porcupine-android:4.0.2`, published by Picovoice.
Binding source: https://github.com/Picovoice/porcupine (Apache License 2.0; license reproduced here and bundled in APK assets).
The Android SDK includes native engine/model assets; usage, licensing and activation are subject to the current Picovoice account/product terms: https://picovoice.ai/terms/ and https://console.picovoice.ai/.
A user-supplied AccessKey is required. Local keyword inference is separate from any SDK license/device activation traffic. This project does not promise unrestricted free/commercial access, offline activation or custom “Hi Jarvis” models. The built-in JARVIS keyword is used. No Picovoice or Google credential is distributed.
Existing AndroidX, Kotlin, OkHttp and WebRTC dependencies remain supplied through their official Maven artifacts and respective licenses.
