# 自定义按键输入法（CustomKeyIME）

一个 Android 输入法（IME）应用。核心功能：**自定义快捷按键** —— 新建一个按键并设置内容（例如 `12`），保存后在任意输入框中点击该按键，键盘会：

1. 立即输入 `12`
2. **自动按下回车（Enter）** —— 在微信、QQ 等聊天框中即等于自动发送

同时具备一个可用的普通键盘：QWERTY + **中/英切换**（中文为内置简版拼音输入）。

纯 Java 实现，无任何第三方依赖，可直接用 Android Studio 打开构建。

## 功能

### 键盘（4 个标签页 + 设置）
- **「键盘」标签**：普通 QWERTY 键盘
  - **中/英切换**：中文模式内置简版拼音输入法（含 6.1 万拼音键词库、切分候选、边打边提示）
  - **首字母简拼联想**：中文模式下输入每个字的声母即可联想，如 `nh` → 你好、`xhs` → 新华社
  - **回车直接上屏所打字母**：中文模式有未转换的拼音时，按回车输出的是你打的字母本身（不是首选词），随后照常回车
  - **英文模式词语联想**：边打边在候选栏给出前缀联想（`hel` → hello / help），点击即补全整词
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
- ✅ **分组折叠**：点击分组栏（或 ⋯ 菜单 →「折叠/展开本组」）即可收起该组按键，
  状态会保存，键盘里的「自定义」面板同步生效（分组名前 ▸ 折叠 / ▾ 展开）
- ✅ **移动**：每个按键支持 ▲ 上移 / ▼ 下移（组内排序），以及「移动到分组」跨组移动
- ✅ **导出**：「⤓ 导出自定义按键」支持三种方式
  - 保存为 **JSON 文件**（系统文件选择器，无需存储权限，可用于备份 / 迁移）
  - **分享为文本**（发微信 / QQ 等，带分组与自动回车标记）
  - **复制 JSON 到剪贴板**
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
│   │   ├── pinyin.txt                   # 拼音词库（约 1.23 MB，6.1 万拼音键，含首字母简拼键）
│   │   └── english_words.txt            # 英文词频表（1150 个常用词，按词频降序）
│   ├── java/com/minecraftmc22/ckime/
│   │   ├── ImeService.java              # 输入法服务（双模式键盘 + 中英切换 + 自动回车）
│   │   ├── PinyinEngine.java            # 简版拼音引擎（精确/切分/前缀三种候选）
│   │   ├── EnglishEngine.java           # 英文联想引擎（前缀匹配 + 词频排序）
│   │   ├── KeyBackup.java               # 自定义按键导出（JSON / 文本）
│   │   ├── KeysActivity.java            # 按键管理界面（分组 / 折叠 / 移动 / 增删改 / 导出）
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

**② 拼音候选（三种策略叠加 + 首字母简拼）**

```java
// PinyinEngine.java → candidates()
1. 精确匹配        nihao      -> 你好
2. 全串切分        woxihuanni -> 我 + 喜欢 + 你
3. 前缀匹配        nih        -> 你好 / 你 / 呢 ...
4. 首字母简拼      nh         -> 你好 / 南海 / 女孩 ...（词库中预生成缩写键）
```

**③ 回车 = 上屏所打字母（中文模式）**

```java
// ImeService.java → onEnter()
if (chinese && !pinyinBuffer.isEmpty()) {
    commitRawPinyin();                            // 输出 "nihao"，而不是首选词「你好」
    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);  // 随后照常回车
    return;
}
```

**④ 英文词语联想**

```java
// ImeService.java → updateCandidates() + EnglishEngine.suggest()
// 英文模式每个字母即时上屏，同时用 englishBuffer 跟踪当前词；
// 候选栏给出前缀匹配（hel -> hello / help / held ...），点击后用完整词替换已输入部分
```

词库由 jieba 词频表 + pypinyin 生成（多音字按词组规则处理，如 `银行 → yinhang`、`重庆 → chongqing`，
并对常用口语词手动加权，避免新闻语料词频导致简拼候选排序靠后），
生成脚本见 `tools/gen_pinyin.py`；英文词表见 `app/src/main/assets/english_words.txt`。

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
git tag v1.7 && git push origin v1.7   # 触发 Release 打包
```

## 常见问题

- **系统设置里找不到本输入法？** 确认装的是最新版；`AndroidManifest.xml` 中 IME 服务的
  action 必须是 `android.view.InputMethod`（不是权限名 `BIND_INPUT_METHOD`）。
- **有的 App 里回车是换行不是发送？** 这取决于该 App 对回车键的行为定义，本输入法发送的回车与手动按回车完全等价。
- **中文候选不准/词太少？** 简版词库优先保证常用词；可调整 `tools/gen_pinyin.py` 里的
  `MAX_WORDS` / `MAX_PER_KEY` / `ABBREV_MAX_SYL` 重新生成（`python tools/gen_pinyin.py`）。
- **英文联想词太少？** 词表就是 `app/src/main/assets/english_words.txt`，按词频降序、每行一个词，
  直接增删即可（越靠前越优先出现在候选栏）。
- **想改按键高度/颜色？** 高度在 `ImeService.java` 的 `dp(48)` / `dp(52)`，颜色在 `res/drawable/key_bg*.xml`。
