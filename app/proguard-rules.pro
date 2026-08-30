# Keep Room generated implementations
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
