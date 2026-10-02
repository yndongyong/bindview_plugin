package com.yndongyong.bindview.helper

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.yndongyong.bindview.model.ViewInfo
import com.yndongyong.bindview.utils.getKotlinClass
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

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

            // 1. 添加自动导入（包含 bindView 与 Custom View / AndroidX 导包，严格防重复）
            ImportHelper.addImportsIfNeeded(project, ktFile, psiFactory, selectedViews, includeBindView = true)

            // 2. 写入类属性
            val declarations = ktClass.declarations
            val existingProperties = declarations.filterIsInstance<KtProperty>()
            val existingNames = existingProperties.mapNotNull { it.name }.toSet()

            val newPropertyList = selectedViews.map { viewInfo ->
                psiFactory.createProperty(viewInfo.getBindViewCode(addM, isPrivate, isCamelCase))
            }.filter { !existingNames.contains(it.name) }

            if (newPropertyList.isEmpty()) {
                return@runWriteCommandAction
            }

            val insertedProperties = mutableListOf<KtProperty>()
            var anchor: org.jetbrains.kotlin.psi.KtDeclaration? = existingProperties.lastOrNull()

            if (anchor != null) {
                // 2.1 类中已有属性：依次追加在最后一个已有属性之后
                for (prop in newPropertyList) {
                    val added = ktClass.addDeclarationAfter(prop, anchor!!) as KtProperty
                    insertedProperties.add(added)
                    anchor = added
                }
            } else {
                // 2.2 类中尚无属性：优先插入在类体开头的首个声明之前（如在 onCreate 函数之前）
                val firstDecl = declarations.firstOrNull()
                if (firstDecl != null) {
                    val firstProp = newPropertyList.first()
                    val firstAdded = ktClass.addDeclarationBefore(firstProp, firstDecl) as KtProperty
                    insertedProperties.add(firstAdded)
                    anchor = firstAdded

                    for (i in 1 until newPropertyList.size) {
                        val prop = newPropertyList[i]
                        val added = ktClass.addDeclarationAfter(prop, anchor!!) as KtProperty
                        insertedProperties.add(added)
                        anchor = added
                    }
                } else {
                    // 2.3 类体完全为空：直接添加到类中
                    for (prop in newPropertyList) {
                        val added = if (anchor == null) {
                            ktClass.addDeclaration(prop) as KtProperty
                        } else {
                            ktClass.addDeclarationAfter(prop, anchor!!) as KtProperty
                        }
                        insertedProperties.add(added)
                        anchor = added
                    }
                }
            }

            // 3. 上下各保留一行空行
            if (insertedProperties.isNotEmpty()) {
                val firstInserted = insertedProperties.first()
                val lastInserted = insertedProperties.last()
                val classBody = ktClass.body

                if (classBody != null) {
                    val prev = firstInserted.prevSibling
                    if (prev is com.intellij.psi.PsiWhiteSpace) {
                        val newlines = prev.text.count { it == '\n' }
                        if (newlines < 2) {
                            classBody.addBefore(psiFactory.createNewLine(2 - newlines), firstInserted)
                        }
                    } else {
                        classBody.addBefore(psiFactory.createNewLine(2), firstInserted)
                    }

                    val next = lastInserted.nextSibling
                    if (next is com.intellij.psi.PsiWhiteSpace) {
                        val newlines = next.text.count { it == '\n' }
                        if (newlines < 2) {
                            classBody.addAfter(psiFactory.createNewLine(2 - newlines), lastInserted)
                        }
                    } else {
                        classBody.addAfter(psiFactory.createNewLine(2), lastInserted)
                    }
                }
            }

            // 4. 自动代码格式化与缩进对齐
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

            // 1. 添加自动导入（针对局部变量中使用的 Custom View / AndroidX 类，严格防重复）
            ImportHelper.addImportsIfNeeded(project, ktFile, psiFactory, selectedViews, includeBindView = false)

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

            // 插入位置：优先在 anchorStatement 之后插入，没有则在 block.lBrace 之后
            var anchor: PsiElement = anchorStatement ?: block.lBrace ?: block

            val insertedProperties = mutableListOf<KtProperty>()
            for (prop in newPropertyList) {
                val added = block.addAfter(prop, anchor) as KtProperty
                insertedProperties.add(added)
                anchor = added
            }

            // 3. 上下各保留一行空行
            if (insertedProperties.isNotEmpty()) {
                val firstInserted = insertedProperties.first()
                val lastInserted = insertedProperties.last()

                val prev = firstInserted.prevSibling
                if (prev is com.intellij.psi.PsiWhiteSpace) {
                    val newlines = prev.text.count { it == '\n' }
                    if (newlines < 2) {
                        block.addBefore(psiFactory.createNewLine(2 - newlines), firstInserted)
                    }
                } else {
                    block.addBefore(psiFactory.createNewLine(2), firstInserted)
                }

                val next = lastInserted.nextSibling
                if (next is com.intellij.psi.PsiWhiteSpace) {
                    val newlines = next.text.count { it == '\n' }
                    if (newlines < 2) {
                        block.addAfter(psiFactory.createNewLine(2 - newlines), lastInserted)
                    }
                } else {
                    block.addAfter(psiFactory.createNewLine(2), lastInserted)
                }
            }

            // 4. 自动缩进对齐格式化
            val codeStyleManager = CodeStyleManager.getInstance(project)
            for (prop in insertedProperties) {
                codeStyleManager.reformat(prop)
            }
        }

        return true
    }
}
