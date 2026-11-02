package com.zigrawr.jbplugin.documentation

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.zigrawr.jbplugin.builtin.ZigBuiltinPsiElement
import com.zigrawr.jbplugin.declaration.ZigDeclarationResolver
import com.zigrawr.jbplugin.file.ZigFileType

class ZigDocumentationProvider : DocumentationTargetProvider, DumbAware {

    override fun documentationTargets(
        file: PsiFile,
        offset: Int
    ): List<DocumentationTarget> {
        if (file.fileType != ZigFileType) return emptyList()
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
        val adjusted = if (document == null) offset else TargetElementUtil.adjustOffset(file, document, offset)
        file.findElementAt(adjusted)?.let { source ->
            ZigBuiltinPsiElement.create(source)?.let { return listOf(ZigBuiltinDocumentationTarget(it)) }
            if (ZigDeclarationResolver.propertyType(file, adjusted) != null) {
                return listOf(ZigPropertyDocumentationTarget(source))
            }
        }
        val declaration = ZigDeclarationResolver.resolve(file, adjusted) ?: return emptyList()
        if (declaration is PsiFile) return emptyList()
        return listOf(ZigDocumentationTarget(declaration))
    }
}
