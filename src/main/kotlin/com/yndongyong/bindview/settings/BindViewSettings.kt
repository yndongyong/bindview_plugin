package com.yndongyong.bindview.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * BindView 插件全局配置与持久化
 */
@Service(Service.Level.APP)
@State(name = "BindViewSettings", storages = [Storage("bindview-settings.xml")])
class BindViewSettings : PersistentStateComponent<BindViewSettings> {

    var isPrivate: Boolean = true

    var isAddM: Boolean = false

    var isCamelCase: Boolean = true

    /**
     * 是否为局部变量模式 (findViewById)
     */
    var isLocalVariable: Boolean = false

    /**
     * 局部变量 findViewById 的调用前缀，例如 "this"、"view"、"rootView" 或 ""
     */
    var localVariablePrefix: String = "this"

    /**
     * 导包语句配置，默认 com.yndongyong.van.bindView
     */
    var bindViewImportPath: String = DEFAULT_IMPORT_PATH

    /**
     * 获取规范化后的有效导入路径（确保以 .bindView 结尾）
     */
    fun getEffectiveImportPath(): String {
        val trimmed = bindViewImportPath.trim()
        if (trimmed.isEmpty()) {
            return DEFAULT_IMPORT_PATH
        }
        return if (trimmed.endsWith(".bindView")) {
            trimmed
        } else {
            "$trimmed.bindView"
        }
    }

    override fun getState(): BindViewSettings {
        return this
    }

    override fun loadState(state: BindViewSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        const val DEFAULT_IMPORT_PATH = "com.yndongyong.van.bindView"

        fun getInstance(): BindViewSettings {
            return ApplicationManager.getApplication().getService(BindViewSettings::class.java)
        }
    }
}
