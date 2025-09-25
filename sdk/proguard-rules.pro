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

##---------------Begin: proguard configuration for SDK build ----------
# Keep attributes needed for Gson serialization during SDK build
-keepattributes Signature, *Annotation*

# Suppress warnings for sun.misc package
-dontwarn sun.misc.**

# Keep SDK classes and dependencies (same as consumer rules)
-keep class com.apphud.** { *; }
-keep class com.android.billingclient.** { *; }
-keep class com.google.gson.** { *; }

# Keep members with SerializedName annotation
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
##---------------End: proguard configuration for SDK build ----------