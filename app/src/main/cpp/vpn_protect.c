#include "vpn_protect.h"

#include <android/log.h>
#include <pthread.h>

#define LOG_TAG "VpnProtect"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM *g_vm = NULL;
static jobject g_vpn_service = NULL;
static jmethodID g_protect_method = NULL;
static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;

void vpn_protect_init(JavaVM *vm) {
    g_vm = vm;
}

void vpn_protect_set_service(JNIEnv *env, jobject vpn_service) {
    pthread_mutex_lock(&g_mutex);
    if (g_vpn_service != NULL) {
        (*env)->DeleteGlobalRef(env, g_vpn_service);
        g_vpn_service = NULL;
        g_protect_method = NULL;
    }
    if (vpn_service != NULL) {
        g_vpn_service = (*env)->NewGlobalRef(env, vpn_service);
        jclass cls = (*env)->GetObjectClass(env, vpn_service);
        g_protect_method = (*env)->GetMethodID(env, cls, "protect", "(I)Z");
        (*env)->DeleteLocalRef(env, cls);
        if (g_protect_method == NULL) {
            ALOGE("VpnService.protect(I)Z not found");
        } else {
            ALOGI("VpnService protect attached");
        }
    }
    pthread_mutex_unlock(&g_mutex);
}

void vpn_protect_clear(JNIEnv *env) {
    vpn_protect_set_service(env, NULL);
}

int vpn_protect_fd(int fd) {
    if (fd < 0 || g_vm == NULL) {
        return 0;
    }

    pthread_mutex_lock(&g_mutex);
    jobject service = g_vpn_service;
    jmethodID method = g_protect_method;
    pthread_mutex_unlock(&g_mutex);

    if (service == NULL || method == NULL) {
        return 0;
    }

    JNIEnv *env = NULL;
    int attached = 0;
    int get_env = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
    if (get_env == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != 0) {
            ALOGE("AttachCurrentThread failed");
            return 0;
        }
        attached = 1;
    } else if (get_env != JNI_OK) {
        return 0;
    }

    jboolean ok = (*env)->CallBooleanMethod(env, service, method, fd);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        ok = JNI_FALSE;
    }

    if (attached) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return ok ? 1 : 0;
}
