# BindView Android Studio 插件

专为 Android 打造的视图绑定代码自动生成插件，完美适配 [`com.yndongyong.van:bindview`](https://github.com) SDK 的属性委托规范。

---

## 核心特性

1. **统一优雅的语法支持**：
   - 自动生成符合规范的属性委托代码：
     ```kotlin
     private val tvTitle: TextView by bindView(R.id.tv_title)
     ```
   - 完美适用于 Activity、Fragment、自定义 View (ViewGroup) 以及 `RecyclerView.ViewHolder`。

2. **精简现代化的 UI 交互**：
   - 保留核心命名选项：`private`、`add "m"`、`isCamelCase`。
   - 选项状态自动持久化记忆（重启 IDE 或下次打开自动恢复）。
   - 去除繁琐无用的传统模板选项，聚焦 Kotlin 属性委托。

3. **双向高效触发**：
   - **XML 布局文件中触发**：在 XML 文件中点击右键菜单 `Generate BindView` 或按 `Ctrl + I`，解析布局中的控件列表，实时预览并一键复制代码（`Copy Code`）。
   - **Kotlin 源码中触发**：在 Kotlin 类或选中的布局名处按 `Ctrl + I`，支持自动探测关联的布局 XML，除了复制代码外，还支持一键直接插入类属性（`Insert Code`），并自动添加 `import com.yndongyong.van.bindView`。

4. **灵活的数据表格与快捷操作**：
   - 支持多选、反选（`Select All` / `Select None` / `Select Invert`）。
   - 表格中支持双击直接自定义变量名称。
   - 自动递归解析 `<include layout="@layout/..."/>` 中的子布局控件。

---

## 界面概览

- **顶部控制栏**：包含 `private`、`add "m"`、`isCamelCase` 三个命名控制开关。
- **视图表格**：列出当前布局中解析出的所有包含 `android:id` 的控件（`select` / `type` / `id` / `name`）。
- **快捷操作按钮**：快速进行全选、全不选或反选。
- **实时代码预览**：随选项动态刷新生成的 `by bindView(...)` 属性声明代码。
- **操作按钮**：
  - `Copy Code`：复制代码到系统剪贴板。
  - `Insert Code`（在 Kotlin 文件中时）：直接安全插入到当前 Kotlin 类的属性区域。
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

### 快捷键
- Windows / Linux / macOS: `Ctrl + I`
- 编辑器右键上下文菜单 -> `Generate BindView`
- 代码生成菜单（`Alt + Insert` / `Cmd + N`） -> `Generate BindView`
