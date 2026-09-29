package com.local.aishell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PtyShellBridge {
    private Process process;
    private OutputStream stdin;
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
    private boolean isRunning = false;

    public interface OutputListener {
        void onOutput(String text);
    }

    public synchronized void start(OutputListener listener) {
        if (isRunning) return;
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
            pb.redirectErrorStream(true);
            process = pb.start();
            stdin = process.getOutputStream();
            isRunning = true;

            readerExecutor.execute(() -> {
                byte[] buffer = new byte[1024];
                InputStream stdout = process.getInputStream();
                int read;
                try {
                    while (isRunning && (read = stdout.read(buffer)) != -1) {
                        String data = new String(buffer, 0, read, StandardCharsets.UTF_8);
                        if (listener != null) {
                            listener.onOutput(data);
                        }
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            if (listener != null) {
                listener.onOutput("\r\n[Failed to spawn local /system/bin/sh]\r\n");
            }
        }
    }

    public synchronized void write(String input) {
        if (!isRunning || stdin == null) return;
        try {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException ignored) {}
    }

    public synchronized void stop() {
        isRunning = false;
        readerExecutor.shutdownNow();
        if (process != null) {
            process.destroy();
            process = null;
        }
    }
}