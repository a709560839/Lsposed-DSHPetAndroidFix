package com.jj.dshpet.lsposed.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块全局配置管理器
 * <p>
 * 统一管理 UI 设置界面与 Hook 进程之间的配置存取，
 * Hook 端通过 ContentProvider 跨进程实时读取，并带有内存缓存加速。
 */
public class ConfigManager {

    private static final String TAG = "[DSHPetFix][Config]";

    /** Provider 授权标识 */
    public static final String AUTHORITY = "com.jj.dshpet.provider.config";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    /** 本地 SharedPreferences 文件名 */
    public static final String PREF_NAME = "dsh_pet_module_config";

    // ---- 配置键名（仅针对交互修改项提供开关，Bug 修复默认强制启用） ----
    /** 双击打开悬浮聊天窗（同时自动开启常驻优化与回复渲染） */
    public static final String KEY_DOUBLE_TAP_CHAT = "pref_double_tap_chat";
    /** 长按直接打开主聊天页面（ChatActivity） */
    public static final String KEY_LONG_PRESS_CHAT = "pref_long_press_chat";

    // ---- 跨进程内存缓存机制（Hook 进程加速） ----
    private static final ConcurrentHashMap<String, Boolean> sCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> sCacheTime = new ConcurrentHashMap<>();
    /** 缓存有效期：1500 毫秒 */
    private static final long CACHE_TTL_MS = 1500L;

    /**
     * 获取模块本地 SharedPreferences（仅限 UI 进程使用）
     */
    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 写入布尔配置（UI 进程使用）
     */
    public static void putBoolean(Context context, String key, boolean value) {
        if (context == null) return;
        getPrefs(context).edit().putBoolean(key, value).apply();
    }

    /**
     * 读取布尔配置（支持 UI 进程与 Hook 进程跨进程读取）
     *
     * @param context      上下文（尽量使用 ApplicationContext）
     * @param key          配置键名
     * @param defaultValue 默认值
     * @return 当前配置值
     */
    public static boolean getBoolean(Context context, String key, boolean defaultValue) {
        if (context == null) return defaultValue;

        // 1. 如果在模块自身进程中，直接读取本地 SP
        if (context.getPackageName().equals("com.jj.dshpet")) {
            return getPrefs(context).getBoolean(key, defaultValue);
        }

        // 2. 如果在目标应用进程（Hook 进程），先检查本地轻量缓存
        long now = SystemClock.uptimeMillis();
        Long lastTime = sCacheTime.get(key);
        if (lastTime != null && (now - lastTime) < CACHE_TTL_MS) {
            Boolean cached = sCache.get(key);
            if (cached != null) return cached;
        }

        // 3. 通过 ContentProvider 跨进程查询最新配置
        try {
            Bundle extras = new Bundle();
            extras.putBoolean("default", defaultValue);
            Bundle result = context.getContentResolver().call(
                    CONTENT_URI,
                    "getBoolean",
                    key,
                    extras
            );
            if (result != null && result.containsKey("value")) {
                boolean val = result.getBoolean("value", defaultValue);
                sCache.put(key, val);
                sCacheTime.put(key, now);
                return val;
            }
        } catch (Throwable t) {
            Log.w(TAG, "ContentProvider call failed for " + key + ": " + t);
        }

        // 4. 若跨进程查询未成功，回退到默认值
        return defaultValue;
    }

    // ---- 便捷开关读取方法 ----

    public static boolean isDoubleTapChatEnabled(Context context) {
        return getBoolean(context, KEY_DOUBLE_TAP_CHAT, true);
    }

    public static boolean isKeepChatWindowEnabled(Context context) {
        return isDoubleTapChatEnabled(context);
    }

    public static boolean isLongPressChatEnabled(Context context) {
        return getBoolean(context, KEY_LONG_PRESS_CHAT, true);
    }
}
