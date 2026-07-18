/*
 * Copyright 2026 肖其顿 (XIAO QI DUN)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.aite.nlmcf;

import android.annotation.SuppressLint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public final class NlmcfModule extends XposedModule {
    private static final String TARGET_PACKAGE = "com.android.systemui";
    private static final String LOCKSCREEN_MEDIA_SETTING = "media_controls_lock_screen";
    private static final int USER_CURRENT = -2;

    private static final String MEDIA_CAROUSEL_CLASS =
            "com.android.systemui.media.controls.ui.controller.MediaCarouselController";
    private static final String MEDIA_CAROUSEL_SETTING_OBSERVER_CLASS = MEDIA_CAROUSEL_CLASS
            + "$listenForLockscreenSettingChanges$1$3";

    private static final String MEDIA_HIERARCHY_CLASS =
            "com.android.systemui.media.controls.ui.controller.MediaHierarchyManager";
    private static final String MEDIA_HIERARCHY_SETTING_OBSERVER_CLASS = MEDIA_HIERARCHY_CLASS
            + "$settingsObserver$1";

    private static final String KEYGUARD_MEDIA_CONTROLLER_CLASS =
            "com.android.systemui.media.controls.ui.controller.KeyguardMediaController";
    private static final String MEDIA_HOST_CLASS =
            "com.android.systemui.media.controls.ui.view.MediaHost";
    private static final String STATUS_BAR_STATE_CONTROLLER_CLASS =
            "com.android.systemui.plugins.statusbar.StatusBarStateController";
    private static final String USER_SETTINGS_PROXY_CLASS =
            "com.android.systemui.util.settings.UserSettingsProxy";
    private static final String FUNCTION_ZERO_CLASS = "kotlin.jvm.functions.Function0";

    private static final String HOOK_ID_CAROUSEL_CONSTRUCTOR =
            "initialize-keyguard-media-setting";
    private static final String HOOK_ID_CAROUSEL_OBSERVER =
            "apply-keyguard-media-setting";
    private static final String HOOK_ID_HIERARCHY_CONSTRUCTOR =
            "initialize-media-hierarchy-setting";
    private static final String HOOK_ID_HIERARCHY_OBSERVER =
            "apply-media-hierarchy-setting";
    private static final String HOOK_ID_KEYGUARD_SHOW =
            "enforce-keyguard-media-setting";
    private static final String HOOK_ID_KEYGUARD_REFRESH =
            "enforce-keyguard-media-setting-fallback";

    private boolean targetProcess;
    private boolean hookInstalled;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        targetProcess = TARGET_PACKAGE.equals(param.getProcessName());
        if (!targetProcess) {
            detach();
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!targetProcess
                || hookInstalled
                || !TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        ClassLoader classLoader = param.getClassLoader();
        installCarouselSettingHooks(classLoader);
        installHierarchySettingHooks(classLoader);
        installKeyguardVisibilityGate(classLoader);
        hookInstalled = true;
    }

    @SuppressLint("PrivateApi")
    private void installCarouselSettingHooks(ClassLoader classLoader) {
        try {
            Class<?> carouselClass = Class.forName(
                    MEDIA_CAROUSEL_CLASS,
                    false,
                    classLoader
            );
            Field secureSettingsField = accessibleField(carouselClass, "secureSettings");
            Field allowedField = accessibleField(
                    carouselClass,
                    "allowMediaPlayerOnLockScreen"
            );
            Method readSettingMethod = resolveSettingReader(classLoader);
            Method getUpdateHostVisibility = accessibleMethod(
                    carouselClass,
                    "getUpdateHostVisibility"
            );
            Method invokeFunction = resolveFunctionZeroInvoke(classLoader);

            int constructorIndex = 0;
            for (Constructor<?> constructor : carouselClass.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                String hookId = HOOK_ID_CAROUSEL_CONSTRUCTOR + '-' + constructorIndex++;
                hook(constructor)
                        .setId(hookId)
                        .setPriority(PRIORITY_HIGHEST)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object controller = chain.getThisObject();
                            applyCurrentSetting(
                                    controller,
                                    secureSettingsField,
                                    allowedField,
                                    readSettingMethod
                            );
                            return result;
                        });
            }

            Class<?> observerClass = Class.forName(
                    MEDIA_CAROUSEL_SETTING_OBSERVER_CLASS,
                    false,
                    classLoader
            );
            Field observerOwnerField = accessibleField(observerClass, "this$0");
            Field emittedSettingField = accessibleField(observerClass, "Z$0");
            Method observerMethod = accessibleMethod(
                    observerClass,
                    "invokeSuspend",
                    Object.class
            );

            hook(observerMethod)
                    .setId(HOOK_ID_CAROUSEL_OBSERVER)
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object observer = chain.getThisObject();
                            Object controller = observerOwnerField.get(observer);
                            boolean allowed = emittedSettingField.getBoolean(observer);
                            allowedField.setBoolean(controller, allowed);
                            invokeUpdateHostVisibility(
                                    controller,
                                    getUpdateHostVisibility,
                                    invokeFunction
                            );
                        } catch (Throwable ignored) {
                        }
                        return result;
                    });
        } catch (Throwable ignored) {
        }
    }

    @SuppressLint("PrivateApi")
    private void installHierarchySettingHooks(ClassLoader classLoader) {
        try {
            Class<?> hierarchyClass = Class.forName(
                    MEDIA_HIERARCHY_CLASS,
                    false,
                    classLoader
            );
            Field secureSettingsField = accessibleField(hierarchyClass, "secureSettings");
            Field allowedField = accessibleField(
                    hierarchyClass,
                    "allowMediaPlayerOnLockScreen"
            );
            Field carouselField = accessibleField(
                    hierarchyClass,
                    "mediaCarouselController"
            );
            Method readSettingMethod = resolveSettingReader(classLoader);
            Method updateUserVisibility = accessibleMethod(
                    hierarchyClass,
                    "updateUserVisibility"
            );

            Class<?> carouselClass = Class.forName(
                    MEDIA_CAROUSEL_CLASS,
                    false,
                    classLoader
            );
            Method getUpdateHostVisibility = accessibleMethod(
                    carouselClass,
                    "getUpdateHostVisibility"
            );
            Method invokeFunction = resolveFunctionZeroInvoke(classLoader);

            int constructorIndex = 0;
            for (Constructor<?> constructor : hierarchyClass.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                String hookId = HOOK_ID_HIERARCHY_CONSTRUCTOR + '-' + constructorIndex++;
                hook(constructor)
                        .setId(hookId)
                        .setPriority(PRIORITY_HIGHEST)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object manager = chain.getThisObject();
                            applyCurrentSetting(
                                    manager,
                                    secureSettingsField,
                                    allowedField,
                                    readSettingMethod
                            );
                            return result;
                        });
            }

            Class<?> observerClass = Class.forName(
                    MEDIA_HIERARCHY_SETTING_OBSERVER_CLASS,
                    false,
                    classLoader
            );
            Field observerOwnerField = accessibleField(observerClass, "this$0");
            Class<?> uriClass = Class.forName("android.net.Uri", false, classLoader);
            Method observerMethod = accessibleMethod(
                    observerClass,
                    "onChange",
                    boolean.class,
                    uriClass
            );

            hook(observerMethod)
                    .setId(HOOK_ID_HIERARCHY_OBSERVER)
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object manager = observerOwnerField.get(chain.getThisObject());
                            updateUserVisibility.invoke(manager);
                            Object carousel = carouselField.get(manager);
                            invokeUpdateHostVisibility(
                                    carousel,
                                    getUpdateHostVisibility,
                                    invokeFunction
                            );
                        } catch (Throwable ignored) {
                        }
                        return result;
                    });
        } catch (Throwable ignored) {
        }
    }

    @SuppressLint("PrivateApi")
    private void installKeyguardVisibilityGate(ClassLoader classLoader) {
        try {
            Class<?> keyguardClass = Class.forName(
                    KEYGUARD_MEDIA_CONTROLLER_CLASS,
                    false,
                    classLoader
            );
            Class<?> mediaHostClass = Class.forName(MEDIA_HOST_CLASS, false, classLoader);
            Class<?> carouselClass = Class.forName(
                    MEDIA_CAROUSEL_CLASS,
                    false,
                    classLoader
            );
            Class<?> statusBarStateControllerClass = Class.forName(
                    STATUS_BAR_STATE_CONTROLLER_CLASS,
                    false,
                    classLoader
            );

            Field mediaHostField = accessibleField(keyguardClass, "mediaHost");
            Field statusBarStateControllerField = accessibleField(
                    keyguardClass,
                    "statusBarStateController"
            );
            Field visibleField = accessibleField(keyguardClass, "visible");
            Field carouselField = accessibleField(
                    mediaHostClass,
                    "mediaCarouselController"
            );
            Field secureSettingsField = accessibleField(carouselClass, "secureSettings");
            Field cachedAllowedField = accessibleField(
                    carouselClass,
                    "allowMediaPlayerOnLockScreen"
            );

            Method readSettingMethod = resolveSettingReader(classLoader);
            Method getStateMethod = accessibleMethod(statusBarStateControllerClass, "getState");
            Method isDozingMethod = accessibleMethod(
                    statusBarStateControllerClass,
                    "isDozing"
            );
            Method hideMediaPlayer = accessibleMethod(keyguardClass, "hideMediaPlayer");
            Method showMediaPlayer = accessibleMethod(keyguardClass, "showMediaPlayer");
            Method refreshMediaPosition = accessibleMethod(
                    keyguardClass,
                    "refreshMediaPosition",
                    String.class
            );

            hook(showMediaPlayer)
                    .setId(HOOK_ID_KEYGUARD_SHOW)
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object controller = chain.getThisObject();
                        if (!isLockedContext(
                                controller,
                                statusBarStateControllerField,
                                getStateMethod,
                                isDozingMethod
                        ) || isLockscreenMediaAllowed(
                                controller,
                                mediaHostField,
                                carouselField,
                                secureSettingsField,
                                cachedAllowedField,
                                readSettingMethod
                        )) {
                            return chain.proceed();
                        }

                        hideKeyguardMedia(controller, visibleField, hideMediaPlayer);
                        return null;
                    });

            if (!tryDeoptimize(refreshMediaPosition)) {
                hook(refreshMediaPosition)
                        .setId(HOOK_ID_KEYGUARD_REFRESH)
                        .setPriority(PRIORITY_HIGHEST)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object controller = chain.getThisObject();
                            if (isLockedContext(
                                    controller,
                                    statusBarStateControllerField,
                                    getStateMethod,
                                    isDozingMethod
                            ) && !isLockscreenMediaAllowed(
                                    controller,
                                    mediaHostField,
                                    carouselField,
                                    secureSettingsField,
                                    cachedAllowedField,
                                    readSettingMethod
                            )) {
                                hideKeyguardMedia(
                                        controller,
                                        visibleField,
                                        hideMediaPlayer
                                );
                            }
                            return result;
                        });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyCurrentSetting(
            Object owner,
            Field secureSettingsField,
            Field allowedField,
            Method readSettingMethod
    ) {
        if (owner == null) {
            return;
        }
        try {
            Object secureSettings = secureSettingsField.get(owner);
            Object value = readSettingMethod.invoke(
                    secureSettings,
                    LOCKSCREEN_MEDIA_SETTING,
                    true,
                    USER_CURRENT
            );
            if (value instanceof Boolean allowed) {
                allowedField.setBoolean(owner, allowed);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isLockscreenMediaAllowed(
            Object keyguardController,
            Field mediaHostField,
            Field carouselField,
            Field secureSettingsField,
            Field cachedAllowedField,
            Method readSettingMethod
    ) {
        Object carousel = null;
        try {
            Object mediaHost = mediaHostField.get(keyguardController);
            carousel = carouselField.get(mediaHost);
            Object secureSettings = secureSettingsField.get(carousel);
            Object value = readSettingMethod.invoke(
                    secureSettings,
                    LOCKSCREEN_MEDIA_SETTING,
                    true,
                    USER_CURRENT
            );
            if (value instanceof Boolean allowed) {
                cachedAllowedField.setBoolean(carousel, allowed);
                return allowed;
            }
        } catch (Throwable ignored) {
        }

        if (carousel != null) {
            try {
                return cachedAllowedField.getBoolean(carousel);
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    private static boolean isLockedContext(
            Object keyguardController,
            Field statusBarStateControllerField,
            Method getStateMethod,
            Method isDozingMethod
    ) {
        try {
            Object statusBarStateController = statusBarStateControllerField.get(
                    keyguardController
            );
            int state = (Integer) getStateMethod.invoke(statusBarStateController);
            boolean dozing = (Boolean) isDozingMethod.invoke(statusBarStateController);
            return state != 0 || dozing;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void hideKeyguardMedia(
            Object keyguardController,
            Field visibleField,
            Method hideMediaPlayer
    ) {
        try {
            visibleField.setBoolean(keyguardController, false);
            hideMediaPlayer.invoke(keyguardController);
        } catch (Throwable ignored) {
        }
    }

    private static void invokeUpdateHostVisibility(
            Object carousel,
            Method getUpdateHostVisibility,
            Method invokeFunction
    ) {
        if (carousel == null) {
            return;
        }
        try {
            Object updateHostVisibility = getUpdateHostVisibility.invoke(carousel);
            if (updateHostVisibility != null) {
                invokeFunction.invoke(updateHostVisibility);
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressLint("PrivateApi")
    private static Method resolveSettingReader(ClassLoader classLoader) throws Exception {
        Class<?> userSettingsProxyClass = Class.forName(
                USER_SETTINGS_PROXY_CLASS,
                false,
                classLoader
        );
        return accessibleMethod(
                userSettingsProxyClass,
                "getBoolForUser",
                String.class,
                boolean.class,
                int.class
        );
    }

    private static Method resolveFunctionZeroInvoke(ClassLoader classLoader) throws Exception {
        Class<?> functionZeroClass = Class.forName(
                FUNCTION_ZERO_CLASS,
                false,
                classLoader
        );
        return accessibleMethod(functionZeroClass, "invoke");
    }

    private static Field accessibleField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method accessibleMethod(
            Class<?> owner,
            String name,
            Class<?>... parameterTypes
    ) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private boolean tryDeoptimize(Executable executable) {
        try {
            return deoptimize(executable);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
