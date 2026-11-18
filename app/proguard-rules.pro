-keep class org.videolan.libvlc.** { *; }
-keep class com.kadri.stalkerplayerpro.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.videolan.libvlc.**
