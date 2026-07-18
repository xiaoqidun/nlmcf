# NLMCF
努比亚锁屏媒体控制器显示开关修复

# 实测机型
Z80 Ultra（NebulaAIOS2.0.26MR_NX741J）

# 使用说明
1. 安装APK，并在LSPosed中启用模块。
2. 确认模块作用域仅包含“系统界面（`com.android.systemui`）”，并重启手机使模块生效。
3. 在系统设置中切换“在锁定的屏幕上显示媒体”：关闭时隐藏锁屏媒体，开启时显示锁屏媒体。

# 构建说明
```batch
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
gradlew.bat clean lintRelease assembleRelease
```

# 问题根因
1. 锁屏媒体开关保存在`Settings.Secure`的`media_controls_lock_screen`项中，SystemUI的`MediaCarouselController`监听该设置并将布尔值传给适配层。
2. `com.zte.adapt.mifavor.media.MediaCarouselControllerAdapt.shouldShowKeyguardMedia(boolean)`在适配启用时对传入的`false`仍返回`true`，导致关闭开关后仍显示锁屏媒体。
3. `MediaHierarchyManager`还维护了一份独立的开关缓存：构造时默认设为`true`，启动后没有立即读取当前用户设置；设置观察者只更新缓存，没有同步刷新用户可见性和媒体宿主显示状态。
4. `KeyguardMediaController`的最终显示路径不会再次读取锁屏媒体开关。SystemUI刚启动或媒体卡片异步恢复时，前述`true`缓存可能先参与可见性判断，造成首次锁屏仍显示；再次锁屏或播放状态变化后才被系统刷新。
5. 模块会修正Carousel与Hierarchy两份缓存，并在最终显示锁屏媒体前读取当前用户的真实设置。只在锁屏、锁定通知栏或息屏状态下阻止显示，不影响解锁后的QS媒体。

# 授权协议
本项目使用 [Apache License 2.0](https://github.com/xiaoqidun/nlmcf/blob/main/LICENSE) 授权协议
