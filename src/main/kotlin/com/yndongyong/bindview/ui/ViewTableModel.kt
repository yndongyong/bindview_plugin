package com.yndongyong.bindview.ui

import com.yndongyong.bindview.model.ViewInfo
import javax.swing.table.AbstractTableModel

/**
 * 视图列表表格适配器
 */
class ViewTableModel(
    val viewInfoList: List<ViewInfo>,
    private var addM: Boolean,
    private var isCamelCase: Boolean,
    private val onDataChangedListener: () -> Unit
) : AbstractTableModel() {

    fun setOptions(addM: Boolean, isCamelCase: Boolean) {
        this.addM = addM
        this.isCamelCase = isCamelCase
        fireTableDataChanged()
        onDataChangedListener.invoke()
    }

    override fun getRowCount(): Int = viewInfoList.size

    override fun getColumnCount(): Int = 4

    override fun getColumnName(column: Int): String {
        return when (column) {
            0 -> "select"
            1 -> "type"
            2 -> "id"
            3 -> "name"
            else -> ""
        }
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            0 -> java.lang.Boolean::class.java
            else -> String::class.java
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        // select 列和 name 列可编辑
        return columnIndex == 0 || columnIndex == 3
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val item = viewInfoList[rowIndex]
        return when (columnIndex) {
            0 -> item.isChecked
            1 -> item.element.viewName ?: ""
            2 -> item.element.id ?: ""
            3 -> item.getEffectiveFieldName(addM, isCamelCase)
            else -> ""
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val item = viewInfoList[rowIndex]
        when (columnIndex) {
            0 -> {
                if (aValue is Boolean) {
                    item.isChecked = aValue
                    fireTableCellUpdated(rowIndex, columnIndex)
                    onDataChangedListener.invoke()
                }
            }
            3 -> {
                val str = aValue?.toString()?.trim()
                if (!str.isNullOrEmpty()) {
                    item.customFieldName = str
                    fireTableCellUpdated(rowIndex, columnIndex)
                    onDataChangedListener.invoke()
                }
            }
        }
    }
}
