#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaState {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    float temperature = 0.2f;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_local_aishell_LlamaEngine_initModel(JNIEnv* env, jobject thiz, jstring jmodelPath, jint nCtx, jfloat temperature) {
    const char* model_path = env->GetStringUTFChars(jmodelPath, nullptr);
    llama_backend_init();

    LlamaState* state = new LlamaState();
    state->temperature = temperature;

    llama_model_params model_params = llama_model_default_params();
    state->model = llama_load_model_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodelPath, model_path);

    if (!state->model) {
        delete state;
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (nCtx > 0) ? nCtx : 1024;
    ctx_params.n_threads = 4;

    state->ctx = llama_new_context_with_model(state->model, ctx_params);
    if (!state->ctx) {
        llama_free_model(state->model);
        delete state;
        return 0;
    }

    return reinterpret_cast<jlong>(state);
}

JNIEXPORT jstring JNICALL
Java_com_local_aishell_LlamaEngine_generateText(JNIEnv* env, jobject thiz, jlong handle, jstring jprompt, jint maxTokens) {
    LlamaState* state = reinterpret_cast<LlamaState*>(handle);
    if (!state || !state->ctx) return env->NewStringUTF("Error: Invalid handle.");

    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt_str(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);

    const int n_prompt_tokens = -llama_tokenize(state->model, prompt_str.c_str(), prompt_str.length(), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    if (llama_tokenize(state->model, prompt_str.c_str(), prompt_str.length(), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        return env->NewStringUTF("Error: Tokenization failed.");
    }

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size(), 0, 0);
    if (llama_decode(state->ctx, batch) != 0) {
        return env->NewStringUTF("Error: Decode failed.");
    }

    std::string output_text = "";
    int n_cur = prompt_tokens.size();
    const int n_vocab = llama_n_vocab(state->model);

    for (int i = 0; i < maxTokens; ++i) {
        auto* logits = llama_get_logits_ith(state->ctx, -1);
        if (!logits) break;

        std::vector<llama_token_data> candidates;
        candidates.reserve(n_vocab);
        for (llama_token token_id = 0; token_id < n_vocab; token_id++) {
            candidates.push_back(llama_token_data{token_id, logits[token_id], 0.0f});
        }

        llama_token_data_array candidates_p = { candidates.data(), candidates.size(), false };

        llama_token new_token_id;
        if (state->temperature > 0.0f) {
            llama_sample_temp(state->ctx, &candidates_p, state->temperature);
            new_token_id = llama_sample_token(state->ctx, &candidates_p);
        } else {
            new_token_id = llama_sample_token_greedy(state->ctx, &candidates_p);
        }

        if (llama_token_is_eog(state->model, new_token_id)) break;

        char buf[256];
        int n = llama_token_to_piece(state->model, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) output_text.append(buf, n);

        llama_batch next_batch = llama_batch_get_one(&new_token_id, 1, n_cur, 0);
        n_cur++;

        if (llama_decode(state->ctx, next_batch) != 0) break;
    }

    return env->NewStringUTF(output_text.c_str());
}

JNIEXPORT void JNICALL
Java_com_local_aishell_LlamaEngine_freeModel(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaState* state = reinterpret_cast<LlamaState*>(handle);
    if (state) {
        if (state->ctx) llama_free(state->ctx);
        if (state->model) llama_free_model(state->model);
        delete state;
        llama_backend_free();
    }
}

}