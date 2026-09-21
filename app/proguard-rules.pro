# Release ProGuard / R8 Rules for Habit1
# Principle: Smallest rules necessary for actual application code.

# 1. Release logging: Strip debug and verbose log calls, preserving warnings and errors
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# 2. Kotlinx Serialization DTOs for backup/restore
# Preserve serializable companions and primary constructors
-keepclassmembers class com.habit1.app.data.backup.model.** {
    *** Companion;
    *** $serializer;
}
-keepclasseswithmembers class com.habit1.app.data.backup.model.** {
    <init>(...);
}
