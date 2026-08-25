#include <string>
#include <vector>
#include <map>
#include <jni.h>
#include <bits/sysconf.h>
#include "defines.h"
#include "voiceinput.h"
#include "jni_common.h"
#include "transcribe.h"
#include "transcribe/whisper.h"
#include "transcribe/asr4all.h"

std::string jstring2string(JNIEnv *env, jstring jStr) {
    if(jStr == nullptr) {
        AKLOGE("jstring is null!");
        return "";
    }

    const jsize stringUtf8Length = env->GetStringUTFLength(jStr);
    if (stringUtf8Length <= 0) {
        return "";
    }
    char stringChars[stringUtf8Length + 1];
    env->GetStringUTFRegion(jStr, 0, env->GetStringLength(jStr), stringChars);
    stringChars[stringUtf8Length] = '\0';

    return {stringChars};
}

jstring string2jstring(JNIEnv *env, const char *str) {
    jobject bb = env->NewDirectByteBuffer((void *)str, strlen(str));

    jclass cls_Charset = env->FindClass("java/nio/charset/Charset");
    jmethodID mid_Charset_forName = env->GetStaticMethodID(cls_Charset, "forName", "(Ljava/lang/String;)Ljava/nio/charset/Charset;");
    jobject charset = env->CallStaticObjectMethod(cls_Charset, mid_Charset_forName, env->NewStringUTF("UTF-8"));

    jmethodID mid_Charset_decode = env->GetMethodID(cls_Charset, "decode", "(Ljava/nio/ByteBuffer;)Ljava/nio/CharBuffer;");
    jobject cb = env->CallObjectMethod(charset, mid_Charset_decode, bb);
    env->DeleteLocalRef(bb);

    jclass cls_CharBuffer = env->FindClass("java/nio/CharBuffer");
    jmethodID mid_CharBuffer_toString = env->GetMethodID(cls_CharBuffer, "toString", "()Ljava/lang/String;");
    jstring s = (jstring)env->CallObjectMethod(cb, mid_CharBuffer_toString);

    return s;
}

struct whisper_context {
    int x;
};

struct WhisperModelState {
    JNIEnv *env = nullptr;
    struct transcribe_session *session = nullptr;

    volatile int cancel_flag = 0;
    jobject partial_result_instance;
    jmethodID partial_result_method;
};

void log_callback(transcribe_log_level level, const char *msg, void *userdata) {
    if(level >= TRANSCRIBE_LOG_LEVEL_ERROR) {
        AKLOGE("[transcribe:%d] %s", level, msg);
    } else {
        AKLOGI("[transcribe:%d] %s", level, msg);
    }
}

bool abort_callback(void* userdata) {
    auto *state = reinterpret_cast<WhisperModelState *>(userdata);
    if(state->cancel_flag) {
        return true;
    }

    if(state->partial_result_instance && state->partial_result_method) {
        const char* text = transcribe_full_text(state->session);
        if(text == nullptr) {
            AKLOGE("No text?");
        } else if(text[0] == '\0') {
            AKLOGE("Text is blank?");
        } else {
            jstring pjstr = string2jstring(state->env, text);
            state->env->CallVoidMethod(state->partial_result_instance, state->partial_result_method,
                                       pjstr);
            state->env->DeleteLocalRef(pjstr);
        }
    }

    return false;
}

static jlong WhisperGGML_open(JNIEnv *env, jclass clazz, jstring model_dir) {
    std::string model_dir_str = jstring2string(env, model_dir);

    auto *state = new WhisperModelState();
    memset(state, 0, sizeof(WhisperModelState));

    AKLOGE("[whisper] WhisperGGML_open from %s", model_dir_str.c_str());
    struct transcribe_model_load_params mlparams;
    transcribe_model_load_params_init(&mlparams);
    mlparams.backend = TRANSCRIBE_BACKEND_CPU;

    long num_procs = sysconf(_SC_NPROCESSORS_ONLN) / 2;
    if(num_procs < 2 || num_procs > 16) num_procs = 4; // Make sure the number is sane

    struct transcribe_session_params sparams;
    transcribe_session_params_init(&sparams);
    sparams.n_threads = (int)num_procs;

    auto result = transcribe_open(model_dir_str.c_str(), &mlparams, &sparams, &state->session);

    if(!state->session || result != TRANSCRIBE_OK){
        AKLOGE("Failed to initialize whisper_context from path '%s' with error: %s", model_dir_str.c_str(), transcribe_status_string(result));
        delete state;
        return 0L;
    }

    AKLOGE("[whisper] WhisperGGML_open success!");
    transcribe_set_abort_callback(state->session, abort_callback, (void*)state);
    return reinterpret_cast<jlong>(state);
}

