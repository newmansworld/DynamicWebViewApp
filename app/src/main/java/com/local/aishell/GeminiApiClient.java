package com.local.aishell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GeminiApiClient {
    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    public static String generateContentSync(String apiKey, String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "Error: Missing API Key. Set your Gemini API key in Settings.";
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(GEMINI_ENDPOINT + apiKey.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            JSONObject textPart = new JSONObject().put("text", prompt);
            JSONObject contentObj = new JSONObject().put("parts", new JSONArray().put(textPart));
            JSONObject payload = new JSONObject().put("contents", new JSONArray().put(contentObj));

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    statusCode >= 200 && statusCode < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8
            ));

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line.trim());
            }

            if (statusCode >= 200 && statusCode < 300) {
                JSONObject root = new JSONObject(response.toString());
                JSONArray candidates = root.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    return candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                }
            }
            return "Gemini API Response Error (" + statusCode + "): " + response.toString();
        } catch (Exception e) {
            return "Network Error: " + e.getLocalizedMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}