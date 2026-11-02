package com.zigrawr.jbplugin.builtin

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.zigrawr.jbplugin.lexer.ZigTokenTypes
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import com.zigrawr.zig.foreign.model.TokenTag

internal class ZigBuiltinPsiElement private constructor(
    internal val source: PsiElement,
    private val builtinName: String,
) : FakePsiElement() {
    val documentationUrl: String
        get() = entry()?.documentationUrl.orEmpty()

    internal fun entry(): ZigBuiltinEntry? = project.service<ZigToolchainService>().builtin(builtinName)

    override fun getParent(): PsiElement = source.containingFile
    override fun getName(): String = builtinName
    override fun getText(): String = builtinName
    override fun getTextOffset(): Int = source.textOffset
    override fun getLocationString(): String =
        "Zig ${project.service<ZigToolchainService>().currentInfo?.version ?: ""} builtin"

    override fun isValid(): Boolean = source.isValid && source.text == builtinName && entry() != null
    override fun canNavigate(): Boolean = isValid
    override fun canNavigateToSource(): Boolean = false

    override fun navigate(requestFocus: Boolean) {
        if (!source.isValid) return
        val url = documentationUrl
        if (url.isNotEmpty()) BrowserUtil.browse(url, project)
    }

    companion object {
        fun create(source: PsiElement): ZigBuiltinPsiElement? {
            if (source.node?.elementType != ZigTokenTypes.forTag(TokenTag.BUILTIN)) return null
            val name = source.text
            if (source.project.service<ZigToolchainService>().builtin(name) == null) return null
            return ZigBuiltinPsiElement(source, name)
        }
    }
}
