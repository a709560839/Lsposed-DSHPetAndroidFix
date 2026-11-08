package com.jj.dshpet.lsposed.hook.fix;

import static com.jj.dshpet.lsposed.constant.Constants.CLS_PET_MANAGER;
import static com.jj.dshpet.lsposed.constant.Constants.TAG;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getFloatField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.getIntField;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeMethod;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeNoArg;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.invokeStaticNoArg;
import static com.jj.dshpet.lsposed.utils.ReflectionUtils.setIntField;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;

/**
 * 修复屏幕旋转时桌宠位置错位 / 比例溢出 Bug 的核心逻辑类。
 * 通过硬件级 DisplayListener 与 ComponentCallbacks2 监听屏幕方向改变与分辨率刷新，
 * 自动按比例计算新坐标并重排桌宠及气泡窗口位置。
 */
public class FixOrientation {

    // 防重标志位
    private static volatile boolean sDisplayListenerRegistered = false;
    private static volatile boolean sCallbacksRegistered = false;

    /**
     * 注册硬件级 DisplayListener 屏幕旋转监听与 ComponentCallbacks2 兜底旋转监听
     */
    public static void ensureDisplayListener(Context context, ClassLoader cl, XposedModule module) {
        if (context == null) return;
        final Context appCtx = (context.getApplicationContext() != null) ? context.getApplicationContext() : context;

        // 1. 注册 DisplayListener 监听显示设备变化（如旋转、分辨率改变）
        if (!sDisplayListenerRegistered) {
            synchronized (FixOrientation.class) {
                if (!sDisplayListenerRegistered) {
                    try {
                        DisplayManager dm = (DisplayManager) appCtx.getSystemService(Context.DISPLAY_SERVICE);
                        if (dm != null) {
                            dm.registerDisplayListener(new DisplayManager.DisplayListener() {
                                @Override
                                public void onDisplayAdded(int displayId) {
                                }

                                @Override
                                public void onDisplayRemoved(int displayId) {
                                }

                                @Override
                                public void onDisplayChanged(int displayId) {
                                    try {
                                        Class<?> pmClass = Class.forName(CLS_PET_MANAGER, true, cl);
                                        Object pm = invokeStaticNoArg(pmClass, "get");
                                        if (pm != null) {
                                            triggerDisplayRefresh(pm, appCtx, module);
                                        }
                                    } catch (Throwable t) {
                                        module.log(Log.WARN, TAG, "[屏幕监听] DisplayListener 回调发生异常: " + t);
                                    }
                                }
                            }, new Handler(Looper.getMainLooper()));

                            sDisplayListenerRegistered = true;
                            module.log(Log.INFO, TAG, "[全局管理] 硬件级 DisplayListener 注册成功");
                        }
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "[全局管理] 硬件级 DisplayListener 注册失败: " + t);
                    }
                }
            }
        }

