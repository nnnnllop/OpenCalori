# Add project specific ProGuard rules here.
# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.opencalori.app.**$$serializer { *; }
-keepclassmembers class com.opencalori.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.opencalori.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.opencalori.app.data.local.entity.** { *; }

# OkHttp / OkIO
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
