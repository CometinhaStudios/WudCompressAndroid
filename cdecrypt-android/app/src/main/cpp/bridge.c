/* JNI adapter for VitaSmith CDecrypt, GPL-3.0-or-later */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>

extern int main_utf8(int argc, char **argv);

/* Captures CDecrypt's stdout/stderr into a readable per-run report.
   All work occurs on Java's background worker, never UI thread. */
JNIEXPORT jint JNICALL
Java_br_com_cdecryptmobile_MainActivity_nativeDecrypt(JNIEnv *env, jclass clazz,
        jstring input, jstring output, jstring logFile) {
    (void)clazz;
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

    char *argv[] = {"cdecrypt", a, b, NULL};
    int f = open(c, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    int oldOut = -1, oldErr = -1;
    if (f < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "CDecrypt", "Could not create diagnostic log");
        free(a); free(b); free(c); return -4;
    }

    dprintf(f, "CDecrypt Android diagnostic report\nInput: %s\nOutput: %s\n----- CDecrypt output -----\n", a, b);
    fflush(stdout);
    fflush(stderr);
    oldOut = dup(STDOUT_FILENO);
    oldErr = dup(STDERR_FILENO);
    if (dup2(f, STDOUT_FILENO) < 0 || dup2(f, STDERR_FILENO) < 0) {
        if (oldOut >= 0) dup2(oldOut, STDOUT_FILENO);
        if (oldErr >= 0) dup2(oldErr, STDERR_FILENO);
        if (oldOut >= 0) close(oldOut);
        if (oldErr >= 0) close(oldErr);
        close(f);
        free(a); free(b); free(c); return -5;
    }
    int result = main_utf8(3, argv);
    fflush(stdout);
    fflush(stderr);
    dprintf(f, "\n----- Exit code: %d -----\n", result);
    if (oldOut >= 0) { dup2(oldOut, STDOUT_FILENO); close(oldOut); }
    if (oldErr >= 0) { dup2(oldErr, STDERR_FILENO); close(oldErr); }
    close(f);
    __android_log_print(result == 0 ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, "CDecrypt",
                        "Extraction exit code %d", result);
    free(a); free(b); free(c);
    return result;
}
