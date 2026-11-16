# Keep protobuf lite
-keep class com.google.protobuf.** { *; }
-keep class com.elgato.vsd2.** { *; }
-keep class vsd2.** { *; }

# Keep app package models & network classes
-keep class com.mash.deck.** { *; }

# Keep BouncyCastle crypto engines used for VSD2 encryption
-keep class org.bouncycastle.crypto.** { *; }
-dontwarn org.bouncycastle.**

# Keep LZ4 compression
-keep class net.jpountz.** { *; }
-dontwarn net.jpountz.**

# Keep ZXing QR Code scanner
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# CameraX
-dontwarn androidx.camera.**
