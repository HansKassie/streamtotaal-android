# kotlinx.serialization: behoud @Serializable model-klassen
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class nl.streamfix.**$$serializer { *; }
-keepclassmembers class nl.streamfix.** {
    *** Companion;
}
-keepclasseswithmembers class nl.streamfix.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Sentry behoudt regelnummers voor leesbare stacktraces
-keepattributes LineNumberTable,SourceFile
