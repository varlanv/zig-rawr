package com.zigrawr.jbplugin.file

import com.intellij.lang.Language

object ZonLanguage : Language("Zon") {
    private fun readResolve(): Any = ZonLanguage
}
