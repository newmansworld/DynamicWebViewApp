package com.local.aishell;

public class LlamaEngine {
    static {
        System.loadLibrary("llama_bridge");
    }

    public native long initModel(String modelPath, int nCtx, float temperature);
    public native String generateText(long modelHandle, String prompt, int maxTokens);
    public native void freeModel(long modelHandle);
}