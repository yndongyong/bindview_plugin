package com.yndongyong.bindview.helper

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportTest {

    @Test
    fun testIsImportNeeded_SamePackage() {
        val filePackage = "com.example.view"
        val existingImports = emptyList<ImportEntry>()

        // 同包类无需导入
        assertFalse(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "com.example.view.CustomHeader"
            )
        )
    }

    @Test
    fun testIsImportNeeded_ExactImportExists() {
        val filePackage = "com.example.activity"
        val existingImports = listOf(
            ImportEntry(
                pathStr = "androidx.recyclerview.widget.RecyclerView",
                isWildcard = false,
                importedSimpleName = "RecyclerView"
            )
        )

        // 已存在精确导入，不应重复导入
        assertFalse(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "androidx.recyclerview.widget.RecyclerView"
            )
        )
    }

    @Test
    fun testIsImportNeeded_WildcardImportExists() {
        val filePackage = "com.example.activity"
        val existingImports = listOf(
            ImportEntry(
                pathStr = "androidx.recyclerview.widget.*",
                isWildcard = true,
                wildcardPackage = "androidx.recyclerview.widget"
            )
        )

        // 已存在同包通配符导入，无需重复导入
        assertFalse(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "androidx.recyclerview.widget.RecyclerView"
            )
        )
    }

    @Test
    fun testIsImportNeeded_ParentWildcardDoesNotCoverSubpackage() {
        val filePackage = "com.example.activity"
        val existingImports = listOf(
            ImportEntry(
                pathStr = "com.example.*",
                isWildcard = true,
                wildcardPackage = "com.example"
            )
        )

        // 父包通配符不递归覆盖子包
        assertTrue(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "com.example.view.CustomHeader"
            )
        )
    }

    @Test
    fun testIsImportNeeded_ConflictingSimpleNameAvoidance() {
        val filePackage = "com.example.activity"
        val existingImports = listOf(
            ImportEntry(
                pathStr = "com.other.widget.CustomHeader",
                isWildcard = false,
                importedSimpleName = "CustomHeader"
            )
        )

        // 已存在同名简短名称的其他类导入，避免冲突导入
        assertFalse(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "com.example.view.CustomHeader"
            )
        )
    }

    @Test
    fun testIsImportNeeded_NewImportNeeded() {
        val filePackage = "com.example.activity"
        val existingImports = listOf(
            ImportEntry(
                pathStr = "android.os.Bundle",
                isWildcard = false,
                importedSimpleName = "Bundle"
            ),
            ImportEntry(
                pathStr = "android.widget.TextView",
                isWildcard = false,
                importedSimpleName = "TextView"
            )
        )

        // 全新的 Custom View 与 AndroidX 控件需正常导入
        assertTrue(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "androidx.recyclerview.widget.RecyclerView"
            )
        )
        assertTrue(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "com.google.android.material.button.MaterialButton"
            )
        )
        assertTrue(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "com.example.view.CustomHeader"
            )
        )
        assertTrue(
            ImportHelper.isImportNeeded(
                filePackage = filePackage,
                existingImports = existingImports,
                targetFqName = "com.yndongyong.van.bindView"
            )
        )
    }

    @Test
    fun testStandardAndroidWidgetsMapping() {
        assertTrue(ImportHelper.STANDARD_ANDROID_WIDGETS.containsKey("TextView"))
        assertTrue(ImportHelper.STANDARD_ANDROID_WIDGETS.containsKey("ImageView"))
        assertTrue(ImportHelper.STANDARD_ANDROID_WIDGETS.containsKey("Button"))
        assertTrue(ImportHelper.STANDARD_ANDROID_WIDGETS.containsKey("View"))
        assertTrue(ImportHelper.STANDARD_ANDROID_WIDGETS.containsKey("ViewStub"))
    }
}

    @Test
    fun testPsiFactory() {
        println("KtPsiFactory test")
    }
