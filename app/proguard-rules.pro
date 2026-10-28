-keep class de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.** { *; }
-keep class io.github.libxposed.** { *; }
-keep interface io.github.libxposed.** { *; }
-keep class io.github.amahteru.locationspoofer.xposed.** { *; }
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

-keep class io.github.amahteru.locationspoofer.utils.ConfigManager { *; }
-keep class io.github.amahteru.locationspoofer.utils.LSPosedManager { *; }
-keep class io.github.amahteru.locationspoofer.provider.** { *; }
-keep class io.github.amahteru.locationspoofer.data.** { *; }
-keepclassmembers class io.github.amahteru.locationspoofer.data.** { *; }

-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.ContentProvider { *; }