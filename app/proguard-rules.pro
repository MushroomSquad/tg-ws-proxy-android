-keepnames class com.flowseal.tgwsproxy.proxy.** { *; }
-keepclassmembers class com.flowseal.tgwsproxy.proxy.** { *; }
-dontwarn javax.annotation.**

# ByeDPI / hev-socks5-tunnel JNI
-keep class com.flowseal.tgwsproxy.byedpi.core.ByeDpiProxy { *; }
-keep class com.flowseal.tgwsproxy.byedpi.core.TProxyService { *; }
-keep class com.flowseal.tgwsproxy.byedpi.core.VpnProtector { *; }
-keepclassmembers class com.flowseal.tgwsproxy.byedpi.core.** {
    native <methods>;
}
