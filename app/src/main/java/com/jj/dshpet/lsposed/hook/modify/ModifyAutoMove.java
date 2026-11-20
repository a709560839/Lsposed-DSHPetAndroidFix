package com.jj.dshpet.lsposed.hook.modify;

import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_WINDOW;
import static com.jj.dshpet.lsposed.constant.Constants.TAG;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.findField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.findMethod;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getContext;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getField;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import com.jj.dshpet.lsposed.config.ConfigManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * [Modify-4] 控制是否让桌宠自动移动，不影响手动拖动与甩飞
 * <p>
 * 拦截桌宠漫游走动 (startWalk / startWalk2) 的发起，并在 tick 循环中清除自动移动状态，
 * 同时通知 WebView 端解除走路动画循环，让桌宠保持原地静止，
 * 绝不干扰用户手动触摸拖拽 (dragging) 与抛掷甩飞 (flying) 行为。
 */
public class ModifyAutoMove {

    private final XposedModule module;
    private final ClassLoader cl;

    public static void hook(XposedModule module, ClassLoader cl) {
        new ModifyAutoMove(module, cl).init();
    }

    public ModifyAutoMove(XposedModule module, ClassLoader cl) {
        this.module = module;
        this.cl = cl;
    }

    public void init() {
        try {
            Class<?> pwClass = Class.forName(CLS_PET_WINDOW, true, cl);
            hookStartWalk(pwClass);
            hookTick(pwClass);
            module.log(Log.INFO, TAG, "[自动移动修改] 相关 Hook 注册成功");
        } catch (Throwable e) {
            module.log(Log.WARN, TAG, "[自动移动修改] 注册失败: " + e);
        }
    }

    /**
     * Hook startWalk 和 startWalk2，拦截自动漫游走动的启动
     */
    private void hookStartWalk(Class<?> pwClass) {
        for (Method m : pwClass.getDeclaredMethods()) {
            String name = m.getName();
            Class<?>[] params = m.getParameterTypes();

            // 匹配 startWalk(int, int, long, long, long)
            boolean isStartWalk = ("startWalk".equals(name) || name.contains("startWalk"))
                    && params.length == 5
                    && params[0] == int.class && params[1] == int.class
                    && params[2] == long.class && params[3] == long.class && params[4] == long.class;

            // 匹配 startWalk2(int, int, int, long, long, long)
            boolean isStartWalk2 = ("startWalk2".equals(name) || name.contains("startWalk2"))
                    && params.length == 6
                    && params[0] == int.class && params[1] == int.class && params[2] == int.class
                    && params[3] == long.class && params[4] == long.class && params[5] == long.class;

            if (isStartWalk || isStartWalk2) {
                module.hook(m).intercept(chain -> {
                    Object pet = chain.getThisObject();
                    Context ctx = getContext(pet);

                    // 若开启了禁止自动移动开关，拦截自动位移请求
                    if (ConfigManager.isDisableAutoMoveEnabled(ctx)) {
                        module.log(Log.INFO, TAG, "[自动移动] 已拦截桌宠自动漫游请求 (" + m.getName() + ")，桌宠保持原地不动");
                        stopWalkMotion(pet);
                        return null; // 阻止原版 startWalk/startWalk2 执行
                    }

                    return chain.proceed();
                });
                module.log(Log.INFO, TAG, "[PetWindow] 已成功 Hook: " + m.getName() + " (参数数量=" + params.length + ")");
            }
        }
    }

    /**
     * Hook PetWindow.tick(double, long)，作为二次防线实时拦截中途产生的 walk
     */
    private void hookTick(Class<?> pwClass) {
        for (Method m : pwClass.getDeclaredMethods()) {
            if (!"tick".equals(m.getName())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 2 || params[0] != double.class || params[1] != long.class) continue;

            module.hook(m).intercept(chain -> {
                Object pet = chain.getThisObject();
                Context ctx = getContext(pet);

                if (ConfigManager.isDisableAutoMoveEnabled(ctx)) {
                    // 若开启了禁止自动移动且当前存在 walk 状态，立即清除并通知 endWalk
                    Object walk = getField(pet, "walk");
                    if (walk != null) {
                        stopWalkMotion(pet);
                    }
                }

                // 正常继续执行 tick，手动拖拽 (dragging) 与甩飞 (flying) 逻辑完全不受影响
                return chain.proceed();
            });
            module.log(Log.INFO, TAG, "[PetWindow] 已成功 Hook: tick(double, long)");
            break;
        }
    }

    /**
     * 彻底清除走动任务并通知前端结束 walk 动画循环
     */
    private static void stopWalkMotion(Object pet) {
        if (pet == null) return;

        // 1. 优先尝试反射调用 cancelWalk()
        try {
            Method cancelWalk = findMethod(pet.getClass(), "cancelWalk", new Class<?>[0]);
            if (cancelWalk != null) {
                cancelWalk.setAccessible(true);
                cancelWalk.invoke(pet);
                return;
            }
        } catch (Throwable ignored) {
        }

        // 2. 直接清空 walk 字段
        try {
            Field fWalk = findField(pet.getClass(), "walk");
            if (fWalk != null) {
                fWalk.setAccessible(true);
                fWalk.set(pet, null);
            }
        } catch (Throwable ignored) {
        }

        // 3. 通知 JS 端结束行走循环动画
        notifyJsEndWalk(pet);
    }

    /**
     * 向 WebView/JS 发送 PetAPI.endWalk() 指令
     */
    private static void notifyJsEndWalk(Object pet) {
        // 尝试通过宿主自身的 js / m2js 方法
        for (String methodName : new String[]{"js", "m2js"}) {
            try {
                Method m = findMethod(pet.getClass(), methodName, new Class<?>[]{String.class});
                if (m != null) {
                    m.setAccessible(true);
                    m.invoke(pet, "PetAPI.endWalk()");
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        // 备选方案：直接使用 WebView.evaluateJavascript
        try {
            Object web = getField(pet, "web");
            if (web instanceof WebView) {
                WebView webView = (WebView) web;
                webView.post(() -> {
                    try {
                        webView.evaluateJavascript("PetAPI.endWalk()", null);
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }
}
