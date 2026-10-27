-keep class de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.** { *; }
-keep class io.github.libxposed.** { *; }
-keep interface io.github.libxposed.** { *; }
-keep class com.vincenthzr.locationspoofer.xposed.** { *; }
-dontwarn io.github.libxposed.api.**

-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**

-keep class com.baidu.** { *; }
-keep class mapsdkvi.com.** { *; }
-keep class vi.com.gdi.bgl.android.** { *; }
-dontwarn com.baidu.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

-keepattributes *Annotation*, InnerClasses
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep @kotlinx.serialization.Serializable class * {
    *;
}

-keep class com.vincenthzr.locationspoofer.utils.ConfigManager { *; }
-keep class com.vincenthzr.locationspoofer.utils.LSPosedManager { *; }
-keep class com.vincenthzr.locationspoofer.provider.** { *; }
-keep class com.vincenthzr.locationspoofer.data.** { *; }
-keepclassmembers class com.vincenthzr.locationspoofer.data.** { *; }

-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.ContentProvider { *; }