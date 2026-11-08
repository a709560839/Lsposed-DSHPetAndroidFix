package com.jj.dshpet.lsposed.constant;

/**
 * 模块全局常量定义类，集中管理所有 Log TAG、目标包名、类名及延时配置等公共常量。
 */
public class Constants {
    /** 日志统一 TAG */
    public static final String TAG = "[DSHPetFix] ";

    /** 目标应用包名 */
    public static final String TARGET_PKG = "io.dsh.pet.ai";

    /** 核心类名：PetManager */
    public static final String CLS_PET_MANAGER = "io.dsh.pet.PetManager";

    /** 核心类名：PetWindow */
    public static final String CLS_PET_WINDOW = "io.dsh.pet.PetWindow";

    /** 核心类名：FloatingChat */
    public static final String CLS_FLOATING_CHAT = "io.dsh.pet.FloatingChat";

    /** 核心类名：SettingsStore */
    public static final String CLS_SETTINGS = "io.dsh.pet.SettingsStore";

    /** 息屏延迟收起桌宠判定时间（毫秒） */
    public static final long SHELVE_DELAY_MS = 5000L;
}
