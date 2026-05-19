# Keep Xposed hook entry points
-keep class com.fan.edgex.hook.MainHook { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }
-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources { *; }

# Keep all hook/overlay/ui classes (referenced via reflection by Xposed)
-keep class com.fan.edgex.** { *; }

# Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep line numbers for crash debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep the entire Kotlin stdlib in the release DEX.
# This module runs as an Xposed hook inside system_server; the dynamically-loaded
# premium DEX resolves all Kotlin runtime classes through the module ClassLoader.
# R8 aggressively removes individual stdlib classes it considers unreferenced
# (Intrinsics, Result, collections helpers, etc.) — keeping the whole package
# avoids a chain of NoClassDefFoundError failures for each removed class.
-keep class kotlin.** { *; }
-keep class kotlin.jvm.** { *; }