#ifndef TGWSPROXY_VPN_PROTECT_H
#define TGWSPROXY_VPN_PROTECT_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

void vpn_protect_init(JavaVM *vm);
void vpn_protect_set_service(JNIEnv *env, jobject vpn_service);
void vpn_protect_clear(JNIEnv *env);
int vpn_protect_fd(int fd);

#ifdef __cplusplus
}
#endif

#endif
