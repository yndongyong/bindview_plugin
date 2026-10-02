package com.yndongyong.bindview.utils

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Android 布局资源查找与处理工具类
 */
object AndroidLayoutUtils {

    /**
     * 从布局字符串提取布局名，例如 "@layout/activity_main" -> "activity_main"
     */
    fun getLayoutName(layout: String?): String? {
        if (layout == null) return null
        val clean = layout.trim().removePrefix("@layout/").removePrefix("layout/").removePrefix("R.layout.")
        val parts = clean.split("/")
        val nameWithExt = parts.lastOrNull() ?: return null
        return nameWithExt.removeSuffix(".xml")
    }

    /**
     * 查找布局 XML 文件（优先在当前模块范围搜索，未找到则在整个项目范围搜索）
     */
    fun findLayoutResourceFile(element: PsiElement, project: Project, layoutFileName: String): PsiFile? {
        val fileName = if (layoutFileName.endsWith(".xml")) layoutFileName else "$layoutFileName.xml"
        val module = ModuleUtil.findModuleForPsiElement(element)

        if (module != null) {
            val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(false)
            val virtualFiles = FilenameIndex.getVirtualFilesByName(fileName, moduleScope)
            if (virtualFiles.isNotEmpty()) {
                val psiFile = PsiManager.getInstance(project).findFile(virtualFiles.first())
                if (psiFile != null) return psiFile
            }
        }

        // 全工程范围搜索
        val projectScope = GlobalSearchScope.projectScope(project)
        val virtualFiles = FilenameIndex.getVirtualFilesByName(fileName, projectScope)
        if (virtualFiles.isNotEmpty()) {
            return PsiManager.getInstance(project).findFile(virtualFiles.first())
        }

        return null
    }
}
