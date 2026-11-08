package com.jj.dshpet.lsposed.hook.modify;

import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_WINDOW;
import static com.jj.dshpet.lsposed.constant.Constants.TAG;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeMethod;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeNoArg;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * [Modify-2] hook PetWindow.m131lambda$handleTouch$4$iodshpetPetWindow()
 * （即长按触发器，原逻辑：showMenu；改为：openChat）
 * <p>
 * 注意：反编译后的方法名因混淆/R8 而带有特殊字符，libxposed 通过
 * getDeclaredMethods() 遍历匹配。
 */
public class ModifyLongPress {

    private final XposedModule module;
    private final ClassLoader cl;

    public static void hook(XposedModule module, ClassLoader cl) {
        new ModifyLongPress(module, cl).init();
    }

    public ModifyLongPress(XposedModule module, ClassLoader cl) {
        this.module = module;
        this.cl = cl;
    }

    public void init() {
        try {
            Class<?> clazz = Class.forName(CLS_PET_WINDOW, true, cl);
            for (Method m : clazz.getDeclaredMethods()) {
                // 匹配反编译后的 lambda 方法名：含 "handleTouch" 且含 "4"
                String name = m.getName();
                if (!name.contains("handleTouch") || !name.contains("4")) continue;
                if (m.getParameterTypes().length != 0) continue;

                module.hook(m).intercept(chain -> {
                    try {
                        Object pet = chain.getThisObject();
                        Object pm = getField(pet, "mgr");
                        if (pm == null) return chain.proceed();

                        // 若有悬浮聊天窗，先关掉
                        Object chat = getField(pm, "floatingChat");
                        if (chat != null) {
                            invokeNoArg(chat, "dismiss");
                        }

                        // 打开 ChatActivity
                        invokeMethod(pm, "openChat", new Class[]{Class.forName(CLS_PET_WINDOW, true, cl)}, new Object[]{pet});

                        // 触感反馈
                        invokeMethod(pm, "haptic", new Class[]{int.class}, new Object[]{12});

                        module.log(Log.INFO, TAG, "[长按桌宠] 成功触发打开聊天主界面");
                        return null;  // 跳过原始 showMenu 逻辑
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "[长按桌宠] 降级兜底处理异常: " + t);
                        return chain.proceed();
                    }
                });
                module.log(Log.INFO, TAG, "[PetWindow] 已成功 Hook 长按 lambda 表达式: " + m.getName());
                return;
            }
            module.log(Log.WARN, TAG, "[PetWindow] 未找到长按 lambda 表达式方法");
        } catch (Throwable e) {
            module.log(Log.WARN, TAG, "[PetWindow] 长按 Hook 失败: " + e);
        }
    }
}
