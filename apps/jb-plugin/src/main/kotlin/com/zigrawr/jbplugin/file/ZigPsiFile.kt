package com.zigrawr.jbplugin.file

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class ZigPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ZigLanguage) {

    override fun getFileType(): FileType = ZigFileType
}
