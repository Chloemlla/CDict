# CDict R8 keep rules (release build).
# androidx libraries (Room, Compose, coroutines) ship their own consumer rules;
# these guard app entry points and Room reflection invoked via generated code.

# Entry point referenced from AndroidManifest.
-keep public class com.chloemlla.cdict.MainActivity { *; }

# FileProvider for in-app update APK installation.
-keep class androidx.core.content.FileProvider { *; }
-keep class com.chloemlla.cdict.core.update.** { *; }

# Room reflection plumbing: entity/DAO/database accessors are reached through
# generated *_Impl classes at runtime, so they must survive obfuscation.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <methods>;
}

# Lumen Crash SDK (com.chloemlla.lumen:lumen-crash). The AAR also ships
# consumer-rules.pro which AGP merges automatically; this copy is a backup so a
# custom shrinker / stripped consumer rules cannot let R8 drop the crash
# pipeline (gate would white-screen or the file provider would 404). From
# Project-Lumen/lumen-crash/host-proguard-template.pro.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations

-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
    public static *** payload();
}
-keepclassmembers class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}

-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow(...);
    public static *** fingerprintHex();
    public static *** verifiedAuthorBlock();
}
-keep class com.chloemlla.lumen.crash.AuthorBlock { *; }

-keep class com.chloemlla.lumen.crash.LumenCrash { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfig { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfigBuilder { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashDefaults { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashFileProvider { *; }
-keep class com.chloemlla.lumen.crash.CrashReport { *; }
-keep class com.chloemlla.lumen.crash.CrashAppInfo { *; }
-keep class com.chloemlla.lumen.crash.CrashReportStore { *; }
-keep class com.chloemlla.lumen.crash.CrashBreadcrumbs { *; }
-keep class com.chloemlla.lumen.crash.CrashReportPasteUploader { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashReportScreenKt { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashGateKt { *; }

-keep class com.chloemlla.lumen.crash.** { *; }
-keepclassmembers class com.chloemlla.lumen.crash.** { *; }
-dontwarn com.chloemlla.lumen.crash.**
