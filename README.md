# biliweb

一个「打开就是哔哩哔哩」的极简 Android 应用：**包名 `com.xiaoyumi.biliweb`，启动后用系统 WebView 加载 <https://www.bilibili.com/>**。
没有广告 SDK、没有统计、没有第三方依赖。

## 一、配置一览

| 项目 | 值 |
| --- | --- |
| 应用名 | `biliweb` |
| 包名 / applicationId | `com.xiaoyumi.biliweb` |
| **最低版本 minSdk** | **21（Android 5.0）** |
| **编译版本 compileSdk** | **37（Android 17）** |
| 目标版本 targetSdk | 37 |
| User-Agent | **电脑版**（Windows Chrome），让 B 站下发完整功能的桌面页面 |
| 网页能力 | JS 弹窗（alert/confirm/prompt）、摄像头/麦克风权限申请、文件上传 都已接管 |
| 检查更新 | 「关于」里可手动检查：请求 `cu.php`，按 `latest_version` / `is_disabled` 判定后弹更新界面 |
| 多语言 | 中文 / English / 跟随系统，在「语言」里切换；**网页请求也会带对应语言的 Accept-Language** |
| 悬浮球 | 原生控件（非网页内容），圆形 + 齿轮图标，可拖到任意位置，点击进设置页；**大小 50%~150%、不透明度 20%~100% 可调** |
| 设置页 | 顶栏（返回箭头 + 标题）+ 长条列表项（缩放 / 屏幕方向 / 虚拟鼠标 / 悬浮球 / 外观 / 调试 / 语言 / 关于）；**横屏变双窗格** |
| 屏幕方向 | **默认**（竖屏打开、可自由旋转）或 **锁定横屏**（打开即横屏、不能旋转），二选一 |
| 虚拟鼠标 | 整页当触控板：单指拖动移指针、轻点=左键、长按=右键、双指拖动=滚动；**光标大小可调 50%~200%**；可选**长按悬浮球快捷开关**（默认关闭） |
| 网页缩放 | **整页缩放**（文字+图片+布局一起变）**50%~200%**，默认 100%，带实时预览，存 SharedPreferences |
| 界面 | 纯 `WebView` + 顶部 3dp 进度条（**只在首次打开时显示**） |
| 外观 | **全屏（默认）**：只有网页内容和悬浮球；**顶部导航栏**：顶部显示后退 / 前进 / 刷新 / 地址栏 / 标签页 / 设置，不再显示悬浮球 |
| 顶栏多标签页 | 顶栏方案的标签页按钮：切换 / 关闭 / 新建标签页；顶栏设置按钮**长按可开关虚拟鼠标** |
| 返回行为 | **全屏方案**走页面栈：上一页还活着就直接切回（不重新加载），已被内存裁剪掉就重新加载；**顶栏方案**走标签页自身的网页历史（前进 / 后退）。开启「不使用多进程 WebView」后，全屏方案的返回会优先回到页面栈里的上一个页面并重新加载（不会再在原地刷新） |
| 调试 | **不使用多进程 WebView**（默认关闭）：开启后不再保留已打开的页面，返回 / 前进都会重新加载页面，用于排查页面缓存相关的问题 |
| WebView 测试 | 「调试」里的按钮：打开本地测试页（`assets/check.html`），查看当前 WebView 的 User-Agent 与能力支持；该页面会应用屏幕方向 / 语言 / 缩放 / 虚拟鼠标 / 自定义 UA |
| 浏览器 UA | 「调试」里可选 **电脑 UA（默认）/ 手机 UA**，另有**自定义**开关，打开后可填写任意 UA；设置后主界面与测试页的 `User-Agent` 都会随之改变。自定义 UA **保存前会校验**：含换行/制表符等控制字符、非 ASCII 或不像 UA 的内容会被拒绝并提示，错误配置不入库（读取时也会兜底，绝不会因此闪退） |
| 第三方依赖 | **无**（不使用 AndroidX / 任何库） |
## 二、目录结构

