package com.jj.dshpet.lsposed.hook.fix;

import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_MANAGER;
import static com.jj.dshpet.lsposed.constant.Constants.CLS_SETTINGS;
import static com.jj.dshpet.lsposed.constant.Constants.SHELVE_DELAY_MS;
import static com.jj.dshpet.lsposed.constant.Constants.TAG;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeNoArg;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeStaticMethod;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeStaticNoArg;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.setBooleanField;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * 修复锁屏界面桌宠无法自动息屏 / 留在锁屏上显示 Bug 的核心逻辑类。
 * 通过深度拦截屏幕熄屏、亮屏、解锁广播，并按 AOD 设置与锁屏状态安全地从 WindowManager 卸载或恢复桌宠视图。
 */
public class FixLockScreen {

    // 全局广播接收器注册状态标志
    private static volatile boolean sReceiverRegistered = false;

    // 保存锁屏前处于活跃状态的宠物 ID 列表，用于解锁时精准恢复
    private static final Set<String> sSavedActivePetIds = new HashSet<>();
    private static volatile boolean sPetsShelvedForLockscreen = false;

    // 息屏延迟判断 Handler 与任务句柄（解决息屏后立即解锁引起的广播延迟导致闪烁/重复重装问题）
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static Runnable sPendingShelveRunnable = null;

