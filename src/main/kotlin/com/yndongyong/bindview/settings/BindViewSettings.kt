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

    override fun getState(): BindViewSettings {
        return this
    }

    override fun loadState(state: BindViewSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): BindViewSettings {
            return ApplicationManager.getApplication().getService(BindViewSettings::class.java)
        }
    }
}
