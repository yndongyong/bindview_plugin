# BindView Android Studio 插件

专为 Android 打造的视图绑定代码自动生成插件，完美适配 [`com.yndongyong.van:bindview`](https://github.com) SDK 的属性委托规范，同时提供高性能局部变量 `findViewById` 代码生成方案。

---

## 核心特性

1. **统一优雅的类属性委托**：
   - 自动生成符合规范的 Kotlin 属性委托代码：
     ```kotlin
     private val tvTitle: TextView by bindView(R.id.tv_title)
     ```
   - 完美适用于 Activity、Fragment、自定义 View (ViewGroup) 以及 `RecyclerView.ViewHolder`。

2. **高性能局部变量模式（Local Variable / findViewById）**：
   - **避免高频 GC 开销**：在诸如 RecyclerView 的列表项绑定闭包（如 `bindHor2 { ... }`）或局部方法体中，局部使用属性委托会产生大量短期分配的委托对象；插件提供轻量的 `findViewById` 模式：
     ```kotlin
     val coverImage = this.findViewById<AsyncImageView>(R.id.iv_scenic_live_play_item_cover)
     ```
   - **动态 Receiver 前缀与快捷预设**：支持自定义前缀输入框（如 `this`、`rootView`、`itemView`、`view` 或留空直接调用），并提供 `[this]`、`[(无前缀)]`、`[view]`、`[rootView]`、`[itemView]` 等快捷标签一键切换。
   - **智能上下文探测**：若光标处于函数体或 Lambda 闭包中，呼出插件时自动切换至局部变量模式，并智能推断适用的前缀。

3. **双向高效触发与智能按钮控制**：
   - **XML 布局文件中触发**：在 XML 文件中点击右键菜单 `Generate BindView` 或按 `Ctrl + I`，解析布局中的控件列表，实时预览并一键复制代码（`Copy Code`）。
   - **Kotlin 源码中触发**：在 Kotlin 类或选中的布局名处按 `Ctrl + I`，支持自动探测关联的布局 XML。
   - **按钮情境感知**：
     - **类属性模式**：提供 `Insert Code` 按钮，直接安全插入到当前 Kotlin 类的属性区域，**并在插入的代码块上下自动保留空行**，同时自动补充 `import <配置的导包路径>`。
     - **局部变量模式**：**自动隐藏 `Insert Code` 按钮**，并以 `Copy Code` 为默认主操作按钮（按回车即可快速复制），避免误将局部变量插入到类属性中，聚焦一键复制并粘贴到当前闭包。

4. **宿主项目自定义导包配置**：
   - 导包路径完全可配置，默认值为 `com.yndongyong.van.bindView`。
   - 方便由宿主项目提供自定义扩展或工具类实现（方法名统一规整为 `bindView`）。
   - 可以在 `Settings` -> `Tools` -> `BindView` 中统一配置，或在生成弹窗顶部即点即改（自动记忆并持久化）。

5. **精简现代化的 UI 交互**：
   - 保留核心命名选项：`private`、`add "m"`、`isCamelCase`。
   - 选项状态自动持久化记忆（重启 IDE 或下次打开自动恢复）。
   - 支持多选、反选（`Select All` / `Select None` / `Select Invert`）。
   - 表格支持双击快速自定义变量名。
   - 自动递归解析 `<include layout="@layout/..."/>` 中的子布局控件。

---

## 界面概览

- **顶部控制栏**：
  - **命名与选项**：包含 `private`、`add "m"`、`isCamelCase` 三个命名控制开关。
  - **导包路径配置**：实时展示当前配置的 `import` 路径，点击即可快速修改。
- **模式切换栏**：
  - 单选切换 `Class Property (by bindView)` 与 `Local Variable (findViewById)`。
  - 选中局部变量模式时展开前缀控制栏（自定义输入框 + `this` / `(无前缀)` / `view` / `rootView` / `itemView` 预设标签）。
- **视图表格**：列出当前布局中解析出的所有包含 `android:id` 的控件（`select` / `type` / `id` / `name`），双击名称单元格可快速改名。
- **快捷操作按钮**：快速进行全选、全不选或反选。
- **实时代码预览**：随模式和选项动态刷新生成的 Kotlin 代码。
- **操作按钮**：
  - `Copy Code`：复制代码到系统剪贴板（局部变量模式下的默认高亮操作）。
  - `Insert Code`（仅类属性模式且处于 Kotlin 文件中时可见）：直接安全插入到当前 Kotlin 类属性区域，并在插入代码块上下自动补齐空行。
  - `Cancel`：关闭窗口。

---

## 安装与使用

### 安装插件
1. 编译打包：
   ```bash
   ./gradlew buildPlugin
   ```
2. 产物路径：`build/distributions/bindview_plugin-1.0.0.zip`
3. 在 Android Studio 中打开 `Settings`（或 `Preferences`） -> `Plugins` -> 点击齿轮图标 -> 选择 `Install Plugin from Disk...`，选择生成的 zip 包即可。

### 快捷键与入口
- **全局快捷键**：Windows / Linux / macOS 统一为 `Ctrl + I`
- **右键上下文菜单**：编辑器内右键 -> `Generate BindView`
- **代码生成菜单**：`Alt + Insert` (macOS: `Cmd + N`) -> `Generate BindView`

