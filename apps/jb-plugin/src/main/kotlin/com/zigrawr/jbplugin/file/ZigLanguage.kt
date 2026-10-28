package com.zigrawr.jbplugin.file

import com.intellij.lang.Language

object ZigLanguage : Language("Zig") {
    private fun readResolve(): Any = ZigLanguage
}
