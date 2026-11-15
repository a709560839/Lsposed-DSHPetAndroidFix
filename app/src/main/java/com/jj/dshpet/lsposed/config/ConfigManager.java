package com.jj.dshpet.lsposed.config;

import static com.jj.dshpet.lsposed.constant.Constants.MODULE_PKG;
import static com.jj.dshpet.lsposed.constant.Constants.PROVIDER_EXTRA_DEFAULT;
import static com.jj.dshpet.lsposed.constant.Constants.PROVIDER_EXTRA_VALUE;
import static com.jj.dshpet.lsposed.constant.Constants.PROVIDER_METHOD_GET_BOOLEAN;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.jj.dshpet.lsposed.constant.Constants;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块全局配置管理器
 * <p>
 * 统一管理 UI 设置界面与 Hook 进程之间的配置存取，
 * Hook 端通过 ContentProvider 跨进程实时读取，并带有内存缓存加速。
 */
public class ConfigManager {

    // ---- 跨进程内存缓存机制（Hook 进程加速） ----
    private static final ConcurrentHashMap<String, Boolean> sCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> sCacheTime = new ConcurrentHashMap<>();

    /**
     * 获取模块本地 SharedPreferences（仅限 UI 进程使用）
     */
    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
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
        if (context.getPackageName().equals(MODULE_PKG)) {
            return getPrefs(context).getBoolean(key, defaultValue);
        }

        // 2. 如果在目标应用进程（Hook 进程），先检查本地轻量缓存
        long now = SystemClock.uptimeMillis();
        Long lastTime = sCacheTime.get(key);
        if (lastTime != null && (now - lastTime) < Constants.CACHE_TTL_MS) {
            Boolean cached = sCache.get(key);
            if (cached != null) return cached;
        }

        // 3. 通过 ContentProvider 跨进程查询最新配置
        try {
            Bundle extras = new Bundle();
            extras.putBoolean(PROVIDER_EXTRA_DEFAULT, defaultValue);
            Bundle result = context.getContentResolver().call(
                    Constants.CONTENT_URI,
                    PROVIDER_METHOD_GET_BOOLEAN,
                    key,
                    extras
            );
            if (result != null && result.containsKey(PROVIDER_EXTRA_VALUE)) {
                boolean val = result.getBoolean(PROVIDER_EXTRA_VALUE, defaultValue);
                sCache.put(key, val);
                sCacheTime.put(key, now);
                return val;
            }
        } catch (Throwable t) {
            Log.w(Constants.TAG_CONFIG, "ContentProvider call failed for " + key + ": " + t);
        }

        // 4. 若跨进程查询未成功，回退到默认值
        return defaultValue;
    }

    // ---- 便捷开关读取与控制方法 ----

    public static boolean isDoubleTapChatEnabled(Context context) {
        return getBoolean(context, Constants.KEY_DOUBLE_TAP_CHAT, true);
    }

    public static boolean isKeepChatWindowEnabled(Context context) {
        return isDoubleTapChatEnabled(context);
    }

    public static boolean isLongPressChatEnabled(Context context) {
        return getBoolean(context, Constants.KEY_LONG_PRESS_CHAT, true);
    }

    public static boolean isHideOnCallEnabled(Context context) {
        return getBoolean(context, Constants.KEY_HIDE_ON_CALL, true);
    }
}
