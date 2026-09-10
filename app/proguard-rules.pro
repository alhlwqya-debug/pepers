# Release safety rules for future minification.
# The current release build intentionally keeps minification disabled until
# CodeAssist's R8 pipeline is verified on the target device.
-keep class com.add.pepers.** { *; }
-keep class com.add.pepers.cloud.** { *; }
-keepclassmembers class * extends android.app.Activity { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { *; }
