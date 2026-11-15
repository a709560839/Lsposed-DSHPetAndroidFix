package com.jj.dshpet.lsposed.hook.fix;

import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_MANAGER;
import static com.jj.dshpet.lsposed.constant.Constants.TAG;
import static com.jj.dshpet.lsposed.constant.Constants.CLS_CHAT_ACTIVITY;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeMethod;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeStaticMethod;

import android.content.Context;
import android.util.Log;

import com.jj.dshpet.lsposed.config.ConfigManager;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * Hook ChatActivity.onResume()。
 * <p>
 * 原始 onResume 会调用 PetManager.setOverlayVisible(false) 把桌宠隐藏，
 * 进入 ChatActivity 时桌宠应保持可见（其他气泡如碎碎念、前台感知仍可正常触发）。
 * 修复：在原始 onResume 执行完后，调用 setOverlayVisible(true) 撤销隐藏。
 */
public class FixChatActivity {

    private final XposedModule module;
    private final ClassLoader cl;

    public static void hook(XposedModule module, ClassLoader cl) {
        new FixChatActivity(module, cl).init();
    }

    public FixChatActivity(XposedModule module, ClassLoader cl) {
        this.module = module;
        this.cl = cl;
    }

    public void init() {
        try {
            Class<?> chatClass = Class.forName(CLS_CHAT_ACTIVITY, true, cl);
            Class<?> pmClass = Class.forName(CLS_PET_MANAGER, true, cl);

            Method onResume = chatClass.getDeclaredMethod("onResume");

            module.hook(onResume).intercept(chain -> {
                // 先执行原始逻辑（setUiVisible(true) + setOverlayVisible(false) + setUiMode）
                Object result = chain.proceed();

                try {
                    Object activity = chain.getThisObject();
                    if (activity instanceof Context ctx) {
                        if (ConfigManager.isLongPressChatEnabled(ctx)) {
                            // 撤销 setOverlayVisible(false)，保持桌宠可见
                            Object pmObj = invokeStaticMethod(pmClass, "get",
                                    new Class[]{Context.class}, new Object[]{ctx});
                            if (pmObj != null) {
                                invokeMethod(pmObj, "setOverlayVisible",
                                        new Class[]{boolean.class}, new Object[]{true});
                                module.log(Log.INFO, TAG, "[ChatActivity] 已撤销桌宠隐藏，保持桌宠可见");
                            }
                        }
                    }
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG, "[ChatActivity] 撤销桌宠隐藏异常: " + t);
                }
                return result;
            });
            module.log(Log.INFO, TAG, "[ChatActivity] 已成功 Hook: onResume() - 桌宠保持可见");

        } catch (Throwable e) {
            module.log(Log.WARN, TAG, "[ChatActivity] overlay Hook 失败: " + e);
        }
    }
}
