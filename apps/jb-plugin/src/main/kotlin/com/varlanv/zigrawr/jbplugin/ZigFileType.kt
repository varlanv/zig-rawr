package com.varlanv.zigrawr.jbplugin

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object ZigFileType : LanguageFileType(ZigLanguage) {
    override fun getName(): String = "Zig"

    override fun getDescription(): String = "Zig source file"

    override fun getDefaultExtension(): String = "zig"

    override fun getIcon(): Icon? = null
}
