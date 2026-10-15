# Release shrink for a Play-style APK. Tests never ship in this variant.

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, Exceptions, SourceFile, LineNumberTable

# Gson / Retrofit DTOs and persisted JSON
-keep class com.example.music.domain.model.** { *; }
-keep class com.example.music.core.network.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Hilt / Dagger (consumer rules usually cover this; keep entry points)
-keep class com.example.music.MusicApplication { *; }
-keep class com.example.music.MainActivity { *; }
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.appwidget.AppWidgetProvider

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.bouncycastle.**
-dontwarn okhttp3.internal.platform.**
