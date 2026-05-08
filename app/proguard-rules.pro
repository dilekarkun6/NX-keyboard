-keep class com.nxkeyboard.service.** { *; }
-keep class com.nxkeyboard.keyboard.** { *; }
-keep class com.nxkeyboard.settings.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**
-keep class okio.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keep class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }

-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
