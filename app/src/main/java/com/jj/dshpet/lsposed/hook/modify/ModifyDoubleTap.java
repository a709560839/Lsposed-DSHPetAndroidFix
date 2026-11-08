package com.jj.dshpet.lsposed.hook.modify;

import static com.jj.dshpet.lsposed.constant.Constants.CLS_FLOATING_CHAT;
import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_MANAGER;
import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_WINDOW;
import static com.jj.dshpet.lsposed.constant.Constants.TAG;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getBooleanField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeMethod;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeNoArg;

import android.text.Editable;
import android.util.Log;
import android.widget.EditText;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * [Modify-1] hook PetManager.onDoubleTap()
 * （即双击触发器，改为：showFloatingChat）
 * <p>
 * 若调用链中有 send() 帧（即"发送后自动关闭"），则跳过 dismiss，
 * 保持悬浮聊天窗打开。
 */
public class ModifyDoubleTap {

    private final XposedModule module;
    private final ClassLoader cl;

    public static void hook(XposedModule module, ClassLoader cl) {
        new ModifyDoubleTap(module, cl).init();
    }

    public ModifyDoubleTap(XposedModule module, ClassLoader cl) {
        this.module = module;
        this.cl = cl;
    }

    public void init() {
        try {
            Class<?> fcClass = Class.forName(CLS_FLOATING_CHAT, true, cl);
            hookFcListenerImpl();
            hookFcSend(fcClass);
            hookFcDismiss(fcClass);
            module.log(Log.INFO, TAG, "[双击修改] 相关 Hook 注册成功");
        } catch (Throwable e) {
            module.log(Log.WARN, TAG, "[双击修改] 注册失败: " + e);
        }
    }

    /**
     * FloatingChat.Listener 匿名实现类
     * <p>
     * FloatingChat.Listener 是由 PetManager 内部匿名类（PetManager.3）实现的。
     * 我们通过 hook PetManager.onDoubleTap() + showFloatingChat() 达成目的，
     * 并对 onReply / onSent 在 Listener 侧做修复。
     */
    private void hookFcListenerImpl() {
        // PetManager.onDoubleTap(PetWindow) → 双击打开悬浮聊天 ---
        try {
            Class<?> pm = Class.forName(CLS_PET_MANAGER, true, cl);
            Class<?> pw = Class.forName(CLS_PET_WINDOW, true, cl);
            Class<?> fc = Class.forName(CLS_FLOATING_CHAT, true, cl);

            for (Method m : pm.getDeclaredMethods()) {
                if (!"onDoubleTap".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 1 || !p[0].isAssignableFrom(pw)) continue;

                module.hook(m).intercept(chain -> {
                    Object pmObj = chain.getThisObject();
                    // 记录 stashed / gameRunning 状态（供 after 判断）
                    boolean stashed = getBooleanField(pmObj, "stashed");
                    boolean gameRunning = getBooleanField(pmObj, "gameRunning");

                    // 执行原始逻辑（播放动画、气泡等）
                    Object result = chain.proceed();

                    // 若未被收起且未在游戏中，打开悬浮聊天窗
                    if (!stashed && !gameRunning) {
                        try {
                            Object petArg = chain.getArg(0);
                            invokeMethod(pmObj, "showFloatingChat", new Class[]{pw}, new Object[]{petArg});
                            module.log(Log.INFO, TAG, "[双击事件] 成功触发显示悬浮聊天窗");
                        } catch (Throwable t) {
                            module.log(Log.WARN, TAG, "[双击事件] 后置处理异常: " + t);
                        }
                    }
                    return result;
                });
                module.log(Log.INFO, TAG, "[PetManager] 已成功 Hook: onDoubleTap(PetWindow)");
                break;
            }

            // --- 寻找 FloatingChat.Listener 匿名实现类 (PetManager$3) ---
            // 通过反射遍历所有已加载类，找到实现 FloatingChat.Listener 的内部类
            Class<?> listenerInterface = null;
            for (Class<?> inner : fc.getDeclaredClasses()) {
                if (inner.isInterface() && "Listener".equals(inner.getSimpleName())) {
                    listenerInterface = inner;
                    break;
                }
            }
            if (listenerInterface == null) {
                // FloatingChat.Listener 是顶层接口（非内部接口）
                listenerInterface = Class.forName(CLS_FLOATING_CHAT + "$Listener", true, cl);
            }

            // 寻找 PetManager$3（即 showFloatingChat 创建的匿名 Listener 实现）
            Class<?> listenerImpl = null;
            for (int i = 1; i <= 10; i++) {
                try {
                    Class<?> candidate = Class.forName(CLS_PET_MANAGER + "$" + i, true, cl);
                    if (listenerInterface.isAssignableFrom(candidate)) {
                        listenerImpl = candidate;
                        module.log(Log.INFO, TAG, "[悬浮聊天] 成功定位 Listener 实现类: PetManager$" + i);
                        break;
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }

            if (listenerImpl != null) {
                hookListenerOnReply(listenerImpl);
                hookListenerOnSent(listenerImpl);
            } else {
                module.log(Log.WARN, TAG, "[悬浮聊天] 未找到 Listener 实现类");
            }

        } catch (Throwable e) {
            module.log(Log.WARN, TAG, "[悬浮聊天] 监听器 Hook 失败: " + e);
        }
    }

    /**
     * hook FloatingChat.send()
     * <p>
     * 在原始 send() 执行之前：
     * 1. 把用户输入文字即时显示到气泡列表（addUser）
     * 2. 显示 typing 动画（showTyping）
     */
    private void hookFcSend(Class<?> fcClass) {
        for (Method m : fcClass.getDeclaredMethods()) {
            if (!"send".equals(m.getName()) || m.getParameterTypes().length != 0) continue;
            module.hook(m).intercept(chain -> {
                Object self = chain.getThisObject();
                try {
                    Object input = getField(self, "input");
                    if (input instanceof EditText) {
                        Editable text = ((EditText) input).getText();
                        if (text != null) {
                            String content = text.toString().trim();
                            if (!content.isEmpty()) {
                                invokeMethod(self, "addUser", new Class[]{String.class}, new Object[]{content});
                                invokeNoArg(self, "showTyping");
                                module.log(Log.INFO, TAG, "[悬浮聊天发送] 已添加用户消息气泡并显示正在输入动画");
                            }
                        }
                    }
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG, "[悬浮聊天发送] 前置处理异常: " + t);
                }
                return chain.proceed();
            });
            module.log(Log.INFO, TAG, "[悬浮聊天] 已成功 Hook: send()");
            break;
        }
    }

    /**
     * 若调用链中有 send() 帧（即"发送后自动关闭"），则跳过 dismiss，
     * 保持悬浮聊天窗打开。
     */
    private void hookFcDismiss(Class<?> fcClass) {
        for (Method m : fcClass.getDeclaredMethods()) {
            if (!"dismiss".equals(m.getName()) || m.getParameterTypes().length != 0) continue;
            module.hook(m).intercept(chain -> {
                // 检查调用栈：是否来自 send()
                for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                    if ("send".equals(e.getMethodName()) && CLS_FLOATING_CHAT.equals(e.getClassName())) {
                        module.log(Log.INFO, TAG, "[悬浮聊天关闭] 已成功阻止窗口自动关闭（由 send 触发）");
                        return null;
                    }
                }
                return chain.proceed();
            });
            module.log(Log.INFO, TAG, "[悬浮聊天] 已成功 Hook: dismiss()");
            break;
        }
    }

