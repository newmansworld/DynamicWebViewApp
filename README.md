# DynamicWebViewApp

Production-oriented Android shell with a packaged local UI, loopback HTTP/WebSocket server, optional Gemini fallback, and JNI llama.cpp inference.

## Build prerequisites

- Java 17
- Android SDK platform 34
- Android NDK 25.2.9519653
- CMake 3.22.1
- llama.cpp source and a `model.gguf` asset supplied by the build process

Build with `./gradlew assembleDebug` after setting `sdk.dir` in `local.properties`. The UI is packaged at `app/src/main/assets/index.html`; it intentionally has no CDN dependency so it can load offline inside the APK.

## Runtime architecture

- `MainActivity` starts the loopback server at `127.0.0.1:8080` and loads the packaged UI.
- `LocalWSServer` serves only APK assets and provides the WebSocket shell endpoint.
- The UI calls `NativeAI.processPromptAsync` for native inference/cloud fallback and uses WebSocket for terminal input.
- WebView debugging is enabled only in debug builds; runtime permission requests are denied by default.

## Important release notes

The GGUF model and llama.cpp checkout are intentionally not committed to Git because of their size and licensing. The build pipeline must fetch and verify them before producing a release APK. Never commit a Gemini API key; keys are stored locally on-device and should be treated as sensitive.
