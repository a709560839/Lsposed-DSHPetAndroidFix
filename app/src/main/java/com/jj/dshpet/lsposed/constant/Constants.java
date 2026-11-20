package com.jj.dshpet.lsposed.constant;

import android.net.Uri;

/**
 * 模块全局常量定义类，集中管理所有 Log TAG、包名、Provider 配置、类名、字段名、方法名及延时配置等公共常量。
 */
public class Constants {

    // ---- 日志与包名 ----
    /** 日志统一 TAG */
    public static final String TAG = "[DSHPetFix] ";
    /** Config 模块日志 TAG */
    public static final String TAG_CONFIG = "[DSHPetFix][Config]";

    /** 模块自身包名 */
    public static final String MODULE_PKG = "com.jj.dshpet";
    /** 目标应用包名 */
    public static final String TARGET_PKG = "io.dsh.pet.ai";

    // ---- 跨进程 Provider 与 SharedPreferences 配置 ----
    /** Provider 授权标识 */
    public static final String AUTHORITY = MODULE_PKG + ".provider.config";
    /** Provider 内容 URI */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    /** 本地模块 SharedPreferences 文件名 */
    public static final String PREF_NAME = "dsh_pet_module_config";
    /** 宿主应用 SharedPreferences 文件名 */
    public static final String PREF_DSH_PET_SETTINGS = "dsh_pet_settings";
    /** 宿主进程内部本地持久化缓存文件名（防模块停止运行配置丢失） */
    public static final String HOST_LOCAL_PREF = "dsh_pet_lsposed_host_cache";

    /** 配置键名：双击打开悬浮聊天窗 */
    public static final String KEY_DOUBLE_TAP_CHAT = "pref_double_tap_chat";
    /** 配置键名：长按直接打开主聊天页面 */
    public static final String KEY_LONG_PRESS_CHAT = "pref_long_press_chat";
    /** 配置键名：隐藏桌面图标 */
    public static final String KEY_HIDE_ICON = "pref_hide_icon";
    /** 配置键名：通话时收起桌宠（含系统电话与微信通话） */
    public static final String KEY_HIDE_ON_CALL = "pref_hide_on_call";
    /** 配置键名：禁止桌宠自动移动（开启后禁止自动漫游，不开启保持原版行为，不影响手动拖拽） */
    public static final String KEY_DISABLE_AUTO_MOVE = "pref_disable_auto_move";

    /** 桌面 Launcher 入口别名名称 */
    public static final String ALIAS_HOME = ".Home";

    /** Provider 调用方法名 */
    public static final String PROVIDER_METHOD_GET_BOOLEAN = "getBoolean";
    /** Provider Bundle 参数键名：默认值 */
    public static final String PROVIDER_EXTRA_DEFAULT = "default";
    /** Provider Bundle 返回键名：结果值 */
    public static final String PROVIDER_EXTRA_VALUE = "value";

    // ---- 延时与时间常量 ----
    /** 内存缓存有效期（毫秒） */
    public static final long CACHE_TTL_MS = 1500L;
    /** 息屏延迟收起桌宠判定时间（毫秒） */
    public static final long SHELVE_DELAY_MS = 5000L;
    /** 亮屏/锁屏二次校验延迟时间（毫秒） */
    public static final long CHECK_SCREEN_DELAY_MS = 3000L;
    /** 锁屏过渡兜底校验延迟时间（毫秒） */
    public static final long KEYGUARD_CHECK_DELAY_MS = 300L;
    /** 旋转后气泡二次重新布局延迟时间（毫秒） */
    public static final long RELAYOUT_BUBBLE_DELAY_MS = 200L;

    // ---- 目标应用核心类名 (Reflection) ----
    /** 核心类名：PetManager */
    public static final String CLS_PET_MANAGER = "io.dsh.pet.PetManager";
    /** 核心类名：PetWindow */
    public static final String CLS_PET_WINDOW = "io.dsh.pet.PetWindow";
    /** 核心类名：ChatActivity */
    public static final String CLS_CHAT_ACTIVITY = "io.dsh.pet.ChatActivity";
    /** 核心类名：FloatingChat */
    public static final String CLS_FLOATING_CHAT = "io.dsh.pet.FloatingChat";
    /** 核心类名：SettingsStore */
    public static final String CLS_SETTINGS = "io.dsh.pet.SettingsStore";
    /** 核心类名：AppCtx */
    public static final String CLS_APP_CTX = "io.dsh.pet.AppCtx";

    // ---- 目标应用常用反射字段与方法名 ----
    public static final String FIELD_SHOW_ON_AOD = "showOnAod";
    public static final String FIELD_FX_22 = "f22fx";
    public static final String FIELD_FX = "fx";
    public static final String METHOD_ATTACH = "attach";
    public static final String METHOD_DETACH = "detach";
    public static final String METHOD_APPLY_SETTINGS = "applySettings";
}