# Keep DTO models and KSP-generated adapters serialized/deserialized with Moshi
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class com.example.data.remote.dto.** { *; }
-keep class com.example.data.remote.*JsonAdapter { *; }
-keep class com.example.data.remote.dto.*JsonAdapter { *; }
-keep class com.example.data.remote.OpenMapsApiClient$* { *; }
-keep class com.example.data.local.CachedPhotonPlace { *; }
-keep class com.example.data.local.CachedPhotonQueryEntry { *; }
-keep class com.example.domain.model.** { *; }

# Keep generic signatures & annotations for Moshi / Kotlin reflection
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep WorkManager workers & BroadcastReceivers instantiated by reflection
-keep class com.example.alarms.** extends androidx.work.ListenableWorker { *; }
-keep class com.example.alarms.** extends android.content.BroadcastReceiver { *; }

# Strip debug/verbose Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
