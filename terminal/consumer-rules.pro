# consumer-rules.pro
# Keep JNI methods
-keepclasseswithmembernames class com.omnimaster.terminal.Pty {
    native <methods>;
}
-keepclasseswithmembernames class com.omnimaster.terminal.Pty$Companion {
    native <methods>;
}
