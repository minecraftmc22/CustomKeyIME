# 自定义按键输入法（CustomKeyIME）

一个 Android 输入法（IME）应用。核心功能：**自定义快捷按键** —— 新建一个按键并设置内容（例如 `12`），保存后在任意输入框中点击该按键，键盘会：

1. 立即输入 `12`
2. **自动按下回车（Enter）** —— 在微信、QQ 等聊天框中即等于自动发送

同时具备一个可用的普通键盘：QWERTY + **中/英切换**（中文为内置简版拼音输入）。

纯 Java 实现，无任何第三方依赖，可直接用 Android Studio 打开构建。

## 功能

### 键盘（4 个标签页 + 设置）
- **「键盘」标签**：普通 QWERTY 键盘
  - **中/英切换**：中文模式内置简版拼音输入法（含 4.9 万条拼音词库、切分候选、边打边提示）
  - 数字/符号面板（`?123` 切换）、大小写、退格、空格、回车、中/英文标点
  - **退格长按 = 快速删除**；**长按后上滑松手 = 删除全部文本**
- **「自定义」标签**：整屏网格面板，按分组排列自定义按键，一点即输入（可自动发送）
- **「剪贴板」标签**：剪贴板历史（自动记录 + 复制当前选中），点击粘贴、长按删除
- **「记忆」标签**：按使用频率记录常用内容，点击再次输入、长按删除
- **⚙ 设置**：键盘高度 / 背景透明度 / 背景明暗，保存即时生效

### 主题
- **Material You 3**：Android 12+ 读取系统动态色，低版本回退 M3 深色基线
- 键盘背景支持**透明度**与**明暗**调节

### 自定义按键管理（独立界面）
- ✅ **分组**：新建 / 重命名 / 删除分组，分组顺序即键盘上的显示顺序
- ✅ **移动**：每个按键支持 ▲ 上移 / ▼ 下移（组内排序），以及「移动到分组」跨组移动
- ✅ 新建 / 编辑 / 删除按键，内容任意（文字、数字、符号、常用短语）
- ✅ 每个按键可独立开关「输入后自动按回车（发送）」
- ✅ 保存立即生效：设置里改完，键盘实时刷新
- ✅ 输入法启用状态实时检测 + 一键跳转启用 / 切换

## 使用步骤

1. 用 Android Studio 打开 `CustomKeyIME` 文件夹，等待 Gradle 同步完成
   （或直接下载 Actions 构建的 APK 安装）
2. 打开 App，看到顶部状态框显示「❌ 尚未启用」
3. 点「去系统设置启用」→ 系统设置 → 语言和输入法 → 虚拟键盘 / 管理键盘 → 开启「自定义按键输入法」
4. 返回 App，点「切换为当前输入法」，在弹出列表里选择「自定义按键输入法」
5. 打开微信等任意聊天框：
   - 点键盘顶部「自定义按键」标签 → 一键发送预设内容 🎉
   - 或留在「键盘」标签正常打字，点左下「中/英」切换中英文

## 工程结构

```
CustomKeyIME/
├── app/src/main/
│   ├── AndroidManifest.xml              # 注册 IME 服务与启动 Activity
│   ├── assets/
│   │   └── pinyin.txt                   # 拼音词库（约 0.95 MB，4.9 万拼音键）
│   ├── java/com/minecraftmc22/ckime/
│   │   ├── ImeService.java              # 输入法服务（双模式键盘 + 中英切换 + 自动回车）
│   │   ├── PinyinEngine.java            # 简版拼音引擎（精确/切分/前缀三种候选）
│   │   ├── KeysActivity.java            # 按键管理界面（分组 / 移动 / 增删改）
│   │   ├── KeyGroup.java                # 分组数据模型
│   │   ├── CustomKey.java               # 按键数据模型
│   │   └── KeyStore.java                # SharedPreferences + JSON 持久化（含老数据迁移）
│   └── res/
│       ├── xml/method.xml               # IME 声明
│       ├── layout/                      # 按键管理页 / 分组头 / 按键项
│       ├── drawable/                    # 按键与标签页背景
│       └── values/                      # 字符串、深色主题
```

## 核心实现

**① 输入后自动回车（发送）**

```java
// ImeService.java → fireCustomKey()
InputConnection ic = getCurrentInputConnection();
ic.commitText("12", 1);                        // 1. 输入内容
sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);   // 2. 自动按回车 → 聊天框即发送
```

**② 拼音候选（三种策略叠加）**

```java
// PinyinEngine.java → candidates()
1. 精确匹配        nihao      -> 你好
2. 全串切分        woxihuanni -> 我 + 喜欢 + 你
3. 前缀匹配        nih        -> 你好 / 你 / 呢 ...
```

词库由 jieba 词频表 + pypinyin 生成（多音字按词组规则处理，如 `银行 → yinhang`、`重庆 → chongqing`），
生成脚本见 `tools/gen_pinyin.py`。

## 环境要求

- Android Studio Hedgehog 及以上（AGP 8.2.2 / Gradle 8.2 / JDK 17）
- minSdk 21（Android 5.0+），targetSdk 34
- 包名 / applicationId：`com.minecraftmc22.ckime`

## GitHub Actions 自动打包

仓库已内置 `.github/workflows/build.yml`：

- 每次 push 到 `main`（或手动触发 workflow_dispatch）自动构建 **debug APK**
- 推送 `v*` 标签（如 `v1.4`）时会额外创建 GitHub Release 并附上 APK
- 构建完成后在仓库 **Actions → 对应运行 → Artifacts** 下载 APK（是 zip，需解压后安装）

```bash
git tag v1.4 && git push origin v1.4   # 触发 Release 打包
```

## 常见问题

- **系统设置里找不到本输入法？** 确认装的是最新版；`AndroidManifest.xml` 中 IME 服务的
  action 必须是 `android.view.InputMethod`（不是权限名 `BIND_INPUT_METHOD`）。
- **有的 App 里回车是换行不是发送？** 这取决于该 App 对回车键的行为定义，本输入法发送的回车与手动按回车完全等价。
- **中文候选不准/词太少？** 简版词库优先保证常用词；可调整 `tools/gen_pinyin.py` 里的
  `MAX_WORDS` / `MAX_PER_KEY` 重新生成。
- **想改按键高度/颜色？** 高度在 `ImeService.java` 的 `dp(48)` / `dp(52)`，颜色在 `res/drawable/key_bg*.xml`。
