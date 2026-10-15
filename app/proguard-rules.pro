# ProGuard rules for OfflineTranslator

# ===== ONNX Runtime =====
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ===== sherpa-onnx =====
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ===== Kotlinx Serialization =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keep,includedescriptorclasses class com.example.offlinetranslator.**$$serializer { *; }
-keepclassmembers class com.example.offlinetranslator.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.offlinetranslator.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ===== Compose =====
-keep class androidx.compose.runtime.** { *; }

# ===== Kotlin reflection =====
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.**

# ===== ML Kit =====
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ===== Native libs =====
-keepclasseswithmembernames class * {
    native <methods>;
}
