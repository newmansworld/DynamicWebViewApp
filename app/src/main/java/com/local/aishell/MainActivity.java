package com.local.aishell;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
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
    private long modelHandle = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        aiExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("AIShellPrefs", MODE_PRIVATE);

        aiExecutor.execute(this::ensureEmbeddedModelExtracted);

        // 1. Start HTTP + WebSocket Server on loopback port 8080
        try {
            wsServer = new LocalWSServer(getApplicationContext(), 8080);
            wsServer.start(5000, false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. Configure WebView
        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // 3. Enable Chrome DevTools Protocol (CDP) for remote debugging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        // 4. Register Native JS Bridges
        webView.addJavascriptInterface(new NativeBridge(), "NativeBridge");
        webView.addJavascriptInterface(new NativeAI(), "NativeAI");

        // 5. Load through local loopback to provide a proper WebGPU/IndexedDB origin
        webView.loadUrl("http://localhost:8080/index.html");
    }

    private void ensureEmbeddedModelExtracted() {
        File modelFile = new File(getFilesDir(), "model.gguf");
        if (!modelFile.exists()) {
            try (InputStream is = getAssets().open("model.gguf");
                 OutputStream os = new FileOutputStream(modelFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class NativeBridge {
        @JavascriptInterface
        public void navigateToUrl(final String url) {
            mainHandler.post(() -> webView.loadUrl(url));
        }

        @JavascriptInterface
        public void setApiKey(String apiKey) {
            prefs.edit().putString("gemini_api_key", apiKey.trim()).apply();
        }

        @JavascriptInterface
        public String getApiKey() {
            return prefs.getString("gemini_api_key", "");
        }
    }

    public class NativeAI {
        @JavascriptInterface
        public void processPromptAsync(final String prompt, final String callbackJsFunction) {
            aiExecutor.execute(() -> {
                String resultJson = routeAndExecutePrompt(prompt);
                mainHandler.post(() -> {
                    String safeScript = callbackJsFunction + "(" + JSONObject.quote(resultJson) + ");";
                    webView.evaluateJavascript(safeScript, null);
                });
            });
        }

        private String routeAndExecutePrompt(String prompt) {
            JSONObject response = new JSONObject();
            try {
                boolean isCodeTask = prompt.toLowerCase().contains("html") || prompt.toLowerCase().contains("code") || prompt.toLowerCase().contains("script");
                String apiKey = prefs.getString("gemini_api_key", "");

                if (isCodeTask && getAvailableMemoryMB() > 300) {
                    response.put("route", "LOCAL_EMBEDDED_CODER");
                    response.put("output", runLocalCoderInference(prompt));
                } else if (apiKey.length() > 10) {
                    response.put("route", "CLOUD_GEMINI");
                    response.put("output", GeminiApiClient.generateContentSync(apiKey, prompt));
                } else {
                    response.put("route", "LOCAL_ROUTER");
                    response.put("output", runLocalCoderInference(prompt));
                }
            } catch (JSONException e) {
                try {
                    response.put("error", e.getMessage());
                } catch (JSONException ignored) {}
            }
            return response.toString();
        }
    }

    private String runLocalCoderInference(String prompt) {
        String modelPath = getFilesDir().getAbsolutePath() + "/model.gguf";
        if (modelHandle == 0) {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                return "Model Extraction Pending: Embedded model is unpacking. Try again in a moment.";
            }
            llamaEngine = new LlamaEngine();
            modelHandle = llamaEngine.initModel(modelPath, 1024, 0.2f);
            if (modelHandle == 0) return "Error initializing model in C++ heap.";
        }
        return llamaEngine.generateText(modelHandle, prompt, 256);
    }

    private long getAvailableMemoryMB() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
            return memoryInfo.availMem / (1024 * 1024);
        }
        return 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wsServer != null) wsServer.stop();
        if (llamaEngine != null && modelHandle != 0) llamaEngine.freeModel(modelHandle);
        if (aiExecutor != null && !aiExecutor.isShutdown()) aiExecutor.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}