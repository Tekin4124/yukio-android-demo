# Add project specific ProGuard rules here.

# ---------- Stack traces ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- Retrofit / OkHttp ----------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep interface com.example.test.data.remote.ApiService { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# R8 tam modunda Retrofit'in Response<T>/suspend imzalari icin resmi uclu kural.
# Response satiri OLMAZSA: yayin derlemesinde her parse cagrisi
# "Response must include generic type" hatasiyla patlar (2026-07-04 sahada
# rc kopyasıyla yakalandi, Play'e yuklenmeden duzeltildi).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---------- Gson ----------
-keep class com.example.test.data.remote.** { *; }
-keep class com.example.test.data.model.** { *; }
# Billing JSON field names are part of the backend contract. Purchase tokens
# remain redacted by their Kotlin toString implementations; these rules only
# preserve Gson serialization names in minified release builds.
-keep interface com.example.test.billing.BillingBackendApi { *; }
-keep class com.example.test.billing.BillingSyncRequest { *; }
-keep class com.example.test.billing.SignedInstallationRequest { *; }
-keep class com.example.test.billing.BillingSyncResponse { *; }
-keep class com.example.test.billing.NotificationTotalSyncRequest { *; }
-keep class com.example.test.billing.NotificationTotalSyncResponse { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- Room ----------
-keep class com.example.test.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---------- Kotlin coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---------- Kotlinx Serialization ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Compose ----------
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# ---------- Google Maps + Play Services ----------
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ---------- Timber ----------
-assumenosideeffects class timber.log.Timber* {
    public static *** v(...);
    public static *** d(...);
}
-dontwarn org.jetbrains.annotations.**

# ---------- App entry points ----------
-keep class com.example.test.YukBulucuApp { *; }
-keep class com.example.test.MainActivity { *; }
-keep class com.example.test.notification.WhatsappNotificationListener { *; }

# ---------- WorkManager ----------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
