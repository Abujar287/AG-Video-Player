# Keep application classes and Room / Media3 / FileProvider components intact
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Media3 ExoPlayer & Audio Effects
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.common.** { *; }

# Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