    /**
     * 拦截原 App 自带的 onScreenOff / onScreenOn 监听逻辑，防止其立即销毁/收起桌宠，由本模块全局接管
     */
    public static void hookScreenOnOff(XposedModule module, Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            String name = m.getName();
            if ("onScreenOff".equals(name) || "onScreenOn".equals(name)) {
                module.hook(m).intercept(chain -> {
                    module.log(Log.INFO, TAG, "[PetManager] 已拦截原版 " + name + " 方法 -> 已成功绕过");
                    return null;
                });
                module.log(Log.INFO, TAG, "[PetManager] 已成功 Hook: " + name);
            }
        }
    }

    /**
     * 确保注册全局屏幕状态广播接收器（息屏隐藏 + 亮屏/解锁恢复）
     */
    public static void ensureGlobalReceivers(Context context, ClassLoader cl, XposedModule module) {
        if (context == null) return;
        final Context appCtx = (context.getApplicationContext() != null) ? context.getApplicationContext() : context;

        if (!sReceiverRegistered) {
            synchronized (FixLockScreen.class) {
                if (!sReceiverRegistered) {
                    try {
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(Intent.ACTION_SCREEN_OFF);
                        filter.addAction(Intent.ACTION_SCREEN_ON);
                        filter.addAction(Intent.ACTION_USER_PRESENT);

                        BroadcastReceiver receiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context c, Intent intent) {
                                if (intent == null) return;
                                String action = intent.getAction();
                                module.log(Log.INFO, TAG, "[屏幕广播] 收到广播 Action: " + action);
                                try {
                                    Class<?> pmClass = Class.forName(CLS_PET_MANAGER, true, cl);
                                    Object pm = invokeStaticNoArg(pmClass, "get");
                                    if (pm == null) return;

                                    KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
                                    boolean locked = (km != null && km.isKeyguardLocked());
                                    boolean showOnAod = isShowOnAod(c, cl);

                                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                                        // 息屏时：取消先前未完成的延迟任务，提交 5 秒延迟二次校验收起
                                        cancelPendingShelve(module);
                                        if (!showOnAod) {
                                            scheduleShelvePets(pm, c, cl, SHELVE_DELAY_MS, module);
                                        }
                                    } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                                        // 亮屏时（如按电源键点亮、指纹解锁点亮）：
                                        if (!locked || showOnAod) {
                                            // 已解锁，或开启了 AOD：取消延迟收起，恢复桌宠显示
                                            cancelPendingShelve(module);
                                            unshelvePetsForLockscreen(pm, c, cl, module);
                                        } else {
                                            // 处于锁屏状态且未开启 showOnAod：
                                            if (sPetsShelvedForLockscreen) {
                                                // 若此前已真正收起（息屏>5s），保持收起，但延迟 300ms 兜底检查指纹解锁过渡期
                                                sMainHandler.postDelayed(() -> {
                                                    try {
                                                        KeyguardManager kmCheck = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
                                                        if (kmCheck != null && !kmCheck.isKeyguardLocked()) {
                                                            unshelvePetsForLockscreen(pm, c, cl, module);
                                                        }
                                                    } catch (Throwable ignored) {
                                                    }
                                                }, 300L);
                                            } else {
                                                // 若此前未收起（息屏<5s 快速点亮/解锁中），切勿在此处立即 shelve！
                                                // 重新提交 3 秒延迟校验任务：若看一眼锁屏未解锁，3 秒后真正收起；若完成解锁，解锁广播或校验会保持显示
                                                module.log(Log.INFO, TAG, "[屏幕广播] 锁屏状态下收到亮屏广播，桌宠尚未收起 -> 安排延迟校验检查");
                                                scheduleShelvePets(pm, c, cl, 3000L, module);
                                            }
                                        }
                                    } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                                        // 用户真正解锁进入系统：取消延迟收起任务，恢复桌宠显示！
                                        cancelPendingShelve(module);
                                        unshelvePetsForLockscreen(pm, c, cl, module);
                                    }
                                } catch (Throwable t) {
                                    module.log(Log.WARN, TAG, "[屏幕广播] 接收处理发生异常: " + t);
                                }
                            }
                        };

                        if (Build.VERSION.SDK_INT >= 33) {
                            // 注意：必须使用 RECEIVER_EXPORTED (2) 才能接收来自系统的全局广播！
                            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                        } else {
                            appCtx.registerReceiver(receiver, filter);
                        }
                        sReceiverRegistered = true;
                        module.log(Log.INFO, TAG, "[全局管理] 屏幕状态广播接收器注册成功");
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "[全局管理] 屏幕状态广播接收器注册失败: " + t);
                    }
                }
            }
        }
    }

    /**
     * 取消未执行的息屏延迟收起任务
     */
    private static synchronized void cancelPendingShelve(XposedModule module) {
        if (sPendingShelveRunnable != null) {
            sMainHandler.removeCallbacks(sPendingShelveRunnable);
            sPendingShelveRunnable = null;
            module.log(Log.INFO, TAG, "[屏幕广播] 先前未执行的延迟收起任务已取消");
        }
    }

    /**
     * 提交延迟收起任务，到期后二次校验屏幕状态
     */
    private static synchronized void scheduleShelvePets(Object pm, Context ctx, ClassLoader cl, long delayMs, XposedModule module) {
        cancelPendingShelve(module);
        sPendingShelveRunnable = () -> {
            synchronized (FixLockScreen.class) {
                sPendingShelveRunnable = null;
            }
            try {
                PowerManager pmService = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                KeyguardManager kmService = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                boolean isInteractive = (pmService != null && pmService.isInteractive());
                boolean isLocked = (kmService != null && kmService.isKeyguardLocked());
                boolean showOnAod = isShowOnAod(ctx, cl);

                // 延迟到期后二次校验：如果屏幕依然处于非交互/息屏状态，或者仍然锁屏且未开启 showOnAod，才真正收起
                if (!isInteractive || (isLocked && !showOnAod)) {
                    module.log(Log.INFO, TAG, "[屏幕广播] " + (delayMs / 1000) + "秒延迟到期，屏幕仍处于息屏或锁屏状态 -> 立即收起桌宠");
                    shelvePetsForLockscreen(pm, ctx, cl, module);
                } else {
                    module.log(Log.INFO, TAG, "[屏幕广播] " + (delayMs / 1000) + "秒延迟到期，但屏幕已点亮且处于解锁状态 -> 跳过收起");
                }
            } catch (Throwable t) {
                module.log(Log.WARN, TAG, "[定时收起] 调度任务执行异常: " + t);
            }
        };
        sMainHandler.postDelayed(sPendingShelveRunnable, delayMs);
        module.log(Log.INFO, TAG, "[屏幕广播] 已预约在 " + delayMs + " 毫秒后执行延迟收起任务");
    }

    /**
     * 锁屏时彻底收起桌宠（将 active 置为 false 并执行 applySettings）
     * 消除所有顶层悬浮图层，恢复系统纯净锁屏，确保正常超时熄屏。
     */
    private static void shelvePetsForLockscreen(Object pm, Context ctx, ClassLoader cl, XposedModule module) {
        if (pm == null || ctx == null) return;
        Runnable action = () -> {
            synchronized (sSavedActivePetIds) {
                if (sPetsShelvedForLockscreen) return; // 已经收起，防止重复执行覆盖记录
                try {
                    Class<?> ssClass = Class.forName(CLS_SETTINGS, true, cl);
                    Object ss = invokeStaticMethod(ssClass, "get", new Class[]{Context.class}, new Object[]{ctx});
                    if (ss == null) return;

                    @SuppressWarnings("unchecked") List<Object> pets = (List<Object>) getField(ss, "pets");
                    if (pets == null || pets.isEmpty()) return;

                    sSavedActivePetIds.clear();
                    for (Object pet : pets) {
                        String petId = (String) getField(pet, "id");
                        if (petId == null) petId = (String) getField(pet, "f43id");
                        Boolean active = (Boolean) getField(pet, "active");
                        if (active != null && active && petId != null) {
                            sSavedActivePetIds.add(petId);
                        }
                    }

                    if (!sSavedActivePetIds.isEmpty()) {
                        sPetsShelvedForLockscreen = true;
                        // 临时将所有宠物的 active 设为 false（仅在内存中，绝不 save 写入磁盘）
                        for (Object pet : pets) {
                            setBooleanField(pet, "active", false);
                        }

                        // 触发 applySettings()，触发每个 PetWindow.destroy()，彻底从 WindowManager removeView
                        invokeNoArg(pm, "applySettings");

                        // 彻底 detach 特效层
                        Object fx = getField(pm, "f22fx");
                        if (fx == null) fx = getField(pm, "fx");
                        if (fx != null) {
                            invokeNoArg(fx, "detach");
                        }

                        module.log(Log.INFO, TAG, "[锁屏收起] 所有活跃桌宠已从 WindowManager 彻底卸载移除: " + sSavedActivePetIds);
                    }
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG, "[锁屏收起] 卸载过程发生异常: " + t);
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            new Handler(Looper.getMainLooper()).post(action);
        }
    }

    /**
     * 解锁进入系统时唤醒桌宠（恢复之前活跃宠物的 active 状态并执行 applySettings）
     */
    private static void unshelvePetsForLockscreen(Object pm, Context ctx, ClassLoader cl, XposedModule module) {
        if (pm == null || ctx == null) return;
        Runnable action = () -> {
            synchronized (sSavedActivePetIds) {
                if (!sPetsShelvedForLockscreen) return; // 未收起，无需唤醒
                try {
                    Class<?> ssClass = Class.forName(CLS_SETTINGS, true, cl);
                    Object ss = invokeStaticMethod(ssClass, "get", new Class[]{Context.class}, new Object[]{ctx});
                    if (ss == null) return;

                    @SuppressWarnings("unchecked") List<Object> pets = (List<Object>) getField(ss, "pets");
                    if (pets != null && !sSavedActivePetIds.isEmpty()) {
                        for (Object pet : pets) {
                            String petId = (String) getField(pet, "id");
                            if (petId == null) petId = (String) getField(pet, "f43id");
                            if (petId != null && sSavedActivePetIds.contains(petId)) {
                                setBooleanField(pet, "active", true);
                            }
                        }
                    }

                    sSavedActivePetIds.clear();
                    sPetsShelvedForLockscreen = false;

                    // 重新 attach 特效层
                    Object fx = getField(pm, "f22fx");
                    if (fx == null) fx = getField(pm, "fx");
                    if (fx != null) {
                        invokeNoArg(fx, "attach");
                    }

                    // 触发 applySettings()，重新通过 addWindow() 创建并挂载桌宠
                    invokeNoArg(pm, "applySettings");

                    module.log(Log.INFO, TAG, "[解锁恢复] 此前处于活跃状态的桌宠已全部恢复挂载到 WindowManager");
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG, "[解锁恢复] 恢复过程发生异常: " + t);
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            new Handler(Looper.getMainLooper()).post(action);
        }
    }

    /**
     * 判断用户是否在设置中开启了【息屏/锁屏显示桌宠】(showOnAod)
     * 优先从 SettingsStore 内存实例读取，兜底直接读取 SharedPreferences
     */
    public static boolean isShowOnAod(Context context, ClassLoader cl) {
        if (context == null) return false;
        try {
            Class<?> ssClass = Class.forName(CLS_SETTINGS, true, cl);
            Object ss = invokeStaticMethod(ssClass, "get", new Class[]{Context.class}, new Object[]{context});
            if (ss != null) {
                Boolean val = (Boolean) getField(ss, "showOnAod");
                if (val != null) return val;
            }
        } catch (Throwable ignored) {
        }

        try {
            Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            SharedPreferences sp = appCtx.getSharedPreferences("dsh_pet_settings", Context.MODE_PRIVATE);
            return sp.getBoolean("showOnAod", false);
        } catch (Throwable ignored) {
        }

        return false;
    }
}
