package com.varlanv.zigrawr.jbplugin

import com.intellij.lang.Language

object ZigLanguage : Language("Zig") {
    private fun readResolve(): Any = ZigLanguage
}
