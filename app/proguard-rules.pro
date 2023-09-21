# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn androidx.databinding.**
-keep class androidx.databinding.** { *; }
-keep class ua.com.programmer.agentventa.databinding.** { *; }
-keep class * extends androidx.databinding.DataBinderMapper { *; }
-keep class * extends androidx.databinding.ViewDataBinding { *; }
-keepclassmembers class * extends androidx.databinding.ViewDataBinding { *; }
-keepclassmembers class ua.com.programmer.agentventa.databinding.**  {
    public <methods>;
}

#-keep class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder { *; }
#-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder { *; }
#-keep class * extends androidx.recyclerview.widget.ListAdapter { *; }
#-keepclassmembers class * extends androidx.recyclerview.widget.ListAdapter { *; }

-keep class kotlin.Metadata { *; }
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
 -keep,allowobfuscation,allowshrinking interface retrofit2.Call
 -keep,allowobfuscation,allowshrinking class retrofit2.Response

 # With R8 full mode generic signatures are stripped for classes that are not
 # kept. Suspend functions are wrapped in continuations where the type argument
 # is used.
 -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

 -keep class ua.com.programmer.agentventa.catalogs.debt.DebtViewModel$Item { *; }
 -keep class ua.com.programmer.agentventa.catalogs.debt.DebtViewModel$Content { *; }

 # Keep dataclasses used for data access and api calls
 -keep class ua.com.programmer.agentventa.dao.entity.** { *; }
 -keep class ua.com.programmer.agentventa.dao.cloud.** { *; }

 ##---------------Begin: proguard configuration for Gson  ----------
 # Gson uses generic type information stored in a class file when working with fields. Proguard
 # removes such information by default, so configure it to keep all of it.
 -keepattributes Signature

 # For using GSON @Expose annotation
 -keepattributes *Annotation*

 # Gson specific classes
 -dontwarn sun.misc.**
 #-keep class com.google.gson.stream.** { *; }

 # Application classes that will be serialized/deserialized over Gson
 -keep class com.google.gson.examples.android.model.** { <fields>; }

 # Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
 # JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
 -keep class * extends com.google.gson.TypeAdapter
 -keep class * implements com.google.gson.TypeAdapterFactory
 -keep class * implements com.google.gson.JsonSerializer
 -keep class * implements com.google.gson.JsonDeserializer

 # Prevent R8 from leaving Data object members always null
 -keepclassmembers,allowobfuscation class * {
   @com.google.gson.annotations.SerializedName <fields>;
 }
 # solve problems with generated classes https://github.com/google/gson/issues/2401
  -keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
  }

 # Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
 -keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
 -keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

 ##---------------End: proguard configuration for Gson  ----------