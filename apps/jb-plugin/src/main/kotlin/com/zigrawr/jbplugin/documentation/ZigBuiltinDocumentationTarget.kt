package com.zigrawr.jbplugin.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.SmartPointerManager
import com.zigrawr.jbplugin.builtin.ZigBuiltinPsiElement

internal class ZigBuiltinDocumentationTarget(private val builtin: ZigBuiltinPsiElement) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val source = SmartPointerManager.createPointer(builtin.source)
        val name = builtin.name
        return Pointer {
            source.element?.takeIf { it.text == name }?.let(ZigBuiltinPsiElement::create)
                ?.let(::ZigBuiltinDocumentationTarget)
        }
    }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(builtin.name).locationText(builtin.locationString).presentation()

    override val navigatable: Navigatable
        get() = builtin

    override fun computeDocumentation(): DocumentationResult? {
        if (!builtin.source.isValid) return null
        val entry = builtin.entry() ?: return null
        val html = buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append(StringUtil.escapeXmlEntities(entry.name))
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<p>Zig ")
            append(StringUtil.escapeXmlEntities(entry.version))
            append(" compiler builtin.</p>")
            append(
                entry.html
                    ?: "<p>The language reference is not bundled with this toolchain. Open the documentation link for details.</p>"
            )
            append(DocumentationMarkup.CONTENT_END)
        }
        return DocumentationResult.documentation(html).externalUrl(entry.documentationUrl)
    }
}
