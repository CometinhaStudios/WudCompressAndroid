/* JNI adapter for VitaSmith CDecrypt, GPL-3.0-or-later */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

extern int main_utf8(int argc, char **argv);

JNIEXPORT jint JNICALL
Java_br_com_cdecryptmobile_MainActivity_nativeDecrypt(JNIEnv *env, jclass clazz, jstring input, jstring output) {
    (void)clazz;
    if (!input || !output) return -1;
    const char *in = (*env)->GetStringUTFChars(env, input, NULL);
    const char *out = (*env)->GetStringUTFChars(env, output, NULL);
    if (!in || !out) {
        if (in) (*env)->ReleaseStringUTFChars(env, input, in);
        if (out) (*env)->ReleaseStringUTFChars(env, output, out);
        return -2;
    }
    char *arg0 = strdup("cdecrypt");
    char *arg1 = strdup(in);
    char *arg2 = strdup(out);
    (*env)->ReleaseStringUTFChars(env, input, in);
    (*env)->ReleaseStringUTFChars(env, output, out);
    if (!arg0 || !arg1 || !arg2) {
        free(arg0); free(arg1); free(arg2);
        return -3;
    }
    char *argv[] = {arg0, arg1, arg2, NULL};
    __android_log_print(ANDROID_LOG_INFO, "CDecrypt", "Extracting Wii U content");
    int result = main_utf8(3, argv);
    __android_log_print(result == 0 ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, "CDecrypt", "Finished status %d", result);
    free(arg0); free(arg1); free(arg2);
    return result;
}
