package com.jj.dshpet.lsposed.hook.modify;

import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_MANAGER;
import static com.jj.dshpet.lsposed.constant.Constants.CLS_SETTINGS;
import static com.jj.dshpet.lsposed.constant.Constants.FIELD_FX;
import static com.jj.dshpet.lsposed.constant.Constants.FIELD_FX_22;
import static com.jj.dshpet.lsposed.constant.Constants.METHOD_APPLY_SETTINGS;
import static com.jj.dshpet.lsposed.constant.Constants.METHOD_ATTACH;
import static com.jj.dshpet.lsposed.constant.Constants.METHOD_DETACH;
import static com.jj.dshpet.lsposed.constant.Constants.TAG;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeNoArg;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeStaticMethod;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeStaticNoArg;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.setBooleanField;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.jj.dshpet.lsposed.config.ConfigManager;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import io.github.libxposed.api.XposedModule;

/**
 * [Modify-3] 通话与微信通话时自动彻底隐藏收起桌宠
 * <p>
 * 参考 FixLockScreen 的实现机制：
 * 通话触发时，将所有活跃桌宠的 active 设为 false，并调用 applySettings 彻底从 WindowManager 卸载，
 * 并 detach 特效图层，确保通话时无任何悬浮物遮挡或干扰；
 * 通话结束后，将此前活跃桌宠的 active 状态恢复，重新 attach 特效层并调用 applySettings 挂载还原。
 * <p>
 * 监听来源：
 * 1. AudioManager 通信模式（MODE_IN_CALL / MODE_IN_COMMUNICATION）：
 * 普通电话、微信语音、微信视频通话均会启用该模式（小窗、后台、全屏全覆盖）；
 * 2. TelephonyManager 系统电话状态与广播（RINGING / OFFHOOK / IDLE）；
 * 3. PetAccessibilityService 无障碍窗口事件：微信 VoIP 页面弹出时即刻捕获。
 */
public class ModifyHideOnCall {

    private static volatile boolean sWatcherInitialized = false;
    private static volatile boolean sWechatVoipActive = false;

    // 保存通话前处于活跃状态的宠物 ID 列表
    private static final Set<String> sSavedActivePetIdsForCall = new HashSet<>();
    private static volatile boolean sPetsShelvedForCall = false;

    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static Context sAppContext = null;
    private static ClassLoader sClassLoader = null;
    private static XposedModule sModule = null;

    /**
     * 定时状态校验任务（通话期间每 1.2 秒校验一次，确保微信悬浮窗或后台挂断后秒级恢复）
     */
    private static final Runnable sPollRunnable = new Runnable() {
        @Override
        public void run() {
            checkAndSyncState();
            if (sPetsShelvedForCall) {
                sMainHandler.postDelayed(this, 1200L);
            }
        }
    };

