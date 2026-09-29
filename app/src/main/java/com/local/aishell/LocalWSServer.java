package com.local.aishell;

import android.content.Context;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elionen.NanoWSD;
import fi.iki.elionen.NanoWSD.WebSocketFrame.CloseCode;

import java.io.IOException;
import java.io.InputStream;

public class LocalWSServer extends NanoWSD {
    private final Context context;

    public LocalWSServer(Context context, int port) {
        super(port);
        this.context = context;
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        return new ShellWebSocket(handshake);
    }

    @Override
    protected Response serveHttp(IHTTPSession session) {
        String uri = session.getUri();
        if (uri == null || uri.equals("/") || uri.isEmpty()) {
            uri = "/index.html";
        }
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }

        try {
            InputStream is = context.getAssets().open(uri);
            String mime = uri.endsWith(".html") ? "text/html" :
                          uri.endsWith(".js") ? "application/javascript" :
                          uri.endsWith(".css") ? "text/css" : "application/octet-stream";
            return newChunkedResponse(Response.Status.OK, mime, is);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found: " + uri);
        }
    }

    private static class ShellWebSocket extends WebSocket {
        private final PtyShellBridge pty = new PtyShellBridge();

        public ShellWebSocket(IHTTPSession handshake) {
            super(handshake);
        }

        @Override
        protected void onOpen() {
            pty.start(output -> {
                try {
                    send(output);
                } catch (IOException ignored) {}
            });
        }

        @Override
        protected void onClose(CloseCode code, String reason, boolean initiatedByRemote) {
            pty.stop();
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            String payload = message.getTextPayload();
            if (payload != null) {
                pty.write(payload);
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {}

        @Override
        protected void onException(IOException exception) {
            pty.stop();
        }
    }
}