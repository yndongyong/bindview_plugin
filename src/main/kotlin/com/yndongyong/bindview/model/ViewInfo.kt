package com.yndongyong.bindview.model

/**
 * 视图绑定表格项包装数据
 */
class ViewInfo(
    var isChecked: Boolean = true,
    var element: Element,
    var customFieldName: String? = null
) {
    /**
     * 获取最终的字段名称（支持手动编辑）
     */
    fun getEffectiveFieldName(addM: Boolean, isCamelCase: Boolean): String {
        val custom = customFieldName?.trim()
        if (!custom.isNullOrEmpty()) {
            return custom
        }
        return element.getFieldName(addM, isCamelCase)
    }

    /**
     * 生成当前 View 的 bindView 属性委托声明代码
     * 例如：private val tvTitle: TextView by bindView(R.id.tv_title)
     */
    fun getBindViewCode(addM: Boolean, isPrivate: Boolean, isCamelCase: Boolean): String {
        val fieldName = getEffectiveFieldName(addM, isCamelCase)
        val viewType = element.viewName ?: "View"
        val idRef = element.fullID()
        val privateModifier = if (isPrivate) "private " else ""
        return "${privateModifier}val $fieldName: $viewType by bindView($idRef)"
    }
}