        // 2. 注册 ComponentCallbacks 兜底配置改变监听（处理切屏与旋转）
        if (!sCallbacksRegistered) {
            synchronized (FixOrientation.class) {
                if (!sCallbacksRegistered) {
                    try {
                        if (appCtx instanceof Application) {
                            appCtx.registerComponentCallbacks(new ComponentCallbacks2() {
                                @Override
                                public void onConfigurationChanged(@NonNull Configuration newConfig) {
                                    try {
                                        Class<?> pmClass = Class.forName(CLS_PET_MANAGER, true, cl);
                                        Object pm = invokeStaticNoArg(pmClass, "get");
                                        if (pm != null) {
                                            module.log(Log.INFO, TAG, "[组件回调] 检测到屏幕配置改变/旋转 -> 刷新布局");
                                            triggerDisplayRefresh(pm, appCtx, module);
                                        }
                                    } catch (Throwable t) {
                                        module.log(Log.WARN, TAG, "[组件回调] ComponentCallbacks 发生异常: " + t);
                                    }
                                }

                                @Override
                                public void onLowMemory() {
                                }

                                @Override
                                public void onTrimMemory(int level) {
                                }
                            });
                            sCallbacksRegistered = true;
                            module.log(Log.INFO, TAG, "[全局管理] ComponentCallbacks 注册成功");
                        }
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "[全局管理] ComponentCallbacks 注册失败: " + t);
                    }
                }
            }
        }
    }

    /**
     * 触发显示刷新，获取最新屏幕宽高并在方向改变时触发重排
     */
    @SuppressWarnings("deprecation")
    private static void triggerDisplayRefresh(Object pm, Context ctx, XposedModule module) {
        try {
            int realW = 0;
            int realH = 0;

            // Android 11+ (API 30+) 使用 WindowMetrics 获取精准屏幕尺寸
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    WindowMetrics metrics = wm.getCurrentWindowMetrics();
                    Rect bounds = metrics.getBounds();
                    realW = bounds.width();
                    realH = bounds.height();
                }
            }

            // 低版本兜底或 WindowManager 未返回有效尺寸时使用 Display.getRealSize
            if (realW <= 0 || realH <= 0) {
                DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
                if (dm != null) {
                    Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
                    if (display != null) {
                        Point size = new Point();
                        display.getRealSize(size);
                        realW = size.x;
                        realH = size.y;
                    }
                }
            }

            int curW = getIntField(pm, "screenW");
            int curH = getIntField(pm, "screenH");

            // 若检测到屏幕宽高发生变化（即发生了横竖屏切换）
            if (realW > 0 && realH > 0 && (realW != curW || realH != curH)) {
                module.log(Log.INFO, TAG, "[屏幕旋转] 检测到屏幕方向/分辨率改变: " + curW + "x" + curH + " -> " + realW + "x" + realH);
                recomputePositions(pm, curW, curH, realW, realH, module);
            }
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "[刷新显示] 触发显示刷新发生异常: " + t);
        }
    }

    /**
     * 按屏幕相对边界计算新位置：
     * 例如竖屏在左下角，转横屏后精确计算依然处于横屏左下角，
     * 并且同步修改 PetManager 与 PetWindow 的 screenW/screenH，使可移动范围自适应。
     */
    private static void recomputePositions(Object pm, int oldW, int oldH, int newW, int newH, XposedModule module) {
        if (pm == null || newW <= 0 || newH <= 0) return;

        @SuppressWarnings("unchecked") List<Object> ws = (List<Object>) getField(pm, "windows");
        if (ws == null || ws.isEmpty()) return;

        // 1. 同步更新 PetManager 的 screenW 和 screenH
        setIntField(pm, "screenW", newW);
        setIntField(pm, "screenH", newH);

        for (Object w : ws) {
            float x = getPetWindowX(w);
            float y = getPetWindowY(w);
            float ww = getPetWindowW(w);
            float hh = getPetWindowH(w);

            // 计算相对移动范围比例（rx: 0=左边, 1=右边; ry: 0=顶边, 1=底边）
            float maxOldX = Math.max(1f, oldW - ww);
            float maxOldY = Math.max(1f, oldH - hh);
            float rx = Math.max(0f, Math.min(1f, x / maxOldX));
            float ry = Math.max(0f, Math.min(1f, y / maxOldY));

            // 计算新坐标
            float maxNewX = Math.max(0f, newW - ww);
            float maxNewY = Math.max(0f, newH - hh);
            float newX = rx * maxNewX;
            float newY = ry * maxNewY;

            // 2. 更新 PetWindow 的屏幕宽高属性，修正物理引擎与拖拽边界
            setIntField(w, "screenW", newW);
            setIntField(w, "screenH", newH);

            // 3. 同步更新气泡 BubbleWindow 的屏幕宽高及宠物目标坐标
            Object bubble = getField(w, "bubble");
            if (bubble != null) {
                setIntField(bubble, "screenW", newW);
                setIntField(bubble, "screenH", newH);
                setIntField(bubble, "petX", Math.round(newX));
                setIntField(bubble, "petY", Math.round(newY));
                setIntField(bubble, "petW", Math.round(ww));
                setIntField(bubble, "petH", Math.round(hh));
            }

            // 4. 应用新坐标（内部会触发 applyPosition -> bubble.moveTo）
            invokeMethod(w, "setPosition", new Class[]{float.class, float.class}, new Object[]{newX, newY});

            // 5. 若气泡当前正在显示，立即重新测量并更新布局，防止旋转后气泡错位脱节
            if (bubble != null) {
                relayoutBubbleIfShowing(bubble, module);
            }

            module.log(Log.INFO, TAG, "[位置重排] 桌宠位置已按比例重定位从 (" + x + "," + y + ") -> (" + newX + "," + newY + ") 适配分辨率 " + newW + "x" + newH);
        }

        // 6. 重附着特效层
        Object fx = getField(pm, "f22fx");
        if (fx == null) fx = getField(pm, "fx");
        if (fx != null) {
            invokeNoArg(fx, "detach");
            invokeNoArg(fx, "attach");
        }
    }

    /**
     * 如果对话气泡当前正在显示，则在旋转后实时重新测量并定位
     */
    private static void relayoutBubbleIfShowing(Object bubble, XposedModule module) {
        if (bubble == null) return;
        try {
            Boolean added = (Boolean) getField(bubble, "added");
            if (added == null || !added) return;

            // 重新测量卡片尺寸以适配新屏幕尺寸
            try {
                Method mMeasure = bubble.getClass().getDeclaredMethod("measureRoot");
                mMeasure.setAccessible(true);
                mMeasure.invoke(bubble);
            } catch (Throwable ignored) {
            }

            // 重新计算吸附位置并更新 View 布局
            try {
                Method mPosition = bubble.getClass().getDeclaredMethod("position");
                mPosition.setAccessible(true);
                mPosition.invoke(bubble);
                module.log(Log.INFO, TAG, "[气泡窗口] 在屏幕旋转时已同步完成重新定位与布局测量");
            } catch (Throwable ignored) {
            }

            // 200ms 后再校验一次，防止系统旋转动画完成后覆盖
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Boolean isStillAdded = (Boolean) getField(bubble, "added");
                    if (isStillAdded != null && isStillAdded) {
                        Method mPos = bubble.getClass().getDeclaredMethod("position");
                        mPos.setAccessible(true);
                        mPos.invoke(bubble);
                    }
                } catch (Throwable ignored) {
                }
            }, 200);

        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "[气泡重排] 气泡重新布局发生异常: " + t);
        }
    }

    private static float getPetWindowX(Object w) {
        float v = getFloatField(w, "x");
        if (v != 0f) return v;
        return getFloatField(w, "f29x");
    }

    private static float getPetWindowY(Object w) {
        float v = getFloatField(w, "y");
        if (v != 0f) return v;
        return getFloatField(w, "f30y");
    }

    private static float getPetWindowW(Object w) {
        float v = getFloatField(w, "w");
        if (v != 0f) return v;
        return getFloatField(w, "f27w");
    }

    private static float getPetWindowH(Object w) {
        float v = getFloatField(w, "h");
        if (v != 0f) return v;
        return getFloatField(w, "f24h");
    }
}
