package com.zigrawr.jbplugin.declaration

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.zigrawr.jbplugin.file.ZigFileType
import com.zigrawr.jbplugin.toolchain.ZigToolchainService
import com.zigrawr.zig.foreign.model.TokenTag

private const val MAX_RESOLUTION_DEPTH = 32

private class Declaration(val file: PsiFile, val symbols: ZigDeclarations, val index: Int)
private class Namespace(val file: PsiFile, val symbols: ZigDeclarations, val scope: Int)

internal object ZigDeclarationResolver {
    fun occurrences(file: PsiFile, offset: Int): List<TextRange> {
        val symbols = ZigDeclarations.get(file)
        val token = symbols.tokenAt(offset)
        if (token < 0 || offset >= symbols.ast.tokenPositions(token).end) return emptyList()
        if (symbols.tag(token) == TokenTag.BUILTIN) {
            val name = symbols.text(token)
            val ranges = ArrayList<TextRange>()
            symbols.ast.forEachToken { index, tag, span ->
                if (index and 255 == 0) ProgressManager.checkCanceled()
                if (tag == TokenTag.BUILTIN && span.end - span.start == name.length &&
                    symbols.source.regionMatches(span.start, name, 0, name.length)
                ) {
                    ranges.add(TextRange(span.start, span.end))
                }
            }
            return ranges
        }
        val name = symbols.name(token) ?: return emptyList()
        val target = identifier(file, symbols, token, 0) ?: return emptyList()
        val ranges = ArrayList<TextRange>()
        symbols.ast.forEachToken { index, tag, span ->
            if (index and 255 == 0) ProgressManager.checkCanceled()
            if (tag != TokenTag.IDENTIFIER || !symbols.matchesName(index, name)) return@forEachToken
            val candidate = identifier(file, symbols, index, 0) ?: return@forEachToken
            if (candidate.file == target.file && candidate.symbols === target.symbols && candidate.index == target.index) {
                ranges.add(TextRange(span.start, span.end))
            }
        }
        return ranges
    }

    fun resolve(file: PsiFile, offset: Int): PsiElement? {
        val symbols = ZigDeclarations.get(file)
        val token = symbols.tokenAt(offset)
        if (token < 0 || offset >= symbols.ast.tokenPositions(token).end) return null
        if (symbols.tag(token) == TokenTag.STRING_LITERAL) return importedFile(file, symbols, token)
        val declaration = identifier(file, symbols, token, 0) ?: return null
        val targetOffset =
            declaration.symbols.ast.tokenPositions(declaration.symbols.nameToken(declaration.index)).start
        return declaration.file.findElementAt(targetOffset)
    }

    private fun identifier(file: PsiFile, symbols: ZigDeclarations, token: Int, depth: Int): Declaration? {
        ProgressManager.checkCanceled()
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        val name = symbols.name(token) ?: return null
        if (symbols.tag(token - 1) == TokenTag.PERIOD) {
            val receiver = receiver(file, symbols, token - 2, depth + 1) ?: return null
            val index = receiver.symbols.find(name, 0, receiver.scope, receiver.file != file)
            return if (index < 0) null else Declaration(receiver.file, receiver.symbols, index)
        }
        val index = symbols.find(name, token)
        return if (index < 0) null else Declaration(file, symbols, index)
    }

    private fun receiver(file: PsiFile, symbols: ZigDeclarations, token: Int, depth: Int): Namespace? {
        if (depth >= MAX_RESOLUTION_DEPTH || token < 0) return null
        return when (symbols.tag(token)) {
            TokenTag.IDENTIFIER -> {
                val declaration = identifier(file, symbols, token, depth + 1) ?: return null
                namespace(declaration, depth + 1)
            }

            TokenTag.R_PAREN -> {
                val open = symbols.matching[token]
                if (open < 0) return null
                if (symbols.tag(open - 1) == TokenTag.BUILTIN) expression(file, symbols, open - 1, depth + 1, token)
                else if (symbols.tag(open - 1) != TokenTag.IDENTIFIER) expression(
                    file,
                    symbols,
                    open + 1,
                    depth + 1,
                    token - 1
                )
                else null
            }

            else -> null
        }
    }

