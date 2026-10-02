package com.yndongyong.bindview.helper

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.yndongyong.bindview.model.ViewInfo
import com.yndongyong.bindview.settings.BindViewSettings
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * 导入项数据结构，便于统一处理与单元测试
 */
data class ImportEntry(
    val pathStr: String,
    val isWildcard: Boolean,
    val wildcardPackage: String? = null,
    val importedSimpleName: String? = null
)

/**
 * 自动导包辅助工具：负责 Custom View、AndroidX 及 bindView 扩展的导包检测与自动添加，
 * 并严格规避重复导包、同包冗余导包及同名冲突。
 */
object ImportHelper {

    /**
     * 常见 Android 标准控件与其完整类名映射
     */
    val STANDARD_ANDROID_WIDGETS = mapOf(
        "View" to "android.view.View",
        "ViewGroup" to "android.view.ViewGroup",
        "TextView" to "android.widget.TextView",
        "ImageView" to "android.widget.ImageView",
        "Button" to "android.widget.Button",
        "EditText" to "android.widget.EditText",
        "ProgressBar" to "android.widget.ProgressBar",
        "CheckBox" to "android.widget.CheckBox",
        "RadioButton" to "android.widget.RadioButton",
        "RadioGroup" to "android.widget.RadioGroup",
        "ScrollView" to "android.widget.ScrollView",
        "HorizontalScrollView" to "android.widget.HorizontalScrollView",
        "FrameLayout" to "android.widget.FrameLayout",
        "LinearLayout" to "android.widget.LinearLayout",
        "RelativeLayout" to "android.widget.RelativeLayout",
        "SeekBar" to "android.widget.SeekBar",
        "Spinner" to "android.widget.Spinner",
        "Switch" to "android.widget.Switch",
        "RatingBar" to "android.widget.RatingBar",
        "ViewStub" to "android.view.ViewStub"
    )

    /**
     * 判断目标类是否需要导入（纯逻辑判断，便于单元测试）
     *
     * @param filePackage 当前 Kotlin 文件的 package
     * @param existingImports 当前文件已有的所有 import 列表
     * @param targetFqName 需要引用的目标类完整类名（例如 com.example.view.CustomView）
     * @return true 表示需要新加入 import，false 表示已有相同导入、同包或存在同名冲突
     */
    fun isImportNeeded(
        filePackage: String,
        existingImports: Collection<ImportEntry>,
        targetFqName: String
    ): Boolean {
        val trimmedTarget = targetFqName.trim()
        if (trimmedTarget.isEmpty()) return false

        val pkgName = trimmedTarget.substringBeforeLast(".", "")
        val simpleName = trimmedTarget.substringAfterLast(".")
        if (pkgName.isEmpty()) return false

        // 1. 同包检查：类与当前文件处于同一 package，无需导入
        if (filePackage.isNotEmpty() && filePackage == pkgName) {
            return false
        }

        // 2. 检查现有导入指令
        for (entry in existingImports) {
            // 2.1 精确匹配导入：如已存在 import com.example.view.CustomView
            if (entry.pathStr == trimmedTarget) {
                return false
            }

            // 2.2 通配符导入：如已存在 import com.example.view.*
            if (entry.isWildcard) {
                val wildcardPkg = entry.wildcardPackage ?: entry.pathStr.removeSuffix(".*")
                if (wildcardPkg == pkgName) {
                    return false
                }
            } else if (entry.pathStr == "$pkgName.*") {
                return false
            }

            // 2.3 冲突检查：如果已有同名简短名称指向不同包的类，避免引入歧义或错误
            if (entry.importedSimpleName == simpleName && entry.pathStr != trimmedTarget) {
                return false
            }
        }

        return true
    }

    /**
     * 针对真实 KtFile 对象判断目标类是否需要导入
     */
    fun isImportNeeded(ktFile: KtFile, targetFqName: String): Boolean {
        val filePackage = ktFile.packageFqName.asString()
        val entries = ktFile.importDirectives.mapNotNull { directive ->
            val path = directive.importPath ?: return@mapNotNull null
            ImportEntry(
                pathStr = path.pathStr,
                isWildcard = path.isAllUnder || path.pathStr.endsWith(".*"),
                wildcardPackage = if (path.isAllUnder) path.fqName.asString() else path.pathStr.removeSuffix(".*"),
                importedSimpleName = directive.aliasName ?: path.importedName?.asString()
            )
        }
        return isImportNeeded(filePackage, entries, targetFqName)
    }

    /**
     * 为选中的视图列表添加所需的导入（包含 Custom View、AndroidX 与 bindView 扩展）
     * 严格检查重复导入
     */
    fun addImportsIfNeeded(
        project: Project,
        ktFile: KtFile,
        psiFactory: KtPsiFactory,
        selectedViews: List<ViewInfo>,
        includeBindView: Boolean
    ) {
        val importsToAdd = mutableListOf<String>()
        val addedImports = mutableSetOf<String>()

        // 1. 类属性模式下：添加 bindView 扩展函数导入
        if (includeBindView) {
            val bindViewImport = BindViewSettings.getInstance().getEffectiveImportPath()
            if (isImportNeeded(ktFile, bindViewImport)) {
                importsToAdd.add(bindViewImport)
                addedImports.add(bindViewImport)
            }
        }

        // 2. 收集 Custom View / AndroidX 以及标准控件所需的类全路径
        for (viewInfo in selectedViews) {
            val element = viewInfo.element
            val fullClassName = element.viewNameFull
                ?: STANDARD_ANDROID_WIDGETS[element.viewName]
                ?: continue

            // 批次内去重：多个控件属于同一个类时只添加一次
            if (!addedImports.add(fullClassName)) {
                continue
            }

            if (isImportNeeded(ktFile, fullClassName)) {
                importsToAdd.add(fullClassName)
            }
        }

        if (importsToAdd.isEmpty()) {
            return
        }

        // 3. 执行写入导入语句
        val importList = ktFile.importList
        val codeStyleManager = CodeStyleManager.getInstance(project)

        for (importPath in importsToAdd) {
            val importDirective = psiFactory.createImportDirective(ImportPath(FqName(importPath), false))
            if (importList != null) {
                importList.add(importDirective)
            } else {
                val pkgDirective = ktFile.packageDirective
                if (pkgDirective != null && pkgDirective.text.isNotEmpty()) {
                    ktFile.addAfter(psiFactory.createNewLine(2), pkgDirective)
                    ktFile.addAfter(importDirective, pkgDirective)
                } else {
                    val added = ktFile.addBefore(importDirective, ktFile.firstChild)
                    ktFile.addAfter(psiFactory.createNewLine(1), added)
                }
            }
        }

        // 格式化导入区域代码风格
        if (importList != null) {
            codeStyleManager.reformat(importList)
        }
    }
}
