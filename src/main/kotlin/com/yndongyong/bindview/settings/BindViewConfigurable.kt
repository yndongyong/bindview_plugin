package com.yndongyong.bindview.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel

/**
 * BindView 设置面板（Settings -> Tools -> BindView）
 */
class BindViewConfigurable : Configurable {

    private val settings = BindViewSettings.getInstance()

    private var importPathField: Cell<JBTextField>? = null
    private var isPrivateCheckBox: Cell<JBCheckBox>? = null
    private var isAddMCheckBox: Cell<JBCheckBox>? = null
    private var isCamelCaseCheckBox: Cell<JBCheckBox>? = null

    override fun getDisplayName(): String = "BindView"

    override fun createComponent() = panel {
        group("导包设置 (Import Configuration)") {
            row("bindView 导入路径:") {
                importPathField = textField()
                    .comment("宿主项目提供的自定义实现，方法名统一为 bindView。<br>例如：com.yndongyong.van.bindView")
                    .also {
                        it.component.text = settings.bindViewImportPath
                        it.component.columns = 35
                    }
            }
        }

        group("默认生成选项 (Default Options)") {
            row {
                isPrivateCheckBox = checkBox("默认添加 private 修饰符")
                    .comment("勾选时生成 private val，未勾选生成 val")
                    .also {
                        it.component.isSelected = settings.isPrivate
                    }
            }
            row {
                isAddMCheckBox = checkBox("默认变量名添加 \"m\" 前缀")
                    .comment("勾选时如 mTvTitle，未勾选如 tvTitle")
                    .also {
                        it.component.isSelected = settings.isAddM
                    }
            }
            row {
                isCamelCaseCheckBox = checkBox("默认驼峰命名转换")
                    .comment("勾选时 tv_title 转换为 tvTitle")
                    .also {
                        it.component.isSelected = settings.isCamelCase
                    }
            }
        }

        // 联动逻辑
        isAddMCheckBox?.component?.addActionListener {
            if (isAddMCheckBox?.component?.isSelected == true) {
                isCamelCaseCheckBox?.component?.isSelected = true
            }
        }
        isCamelCaseCheckBox?.component?.addActionListener {
            if (isCamelCaseCheckBox?.component?.isSelected == false) {
                isAddMCheckBox?.component?.isSelected = false
            }
        }
    }

    override fun isModified(): Boolean {
        val currentImport = importPathField?.component?.text?.trim() ?: ""
        return currentImport != settings.bindViewImportPath ||
                isPrivateCheckBox?.component?.isSelected != settings.isPrivate ||
                isAddMCheckBox?.component?.isSelected != settings.isAddM ||
                isCamelCaseCheckBox?.component?.isSelected != settings.isCamelCase
    }

    override fun apply() {
        val inputPath = importPathField?.component?.text?.trim()
        if (!inputPath.isNullOrEmpty()) {
            settings.bindViewImportPath = if (inputPath.endsWith(".bindView")) {
                inputPath
            } else {
                "$inputPath.bindView"
            }
            importPathField?.component?.text = settings.bindViewImportPath
        } else {
            settings.bindViewImportPath = BindViewSettings.DEFAULT_IMPORT_PATH
            importPathField?.component?.text = BindViewSettings.DEFAULT_IMPORT_PATH
        }

        settings.isPrivate = isPrivateCheckBox?.component?.isSelected ?: true
        settings.isAddM = isAddMCheckBox?.component?.isSelected ?: false
        settings.isCamelCase = isCamelCaseCheckBox?.component?.isSelected ?: true
    }

    override fun reset() {
        importPathField?.component?.text = settings.bindViewImportPath
        isPrivateCheckBox?.component?.isSelected = settings.isPrivate
        isAddMCheckBox?.component?.isSelected = settings.isAddM
        isCamelCaseCheckBox?.component?.isSelected = settings.isCamelCase
    }
}
