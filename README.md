# NLMCF
努比亚锁屏媒体控制器显示开关修复

# 实测机型
Z80 Ultra（NebulaAIOS2.0.26MR_NX741J）

# 构建说明
```batch
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
gradlew.bat clean lint assembleRelease
```

# 问题根因
1. 锁屏媒体开关保存在`Settings.Secure`的`media_controls_lock_screen`项中，SystemUI的`MediaCarouselController`监听该设置并将布尔值传给适配层。
2. `com.zte.adapt.mifavor.media.MediaCarouselControllerAdapt.shouldShowKeyguardMedia(boolean)`在适配启用时对传入的`false`仍返回`true`，导致关闭开关后仍显示锁屏媒体。
3. 该方法由`MediaCarouselController$listenForLockscreenSettingChanges$1$3.invokeSuspend(Object)`和`MediaCarouselController`构造函数直接调用；被ART内联后，这些调用路径会绕过方法Hook。

# 授权协议
本项目使用 [Apache License 2.0](https://github.com/xiaoqidun/nlmcf/blob/main/LICENSE) 授权协议