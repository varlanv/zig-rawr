package com.varlanv.zigrawr.jbplugin

import com.intellij.lang.Language

object ZonLanguage : Language("Zon") {
    private fun readResolve(): Any = ZonLanguage
}
