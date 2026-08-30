package com.geolock.app.service

import android.view.accessibility.AccessibilityNodeInfo

object ResetScreenDetector {
    private val keywords = listOf(
        "factory reset",
        "factory data reset",
        "erase all data",
        "erase all your data",
        "erase all",
        "reset phone",
        "reset this phone",
        "reset your phone",
        "wipe data",
        "master clear",
        "restore factory",
        "delete all data",
        "erasing your phone",
        "erase device"
    )

    fun isResetScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val texts = ArrayList<String>()
        collect(root, texts)
        return texts.any { text ->
            keywords.any { keyword -> text.contains(keyword) }
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.lowercase()?.let { if (it.isNotBlank()) out += it }
        node.contentDescription?.toString()?.lowercase()?.let { if (it.isNotBlank()) out += it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out)
            child.recycle()
        }
    }
}
