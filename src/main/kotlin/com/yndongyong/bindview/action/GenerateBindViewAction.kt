package com.yndongyong.bindview.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.yndongyong.bindview.ui.BindViewDialog
import com.yndongyong.bindview.utils.AndroidLayoutUtils
import com.yndongyong.bindview.utils.findLayoutNameInContext
import com.yndongyong.bindview.utils.getAndroidViewIds

/**
 * 生成 BindView 代码的 Action 入口
 */
class GenerateBindViewAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val extension = virtualFile.extension?.lowercase()
        e.presentation.isEnabledAndVisible = (extension == "xml" || extension == "kt")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)

        val extension = psiFile.virtualFile?.extension?.lowercase() ?: ""
        when (extension) {
            "xml" -> {
                val elements = psiFile.getAndroidViewIds()
                if (elements.isEmpty()) {
                    Messages.showInfoMessage(project, "No view elements with android:id found in this layout.", "BindView")
                    return
                }
                val dialog = BindViewDialog(project, psiFile, elements, isKotlinFile = false)
                dialog.show()
            }
            "kt" -> {
                val offset = editor?.caretModel?.offset ?: 0
                val selectedText = editor?.selectionModel?.selectedText?.trim()

                var layoutName: String? = null
                if (!selectedText.isNullOrEmpty()) {
                    layoutName = AndroidLayoutUtils.getLayoutName(selectedText)
                }

                // 未选中文本时，尝试从当前类上下文中自动识别
                if (layoutName.isNullOrEmpty()) {
                    layoutName = psiFile.findLayoutNameInContext(offset)
                }

                if (layoutName.isNullOrEmpty()) {
                    Messages.showWarningDialog(
                        project,
                        "Layout file name not found. Please select layout name (e.g. activity_main) in editor.",
                        "BindView"
                    )
                    return
                }

                val layoutFile = AndroidLayoutUtils.findLayoutResourceFile(psiFile, project, layoutName)
                if (layoutFile == null) {
                    Messages.showErrorDialog(
                        project,
                        "Cannot find layout file: $layoutName.xml",
                        "BindView"
                    )
                    return
                }

                val elements = layoutFile.getAndroidViewIds()
                if (elements.isEmpty()) {
                    Messages.showInfoMessage(
                        project,
                        "No view elements with android:id found in $layoutName.xml",
                        "BindView"
                    )
                    return
                }

                val dialog = BindViewDialog(
                    project = project,
                    psiFile = psiFile,
                    elements = elements,
                    isKotlinFile = true,
                    offset = offset
                )
                dialog.show()
            }
            else -> {
                Messages.showWarningDialog(project, "Only XML layout and Kotlin files are supported.", "BindView")
            }
        }
    }
}
