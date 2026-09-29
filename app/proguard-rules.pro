# Starter keep rules for when you switch minifyEnabled to true in app/build.gradle.
# Most of these libraries ship their own consumer rules, these are just extra safety nets.

# ML Kit barcode scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Room-generated code and our entities/DAOs
-keep class androidx.room.** { *; }
-keep class com.quickqr.app.data.** { *; }

# Keep line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
