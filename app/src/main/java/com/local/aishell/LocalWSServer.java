package com.local.aishell;

import android.content.Context;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Serves the packaged UI and exposes a loopback-only WebSocket shell. */
public class LocalWSServer extends NanoWSD {
    private final Context context;

    public LocalWSServer(Context context, int port) {
        super(port);
        this.context = context.getApplicationContext();
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        return new ShellWebSocket(handshake);
    }

    @Override
    protected Response serveHttp(IHTTPSession session) {
        String uri = session.getUri();
        if (uri == null || uri.isEmpty() || "/".equals(uri)) {
            uri = "/index.html";
        }
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }

        // Do not allow an HTTP request to escape the APK assets directory.
        if (uri.contains("..") || uri.contains("\\") || uri.startsWith("/")) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden");
        }

        try {
            InputStream stream = context.getAssets().open(uri);
            return newChunkedResponse(Response.Status.OK, mimeType(uri), stream);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
        }
    }

    private static String mimeType(String path) {
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static final class ShellWebSocket extends WebSocket {
        private final PtyShellBridge pty = new PtyShellBridge();

        ShellWebSocket(IHTTPSession handshake) {
            super(handshake);
        }

        @Override
        protected void onOpen() {
            pty.start(output -> {
                try {
                    send(output);
                } catch (IOException ignored) {
                    pty.stop();
                }
            });
        }

        @Override
        protected void onClose(CloseCode code, String reason, boolean initiatedByRemote) {
            pty.stop();
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            String payload = message.getTextPayload();
            if (payload != null) pty.write(payload);
        }

        @Override
        protected void onPong(WebSocketFrame pong) { }

        @Override
        protected void onException(IOException exception) {
            pty.stop();
        }
    }
}
