package com.jj.dshpet.lsposed.hook;

import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_MANAGER;
import static com.jj.dshpet.lsposed.constant.Constants.TAG;
import static com.jj.dshpet.lsposed.constant.Constants.TARGET_PKG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.jj.dshpet.lsposed.hook.fix.FixLockScreen;
import com.jj.dshpet.lsposed.hook.fix.FixOrientation;
import com.jj.dshpet.lsposed.hook.modify.ModifyDoubleTap;
import com.jj.dshpet.lsposed.hook.modify.ModifyLongPress;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * DSH Pet Android Fix - LSPosed Module (libxposed API)
 * <p>
 * Target package : io.dsh.pet.ai (大肥鱼桌宠, v1.1.22)
 * 模块入口与主引导类，负责初始化各项 Hook 逻辑（双击、长按、锁屏息屏修复、屏幕旋转重定位等）。
 */
@SuppressLint("PrivateApi")
public class HookInit extends XposedModule {

    /**
     * 当目标应用准备就绪时回调，注册所有 Hook 模块
     */
    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PKG.equals(param.getPackageName())) return;

        log(Log.INFO, TAG, "=== DSHPetAndroidFix 模块已成功加载到目标应用: " + TARGET_PKG + " ===");
        ClassLoader cl = param.getClassLoader();

        // 修改双击桌宠的功能
        ModifyDoubleTap.hook(this, cl);

        // 修改长按桌宠的功能
        ModifyLongPress.hook(this, cl);

        // 修复屏幕旋转 Bug 和锁屏界面无法息屏 Bug
        hookPetManager(cl);

        log(Log.INFO, TAG, "=== 所有 Hook 已成功注册 ===");
    }

    /**
     * Hook PetManager 相关方法，注入锁屏拦截与屏幕旋转自适应逻辑
     */
    private void hookPetManager(ClassLoader cl) {
        try {
            Class<?> clazz = Class.forName(CLS_PET_MANAGER, true, cl);

            // Hook PetManager.start(Context, int) 方法，在桌宠启动时初始化全局广播与显示监听
            for (Method m : clazz.getDeclaredMethods()) {
                if (!"start".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2 || p[1] != int.class) continue;
                hook(m).intercept(chain -> {
                    Context ctx = (Context) chain.getArg(0);

                    // 注册锁屏深度拦截与息屏管理全局接收器
                    FixLockScreen.ensureGlobalReceivers(ctx, cl, this);

                    // 注册硬件级 DisplayListener 与 ComponentCallbacks 屏幕旋转自适应监听
                    FixOrientation.ensureDisplayListener(ctx, cl, this);
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "[PetManager] 已成功 Hook: start(Context, int)");
                break;
            }

            // 屏蔽原 App 自带的屏幕亮灭监听，防止其直接销毁桌宠，由本模块全局接管
            FixLockScreen.hookScreenOnOff(this, clazz);
            log(Log.INFO, TAG, "[PetManager] 相关 Hook 注册完成");
        } catch (Throwable e) {
            log(Log.WARN, TAG, "[PetManager] Hook 失败: " + e);
        }
    }
}
