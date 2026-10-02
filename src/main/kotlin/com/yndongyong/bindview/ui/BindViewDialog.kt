package com.yndongyong.bindview.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.yndongyong.bindview.helper.KtFileWriteHelper
import com.yndongyong.bindview.model.Element
import com.yndongyong.bindview.model.ViewInfo
import com.yndongyong.bindview.settings.BindViewSettings
import com.yndongyong.bindview.utils.inferContextScope
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * BindView 代码生成弹窗，支持类属性委托 (by bindView) 与局部变量 (findViewById) 双模式
 */
class BindViewDialog(
    private val project: Project,
    private val psiFile: PsiFile,
    elements: List<Element>,
    private val isKotlinFile: Boolean = false,
    private val offset: Int = 0
) : DialogWrapper(project, true) {

    private val settings = BindViewSettings.getInstance()

    // 命名配置项
    private val isPrivateCheckBox = JBCheckBox("private", settings.isPrivate)
    private val addMCheckBox = JBCheckBox("add \"m\"", settings.isAddM)
    private val isCamelCaseCheckBox = JBCheckBox("isCamelCase", settings.isCamelCase)

    // 局部变量 (findViewById) 配置项
    private val isLocalVariableCheckBox = JBCheckBox("Local Variable (findViewById)", settings.isLocalVariable)
    private val prefixLabel = JLabel("Prefix:")
    private val prefixTextField = JBTextField(settings.localVariablePrefix, 8)

    // 预设前缀按钮列表
    private val presetButtons = mutableListOf<JButton>()

    // 数据列表与表格模型
    private val viewInfoList = elements.map { ViewInfo(isChecked = true, element = it) }
    private lateinit var tableModel: ViewTableModel
    private val viewTable = JBTable()

    // 全选/反选按钮
    private val selectAllButton = JButton("Select All")
    private val selectNoneButton = JButton("Select None")
    private val selectInvertButton = JButton("Select Invert")

    // 代码预览框
    private val previewTextArea = JBTextArea()

    // 底部 Action 引用
    private var insertAction: DialogWrapperAction? = null
    private lateinit var copyAction: DialogWrapperAction

    init {
        title = if (isKotlinFile) "Generate BindView Code (Kotlin)" else "Generate BindView Code (XML)"

        // 智能探测初值（若光标处于方法体或 lambda 闭包内，自动切换为局部变量模式）
        if (isKotlinFile) {
            val inference = psiFile.inferContextScope(offset)
            if (inference.isLocalScope) {
                isLocalVariableCheckBox.isSelected = true
                if (!inference.suggestedPrefix.isNullOrEmpty()) {
                    prefixTextField.text = inference.suggestedPrefix
                }
            }
        }

        init()
        updateLocalVariableUIState()
    }

    override fun createCenterPanel(): JComponent {
        val rootPanel = JPanel(BorderLayout(0, 10))
        rootPanel.preferredSize = Dimension(860, 590)

        // 1. 顶部控制栏
        val topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)

        // 第一行：标题、导包设置链接、以及右侧 private, add "m", isCamelCase
        val row1 = JPanel(BorderLayout())
        val westPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0))
        val titleLabel = JLabel("BindView (Kotlin)")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        westPanel.add(titleLabel)

        if (isKotlinFile) {
            val importLabel = JLabel("import ${settings.getEffectiveImportPath()}")
            importLabel.foreground = JBColor.GRAY
            val editImportBtn = JButton("配置导包").apply {
                isBorderPainted = false
                isContentAreaFilled = false
                foreground = JBColor.blue
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                margin = Insets(0, 0, 0, 0)
                addActionListener {
                    val current = settings.bindViewImportPath
                    val newPath = Messages.showInputDialog(
                        rootPanel,
                        "请输入宿主项目提供的 bindView 完整导包路径（方法名必须为 bindView）：",
                        "配置 BindView 导包",
                        Messages.getQuestionIcon(),
                        current,
                        null
                    )
                    if (!newPath.isNullOrBlank()) {
                        val formatted = if (newPath.trim().endsWith(".bindView")) newPath.trim() else "${newPath.trim()}.bindView"
                        settings.bindViewImportPath = formatted
                        importLabel.text = "import $formatted"
                    }
                }
            }
            westPanel.add(importLabel)
            westPanel.add(editImportBtn)
        }
        row1.add(westPanel, BorderLayout.WEST)

        val optionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 15, 0))
        optionsPanel.add(isPrivateCheckBox)
        optionsPanel.add(addMCheckBox)
        optionsPanel.add(isCamelCaseCheckBox)
        row1.add(optionsPanel, BorderLayout.EAST)
        topPanel.add(row1)

        topPanel.add(Box.createVerticalStrut(6))

        // 第二行：Local Variable 模式与前缀输入框、快捷预设
        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        row2.add(isLocalVariableCheckBox)
        row2.add(prefixLabel)
        prefixTextField.preferredSize = Dimension(110, 26)
        row2.add(prefixTextField)

        val presetLabel = JLabel("Presets:")
        presetLabel.foreground = JBColor.GRAY
        row2.add(presetLabel)

        val presets = listOf("this", "(无前缀)", "view", "rootView", "itemView")
        for (preset in presets) {
            val btn = JButton(preset).apply {
                isBorderPainted = false
                isContentAreaFilled = false
                foreground = JBColor.blue
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                margin = Insets(0, 2, 0, 2)
                addActionListener {
                    prefixTextField.text = if (preset == "(无前缀)") "" else preset
                    updateGeneratedCode()
                }
            }
            presetButtons.add(btn)
            row2.add(btn)
        }

        topPanel.add(row2)
        rootPanel.add(topPanel, BorderLayout.NORTH)

        // 2. 中间区域：表格 + 全选按钮 + 代码预览
        val centerPanel = JPanel(BorderLayout(0, 8))

        // 表格初始化
        tableModel = ViewTableModel(
            viewInfoList = viewInfoList,
            addM = addMCheckBox.isSelected,
            isCamelCase = isCamelCaseCheckBox.isSelected
        ) {
            updateGeneratedCode()
        }
        viewTable.model = tableModel
        viewTable.rowHeight = 24
        viewTable.columnModel.getColumn(0).preferredWidth = 60
        viewTable.columnModel.getColumn(1).preferredWidth = 200
        viewTable.columnModel.getColumn(2).preferredWidth = 240
        viewTable.columnModel.getColumn(3).preferredWidth = 240

        val tableScrollPane = JBScrollPane(viewTable)
        tableScrollPane.preferredSize = Dimension(820, 200)

        // 表格操作按钮
        val buttonBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0))
        buttonBar.add(selectAllButton)
        buttonBar.add(selectNoneButton)
        buttonBar.add(selectInvertButton)

        val tableContainer = JPanel(BorderLayout(0, 6))
        tableContainer.add(tableScrollPane, BorderLayout.CENTER)
        tableContainer.add(buttonBar, BorderLayout.SOUTH)

        // 代码预览文本区域
        val font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
        previewTextArea.font = font
        previewTextArea.rows = 8
        val codeScrollPane = JBScrollPane(previewTextArea)
        codeScrollPane.preferredSize = Dimension(820, 180)

        centerPanel.add(tableContainer, BorderLayout.CENTER)
        centerPanel.add(codeScrollPane, BorderLayout.SOUTH)

        rootPanel.add(centerPanel, BorderLayout.CENTER)

        // 初始化组件联动与事件监听
        initListeners()

        // 根据初始状态更新 UI 开关
        updateLocalVariableUIState()

        // 初始生成代码
        updateGeneratedCode()

        return rootPanel
    }

    private fun initListeners() {
        // 模式切换联动
        isLocalVariableCheckBox.addActionListener {
            updateLocalVariableUIState()
            updateGeneratedCode()
        }

        // 前缀文本输入监听（实时刷新）
        prefixTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateGeneratedCode()
            override fun removeUpdate(e: DocumentEvent?) = updateGeneratedCode()
            override fun changedUpdate(e: DocumentEvent?) = updateGeneratedCode()
        })

        // 复选框事件与联动
        isPrivateCheckBox.addActionListener {
            settings.isPrivate = isPrivateCheckBox.isSelected
            updateGeneratedCode()
        }

        addMCheckBox.addActionListener {
            if (addMCheckBox.isSelected) {
                isCamelCaseCheckBox.isSelected = true
            }
            syncSettingsAndRefresh()
        }

        isCamelCaseCheckBox.addActionListener {
            if (!isCamelCaseCheckBox.isSelected) {
                addMCheckBox.isSelected = false
            }
            syncSettingsAndRefresh()
        }

        // 全选 / 全不选 / 反选
        selectAllButton.addActionListener {
            viewInfoList.forEach { it.isChecked = true }
            tableModel.fireTableDataChanged()
            updateGeneratedCode()
        }

        selectNoneButton.addActionListener {
            viewInfoList.forEach { it.isChecked = false }
            tableModel.fireTableDataChanged()
            previewTextArea.text = ""
        }

        selectInvertButton.addActionListener {
            viewInfoList.forEach { it.isChecked = !it.isChecked }
            tableModel.fireTableDataChanged()
            updateGeneratedCode()
        }
    }

    private fun updateLocalVariableUIState() {
        val isLocal = isLocalVariableCheckBox.isSelected
        prefixLabel.isEnabled = isLocal
        prefixTextField.isEnabled = isLocal
        presetButtons.forEach { it.isEnabled = isLocal }

        // 局部变量下不能声明 private，禁用 private 复选框
        isPrivateCheckBox.isEnabled = !isLocal
        if (isLocal) {
            isPrivateCheckBox.isSelected = false
        } else {
            isPrivateCheckBox.isSelected = settings.isPrivate
        }

        // 只有类属性模式下才显示 Insert Code 按钮，Local Variable 模式下隐藏/不要
        if (isKotlinFile && insertAction != null) {
            val insertBtn = getButton(insertAction!!)
            val copyBtn = getButton(copyAction)
            if (isLocal) {
                insertBtn?.isVisible = false
                copyBtn?.let { rootPane?.defaultButton = it }
            } else {
                insertBtn?.isVisible = true
                insertBtn?.let { rootPane?.defaultButton = it }
            }
        }
    }

    private fun syncSettingsAndRefresh() {
        settings.isAddM = addMCheckBox.isSelected
        settings.isCamelCase = isCamelCaseCheckBox.isSelected
        tableModel.setOptions(addMCheckBox.isSelected, isCamelCaseCheckBox.isSelected)
    }

    /**
     * 实时重新生成并更新代码预览
     */
    private fun updateGeneratedCode() {
        val addM = addMCheckBox.isSelected
        val isPrivate = isPrivateCheckBox.isSelected
        val isCamelCase = isCamelCaseCheckBox.isSelected
        val isLocal = isLocalVariableCheckBox.isSelected
        val prefix = prefixTextField.text.trim()

        val sb = StringBuilder()
        val checkedList = viewInfoList.filter { it.isChecked }
        for (item in checkedList) {
            if (isLocal) {
                sb.append(item.getLocalVariableCode(addM, isCamelCase, prefix)).append("\n")
            } else {
                sb.append(item.getBindViewCode(addM, isPrivate, isCamelCase)).append("\n")
            }
        }

        previewTextArea.text = sb.toString().trimEnd()
        previewTextArea.caretPosition = 0
    }

    /**
     * 自定义底部按钮
     */
    override fun createActions(): Array<Action> {
        val actions = mutableListOf<Action>()

        if (isKotlinFile) {
            val action = object : DialogWrapperAction("Insert Code") {
                override fun doAction(e: ActionEvent?) {
                    saveState()
                    val success = KtFileWriteHelper(
                        project = project,
                        psiFile = psiFile,
                        offset = offset,
                        viewInfos = viewInfoList,
                        addM = addMCheckBox.isSelected,
                        isPrivate = isPrivateCheckBox.isSelected,
                        isCamelCase = isCamelCaseCheckBox.isSelected,
                        isLocalVariable = false,
                        prefix = ""
                    ).execute()
                    if (success) {
                        showNotification("Code inserted successfully")
                        close(OK_EXIT_CODE)
                    }
                }
            }
            action.putValue(DEFAULT_ACTION, true)
            insertAction = action
            actions.add(action)
        }

        copyAction = object : DialogWrapperAction("Copy Code") {
            override fun doAction(e: ActionEvent?) {
                saveState()
                copyCodeToClipboard()
                showNotification("Code copied to clipboard")
                close(OK_EXIT_CODE)
            }
        }
        if (!isKotlinFile) {
            copyAction.putValue(DEFAULT_ACTION, true)
        }
        actions.add(copyAction)

        actions.add(cancelAction)

        return actions.toTypedArray()
    }

    private fun saveState() {
        settings.isLocalVariable = isLocalVariableCheckBox.isSelected
        settings.localVariablePrefix = prefixTextField.text.trim()
    }

    private fun copyCodeToClipboard() {
        val text = previewTextArea.text
        if (text.isNotEmpty()) {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
        }
    }

    private fun showNotification(content: String) {
        val notification = Notification(
            "BindView Plugin",
            "BindView",
            content,
            NotificationType.INFORMATION
        )
        Notifications.Bus.notify(notification, project)
    }
}
