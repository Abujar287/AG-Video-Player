# ProGuard / R8 rules for AG Video Player
# Keep minimal reflection targets; Compose, Media3, Room, DataStore & Coroutines
# include their own consumer ProGuard rules inside their AARs.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room Database constructor safety
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.**

# ViewModel reflection constructors used by viewModel()
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# Silence optional Play Core / annotation warnings during R8
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Strip verbose debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
