package com.local.aishell;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ExecutorService aiExecutor;
    private Handler mainHandler;
    private SharedPreferences prefs;
    private LocalWSServer wsServer;
    private LlamaEngine llamaEngine;
    private long modelHandle;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        aiExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("AIShellPrefs", MODE_PRIVATE);
        aiExecutor.execute(this::ensureEmbeddedModelExtracted);
        try {
            wsServer = new LocalWSServer(getApplicationContext(), 8080);
            wsServer.start(5000, false);
        } catch (IOException e) { e.printStackTrace(); }

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                // The UI does not request camera/microphone access by default.
                request.deny();
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "NativeBridge");
        webView.addJavascriptInterface(new NativeAI(), "NativeAI");
        webView.loadUrl("http://127.0.0.1:8080/index.html");
    }

    private void ensureEmbeddedModelExtracted() {
        File modelFile = new File(getFilesDir(), "model.gguf");
        if (modelFile.exists()) return;
        try (InputStream is = getAssets().open("model.gguf"); OutputStream os = new FileOutputStream(modelFile)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public class NativeBridge {
        @JavascriptInterface public void setApiKey(String apiKey) { prefs.edit().putString("gemini_api_key", apiKey == null ? "" : apiKey.trim()).apply(); }
        @JavascriptInterface public String getApiKey() { return prefs.getString("gemini_api_key", ""); }
    }

    public class NativeAI {
        @JavascriptInterface public void processPromptAsync(final String prompt, final String callbackJsFunction) {
            if (prompt == null || callbackJsFunction == null || !callbackJsFunction.matches("[A-Za-z_$][A-Za-z0-9_$\.]*")) return;
            aiExecutor.execute(() -> {
                String result = routeAndExecutePrompt(prompt);
                mainHandler.post(() -> webView.evaluateJavascript(callbackJsFunction + "(" + JSONObject.quote(result) + ");", null));
            });
        }
        private String routeAndExecutePrompt(String prompt) {
            JSONObject response = new JSONObject();
            try {
                String key = prefs.getString("gemini_api_key", "");
                boolean codeTask = prompt.toLowerCase().contains("html") || prompt.toLowerCase().contains("code") || prompt.toLowerCase().contains("script");
                if (codeTask && getAvailableMemoryMB() > 300) { response.put("route", "LOCAL_EMBEDDED_CODER"); response.put("output", runLocalCoderInference(prompt)); }
                else if (key.length() > 10) { response.put("route", "CLOUD_GEMINI"); response.put("output", GeminiApiClient.generateContentSync(key, prompt)); }
                else { response.put("route", "LOCAL_ROUTER"); response.put("output", runLocalCoderInference(prompt)); }
            } catch (JSONException e) { try { response.put("error", e.getMessage()); } catch (JSONException ignored) {} }
            return response.toString();
        }
    }

    private String runLocalCoderInference(String prompt) {
        String path = new File(getFilesDir(), "model.gguf").getAbsolutePath();
        if (!new File(path).exists()) return "Model is still unpacking. Try again shortly.";
        if (modelHandle == 0) { llamaEngine = new LlamaEngine(); modelHandle = llamaEngine.initModel(path, 1024, .2f); }
        return modelHandle == 0 ? "Error initializing the embedded model." : llamaEngine.generateText(modelHandle, prompt, 256);
    }

    private long getAvailableMemoryMB() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) { manager.getMemoryInfo(info); return info.availMem / (1024 * 1024); }
        return 0;
    }

    @Override protected void onDestroy() {
        if (wsServer != null) wsServer.stop();
        if (llamaEngine != null && modelHandle != 0) llamaEngine.freeModel(modelHandle);
        if (aiExecutor != null) aiExecutor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
