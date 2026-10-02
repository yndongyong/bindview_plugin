package com.yndongyong.bindview.helper

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.yndongyong.bindview.model.ViewInfo
import com.yndongyong.bindview.settings.BindViewSettings
import com.yndongyong.bindview.utils.getKotlinClass
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * 辅助将生成的 bindView 属性或局部变量写入 Kotlin 类文件
 */
class KtFileWriteHelper(
    private val project: Project,
    private val psiFile: PsiFile,
    private val offset: Int,
    private val viewInfos: List<ViewInfo>,
    private val addM: Boolean,
    private val isPrivate: Boolean,
    private val isCamelCase: Boolean,
    private val isLocalVariable: Boolean = false,
    private val prefix: String = ""
) {

    fun execute(): Boolean {
        if (psiFile !is KtFile) {
            Messages.showErrorDialog(project, "Target file is not a Kotlin file", "Error")
            return false
        }

        val selectedViews = viewInfos.filter { it.isChecked }
        if (selectedViews.isEmpty()) {
            Messages.showWarningDialog(project, "No views selected", "Warning")
            return false
        }

        if (isLocalVariable) {
            // 写入局部变量 findViewById
            return executeWriteLocalVariables(psiFile, selectedViews)
        } else {
            // 写入类属性 by bindView
            return executeWriteProperties(psiFile, selectedViews)
        }
    }

    private fun executeWriteProperties(ktFile: KtFile, selectedViews: List<ViewInfo>): Boolean {
        val ktClass = ktFile.getKotlinClass(offset)
        if (ktClass == null) {
            Messages.showErrorDialog(project, "Cannot find Kotlin class at current position", "Error")
            return false
        }

        val body = ktClass.body
        if (body == null) {
            Messages.showErrorDialog(project, "Kotlin class body not found", "Error")
            return false
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val psiFactory = KtPsiFactory(project)

            // 1. 添加自动导入
            addImportIfNeeded(ktFile, psiFactory)

            // 2. 写入类属性
            val existingProperties = body.declarations.filterIsInstance<KtProperty>()
            val existingNames = existingProperties.mapNotNull { it.name }.toSet()

            val newPropertyList = selectedViews.map { viewInfo ->
                psiFactory.createProperty(viewInfo.getBindViewCode(addM, isPrivate, isCamelCase))
            }.filter { !existingNames.contains(it.name) }

            if (newPropertyList.isEmpty()) {
                return@runWriteCommandAction
            }

            // 确定插入锚点（有属性则追加在最后一个属性后面，无属性则在 '{' 后面）
            val anchor: PsiElement = existingProperties.lastOrNull() ?: body.lBrace ?: body

            // 上方增加空行（两个换行）
            var current: PsiElement = body.addAfter(psiFactory.createNewLine(2), anchor)

            // 依次插入各个属性，属性之间单换行
            val insertedProperties = mutableListOf<KtProperty>()
            for (prop in newPropertyList) {
                val added = body.addAfter(prop, current) as KtProperty
                insertedProperties.add(added)
                current = body.addAfter(psiFactory.createNewLine(1), added)
            }

            // 下方增加空行（再加一个换行）
            body.addAfter(psiFactory.createNewLine(1), current)

            // 自动代码格式化与缩进对齐
            val codeStyleManager = CodeStyleManager.getInstance(project)
            for (prop in insertedProperties) {
                codeStyleManager.reformat(prop)
            }
        }

        return true
    }

    private fun executeWriteLocalVariables(ktFile: KtFile, selectedViews: List<ViewInfo>): Boolean {
        WriteCommandAction.runWriteCommandAction(project) {
            val psiFactory = KtPsiFactory(project)

            // 找到光标所在处的代码块
            var elementAtOffset = ktFile.findElementAt(offset)
            var block: KtBlockExpression? = null
            var anchorStatement: PsiElement? = null

            var curr = elementAtOffset
            while (curr != null) {
                if (curr.parent is KtBlockExpression) {
                    block = curr.parent as KtBlockExpression
                    anchorStatement = curr
                    break
                }
                curr = curr.parent
            }

            if (block == null) {
                // 回退到按函数/lambda 体找 block
                var p = elementAtOffset
                while (p != null) {
                    if (p is KtBlockExpression) {
                        block = p
                        anchorStatement = p.statements.lastOrNull() ?: p.lBrace
                        break
                    }
                    p = p.parent
                }
            }

            if (block == null) {
                Messages.showErrorDialog(project, "Cannot find method or lambda body to insert local variables", "Error")
                return@runWriteCommandAction
            }

            val newPropertyList = selectedViews.map { viewInfo ->
                psiFactory.createProperty(viewInfo.getLocalVariableCode(addM, isCamelCase, prefix))
            }

            if (newPropertyList.isEmpty()) return@runWriteCommandAction

            // 插入位置：优先在 anchorStatement 之后插入
            val anchor = anchorStatement ?: block.lBrace ?: block

            // 上方增加空行
            var current: PsiElement = block.addAfter(psiFactory.createNewLine(2), anchor)

            val insertedProperties = mutableListOf<KtProperty>()
            for (prop in newPropertyList) {
                val added = block.addAfter(prop, current) as KtProperty
                insertedProperties.add(added)
                current = block.addAfter(psiFactory.createNewLine(1), added)
            }

            // 下方增加空行
            block.addAfter(psiFactory.createNewLine(1), current)

            // 自动缩进对齐格式化
            val codeStyleManager = CodeStyleManager.getInstance(project)
            for (prop in insertedProperties) {
                codeStyleManager.reformat(prop)
            }
        }

        return true
    }

    private fun addImportIfNeeded(ktFile: KtFile, psiFactory: KtPsiFactory) {
        val targetImport = BindViewSettings.getInstance().getEffectiveImportPath()
        val wildcardImport = targetImport.substringBeforeLast(".") + ".*"

        val hasImport = ktFile.importDirectives.any { directive ->
            val path = directive.importPath?.pathStr
            path == targetImport || path == wildcardImport
        }
        if (!hasImport) {
            val importDirective = psiFactory.createImportDirective(ImportPath(FqName(targetImport), false))
            val importList = ktFile.importList
            if (importList != null) {
                importList.add(importDirective)
            } else {
                ktFile.packageDirective?.let { pkg ->
                    ktFile.addAfter(psiFactory.createNewLine(2), pkg)
                    ktFile.addAfter(importDirective, pkg)
                }
            }
        }
    }
}
