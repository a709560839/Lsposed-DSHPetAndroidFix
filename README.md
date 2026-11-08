# DSH Pet Android Fix

一个针对「大肥鱼桌宠」(io.dsh.pet.ai) 的现代 LSPosed 模块（基于 libxposed API），用于修复该应用在锁屏息屏、屏幕旋转切屏及悬浮窗交互上的一些Bug。

## 项目结构

```
Lsposed-DSHPetAndroidFix/
├── app/
│   └── src/main/
│       ├── java/com/jj/dshpet/lsposed/
│       │   ├── constant/
│       │   │   └── Constants.java          ← 全局公共常量定义
│       │   ├── hook/
│       │   │   ├── HookInit.java           ← LSPosed 模块核心 Hook 入口
│       │   │   ├── fix/
│       │   │   │   ├── FixLockScreen.java  ← 锁屏息屏深度修复（防阻止自动熄屏）
│       │   │   │   └── FixOrientation.java ← 屏幕旋转切屏自适应与按比例重定位
│       │   │   └── modify/
│       │   │       ├── ModifyDoubleTap.java  ← 个人喜好修改，可无视：双击桌宠打开悬浮聊天窗与交互优化
│       │   │       └── ModifyLongPress.java  ← 个人喜好修改，可无视：长按桌宠打开 ChatActivity 主界面
│       │   └── utils/
│       │       └── ReflectionUtils.java    ← 反射工具类（支持父类链递归查找）
│       ├── res/values/strings.xml
│       ├── resources/META-INF/xposed/
│       │   ├── java_init.list              ← 声明 HookInit 类
│       │   ├── module.prop                 ← 模块元信息
│       │   └── scope.list                  ← 作用域: io.dsh.pet.ai
│       └── AndroidManifest.xml
├── gradle/
├── build.gradle
├── settings.gradle
└── README.md
```

## 核心修复与功能特性

| 模块 / 类 | 功能点 | 详细说明 |
|---|---|---|
| `FixLockScreen` | 锁屏息屏深度修复 | 拦截熄屏、亮屏与解锁广播；在未开启 AOD 时息屏 5 秒后彻底从 `WindowManager` 移除桌宠悬浮窗，恢复系统纯净锁屏，确保正常超时熄屏；解锁后精准唤醒。 |
| `FixOrientation` | 屏幕旋转自适应 | 注册硬件级 `DisplayListener` 与 `ComponentCallbacks2` 监听横竖屏切换；结合 Android 11+ `WindowMetrics`，按屏幕相对边界比例自动重排桌宠及气泡窗口位置。 |
| `ModifyDoubleTap` | 个人喜好，可无视：双击交互修改 | 双击桌宠触发打开悬浮聊天窗；发送消息时即时显示用户消息与 typing 动画；阻止原版发送后立即关闭的行为；完整渲染 AI 回复（`onReply`）。 |
| `ModifyLongPress` | 个人喜好，可无视：长按交互修改 | 长按桌宠由原版的菜单改为直接打开聊天主界面（`ChatActivity`），并伴有触感反馈。 |
| `ReflectionUtils` | 反射工具类 | 提供安全高效的字段读写、实例/静态方法调用以及递归父类链查找支持。 |
| `Constants` | 全局常量管理 | 集中管理日志 TAG、目标包名、核心类名及延时配置。 |

## 目标应用信息

- **包名**: `io.dsh.pet.ai`
- **支持版本**: v1.1.22 及兼容版本
- **minSdk**: 26 (Android 8.0)

## 构建

在 Android Studio 中打开本项目，或执行：

```bash
./gradlew assembleDebug
```

## 参考

- [libxposed API](https://github.com/libxposed/api)