static jstring WhisperGGML_infer(JNIEnv *env, jobject instance, jlong handle, jfloatArray samples_array, jstring prompt, jobjectArray languages, jobjectArray bail_languages, jint decoding_mode, jboolean suppress_non_speech_tokens) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    state->cancel_flag = 0;

    std::string language_hint;
    int num_languages = env->GetArrayLength(languages);
    for (int i=0; i<num_languages; i++) {
        jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(languages, i));
        std::string str = jstring2string(env, jstr);

        language_hint.append(str);
        if(i < (num_languages - 1)) {
            language_hint.append(",");
        }
    }

    std::string bail_language_hint;
    int num_bail_languages = env->GetArrayLength(bail_languages);
    for (int i=0; i<num_bail_languages; i++) {
        jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(bail_languages, i));
        std::string str = jstring2string(env, jstr);

        bail_language_hint.append(str);
        if(i < (num_bail_languages - 1)) {
            bail_language_hint.append(",");
        }
    }

    state->env = env;
    state->partial_result_instance = instance;
    state->partial_result_method = env->GetMethodID(
        env->GetObjectClass(instance),
        "invokePartialResult",
          "(Ljava/lang/String;)V");

    // TODO: ban symbols toggle?

    int num_samples = env->GetArrayLength(samples_array);
    jfloat *samples = env->GetFloatArrayElements(samples_array, nullptr);


    struct transcribe_whisper_run_ext wp = { };
    struct transcribe_asr4all_stream_ext a4ap = { };
    struct transcribe_run_params params;
    transcribe_run_params_init(&params);
    params.task = TRANSCRIBE_TASK_TRANSCRIBE;
    params.timestamps = TRANSCRIBE_TIMESTAMPS_NONE;
    params.language = language_hint.c_str();
    params.keep_special_tags = false;
    //params.spec_k_drafts = 0;
    params.bail_languages = bail_language_hint.c_str();

    if(transcribe_model_accepts_ext_kind(
            transcribe_get_model(state->session),
            TRANSCRIBE_EXT_SLOT_STREAM,
            TRANSCRIBE_EXT_KIND_ASR4ALL_STREAM
    )) {
        // TODO: for streaming
    } else if(transcribe_model_accepts_ext_kind(
            transcribe_get_model(state->session),
            TRANSCRIBE_EXT_SLOT_RUN,
            TRANSCRIBE_EXT_KIND_WHISPER_RUN
    )) {

        struct transcribe_whisper_run_ext wp = { };
        transcribe_whisper_run_ext_init(&wp);

        wp.audio_ctx = std::max(160, std::min(1500, (int)ceil((double)num_samples / (double)(320.0)) + 32));

        std::string prompt_str;
        if(prompt) {
            prompt_str = jstring2string(env, prompt);
            if(!prompt_str.empty()) {
                wp.initial_prompt = prompt_str.c_str();
            }
        }

        params.family = &wp.ext;
    }

    auto result = transcribe_run(state->session, samples, num_samples, &params);

    std::string output;
    if(result == TRANSCRIBE_ERR_LANG_BAILED) {
        auto language = transcribe_detected_language(state->session);
        if(language == nullptr || language[0] == '\0') {
            AKLOGE("Bailed due to language detection which is null/blank!");
            return 0;
        }

        output = "<>CANCELLED<> lang=";
        output.append(language);

        jstring jstr = string2jstring(env, output.c_str());
        return jstr;
    }

    if(result == TRANSCRIBE_ERR_ABORTED) {
        output = "<>CANCELLED<> flag";

        jstring jstr = string2jstring(env, output.c_str());
        return jstr;
    }

    if(result != TRANSCRIBE_OK) {
        AKLOGE("Failed to run! Status: %s", transcribe_status_string(result));
        return 0;
    }

    auto txt = transcribe_full_text(state->session);
    return string2jstring(env, txt);
}

static void WhisperGGML_close(JNIEnv *env, jclass clazz, jlong handle) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    if(!state) return;

    transcribe_session_free(state->session);

    delete state;
}

static void WhisperGGML_cancel(JNIEnv *env, jclass clazz, jlong handle) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    state->cancel_flag = 1;
}

static const JNINativeMethod sMethods[] = {
        {
                const_cast<char *>("openNative"),
                const_cast<char *>("(Ljava/lang/String;)J"),
                reinterpret_cast<void *>(WhisperGGML_open)
        },
        {
                const_cast<char *>("inferNative"),
                const_cast<char *>("(J[FLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;IZ)Ljava/lang/String;"),
                reinterpret_cast<void *>(WhisperGGML_infer)
        },
        {
                const_cast<char *>("cancelNative"),
                const_cast<char *>("(J)V"),
                reinterpret_cast<void *>(WhisperGGML_cancel)
        },
        {
                const_cast<char *>("closeNative"),
                const_cast<char *>("(J)V"),
                reinterpret_cast<void *>(WhisperGGML_close)
        }
};

int register_WhisperGGML(JNIEnv *env) {
    transcribe_log_set(log_callback, nullptr);

    const char *const kClassPathName = "org/futo/voiceinput/shared/ggml/WhisperGGML";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}