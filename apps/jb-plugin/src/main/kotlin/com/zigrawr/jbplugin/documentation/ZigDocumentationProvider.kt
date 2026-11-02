package com.zigrawr.jbplugin.documentation

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.zigrawr.jbplugin.declaration.ZigDeclarationResolver
import com.zigrawr.jbplugin.file.ZigFileType

class ZigDocumentationProvider : DocumentationTargetProvider {

    override fun documentationTargets(
        file: PsiFile,
        offset: Int
    ): List<DocumentationTarget> {
        if (file.fileType != ZigFileType) return emptyList()
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
        val adjusted = if (document == null) offset else TargetElementUtil.adjustOffset(file, document, offset)
        val declaration = ZigDeclarationResolver.resolve(file, adjusted) ?: return emptyList()
        if (declaration is PsiFile) return emptyList()
        return listOf(ZigDocumentationTarget(declaration))
    }
}
