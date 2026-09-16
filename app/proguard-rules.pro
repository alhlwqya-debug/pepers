# Release safety rules for the Pepers app.
# Keep the application's own classes stable while R8 removes unused
# code from dependencies and shrinks the release package.
-keep class com.add.pepers.** { *; }
-keep class com.add.pepers.cloud.** { *; }
-keepclassmembers class * extends android.app.Activity { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { *; }
