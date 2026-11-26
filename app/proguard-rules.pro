# Release ProGuard / R8 Rules for Habit1
# Principle: Smallest rules necessary for actual application code.

# 1. Release logging: Strip debug and verbose log calls, preserving warnings and errors
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# 2. Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keepclassmembers class * extends kotlinx.serialization.KSerializer {
    *** Companion;
    *** INSTANCE;
}
-keepclassmembers class * {
    *** Companion;
    *** $serializer;
}
-keepclassmembers class com.habit1.app.data.backup.model.** {
    *** Companion;
    *** $serializer;
}
-keepclasseswithmembers class com.habit1.app.data.backup.model.** {
    <init>(...);
}
