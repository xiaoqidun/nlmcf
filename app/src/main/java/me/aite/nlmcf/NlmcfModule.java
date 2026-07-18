/*
 * Copyright 2026 肖其顿 (XIAO QI DUN)

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.aite.nlmcf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public final class NlmcfModule extends XposedModule {
    private static final String TARGET_PACKAGE = "com.android.systemui";
    private static final String TARGET_CLASS =
            "com.zte.adapt.mifavor.media.MediaCarouselControllerAdapt";
    private static final String TARGET_METHOD = "shouldShowKeyguardMedia";
    private static final String HOOK_ID = "restore-keyguard-media-setting";
    private static final String MEDIA_CAROUSEL_CLASS =
            "com.android.systemui.media.controls.ui.controller.MediaCarouselController";
    private static final String SETTING_OBSERVER_CLASS = MEDIA_CAROUSEL_CLASS
            + "$listenForLockscreenSettingChanges$1$3";

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

        try {
            Class<?> adapterClass = Class.forName(
                    TARGET_CLASS,
                    false,
                    param.getClassLoader()
            );
            Method method = adapterClass.getDeclaredMethod(TARGET_METHOD, boolean.class);
            if (method.getReturnType() != boolean.class) {
                return;
            }

            hook(method)
                    .setId(HOOK_ID)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object settingValue = chain.getArg(0);
                        if (!(settingValue instanceof Boolean enabled)) {
                            return chain.proceed();
                        }
                        return enabled;
                    });

            hookInstalled = true;
            deoptimizeKnownCallers(param.getClassLoader());
        } catch (Throwable ignored) {
        }
    }

    private void deoptimizeKnownCallers(ClassLoader classLoader) {
        try {
            Class<?> observerClass = Class.forName(
                    SETTING_OBSERVER_CLASS,
                    false,
                    classLoader
            );
            Method observer = observerClass.getDeclaredMethod("invokeSuspend", Object.class);
            deoptimize(observer);
        } catch (Throwable ignored) {
        }

        try {
            Class<?> carouselClass = Class.forName(
                    MEDIA_CAROUSEL_CLASS,
                    false,
                    classLoader
            );
            for (Constructor<?> constructor : carouselClass.getDeclaredConstructors()) {
                try {
                    deoptimize(constructor);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
