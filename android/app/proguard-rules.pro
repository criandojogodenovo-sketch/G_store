# Regras ProGuard/R8 do G Store

# kotlinx.serialization — mantém serializadores gerados
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.gstore.app.**$$serializer { *; }
-keepclassmembers class com.gstore.app.** { *** Companion; }
-keepclasseswithmembers class com.gstore.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit/OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
