package com.yndongyong.bindview.model

import java.util.regex.Pattern

/**
 * 从 XML 中解析出的 View 元素信息
 */
class Element(viewClassName: String, viewId: String) {

    var id: String? = null
    var isAndroidNS: Boolean = false
    var viewNameFull: String? = null // View 完整类名
    var viewName: String? = null     // View 简短类名

    init {
        // 解析 View id
        val matcher = ID_PATTERN.matcher(viewId)
        if (matcher.find() && matcher.groupCount() > 1) {
            this.id = matcher.group(2)
            val androidNS = matcher.group(1)
            this.isAndroidNS = !androidNS.isNullOrEmpty()
        }

        // 解析 View 类名
        val packages = viewClassName.split(".").dropLastWhile { it.isEmpty() }.toTypedArray()
        if (packages.size > 1) {
            viewNameFull = viewClassName
            viewName = packages[packages.size - 1]
        } else {
            viewNameFull = null
            viewName = viewClassName
        }
    }

    /**
     * 根据设置计算字段变量名称
     * @param addM 是否添加前缀 'm'
     * @param isCamelCase 是否转换为驼峰命名
     */
    fun getFieldName(addM: Boolean, isCamelCase: Boolean): String {
        val rawId = id ?: return ""
        if (!isCamelCase) {
            return if (addM) "m_${rawId}" else rawId
        }

        val parts = rawId.split("_").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return rawId

        val sb = StringBuilder()
        if (addM) {
            sb.append("m")
            for (part in parts) {
                sb.append(part.firstToUpperCase())
            }
        } else {
            for ((index, part) in parts.withIndex()) {
                if (index == 0) {
                    sb.append(part.firstToLowerCase())
                } else {
                    sb.append(part.firstToUpperCase())
                }
            }
        }
        return sb.toString()
    }

    /**
     * 获取完整引用的资源 ID，如 R.id.tv_title 或 android.R.id.text1
     */
    fun fullID(): String {
        val prefix = if (isAndroidNS) "android.R.id." else "R.id."
        return "$prefix$id"
    }

    companion object {
        private val ID_PATTERN = Pattern.compile("@\\+?(android:)?id/([^$]+)$", Pattern.CASE_INSENSITIVE)

        private fun String.firstToUpperCase(): String {
            if (isEmpty()) return this
            return substring(0, 1).uppercase() + substring(1)
        }

        private fun String.firstToLowerCase(): String {
            if (isEmpty()) return this
            return substring(0, 1).lowercase() + substring(1)
        }
    }
}
