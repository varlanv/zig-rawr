package com.zigrawr.jbplugin.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.zigrawr.jbplugin.declaration.ZigDeclarationResolver

internal class ZigPropertyDocumentationTarget(private val source: PsiElement) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val pointer = SmartPointerManager.createPointer(source)
        return Pointer { pointer.element?.let(::ZigPropertyDocumentationTarget) }
    }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(source.text).locationText(source.containingFile.name).presentation()

    override val navigatable: Navigatable?
        get() {
            if (!source.isValid) return null
            val target = ZigDeclarationResolver.resolve(source.containingFile, source.textOffset) ?: return null
            val file = target.containingFile.virtualFile ?: return null
            return OpenFileDescriptor(source.project, file, target.textOffset)
        }

    override fun computeDocumentation(): DocumentationResult? {
        if (!source.isValid) return null
        val type = ZigDeclarationResolver.propertyType(source.containingFile, source.textOffset) ?: return null
        val html = buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append(StringUtil.escapeXmlEntities(source.text))
            append(": ")
            append(StringUtil.escapeXmlEntities(type))
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("Built-in Zig property.")
            append(DocumentationMarkup.CONTENT_END)
        }
        return DocumentationResult.documentation(html)
    }
}