    /**
     * 注册无障碍事件 Hook（用于捕捉微信前台通话界面）
     */
    public static void hook(XposedModule module, ClassLoader cl) {
        try {
            Class<?> serviceClass = Class.forName("io.dsh.pet.PetAccessibilityService", true, cl);
            for (Method m : serviceClass.getDeclaredMethods()) {
                if (!"onAccessibilityEvent".equals(m.getName()))
                    continue;
                module.hook(m).intercept(chain -> {
                    try {
                        Object event = chain.getArg(0);
                        if (event instanceof AccessibilityEvent) {
                            AccessibilityEvent ae = (AccessibilityEvent) event;
                            CharSequence pkg = ae.getPackageName();
                            CharSequence cls = ae.getClassName();
                            if (pkg != null && "com.tencent.mm".equals(pkg.toString())) {
                                String clsName = cls != null ? cls.toString().toLowerCase() : "";
                                if (clsName.contains("voip") || clsName.contains("talk")
                                        || clsName.contains("videotalk")) {
                                    sWechatVoipActive = true;
                                    triggerCheck();
                                } else if (ae.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                                    sWechatVoipActive = false;
                                    triggerCheck();
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return chain.proceed();
                });
                module.log(Log.INFO, TAG, "[通话收起] 已成功注册无障碍事件 Hook");
                break;
            }
        } catch (Throwable e) {
            module.log(Log.WARN, TAG, "[通话收起] 无障碍 Hook 注册失败(非必要): " + e);
        }
    }

    /**
     * 当桌宠启动 (PetManager.start) 时初始化通话状态观察器
     */
    public static void ensureCallWatcher(Context context, ClassLoader cl, XposedModule module) {
        if (context == null)
            return;
        sAppContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        sClassLoader = cl;
        sModule = module;

        if (sWatcherInitialized)
            return;
        synchronized (ModifyHideOnCall.class) {
            if (sWatcherInitialized)
                return;

            // 1. 注册系统广播监听电话状态改变
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent intent) {
                        triggerCheck();
                    }
                };
                if (Build.VERSION.SDK_INT >= 33) {
                    sAppContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    sAppContext.registerReceiver(receiver, filter);
                }
                module.log(Log.INFO, TAG, "[通话收起] 电话状态广播监听器注册成功");
            } catch (Throwable t) {
                module.log(Log.WARN, TAG, "[通话收起] 电话广播注册失败: " + t);
            }

            // 2. Android 12+ 注册 AudioManager 模式监听（直接感知微信通话与电话模式变更）
            try {
                AudioManager am = (AudioManager) sAppContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null && Build.VERSION.SDK_INT >= 31) {
                    am.addOnModeChangedListener(Executors.newSingleThreadExecutor(), mode -> triggerCheck());
                    module.log(Log.INFO, TAG, "[通话收起] AudioManager 模式监听器注册成功");
                }
            } catch (Throwable t) {
                module.log(Log.WARN, TAG, "[通话收起] AudioManager 模式监听注册失败: " + t);
            }

            sWatcherInitialized = true;
            module.log(Log.INFO, TAG, "[通话收起] 通话感知观察器初始化完成");
        }
    }

    /**
     * 触发异步状态检查
     */
    public static void triggerCheck() {
        sMainHandler.post(ModifyHideOnCall::checkAndSyncState);
    }

