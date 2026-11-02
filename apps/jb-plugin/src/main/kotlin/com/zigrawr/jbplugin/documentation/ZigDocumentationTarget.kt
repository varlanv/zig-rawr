package com.zigrawr.jbplugin.documentation

import com.intellij.model.Pointer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

internal class ZigDocumentationTarget(private val declaration: PsiElement) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val pointer = SmartPointerManager.createPointer(declaration)
        return Pointer { pointer.element?.let(::ZigDocumentationTarget) }
    }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(declaration.text).locationText(declaration.containingFile.name).presentation()

    override val navigatable: Navigatable?
        get() {
            if (!declaration.isValid) return null
            val file = declaration.containingFile.virtualFile ?: return null
            return OpenFileDescriptor(declaration.project, file, declaration.textOffset)
        }

    override fun computeDocumentation(): DocumentationResult? {
        if (!declaration.isValid) return null
        val html = ZigDocumentationRenderer.render(declaration) ?: return null
        return DocumentationResult.documentation(html)
    }
}
