package com.yndongyong.bindview.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.yndongyong.bindview.model.Element
import org.jetbrains.kotlin.psi.KtClass
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.util.regex.Pattern
import javax.xml.parsers.SAXParserFactory

/**
 * 解析 XML 布局文件中的所有 Android 视图控件
 */
fun PsiFile.getAndroidViewIds(): List<Element> {
    val elements = ArrayList<Element>()
    val seenIds = HashSet<String>()

    try {
        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()

        parser.parse(this.text.byteInputStream(), object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                if ("include".equals(qName, ignoreCase = true)) {
                    val layout = attributes?.getValue("layout")
                    if (layout != null) {
                        val layoutName = AndroidLayoutUtils.getLayoutName(layout)
                        if (layoutName != null) {
                            val includeFile = AndroidLayoutUtils.findLayoutResourceFile(
                                this@getAndroidViewIds,
                                this@getAndroidViewIds.project,
                                "$layoutName.xml"
                            )
                            if (includeFile != null) {
                                for (incElem in includeFile.getAndroidViewIds()) {
                                    val id = incElem.id
                                    if (id != null && seenIds.add(id)) {
                                        elements.add(incElem)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val id = attributes?.getValue("android:id") ?: return
                    var name = qName ?: "View"
                    val clazz = attributes.getValue("class")
                    if (!clazz.isNullOrEmpty()) {
                        name = clazz
                    }

                    try {
                        val element = Element(name, id)
                        val viewId = element.id
                        if (viewId != null && seenIds.add(viewId)) {
                            elements.add(element)
                        }
                    } catch (e: Exception) {
                        // 忽略单个标签解析异常
                    }
                }
            }
        })
    } catch (e: Exception) {
        // 捕获 SAX 解析异常
    }

    return elements
}

/**
 * 获取指定位置所在的 Kotlin 类
 */
fun PsiFile.getKotlinClass(offset: Int): KtClass? {
    var psiElement: PsiElement? = this.findElementAt(offset)
    while (psiElement != null) {
        if (psiElement is KtClass) {
            return psiElement
        }
        psiElement = psiElement.parent
    }
    return null
}

/**
 * 从当前光标位置或整个类上下文中尝试自动识别布局文件名 (R.layout.xxx)
 */
fun PsiFile.findLayoutNameInContext(offset: Int): String? {
    val ktClass = getKotlinClass(offset) ?: return null
    val classText = ktClass.text

    // 匹配 R.layout.xxx
    val pattern = Pattern.compile("R\\.layout\\.([a-zA-Z0-9_]+)")
    val matcher = pattern.matcher(classText)
    if (matcher.find()) {
        return matcher.group(1)
    }
    return null
}
