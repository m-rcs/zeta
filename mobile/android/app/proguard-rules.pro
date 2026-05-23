# Zed mobile uses NativeActivity; keep its hooks visible to the JVM.
-keep class android.app.NativeActivity { *; }
-keep class dev.zed.mobile.** { *; }
