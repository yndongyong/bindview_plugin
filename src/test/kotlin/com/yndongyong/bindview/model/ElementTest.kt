package com.yndongyong.bindview.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ElementTest {

    @Test
    fun testElementFieldNaming() {
        // 1. 普通下划线 id
        val elem1 = Element("android.widget.TextView", "@+id/tv_title")
        assertEquals("TextView", elem1.viewName)
        assertEquals("tv_title", elem1.id)
        assertEquals("R.id.tv_title", elem1.fullID())
        assertEquals("tvTitle", elem1.getFieldName(addM = false, isCamelCase = true))
        assertEquals("mTvTitle", elem1.getFieldName(addM = true, isCamelCase = true))
        assertEquals("tv_title", elem1.getFieldName(addM = false, isCamelCase = false))
        assertEquals("m_tv_title", elem1.getFieldName(addM = true, isCamelCase = false))

        // 2. 自定义 View 及驼峰 id
        val elem2 = Element("com.example.view.BaseWebView", "@id/vWebview")
        assertEquals("BaseWebView", elem2.viewName)
        assertEquals("com.example.view.BaseWebView", elem2.viewNameFull)
        assertEquals("vWebview", elem2.id)
        assertEquals("R.id.vWebview", elem2.fullID())
        assertEquals("vWebview", elem2.getFieldName(addM = false, isCamelCase = true))
        assertEquals("mVWebview", elem2.getFieldName(addM = true, isCamelCase = true))

        // 3. 多段下划线 id
        val elem3 = Element("PictureTitleBar", "@+id/picture_title_bar")
        assertEquals("pictureTitleBar", elem3.getFieldName(addM = false, isCamelCase = true))
        assertEquals("mPictureTitleBar", elem3.getFieldName(addM = true, isCamelCase = true))

        // 4. Android 系统命名空间 id
        val elem4 = Element("TextView", "@android:id/text1")
        assertEquals("text1", elem4.id)
        assertEquals(true, elem4.isAndroidNS)
        assertEquals("android.R.id.text1", elem4.fullID())
    }

    @Test
    fun testViewInfoCodeGeneration() {
        val elem = Element("android.widget.TextView", "@+id/tv_title")
        val viewInfo = ViewInfo(isChecked = true, element = elem)

        // 默认 private + camelCase
        assertEquals(
            "private val tvTitle: TextView by bindView(R.id.tv_title)",
            viewInfo.getBindViewCode(addM = false, isPrivate = true, isCamelCase = true)
        )

        // 非 private
        assertEquals(
            "val tvTitle: TextView by bindView(R.id.tv_title)",
            viewInfo.getBindViewCode(addM = false, isPrivate = false, isCamelCase = true)
        )

        // add "m"
        assertEquals(
            "private val mTvTitle: TextView by bindView(R.id.tv_title)",
            viewInfo.getBindViewCode(addM = true, isPrivate = true, isCamelCase = true)
        )

        // 非驼峰
        assertEquals(
            "private val tv_title: TextView by bindView(R.id.tv_title)",
            viewInfo.getBindViewCode(addM = false, isPrivate = true, isCamelCase = false)
        )

        // 手动自定义重命名
        viewInfo.customFieldName = "myCustomTitle"
        assertEquals(
            "private val myCustomTitle: TextView by bindView(R.id.tv_title)",
            viewInfo.getBindViewCode(addM = false, isPrivate = true, isCamelCase = true)
        )
    }

    @Test
    fun testImportPathConfiguration() {
        val settings = com.yndongyong.bindview.settings.BindViewSettings()
        // 默认
        assertEquals("com.yndongyong.van.bindView", settings.getEffectiveImportPath())

        // 自定义全称
        settings.bindViewImportPath = "com.custom.ui.bindView"
        assertEquals("com.custom.ui.bindView", settings.getEffectiveImportPath())

        // 仅包名（自动补齐 .bindView）
        settings.bindViewImportPath = "com.custom.ui"
        assertEquals("com.custom.ui.bindView", settings.getEffectiveImportPath())

        // 空格与空字符串处理
        settings.bindViewImportPath = "   "
        assertEquals("com.yndongyong.van.bindView", settings.getEffectiveImportPath())
    }

    @Test
    fun testLocalVariableCodeGeneration() {
        val elem = Element("com.example.AsyncImageView", "@+id/iv_scenic_live_play_item_cover")
        val viewInfo = ViewInfo(isChecked = true, element = elem)

        // 1. 带 this 前缀
        assertEquals(
            "val ivScenicLivePlayItemCover = this.findViewById<AsyncImageView>(R.id.iv_scenic_live_play_item_cover)",
            viewInfo.getLocalVariableCode(addM = false, isCamelCase = true, prefix = "this")
        )

        // 2. 带 rootView 前缀
        assertEquals(
            "val ivScenicLivePlayItemCover = rootView.findViewById<AsyncImageView>(R.id.iv_scenic_live_play_item_cover)",
            viewInfo.getLocalVariableCode(addM = false, isCamelCase = true, prefix = "rootView")
        )

        // 3. 带 holder.itemView 前缀
        assertEquals(
            "val ivScenicLivePlayItemCover = holder.itemView.findViewById<AsyncImageView>(R.id.iv_scenic_live_play_item_cover)",
            viewInfo.getLocalVariableCode(addM = false, isCamelCase = true, prefix = "holder.itemView")
        )

        // 4. 无前缀（留空）
        assertEquals(
            "val ivScenicLivePlayItemCover = findViewById<AsyncImageView>(R.id.iv_scenic_live_play_item_cover)",
            viewInfo.getLocalVariableCode(addM = false, isCamelCase = true, prefix = "")
        )

        // 5. 非驼峰
        assertEquals(
            "val iv_scenic_live_play_item_cover = this.findViewById<AsyncImageView>(R.id.iv_scenic_live_play_item_cover)",
            viewInfo.getLocalVariableCode(addM = false, isCamelCase = false, prefix = "this")
        )
    }
}
