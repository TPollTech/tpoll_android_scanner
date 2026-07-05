-keep class com.tpoll.scanner.model.** { *; }
-keep class com.tpoll.scanner.scanner.** { *; }
-keep class com.tpoll.scanner.updater.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Obfuscate everything else aggressively
-renamesourcefileattribute SourceFile
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, SourceFile, LineNumberTable, EnclosingMethod

# Keep only entry points
-keep class com.tpoll.scanner.TPollApp { *; }
-keep class com.tpoll.scanner.MainActivity { *; }
-keep class com.tpoll.scanner.protection.ShieldService { *; }
-keep class com.tpoll.scanner.protection.PackageReceiver { *; }
-keep class com.tpoll.scanner.BootReceiver { *; }
