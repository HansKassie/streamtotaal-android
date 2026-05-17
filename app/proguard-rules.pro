# ---- Algemene attributen ----
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepattributes Signature, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
# Sentry/leesbare stacktraces
-keepattributes LineNumberTable, SourceFile

# ---- kotlinx.serialization ----
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class nl.streamfix.**$$serializer { *; }
-keepclassmembers class nl.streamfix.** {
    *** Companion;
}
-keepclasseswithmembers class nl.streamfix.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
# @Serializable model/DTO-klassen die alleen via reflectie/serializer leven
-keep @kotlinx.serialization.Serializable class nl.streamfix.** { *; }
-keep class nl.streamfix.data.remote.dto.** { *; }

# ---- Retrofit 2 (R8 fullMode-veilig) ----
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Onze Retrofit-API interface
-keep interface nl.streamfix.data.remote.XtreamApi { *; }

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Media3 / ExoPlayer ----
-dontwarn androidx.media3.**

# ---- Google Cast ----
# CastOptionsProvider wordt via reflectie geladen vanuit de manifest-meta-data;
# zonder keep verdwijnt hij in de release en werkt casten niet meer.
-keep class nl.streamfix.cast.CastOptionsProvider { *; }
-keep class * implements com.google.android.gms.cast.framework.OptionsProvider { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn androidx.mediarouter.**

# Hilt, Room en Coil leveren hun eigen consumer-regels; niets extra nodig.
