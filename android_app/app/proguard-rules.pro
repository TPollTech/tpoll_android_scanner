# ============================================================
# TPoll Scanner - ProGuard Rules
# ============================================================

# --- Keep annotations ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile
-keepattributes LineNumberTable

# Rename source file to reduce string exposure
-renamesourcefileattribute SourceFile

# --- Gson ---
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- Compose ---
-dontwarn androidx.compose.**

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- App Models (keep for Gson deserialization) ---
-keep class com.tpoll.scanner.model.** { *; }
-keep class com.tpoll.scanner.scanner.ScoringRules { *; }
-keep class com.tpoll.scanner.scanner.DangerousCombo { *; }
-keep class com.tpoll.scanner.scanner.AppAnalyzer$VirusDb { *; }
-keep class com.tpoll.scanner.scanner.AppAnalyzer$VirusDbEntry { *; }
-keep class com.tpoll.scanner.scanner.AppAnalyzer$SuspiciousPattern { *; }

# --- Entry Points (keep unobfuscated) ---
-keep class com.tpoll.scanner.TPollApp { *; }
-keep class com.tpoll.scanner.MainActivity { *; }
-keep class com.tpoll.scanner.ScanService { *; }
-keep class com.tpoll.scanner.ScanWorker { *; }
-keep class com.tpoll.scanner.BootReceiver { *; }
-keep class com.tpoll.scanner.ScanReceiver { *; }
-keep class com.tpoll.scanner.protection.ShieldService { *; }
-keep class com.tpoll.scanner.protection.PackageReceiver { *; }

# --- Remove logging in release ---
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# --- Optimize ---
-optimizations !code/simplification/variable,!code/simplification/advanced,!field/*,!class/merging/*
-optimizationpasses 5
