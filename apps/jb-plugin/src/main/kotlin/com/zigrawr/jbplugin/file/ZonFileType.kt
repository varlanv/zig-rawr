package com.zigrawr.jbplugin.file

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object ZonFileType : LanguageFileType(ZonLanguage) {
    override fun getName(): String = "Zon"

    override fun getDescription(): String = "Zon source file"

    override fun getDefaultExtension(): String = "zon"

    override fun getIcon(): Icon? = null
}
