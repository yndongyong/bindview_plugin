package com.yndongyong.bindview.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.yndongyong.bindview.helper.KtFileWriteHelper
import com.yndongyong.bindview.model.Element
import com.yndongyong.bindview.model.ViewInfo
import com.yndongyong.bindview.settings.BindViewSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * BindView 代码生成弹窗
 */
class BindViewDialog(
    private val project: Project,
    private val psiFile: PsiFile,
    elements: List<Element>,
    private val isKotlinFile: Boolean = false,
    private val offset: Int = 0
) : DialogWrapper(project, true) {

    private val settings = BindViewSettings.getInstance()

    // 顶部配置项
    private val isPrivateCheckBox = JBCheckBox("private", settings.isPrivate)
    private val addMCheckBox = JBCheckBox("add \"m\"", settings.isAddM)
    private val isCamelCaseCheckBox = JBCheckBox("isCamelCase", settings.isCamelCase)

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

    init {
        title = if (isKotlinFile) "Generate BindView Code (Kotlin)" else "Generate BindView Code (XML)"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val rootPanel = JPanel(BorderLayout(0, 10))
        rootPanel.preferredSize = Dimension(820, 560)

        // 1. 顶部控制栏 (右上角 private, add "m", isCamelCase)
        val topPanel = JPanel(BorderLayout())
        val westPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0))
        val titleLabel = JLabel("BindView (Kotlin)")
        titleLabel.font = titleLabel.font.deriveFont(java.awt.Font.BOLD)
        westPanel.add(titleLabel)

        if (isKotlinFile) {
            val importLabel = JLabel("import ${settings.getEffectiveImportPath()}")
            importLabel.foreground = com.intellij.ui.JBColor.GRAY
            val editImportBtn = JButton("配置导包").apply {
                isBorderPainted = false
                isContentAreaFilled = false
                foreground = com.intellij.ui.JBColor.blue
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                margin = java.awt.Insets(0, 0, 0, 0)
                addActionListener {
                    val current = settings.bindViewImportPath
                    val newPath = com.intellij.openapi.ui.Messages.showInputDialog(
                        rootPanel,
                        "请输入宿主项目提供的 bindView 完整导包路径（方法名必须为 bindView）：",
                        "配置 BindView 导包",
                        com.intellij.openapi.ui.Messages.getQuestionIcon(),
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
        topPanel.add(westPanel, BorderLayout.WEST)

        val optionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 15, 0))
        optionsPanel.add(isPrivateCheckBox)
        optionsPanel.add(addMCheckBox)
        optionsPanel.add(isCamelCaseCheckBox)
        topPanel.add(optionsPanel, BorderLayout.EAST)
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
        tableScrollPane.preferredSize = Dimension(800, 200)

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
        codeScrollPane.preferredSize = Dimension(800, 180)

        centerPanel.add(tableContainer, BorderLayout.CENTER)
        centerPanel.add(codeScrollPane, BorderLayout.SOUTH)

        rootPanel.add(centerPanel, BorderLayout.CENTER)

        // 事件监听绑定
        initListeners()

        // 初始生成代码
        updateGeneratedCode()

        return rootPanel
    }

    private fun initListeners() {
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

        val sb = StringBuilder()
        val checkedList = viewInfoList.filter { it.isChecked }
        for (item in checkedList) {
            sb.append(item.getBindViewCode(addM, isPrivate, isCamelCase)).append("\n")
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
            // 在 Kotlin 文件中提供直接插入代码和复制两个选项
            val insertAction = object : DialogWrapperAction("Insert Code") {
                override fun doAction(e: ActionEvent?) {
                    val success = KtFileWriteHelper(
                        project = project,
                        psiFile = psiFile,
                        offset = offset,
                        viewInfos = viewInfoList,
                        addM = addMCheckBox.isSelected,
                        isPrivate = isPrivateCheckBox.isSelected,
                        isCamelCase = isCamelCaseCheckBox.isSelected
                    ).execute()
                    if (success) {
                        showNotification("BindView code inserted successfully")
                        close(OK_EXIT_CODE)
                    }
                }
            }
            insertAction.putValue(DEFAULT_ACTION, true)
            actions.add(insertAction)
        }

        val copyAction = object : DialogWrapperAction("Copy Code") {
            override fun doAction(e: ActionEvent?) {
                copyCodeToClipboard()
                showNotification("BindView code copied to clipboard")
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
