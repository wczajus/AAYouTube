# R8 rules for the release build (used when R8 is switched on, see app/build.gradle.kts).
# GeckoView and the Car App Library bring their own rules; only the projection library needs help.

# Legacy Android Auto SDK (aauto.aar): closed, partly obfuscated library loaded by Android Auto.
-keep class com.google.android.apps.auto.sdk.** { *; }
-dontwarn com.google.android.apps.auto.sdk.**
-dontwarn com.google.a.a.a.a.**
