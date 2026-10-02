package com.yndongyong.bindview.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AndroidLayoutUtilsTest {

    @Test
    fun testGetLayoutName() {
        assertEquals("activity_main", AndroidLayoutUtils.getLayoutName("@layout/activity_main"))
        assertEquals("activity_main", AndroidLayoutUtils.getLayoutName("layout/activity_main"))
        assertEquals("activity_main", AndroidLayoutUtils.getLayoutName("R.layout.activity_main"))
        assertEquals("activity_main", AndroidLayoutUtils.getLayoutName("activity_main.xml"))
        assertEquals("activity_main", AndroidLayoutUtils.getLayoutName("activity_main"))
        assertEquals("item_user", AndroidLayoutUtils.getLayoutName("  @layout/item_user  "))
        assertNull(AndroidLayoutUtils.getLayoutName(null))
    }
}
