# 自定义按键输入法（CustomKeyIME）

一个 Android 输入法（IME）应用。核心功能：**自定义快捷按键** —— 新建一个按键并设置内容（例如 `12`），保存后在任意输入框中点击该按键，键盘会：

1. 立即输入 `12`
2. **自动按下回车（Enter）** —— 在微信、QQ 等聊天框中即等于自动发送

纯 Java 实现，无任何第三方依赖，可直接用 Android Studio 打开构建。

## 功能

- ✅ 自定义按键：内容任意（文字、数字、符号、表情、常用短语均可），支持多个
- ✅ 自动回车：每个按键可独立开关「输入后自动按回车（发送）」
- ✅ 保存立即生效：设置里保存后，键盘顶部的按键行实时刷新
- ✅ 完整键盘：QWERTY 字母面板 + 数字符号面板（?123 切换）、大写、退格、空格、回车
- ✅ 自定义按键行可横向滚动，数量不限
- ✅ 深色主题界面

## 使用步骤

1. 用 Android Studio 打开 `CustomKeyIME` 文件夹，等待 Gradle 同步完成
2. 连接手机（开启 USB 调试），点击 Run 安装；或 Build APK 后自行安装
   （`app/build/outputs/apk/debug/app-debug.apk`）
3. 打开 App，点击「＋ 新建自定义按键」，输入内容如 `12`，勾选「输入后自动按回车」，保存
4. 点击「去系统设置启用本输入法」，在 系统设置 → 语言和输入法 → 虚拟键盘中
   开启「自定义按键输入法」，并将其设为当前输入法
5. 打开微信等任意聊天，键盘顶部第一行即你的自定义按键，点击 → 自动输入并发送 🎉

## 工程结构

```
CustomKeyIME/
├── app/src/main/
│   ├── AndroidManifest.xml            # 注册 IME 服务与启动 Activity
│   ├── java/com/minecraftmc22/CKIME/
│   │   ├── ImeService.java            # 输入法服务（键盘 + 自定义按键 + 自动回车）
│   │   ├── SettingsActivity.java      # 按键管理界面（新建/编辑/删除）
│   │   ├── CustomKey.java             # 按键数据模型
│   │   └── KeyStore.java              # SharedPreferences + JSON 持久化
│   └── res/
│       ├── xml/method.xml             # IME 声明
│       ├── layout/                    # 设置页、列表项布局
│       ├── drawable/                  # 按键背景（普通/功能/自定义）
│       └── values/                    # 字符串、深色主题
```

## 核心实现（自动回车）

```java
// ImeService.java → fireCustomKey()
InputConnection ic = getCurrentInputConnection();
ic.commitText("12", 1);                        // 1. 输入内容
sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);   // 2. 自动按回车 → 聊天框即发送
```

## 环境要求

- Android Studio Hedgehog 及以上（AGP 8.2.2 / Gradle 8.2 / JDK 17）
- minSdk 21（Android 5.0+），targetSdk 34
- 包名 / applicationId：`com.minecraftmc22.CKIME`

## GitHub Actions 自动打包

仓库已内置 `.github/workflows/build.yml`：

- 每次 push 到 `main`（或手动触发 workflow_dispatch）自动构建 **debug APK**
- 推送 `v*` 标签（如 `v1.0`）时会额外创建 GitHub Release 并附上 APK
- 构建完成后在仓库 **Actions → 对应运行 → Artifacts** 下载 APK

```bash
git tag v1.0 && git push origin v1.0   # 触发 Release 打包
```

## 常见问题

- **键盘里找不到自定义按键？** 按键显示在键盘最顶上一行（青蓝色），可左右滚动；或点「＋ 设置」确认已保存。
- **有的 App 里回车是换行不是发送？** 这取决于该 App 对回车键的行为定义，本输入法发送的回车与手动按回车完全等价。
- **想改按键高度/颜色？** 高度在 `ImeService.java` 的 `lp()` 方法（`dp(48)`），颜色在 `res/drawable/key_bg*.xml`。