    /**
     * 核心校验方法：判断当前是否处于系统电话或微信语音/视频通话状态
     */
    public static boolean isCallActive(Context context) {
        if (context == null)
            return false;

        // 1. 音频模式检测（覆盖微信语音/视频通话、QQ通话及电话）
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getMode();
                if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. 系统电话状态检测
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                int callState = tm.getCallState();
                if (callState == TelephonyManager.CALL_STATE_RINGING
                        || callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. 微信 VoIP 前台活动辅助判定
        if (sWechatVoipActive) {
            return true;
        }

        return false;
    }

    /**
     * 检查当前状态并执行桌宠收起或恢复
     */
    private static void checkAndSyncState() {
        if (sAppContext == null || sClassLoader == null || sModule == null)
            return;

        // 若用户未开启该功能开关，直接跳过
        if (!ConfigManager.isHideOnCallEnabled(sAppContext)) {
            if (sPetsShelvedForCall) {
                unshelvePetsForCall();
            }
            return;
        }

        boolean calling = isCallActive(sAppContext);

        if (calling) {
            shelvePetsForCall();
        } else {
            if (sPetsShelvedForCall) {
                unshelvePetsForCall();
            }
        }
    }

    /**
     * 彻底隐藏收起桌宠（参考 FixLockScreen.shelvePetsForLockscreen 实现）
     * 将活跃宠物的 active 设为 false 并执行 applySettings 从 WindowManager 移除
     */
    private static void shelvePetsForCall() {
        if (sAppContext == null || sClassLoader == null || sModule == null)
            return;
        Runnable action = () -> {
            synchronized (sSavedActivePetIdsForCall) {
                if (sPetsShelvedForCall)
                    return; // 已经收起，防止重复执行覆盖记录
                try {
                    Class<?> pmClass = Class.forName(CLS_PET_MANAGER, true, sClassLoader);
                    Object pm = invokeStaticNoArg(pmClass, "get");
                    if (pm == null)
                        return;

                    Class<?> ssClass = Class.forName(CLS_SETTINGS, true, sClassLoader);
                    Object ss = invokeStaticMethod(ssClass, "get", new Class[] { Context.class },
                            new Object[] { sAppContext });
                    if (ss == null)
                        return;

                    @SuppressWarnings("unchecked")
                    List<Object> pets = (List<Object>) getField(ss, "pets");
                    if (pets == null || pets.isEmpty())
                        return;

                    sSavedActivePetIdsForCall.clear();
                    for (Object pet : pets) {
                        String petId = (String) getField(pet, "id");
                        if (petId == null)
                            petId = (String) getField(pet, "f43id");
                        Boolean active = (Boolean) getField(pet, "active");
                        if (active != null && active && petId != null) {
                            sSavedActivePetIdsForCall.add(petId);
                        }
                    }

                    if (!sSavedActivePetIdsForCall.isEmpty()) {
                        sPetsShelvedForCall = true;
                        // 临时将所有宠物的 active 设为 false（仅在内存中，不保存到磁盘）
                        for (Object pet : pets) {
                            setBooleanField(pet, "active", false);
                        }

                        // 触发 applySettings()，触发每个 PetWindow.destroy()，彻底从 WindowManager removeView
                        invokeNoArg(pm, METHOD_APPLY_SETTINGS);

                        // 彻底 detach 特效层
                        Object fx = getField(pm, FIELD_FX_22);
                        if (fx == null)
                            fx = getField(pm, FIELD_FX);
                        if (fx != null) {
                            invokeNoArg(fx, METHOD_DETACH);
                        }

                        sModule.log(Log.INFO, TAG,
                                "[通话收起] 检测到通话开始，所有活跃桌宠已从 WindowManager 彻底卸载移除: " + sSavedActivePetIdsForCall);

                        // 启动轮询检查，以便微信通话挂断后第一时间恢复
                        sMainHandler.removeCallbacks(sPollRunnable);
                        sMainHandler.postDelayed(sPollRunnable, 1200L);
                    }
                } catch (Throwable t) {
                    sModule.log(Log.WARN, TAG, "[通话收起] 卸载过程发生异常: " + t);
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            sMainHandler.post(action);
        }
    }

    /**
     * 恢复桌宠（参考 FixLockScreen.unshelvePetsForLockscreen 实现）
     * 将之前活跃宠物的 active 状态恢复，重新挂载到 WindowManager
     */
    private static void unshelvePetsForCall() {
        if (sAppContext == null || sClassLoader == null || sModule == null)
            return;
        Runnable action = () -> {
            synchronized (sSavedActivePetIdsForCall) {
                if (!sPetsShelvedForCall)
                    return; // 未收起，无需唤醒
                try {
                    Class<?> pmClass = Class.forName(CLS_PET_MANAGER, true, sClassLoader);
                    Object pm = invokeStaticNoArg(pmClass, "get");
                    if (pm == null)
                        return;

                    Class<?> ssClass = Class.forName(CLS_SETTINGS, true, sClassLoader);
                    Object ss = invokeStaticMethod(ssClass, "get", new Class[] { Context.class },
                            new Object[] { sAppContext });
                    if (ss == null)
                        return;

                    @SuppressWarnings("unchecked")
                    List<Object> pets = (List<Object>) getField(ss, "pets");
                    if (pets != null && !sSavedActivePetIdsForCall.isEmpty()) {
                        for (Object pet : pets) {
                            String petId = (String) getField(pet, "id");
                            if (petId == null)
                                petId = (String) getField(pet, "f43id");
                            if (petId != null && sSavedActivePetIdsForCall.contains(petId)) {
                                setBooleanField(pet, "active", true);
                            }
                        }
                    }

                    sSavedActivePetIdsForCall.clear();
                    sPetsShelvedForCall = false;
                    sMainHandler.removeCallbacks(sPollRunnable);

                    // 重新 attach 特效层
                    Object fx = getField(pm, FIELD_FX_22);
                    if (fx == null)
                        fx = getField(pm, FIELD_FX);
                    if (fx != null) {
                        invokeNoArg(fx, METHOD_ATTACH);
                    }

                    // 触发 applySettings()，重新通过 addWindow() 创建并挂载桌宠
                    invokeNoArg(pm, METHOD_APPLY_SETTINGS);

                    sModule.log(Log.INFO, TAG, "[通话收起] 通话结束，此前处于活跃状态的桌宠已全部恢复挂载到 WindowManager");
                } catch (Throwable t) {
                    sModule.log(Log.WARN, TAG, "[通话收起] 恢复过程发生异常: " + t);
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            sMainHandler.post(action);
        }
    }
}