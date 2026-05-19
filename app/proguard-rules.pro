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

# Kotlin runtime support classes must survive R8 shrinking.
# The premium DEX is loaded via DexClassLoader at runtime and resolves these
# through the parent (module) ClassLoader — if R8 removes them from the host
# DEX they become unresolvable, causing NoClassDefFoundError in system_server.
-keep class kotlin.jvm.internal.** { *; }