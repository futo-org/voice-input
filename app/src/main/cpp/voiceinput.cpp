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

    size_t ticker = 0;
    size_t last_partial_emit = 0;
    size_t last_exit = 0;
    size_t inference_time = 0;
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

    /*
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
    */
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
    if(num_procs < 1 || num_procs > 4) num_procs = 4; // Make sure the number is sane*/

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

static std::string narrowLanguageCode(const char *language, struct transcribe_capabilities *capabilities) {
    size_t len_ref = strlen(language);

    std::string result;
    bool exact_match = false;
    for(int i=0; i<capabilities->n_languages; i++) {
        size_t len_cmp = strlen(capabilities->languages[i]);
        if(strncmp(capabilities->languages[i], language, std::min(len_ref, len_cmp)) == 0) {
            if(len_cmp == len_ref) return capabilities->languages[i];

            // parakeet/nemotron dont support comma separated languages yet
            // otherwise we should be providing that

            //result.append(capabilities->languages[i]);
            //result.append(",");
            if(result.empty()) result = capabilities->languages[i];
        }
    }

    if(result.size() > 0 && result[result.size() - 1] == ',') result.pop_back();

    return result;
}

static std::string languageArrayToMergedString(JNIEnv *env, jobjectArray languages, struct transcribe_capabilities *capabilities) {
    std::string language_hint;
    int num_languages = env->GetArrayLength(languages);
    for (int i=0; i<num_languages; i++) {
        jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(languages, i));
        std::string str = jstring2string(env, jstr);

        language_hint.append(narrowLanguageCode(str.c_str(), capabilities));
        if(i < (num_languages - 1)) {
            language_hint.append(",");
        }
    }

    return language_hint;
}

static jstring WhisperGGML_infer(JNIEnv *env, jobject instance, jlong handle, jfloatArray samples_array, jstring prompt, jobjectArray languages, jobjectArray bail_languages) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    state->cancel_flag = 0;

    struct transcribe_capabilities capabilities;
    transcribe_capabilities_init(&capabilities);
    transcribe_model_get_capabilities(transcribe_get_model(state->session), &capabilities);

    std::string language_hint = languageArrayToMergedString(env, languages, &capabilities);
    std::string bail_language_hint = languageArrayToMergedString(env, bail_languages, &capabilities);

    state->env = env;
    state->partial_result_instance = instance;
    state->partial_result_method = env->GetMethodID(
        env->GetObjectClass(instance),
        "invokePartialResult",
          "(Ljava/lang/String;)V");

    // TODO: ban symbols toggle?

    int num_samples = env->GetArrayLength(samples_array);
    jfloat *samples = env->GetFloatArrayElements(samples_array, nullptr);


    struct transcribe_whisper_run_ext wp;

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
            TRANSCRIBE_EXT_SLOT_RUN,
            TRANSCRIBE_EXT_KIND_WHISPER_RUN
    )) {
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

static jboolean WhisperGGML_startStream(JNIEnv *env, jobject instance, jlong handle, jstring prompt, jobjectArray languages, jobjectArray bail_languages) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    state->cancel_flag = 0;

    struct transcribe_capabilities capabilities;
    transcribe_capabilities_init(&capabilities);
    transcribe_model_get_capabilities(transcribe_get_model(state->session), &capabilities);

    std::string language_hint = languageArrayToMergedString(env, languages, &capabilities);
    std::string bail_language_hint = languageArrayToMergedString(env, bail_languages, &capabilities);

    state->env = env;
    state->partial_result_instance = instance;
    state->partial_result_method = env->GetMethodID(
            env->GetObjectClass(instance),
            "invokePartialResult",
            "(Ljava/lang/String;)V");

    struct transcribe_asr4all_stream_ext a4ap;

    struct transcribe_stream_params s_params;
    struct transcribe_run_params r_params;
    transcribe_stream_params_init(&s_params);
    transcribe_run_params_init(&r_params);

    r_params.task = TRANSCRIBE_TASK_TRANSCRIBE;
    r_params.timestamps = TRANSCRIBE_TIMESTAMPS_NONE;
    r_params.language = language_hint.c_str();
    r_params.keep_special_tags = false;
    r_params.bail_languages = bail_language_hint.c_str();

    if(transcribe_model_accepts_ext_kind(
            transcribe_get_model(state->session),
            TRANSCRIBE_EXT_SLOT_STREAM,
            TRANSCRIBE_EXT_KIND_ASR4ALL_STREAM
    )) {
        transcribe_asr4all_stream_ext_init(&a4ap);

        /* TODO: Tier selection by settings or hardware?
            {
              "name": "stream_c32r4",
              "commit": 32,
              "right_ctx": 4,
              "lookahead": 22
            },
            {
              "name": "stream_c128r32",
              "commit": 128,
              "right_ctx": 32,
              "lookahead": 162
            },
            {
              "name": "stream_c256r32",
              "commit": 256,
              "right_ctx": 32,
              "lookahead": 162
            },
            {
              "name": "stream_c512r32",
              "commit": 512,
              "right_ctx": 32,
              "lookahead": 162
            }
        */

        a4ap.commit_frames = 32;
        a4ap.right_ctx_frames = 4;
        a4ap.lookahead_frames = 22;

        s_params.family = &a4ap.ext;
    }

    state->ticker = 0;
    state->inference_time = 0;

    return transcribe_stream_begin(state->session, &r_params, &s_params) == TRANSCRIBE_OK;
}

