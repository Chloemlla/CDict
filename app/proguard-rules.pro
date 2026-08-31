# CDict R8 keep rules (release build).
# androidx libraries (Room, Compose, coroutines) ship their own consumer rules;
# these guard app entry points and Room reflection invoked via generated code.

# Entry point referenced from AndroidManifest.
-keep public class com.chloemlla.cdict.MainActivity { *; }

# FileProvider for in-app update APK installation.
-keep class androidx.core.content.FileProvider { *; }

# Room reflection plumbing: entity/DAO/database accessors are reached through
# generated *_Impl classes at runtime, so they must survive obfuscation. Room
# instantiates the *_Impl via getDeclaredConstructor().newInstance(), so the
# no-arg constructor has to be kept as well — <methods> does not cover <init>.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
    <methods>;
}

# Lumen Crash SDK (com.chloemlla.lumen:lumen-crash). The AAR also ships
# consumer-rules.pro which AGP merges automatically; this copy is a backup so a
# custom shrinker / stripped consumer rules cannot let R8 drop the crash
# pipeline (gate would white-screen or the file provider would 404). The whole
# package is kept on purpose: the SDK reaches its own entry points reflectively
# and CI has no way to exercise the shrunk crash path, so an enumerated list
# would only be verified by users' devices. From
# Project-Lumen/lumen-crash/host-proguard-template.pro.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations

-keep class com.chloemlla.lumen.crash.** { *; }
-keepclassmembers class com.chloemlla.lumen.crash.** { *; }
-dontwarn com.chloemlla.lumen.crash.**
