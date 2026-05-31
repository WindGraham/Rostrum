# Extension API ProGuard rules

# Keep all API interfaces
-keep interface com.omnimaster.extension.api.** { *; }

# Keep Extension interface implementations
-keep class * implements com.omnimaster.extension.api.Extension {
    <init>(...);
}

# Keep data classes
-keep class com.omnimaster.extension.api.** { *; }