static jboolean WhisperGGML_feedStream(JNIEnv *env, jobject instance, jlong handle, jfloatArray samples_array) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    state->cancel_flag = 0;

    state->env = env;
    state->partial_result_instance = instance;
    state->partial_result_method = env->GetMethodID(
            env->GetObjectClass(instance),
            "invokePartialResult",
            "(Ljava/lang/String;)V");

    int num_samples = env->GetArrayLength(samples_array);
    jfloat *samples = env->GetFloatArrayElements(samples_array, nullptr);

    auto t0 = duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();

    bool is_ahead = (state->last_exit - t0) > ((num_samples / 16) * 3 / 2);

    transcribe_status status;
    struct transcribe_stream_update result;
    transcribe_stream_update_init(&result);
    if((status = transcribe_stream_feed(state->session, samples, num_samples, &result)) != TRANSCRIBE_OK) {
        AKLOGE("stream feed failed! %s", transcribe_status_string(status));
        return false;
    }

    auto t1 = duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();

    struct transcribe_stream_text text_result;
    transcribe_stream_text_init(&text_result);
    if ((status = transcribe_stream_get_text(state->session, &text_result)) != TRANSCRIBE_OK) {
        AKLOGE("stream get_text failed! %s", transcribe_status_string(status));
        return false;
    }

    auto t2 = duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();


    bool preview = (state->ticker - state->last_partial_emit) > 4000;
    if(is_ahead && preview && (status = transcribe_stream_feed(state->session, nullptr, 0, &result)) != TRANSCRIBE_OK) {
        AKLOGE("stream tail feed failed! %s", transcribe_status_string(status));
        // non critical
    }

    if(preview) state->last_partial_emit = state->ticker;
    state->ticker += num_samples;


    auto t3 = duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();

    //AKLOGI("[%d] processed %d samples in %d ms + %d ms + %d ms (%s)", state->ticker, num_samples, (int)(t1-t0), (int)(t2-t1), (int)(t3-t1), preview ? "previewed" : "no");

    if (text_result.full_text != nullptr) {
        jstring pjstr = string2jstring(env, text_result.full_text);
        state->env->CallVoidMethod(state->partial_result_instance, state->partial_result_method,
                                   pjstr);
        state->env->DeleteLocalRef(pjstr);
    }

    state->last_exit = duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
    state->inference_time += state->last_exit - t0;

    double xRT = (((double)state->ticker) / 16.0) / ((double)(state->inference_time));
    //AKLOGI("xRT = %.2f", (float)xRT);
    if(xRT < 0.8 && (state->ticker > 32000)) {
        AKLOGE("Falling back to offline.");
        std::string s = text_result.full_text;
        s.append(" [...]");
        jstring pjstr = string2jstring(env, s.c_str());
        state->env->CallVoidMethod(state->partial_result_instance, state->partial_result_method, pjstr);
        state->env->DeleteLocalRef(pjstr);
        return false;
    }

    return true;
}

static jstring WhisperGGML_stopStream(JNIEnv *env, jobject instance, jlong handle, jboolean finalize) {
    auto *state = reinterpret_cast<WhisperModelState *>(handle);
    state->cancel_flag = 0;
    state->env = env;

    if(!finalize) {
        transcribe_stream_reset(state->session);
        return nullptr;
    }

    transcribe_status status;
    struct transcribe_stream_update result;
    transcribe_stream_update_init(&result);
    if((status = transcribe_stream_finalize(state->session, &result)) != TRANSCRIBE_OK) {
        AKLOGE("stream finalize failed! %s", transcribe_status_string(status));
        return nullptr;
    }

    struct transcribe_stream_text text_result;
    transcribe_stream_text_init(&text_result);
    if((status = transcribe_stream_get_text(state->session, &text_result)) != TRANSCRIBE_OK) {
        AKLOGE("stream get_text failed! %s", transcribe_status_string(status));
        return nullptr;
    }

    jstring pjstr = string2jstring(state->env, text_result.full_text);
    return pjstr;
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
                const_cast<char *>("offlineInferNative"),
                const_cast<char *>("(J[FLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;"),
                reinterpret_cast<void *>(WhisperGGML_infer)
        },
        {
                const_cast<char *>("startStreamNative"),
                const_cast<char *>("(JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)Z"),
                reinterpret_cast<void *>(WhisperGGML_startStream)
        },
        {
                const_cast<char *>("feedStreamNative"),
                const_cast<char *>("(J[F)Z"),
                reinterpret_cast<void *>(WhisperGGML_feedStream)
        },
        {
                const_cast<char *>("stopStreamNative"),
                const_cast<char *>("(JZ)Ljava/lang/String;"),
                reinterpret_cast<void *>(WhisperGGML_stopStream)
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