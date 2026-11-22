# Keep application classes and Room / Media3 / FileProvider components intact
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Media3 ExoPlayer & Audio Effects
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
