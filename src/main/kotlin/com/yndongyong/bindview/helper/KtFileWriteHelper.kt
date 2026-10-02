package com.yndongyong.bindview.helper

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.yndongyong.bindview.model.ViewInfo
import com.yndongyong.bindview.utils.getKotlinClass
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * 辅助将生成的 bindView 属性写入 Kotlin 类文件
 */
class KtFileWriteHelper(
    private val project: Project,
    private val psiFile: PsiFile,
    private val offset: Int,
    private val viewInfos: List<ViewInfo>,
    private val addM: Boolean,
    private val isPrivate: Boolean,
    private val isCamelCase: Boolean
) {

    fun execute(): Boolean {
        if (psiFile !is KtFile) {
            Messages.showErrorDialog(project, "Target file is not a Kotlin file", "Error")
            return false
        }

        val ktClass = psiFile.getKotlinClass(offset)
        if (ktClass == null) {
            Messages.showErrorDialog(project, "Cannot find Kotlin class at current position", "Error")
            return false
        }

        val body = ktClass.body
        if (body == null) {
            Messages.showErrorDialog(project, "Kotlin class body not found", "Error")
            return false
        }

        val selectedViews = viewInfos.filter { it.isChecked }
        if (selectedViews.isEmpty()) {
            Messages.showWarningDialog(project, "No views selected", "Warning")
            return false
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val psiFactory = KtPsiFactory(project)

            // 1. 添加自动导入 com.yndongyong.van.bindview.bindView
            addImportIfNeeded(psiFile, psiFactory)

            // 2. 写入类属性
            val existingProperties = body.declarations.filterIsInstance<KtProperty>()
            val existingNames = existingProperties.mapNotNull { it.name }.toSet()

            val newPropertyList = selectedViews.map { viewInfo ->
                psiFactory.createProperty(viewInfo.getBindViewCode(addM, isPrivate, isCamelCase))
            }.filter { !existingNames.contains(it.name) }

            if (newPropertyList.isNotEmpty()) {
                val firstProperty: PsiElement? = existingProperties.firstOrNull()
                val toAfter = (firstProperty == null)
                var e: PsiElement = firstProperty ?: body.lBrace ?: return@runWriteCommandAction

                var index = 0
                for (i in newPropertyList.indices) {
                    val p = newPropertyList[i]
                    if (toAfter) {
                        e = body.addAfter(p, e)
                        body.addBefore(psiFactory.createNewLine(2), e)
                    } else {
                        e = body.addBefore(p, e)
                    }
                    index = i + 1
                    break
                }

                for (i in index until newPropertyList.size) {
                    val p = newPropertyList[i]
                    e = body.addAfter(p, e)
                }
            }
        }

        return true
    }

    private fun addImportIfNeeded(ktFile: KtFile, psiFactory: KtPsiFactory) {
        val targetImport = "com.yndongyong.van.bindview.bindView"
        val hasImport = ktFile.importDirectives.any { directive ->
            directive.importPath?.pathStr == targetImport || directive.importPath?.pathStr == "com.yndongyong.van.bindview.*"
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
