# DynamicWebViewApp

Android app featuring:
- **Local AI Inference**: Embedded llama.cpp with SmolLM2-135M GGUF model
- **WebSocket PTY Shell**: Live terminal access via HTTP + WebSocket on `localhost:8080`
- **Cloud Fallback**: Gemini 2.0 Flash API integration with offline-first routing
- **WebView + CDP**: Chrome DevTools Protocol debugging, WebGPU/IndexedDB origin isolation
- **NDK + JNI**: C++ llama.cpp bindings for fast on-device inference

## Build

Prerequisites:
- Java 17 (OpenJDK)
- Android SDK 34
- NDK 25.2.9519653
- Gradle 8.0+

```bash
./gradlew assembleDebug
```

The build script will:
1. Resolve Java 17 environment
2. Download Android command-line tools and NDK
3. Fetch llama.cpp (commit b3568) and SmolLM2 GGUF model
4. Compile C++ bindings and generate APK

## Project Structure

```
app/
├── src/main/
│   ├── java/com/local/aishell/
│   │   ├── MainActivity.java           (WebView + lifecycle)
│   │   ├── LlamaEngine.java            (JNI bindings)
│   │   ├── LocalWSServer.java          (HTTP + WebSocket daemon)
│   │   ├── PtyShellBridge.java         (PTY shell spawning)
│   │   └── GeminiApiClient.java        (Cloud API fallback)
│   ├── cpp/
│   │   ├── CMakeLists.txt              (Build config)
│   │   └── llama_bridge.cpp            (C++ JNI adapter)
│   ├── assets/                         (WebGL/PWA assets)
│   └── res/
│       ├── layout/activity_main.xml
│       └── values/strings.xml
├── build.gradle                        (App build config)
settings.gradle
build.gradle                            (Root project)
gradle.properties                       (Gradle settings)
```

## Configuration

- **Port**: 8080 (WebSocket server)
- **Model**: SmolLM2-135M-Instruct (quantized Q4_K_M)
- **Min SDK**: 26 (Android 8.0+)
- **Target SDK**: 34 (Android 14)
- **ABI**: arm64-v8a only

## API Keys

Set Gemini API key via WebView bridge:
```javascript
NativeBridge.setApiKey('your-api-key');
```

## License

See LICENSE file.