```
biliweb/
├── settings.gradle                 项目名与模块
├── build.gradle                    顶层：AGP 9.4.1 + 仓库（含阿里云镜像）
├── gradle.properties               构建参数
├── gradlew / gradlew.bat           Gradle wrapper 脚本（9.7.1）
├── gradle/wrapper/                 wrapper jar + 配置
├── tools/make_icons.py             图标生成脚本（纯 Python 标准库；启动图标 = 粉底白电视 + 屏幕里 5x7 点阵写 WEB，另有齿轮/返回箭头/鼠标指针）
└── app/
    ├── build.gradle                模块配置：namespace、三个版本号
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml     权限、三个 Activity、主题
        ├── assets/check.html       WebView 测试页（本地页面，查看 UA 与能力支持）
        ├── res/raw(-en)/preview.html   缩放预览用的网页内容（按语言自动选）
        ├── java/com/xiaoyumi/biliweb/
        │   ├── MainActivity.java       主界面：页面栈 + 悬浮球 + WebView 各类接管
        │   ├── SettingsActivity.java   设置页：顶栏 + 列表 + 面板（横屏双窗格）
        │   ├── WebViewCheckActivity.java  WebView 测试页（应用用户自定义配置）
        │   ├── ZoomPrefs.java          缩放设置存储
        │   ├── OrientationPrefs.java   屏幕方向设置存储
        │   ├── PageZoom.java           整页缩放的实现（注入 CSS zoom）
        │   ├── VirtualMouseView.java   虚拟鼠标：指针绘制 + 触控板手势 + 合成鼠标事件
        │   ├── MousePointerPrefs.java  虚拟鼠标开关存储
        │   ├── FloatBallPrefs.java     悬浮球大小/不透明度存储
        │   ├── AppearancePrefs.java    外观方案存储（全屏 / 顶部导航栏）
        │   ├── DebugPrefs.java         调试开关存储（不使用多进程 WebView）
        │   ├── UserAgentPrefs.java     浏览器 UA 存储（电脑 / 手机 / 自定义）
        │   ├── UpdateChecker.java      检查更新（请求接口 + 版本号比较）
        │   ├── AppLanguage.java        应用语言（中文/英文/跟随系统 + Accept-Language）
        │   └── EdgeToEdge.java         Android 15+ 沉浸式避让工具
        └── res/
            ├── layout/activity_main.xml          顶栏（可选）+ 页面栈容器 + 进度条 + 悬浮球
            ├── layout/main_top_bar.xml           主界面顶部导航栏（后退/前进/刷新/地址栏/标签页/设置）
            ├── layout/activity_settings.xml      设置页·竖屏（顶栏 + 列表）
            ├── layout-land/activity_settings.xml 设置页·横屏（左边栏 + 右面板）
            ├── layout/settings_top_bar.xml       顶栏（返回箭头 + 标题）
            ├── layout/settings_rows.xml          设置项列表（长条按钮）
            ├── layout/panel_zoom.xml             缩放设置面板
            ├── layout/panel_orientation.xml      屏幕方向面板
            ├── layout/panel_mouse.xml            虚拟鼠标开关面板
            ├── layout/panel_float_ball.xml       悬浮球大小/不透明度面板
            ├── layout/panel_language.xml         语言
            ├── layout/panel_appearance.xml       外观（全屏 / 顶部导航栏）
            ├── layout/panel_debug.xml            调试（WebView 测试 / 多进程开关 / 自定义 UA）
            ├── layout/activity_webview_check.xml WebView 测试页（顶栏 + 网页容器）
            ├── layout/panel_about.xml            关于 + 检查更新
            ├── drawable/bg_float_ball.xml        悬浮球的圆形背景
            ├── drawable-*/ic_settings.png        悬浮球上的齿轮图标
            ├── drawable-*/ic_arrow_back.png      顶栏的返回箭头
            ├── drawable-*/ic_mouse_cursor.png    虚拟鼠标指针（黑箭头 + 白描边）
            ├── values/bools.xml + values-land/bools.xml   单窗格/双窗格开关
            ├── values/strings.xml                文案
            ├── values/styles.xml                 Material Light 无标题栏主题
            └── mipmap-*/ic_launcher.png          五套密度启动图标（粉底白电视，屏幕里写 WEB）
```

## 三、构建

```bash
./gradlew assembleDebug      # 调试包：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # 发布包（未签名）：app/build/outputs/apk/release/app-release-unsigned.apk
```

lint 不参与构建：AGP 的 lint 会去遍历 packaging 的临时目录
（`intermediates/incremental/packageRelease/tmp/...`），该目录缺失/被清理时会抛
`NoSuchFileException`（`lintAnalyze*` 任务崩溃），属于 lint 基础设施问题。
需要跑 lint 时显式打开：`./gradlew lint -PenableLint`。