    private fun namespace(declaration: Declaration, depth: Int): Namespace? {
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        val symbols = declaration.symbols
        val type = symbols.typeToken(declaration.index)
        if (type >= 0 && symbols.name(type) != "type") {
            return expression(declaration.file, symbols, type, depth + 1)
        }
        val value = symbols.valueToken(declaration.index)
        return if (value < 0) null else expression(declaration.file, symbols, value, depth + 1)
    }

    private fun expression(
        file: PsiFile, symbols: ZigDeclarations, first: Int, depth: Int,
        last: Int = Int.MAX_VALUE
    ): Namespace? {
        ProgressManager.checkCanceled()
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        var token = first
        while (symbols.tag(token) == TokenTag.ASTERISK || symbols.tag(token) == TokenTag.QUESTION_MARK ||
            symbols.tag(token) == TokenTag.KEYWORD_CONST || symbols.tag(token) == TokenTag.KEYWORD_VOLATILE
        ) token++
        var result = when (symbols.tag(token)) {
            TokenTag.IDENTIFIER -> {
                val declaration = identifier(file, symbols, token, depth + 1) ?: return null
                namespace(declaration, depth + 1)
            }

            TokenTag.BUILTIN -> when (symbols.text(token)) {
                "@import" -> {
                    val imported = importedFile(file, symbols, token + 2) ?: return null
                    Namespace(imported, ZigDeclarations.get(imported), -1)
                }

                "@This" -> Namespace(file, symbols, symbols.enclosingNamespace(token))
                else -> null
            }

            TokenTag.KEYWORD_STRUCT, TokenTag.KEYWORD_UNION, TokenTag.KEYWORD_ENUM, TokenTag.KEYWORD_OPAQUE -> {
                val brace = symbols.containerBrace(token)
                if (brace < 0) null else Namespace(file, symbols, brace)
            }

            TokenTag.L_PAREN -> expression(file, symbols, token + 1, depth + 1, symbols.matching[token] - 1)
            else -> null
        } ?: return null
        if (symbols.tag(token) == TokenTag.BUILTIN && symbols.tag(token + 1) == TokenTag.L_PAREN) {
            token = symbols.matching[token + 1]
        } else if (symbols.tag(token) == TokenTag.L_PAREN) token = symbols.matching[token]
        while (token >= 0 && token + 2 <= last && symbols.tag(token + 1) == TokenTag.PERIOD &&
            symbols.tag(token + 2) == TokenTag.IDENTIFIER
        ) {
            val name = symbols.name(token + 2) ?: return null
            val index = result.symbols.find(name, 0, result.scope, result.file != file)
            if (index < 0) return null
            result = namespace(Declaration(result.file, result.symbols, index), depth + 1) ?: return null
            token += 2
        }
        return result
    }

    private fun importedFile(file: PsiFile, symbols: ZigDeclarations, token: Int): PsiFile? {
        if (symbols.tag(token) != TokenTag.STRING_LITERAL || symbols.tag(token - 1) != TokenTag.L_PAREN ||
            symbols.tag(token - 2) != TokenTag.BUILTIN || symbols.text(token - 2) != "@import"
        ) return null
        val close = symbols.matching[token - 1]
        if (close != token + 1 && !(close == token + 2 && symbols.tag(token + 1) == TokenTag.COMMA)) return null
        val path = symbols.string(token) ?: return null
        if (path == "std") {
            val target = file.project.service<ZigToolchainService>().standardLibraryFile() ?: return null
            return PsiManager.getInstance(file.project).findFile(target)
        }
        if (!path.endsWith(".zig") || path.indexOf('\u0000') >= 0) return null
        val directory = file.virtualFile?.parent ?: return null
        val target = directory.findFileByRelativePath(path) ?: return null
        if (target.isDirectory || target.fileType != ZigFileType) return null
        return PsiManager.getInstance(file.project).findFile(target)
    }
}
