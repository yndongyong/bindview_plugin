package com.yndongyong.bindview.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.yndongyong.bindview.model.Element
import org.jetbrains.kotlin.psi.KtClass
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.util.regex.Pattern
import javax.xml.parsers.SAXParserFactory

/**
 * 解析 XML 布局文件中的所有 Android 视图控件
 * 支持 XmlFile PSI 原生遍历与循环 include 防爆保护
 */
fun PsiFile.getAndroidViewIds(visitedFiles: MutableSet<Any> = HashSet()): List<Element> {
    val elements = ArrayList<Element>()
    val seenIds = HashSet<String>()
    collectAndroidViewIds(elements, seenIds, visitedFiles)
    return elements
}

private fun PsiFile.collectAndroidViewIds(
    elements: MutableList<Element>,
    seenIds: MutableSet<String>,
    visitedFiles: MutableSet<Any>
) {
    // 循环引用防护：以虚拟文件（或 PsiFile）为唯一凭据
    val fileKey: Any = this.virtualFile ?: this
    if (!visitedFiles.add(fileKey)) {
        return
    }

    if (this is XmlFile) {
        val rootTag = this.rootTag ?: return
        rootTag.accept(object : XmlRecursiveElementVisitor() {
            override fun visitXmlTag(tag: XmlTag) {
                processXmlTag(tag, elements, seenIds, visitedFiles)
                super.visitXmlTag(tag)
            }
        })
    } else {
        // 降级兼容：如果不是 XmlFile 实例，使用 SAX 解析
        parseWithSax(this, elements, seenIds, visitedFiles)
    }
}

private fun PsiFile.processXmlTag(
    tag: XmlTag,
    elements: MutableList<Element>,
    seenIds: MutableSet<String>,
    visitedFiles: MutableSet<Any>
) {
    val tagName = tag.name
    if ("include".equals(tagName, ignoreCase = true)) {
        // 如果 include 标签自身定义了 android:id，也作为 View 元素记录
        val includeId = tag.getAttributeValue("id", "http://schemas.android.com/apk/res/android")
            ?: tag.getAttributeValue("android:id")
        if (!includeId.isNullOrEmpty()) {
            try {
                val element = Element("View", includeId)
                val viewId = element.id
                if (viewId != null && seenIds.add(viewId)) {
                    elements.add(element)
                }
            } catch (_: Exception) {}
        }

        // 解析并递归收集 include 引用的布局控件
        val layout = tag.getAttributeValue("layout")
        if (!layout.isNullOrEmpty()) {
            val layoutName = AndroidLayoutUtils.getLayoutName(layout)
            if (!layoutName.isNullOrEmpty()) {
                val includeFile = AndroidLayoutUtils.findLayoutResourceFile(
                    this,
                    this.project,
                    "$layoutName.xml"
                )
                if (includeFile != null) {
                    val targetKey: Any = includeFile.virtualFile ?: includeFile
                    if (!visitedFiles.contains(targetKey)) {
                        includeFile.collectAndroidViewIds(elements, seenIds, visitedFiles)
                    }
                }
            }
        }
    } else {
        val id = tag.getAttributeValue("id", "http://schemas.android.com/apk/res/android")
            ?: tag.getAttributeValue("android:id")
            ?: return

        var name = tagName
        val clazz = tag.getAttributeValue("class")
        if (!clazz.isNullOrEmpty()) {
            name = clazz
        } else if ("view".equals(name, ignoreCase = true)) {
            name = "View"
        }

        try {
            val element = Element(name, id)
            val viewId = element.id
            if (viewId != null && seenIds.add(viewId)) {
                elements.add(element)
            }
        } catch (_: Exception) {
            // 忽略单个标签解析异常
        }
    }
}

/**
 * 降级解析方案（针对非 XmlFile 的特殊场景）
 */
private fun parseWithSax(
    file: PsiFile,
    elements: MutableList<Element>,
    seenIds: MutableSet<String>,
    visitedFiles: MutableSet<Any>
) {
    try {
        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()

        parser.parse(file.text.byteInputStream(), object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                if ("include".equals(qName, ignoreCase = true)) {
                    val incId = attributes?.getValue("android:id")
                    if (!incId.isNullOrEmpty()) {
                        try {
                            val element = Element("View", incId)
                            val viewId = element.id
                            if (viewId != null && seenIds.add(viewId)) {
                                elements.add(element)
                            }
                        } catch (_: Exception) {}
                    }

                    val layout = attributes?.getValue("layout")
                    if (layout != null) {
                        val layoutName = AndroidLayoutUtils.getLayoutName(layout)
                        if (layoutName != null) {
                            val includeFile = AndroidLayoutUtils.findLayoutResourceFile(
                                file,
                                file.project,
                                "$layoutName.xml"
                            )
                            if (includeFile != null) {
                                val targetKey: Any = includeFile.virtualFile ?: includeFile
                                if (!visitedFiles.contains(targetKey)) {
                                    includeFile.collectAndroidViewIds(elements, seenIds, visitedFiles)
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
                    } else if ("view".equals(name, ignoreCase = true)) {
                        name = "View"
                    }

                    try {
                        val element = Element(name, id)
                        val viewId = element.id
                        if (viewId != null && seenIds.add(viewId)) {
                            elements.add(element)
                        }
                    } catch (_: Exception) {
                        // 忽略单个标签解析异常
                    }
                }
            }
        })
    } catch (_: Exception) {
        // 捕获 SAX 解析异常
    }
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
    // 回退机制：若光标处未直接命中 KtClass，回退查找当前 Kotlin 文件的顶级类
    if (this is org.jetbrains.kotlin.psi.KtFile) {
        return this.declarations.filterIsInstance<KtClass>().firstOrNull()
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

/**
 * 上下文推断结果
 */
data class ContextInference(
    val isLocalScope: Boolean,
    val suggestedPrefix: String?
)

/**
 * 探测光标所在上下文，判断是否在方法或 Lambda 闭包中，并推测适用的前缀
 */
fun PsiFile.inferContextScope(offset: Int): ContextInference {
    var psiElement = this.findElementAt(offset)
    while (psiElement != null) {
        if (psiElement is KtClass) {
            // 已经回退到类级别，说明处在类成员变量区域
            break
        }
        if (psiElement is org.jetbrains.kotlin.psi.KtLambdaExpression) {
            val text = psiElement.text
            val prefix = when {
                text.contains("this: View") || text.contains("this:View") || text.contains("this ->") -> "this"
                text.contains("view,") || text.contains("view ->") -> "view"
                text.contains("rootView") -> "rootView"
                text.contains("itemView") -> "itemView"
                text.contains("holder") -> "holder.itemView"
                else -> "this"
            }
            return ContextInference(isLocalScope = true, suggestedPrefix = prefix)
        }
        if (psiElement is org.jetbrains.kotlin.psi.KtNamedFunction) {
            val fnName = psiElement.name ?: ""
            val params = psiElement.valueParameters.mapNotNull { it.name }
            val prefix = when {
                params.contains("view") -> "view"
                params.contains("rootView") -> "rootView"
                params.contains("itemView") -> "itemView"
                params.contains("holder") -> "holder.itemView"
                fnName.contains("onViewCreated", ignoreCase = true) -> "view"
                else -> null
            }
            // 普通函数（如 onCreate、initView）内默认仍然生成类属性，不强制为局部模式
            return ContextInference(isLocalScope = false, suggestedPrefix = prefix ?: "this")
        }
        psiElement = psiElement.parent
    }
    return ContextInference(isLocalScope = false, suggestedPrefix = null)
}
