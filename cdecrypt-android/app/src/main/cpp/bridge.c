/* Native JNI bridge for CDecrypt Android, GPL-3.0-or-later.
   Calls back on the same background worker thread that invoked JNI. */
#define _GNU_SOURCE
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>

extern int main_utf8(int argc, char **argv);
static JNIEnv *g_env = NULL;
static jclass g_class = NULL;
static jmethodID g_progress = NULL;
static jint g_last_progress = -1;
static char g_last_file[256] = {0};

void android_cdecrypt_progress(uint64_t done, uint64_t total, const char *file) {
    if (!g_env || !g_class || !g_progress) return;
    jint promille = total ? (jint)((double)done * 1000.0 / (double)total) : 0;
    if (promille > 999) promille = 999;
    if (promille < 0) promille = 0;
    if (file == NULL) file = "";
    if (promille == g_last_progress && strncmp(file, g_last_file, 255) == 0) return;
    if (promille == g_last_progress && done != 0 && (promille % 10 != 0)) return;
    snprintf(g_last_file, sizeof(g_last_file), "%s", file);
    g_last_progress = promille;
    jstring filename = (*g_env)->NewStringUTF(g_env, g_last_file);
    if (filename) {
        (*g_env)->CallStaticVoidMethod(g_env, g_class, g_progress, promille, filename);
        (*g_env)->DeleteLocalRef(g_env, filename);
    }
    if ((*g_env)->ExceptionCheck(g_env)) {
        (*g_env)->ExceptionDescribe(g_env);
        (*g_env)->ExceptionClear(g_env);
    }
}

JNIEXPORT jint JNICALL
Java_br_com_cdecryptmobile_CDecryptService_nativeDecrypt(JNIEnv *env, jclass clazz,
        jstring input, jstring output, jstring logFile) {
    if (!input || !output || !logFile) return -1;
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    const char *out = (*env)->GetStringUTFChars(env, output, NULL);
    const char *log = (*env)->GetStringUTFChars(env, logFile, NULL);
    if (!in || !out || !log) {
        if (in) (*env)->ReleaseStringUTFChars(env, input, in);
        if (out) (*env)->ReleaseStringUTFChars(env, output, out);
        if (log) (*env)->ReleaseStringUTFChars(env, logFile, log);
        return -2;
    }
    char *a = strdup(in), *b = strdup(out), *c = strdup(log);
    (*env)->ReleaseStringUTFChars(env, input, in);
    (*env)->ReleaseStringUTFChars(env, output, out);
    (*env)->ReleaseStringUTFChars(env, logFile, log);
    if (!a || !b || !c) { free(a); free(b); free(c); return -3; }
    int f = open(c, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (f < 0) { free(a); free(b); free(c); return -4; }

    char *argv[] = {"cdecrypt", a, b, NULL};
    dprintf(f, "CDecrypt Android v0.3 - Diagnostico\nEntrada: %s\nSaida: %s\n", a, b);
    fflush(stdout);
    fflush(stderr);
    int oldOut = dup(STDOUT_FILENO);
    int oldErr = dup(STDERR_FILENO);
    if (oldOut < 0 || oldErr < 0 || dup2(f, STDOUT_FILENO) < 0 || dup2(f, STDERR_FILENO) < 0) {
        if (oldOut >= 0) { dup2(oldOut, STDOUT_FILENO); close(oldOut); }
        if (oldErr >= 0) { dup2(oldErr, STDERR_FILENO); close(oldErr); }
        close(f); free(a); free(b); free(c); return -5;
    }

    g_env = env;
    g_class = clazz;
    g_progress = (*env)->GetStaticMethodID(env, clazz, "nativeProgress", "(ILjava/lang/String;)V");
    g_last_progress = -1;
    g_last_file[0] = 0;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); g_progress = NULL; }

    int result = main_utf8(3, argv);
    fflush(stdout); fflush(stderr);
    dprintf(f, "\n----- Resultado: %d -----\n", result);
    g_env = NULL; g_class = NULL; g_progress = NULL;
    dup2(oldOut, STDOUT_FILENO); close(oldOut);
    dup2(oldErr, STDERR_FILENO); close(oldErr);
    close(f);
    __android_log_print(result ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO,
                        "CDecrypt", "Final status: %d", result);
    free(a); free(b); free(c);
    return result;
}
