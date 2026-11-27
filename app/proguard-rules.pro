# Keep Room Database, Entities, DAOs and exported schemas
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Keep kotlinx.serialization models & serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.personal.workhours.domain.model.**$$serializer { *; }
-keepclassmembers class com.personal.workhours.domain.model.** { *** Companion; }
-keepclasseswithmembers class com.personal.workhours.domain.model.** { kotlinx.serialization.KSerializer serializer(...); }

# Strip verbose/debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