    /**
     * hook FloatingChat.Listener.onReply(String text, String image, boolean sing)
     * <p>
     * 在原始 onReply 执行之前，把 AI 回复文字/图片渲染到悬浮聊天窗。
     * 原始实现会调用 PetWindow.showBubble()，这里额外调 floatingChat.addPet()。
     */
    private void hookListenerOnReply(Class<?> listenerImpl) {
        for (Method m : listenerImpl.getDeclaredMethods()) {
            if (!"onReply".equals(m.getName())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 3) continue;

            module.hook(m).intercept(chain -> {
                // 先执行原始逻辑（PetWindow 气泡等）
                Object result = chain.proceed();

                // 再把回复渲染到悬浮聊天窗
                try {
                    Object self = chain.getThisObject();
                    // listenerImpl 是 PetManager 内部匿名类，持有 PetManager 引用
                    // 字段名通常是 "this$0"
                    Object pmObj = getField(self, "this$0");
                    if (pmObj == null) return result;

                    Object chat = getField(pmObj, "floatingChat");
                    if (chat == null) return result;

                    String text = (String) chain.getArg(0);
                    String image = (String) chain.getArg(1);
                    // 先移除 typing 动画，再渲染 AI 回复
                    invokeNoArg(chat, "removeTyping");
                    invokeMethod(chat, "addPet", new Class[]{String.class, String.class}, new Object[]{text, image});
                    module.log(Log.INFO, TAG, "[AI回复] 已移除输入动画并将 AI 回复渲染到悬浮聊天窗");
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG, "[AI回复] 处理异常: " + t);
                }
                return result;
            });
            module.log(Log.INFO, TAG, "[悬浮聊天监听] 已成功 Hook: onReply()");
            break;
        }
    }

    /**
     * hook FloatingChat.Listener.onSent()
     * <p>
     * 原始逻辑：让 PetWindow 显示 "收到，让我想想…" 气泡。
     * 这里完全跳过原始逻辑，防止关闭悬浮窗。
     */
    private void hookListenerOnSent(Class<?> listenerImpl) {
        for (Method m : listenerImpl.getDeclaredMethods()) {
            if (!"onSent".equals(m.getName()) || m.getParameterTypes().length != 0) continue;
            module.hook(m).intercept(chain -> {
                module.log(Log.INFO, TAG, "[消息发送] 已成功拦截并抑制原版发送气泡行为");
                return null;  // 跳过原始实现
            });
            module.log(Log.INFO, TAG, "[悬浮聊天监听] 已成功 Hook: onSent()");
            break;
        }
    }
}
