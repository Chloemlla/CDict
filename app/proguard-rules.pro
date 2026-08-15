# CDict R8 keep rules (release build).
# androidx libraries (Room, Compose, coroutines) ship their own consumer rules;
# these guard app entry points and Room reflection invoked via generated code.

# Entry point referenced from AndroidManifest.
-keep public class com.chloemlla.cdict.MainActivity { *; }

# Room reflection plumbing: entity/DAO/database accessors are reached through
# generated *_Impl classes at runtime, so they must survive obfuscation.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <methods>;
}
