package com.zigrawr.jbplugin.declaration

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag

internal object ZigInferredType {
    fun typeText(file: PsiFile, offset: Int): String? {
        ZigDeclarationResolver.propertyType(file, offset)?.let { return it }
        val declaration = ZigDeclarationResolver.resolve(file, offset) ?: return null
        val symbols = ZigDeclarations.get(declaration.containingFile)
        return declarationType(declaration.containingFile, symbols, symbols.tokenAt(declaration.textOffset), 0)
    }

    private fun declarationType(file: PsiFile, symbols: ZigDeclarations, token: Int, depth: Int): String? {
        ProgressManager.checkCanceled()
        if (depth >= 32) return null
        val name = symbols.name(token) ?: return null
        val index = symbols.find(name, token)
        if (index < 0 || symbols.isFunction(index)) return null
        val type = symbols.typeToken(index)
        if (type >= 0) return text(symbols, type, typeEnd(symbols, type))
        if (symbols.isField(index)) return symbols.namespacePath(token)
        val value = symbols.valueToken(index)
        return if (value < 0) null else expression(file, symbols, value, depth + 1)
    }

    private fun expression(file: PsiFile, symbols: ZigDeclarations, first: Int, depth: Int): String? {
        ProgressManager.checkCanceled()
        if (depth >= 32 || first < 0) return null
        var token = first
        val unwrap = symbols.tag(token) == TokenTag.KEYWORD_TRY
        if (unwrap) token++
        var result = when (symbols.tag(token)) {
            TokenTag.STRING_LITERAL -> {
                val length = stringByteLength(symbols, token) ?: return null
                "*const [$length:0]u8"
            }
            TokenTag.NUMBER_LITERAL -> {
                val number = symbols.text(token)
                if (number.indexOf('.') >= 0 || number.indexOfAny(charArrayOf('p', 'P')) >= 0 ||
                    (!number.startsWith("0x", true) && number.indexOfAny(charArrayOf('e', 'E')) >= 0)
                ) "comptime_float" else "comptime_int"
            }
            TokenTag.CHAR_LITERAL -> "comptime_int"
            TokenTag.AMPERSAND -> expression(file, symbols, token + 1, depth + 1)?.let { "*$it" }
            TokenTag.L_PAREN -> expression(file, symbols, token + 1, depth + 1)
            TokenTag.IDENTIFIER -> {
                val name = symbols.name(token)
                if (name == "true" || name == "false") "bool" else {
                    var end = token
                    while (symbols.tag(end + 1) == TokenTag.PERIOD && symbols.tag(end + 2) == TokenTag.IDENTIFIER) end += 2
                    if (symbols.tag(end + 1) == TokenTag.L_PAREN) {
                        val close = symbols.matching[end + 1]
                        if (close < 0) return null
                        val type = callResult(file, symbols, end, end + 1, depth + 1)
                        token = close
                        type
                    } else {
                        token = end
                        val offset = symbols.ast.tokenPositions(end).start
                        ZigDeclarationResolver.propertyType(file, offset) ?: run {
                            val resolved = ZigDeclarationResolver.resolve(file, offset) ?: return null
                            val target = ZigDeclarations.get(resolved.containingFile)
                            declarationType(resolved.containingFile, target, target.tokenAt(resolved.textOffset), depth + 1)
                        }
                    }
                }
            }
            else -> null
        } ?: return null
        if (symbols.tag(token + 1) == TokenTag.PERIOD && symbols.tag(token + 2) == TokenTag.IDENTIFIER) {
            val member = token + 2
            result = ZigDeclarationResolver.propertyType(file, symbols.ast.tokenPositions(member).start) ?: return null
            token = member
        }
        if (symbols.tag(token + 1) == TokenTag.PERIOD_ASTERISK) {
            result = result.removePrefix("*").removePrefix("const ").removePrefix("volatile ")
        }
        if (unwrap || symbols.tag(token + 1) == TokenTag.KEYWORD_CATCH) result = payload(result)
        if (symbols.tag(token + 1) == TokenTag.KEYWORD_ORELSE) result = result.removePrefix("?")
        return result
    }

    private fun callResult(
        file: PsiFile, source: ZigDeclarations, callee: Int, arguments: Int, depth: Int,
        argumentSymbols: ZigDeclarations = source,
    ): String? {
        if (depth >= 32) return null
        val declaration = ZigDeclarationResolver.resolve(file, source.ast.tokenPositions(callee).start) ?: return null
        val targetFile = declaration.containingFile
        val target = ZigDeclarations.get(targetFile)
        val nameToken = target.tokenAt(declaration.textOffset)
        val record = target.find(target.name(nameToken) ?: return null, nameToken)
        if (record < 0) return null
        if (!target.isFunction(record)) {
            var value = target.valueToken(record)
            if (value < 0) return null
            while (target.tag(value + 1) == TokenTag.PERIOD && target.tag(value + 2) == TokenTag.IDENTIFIER) value += 2
            return callResult(targetFile, target, value, arguments, depth + 1, argumentSymbols)
        }
        var type = target.functionReturnToken(record)
        if (type < 0) return null
        if (target.tag(type - 1) == TokenTag.BANG) type--
        val end = returnEnd(target, nameToken)
        if (end <= type) return null
        val params = nameToken + 2
        var parameter = params
        var argument = arguments + 1
        var substitutions: ArrayList<String>? = null
        while (parameter < target.matching[nameToken + 1] && argument < argumentSymbols.matching[arguments]) {
            while (target.tag(parameter) == TokenTag.KEYWORD_COMPTIME || target.tag(parameter) == TokenTag.KEYWORD_NOALIAS) parameter++
            if (target.tag(parameter + 1) == TokenTag.COLON && target.matchesName(parameter + 2, "type")) {
                val argumentEnd = argumentEnd(argumentSymbols, argument, argumentSymbols.matching[arguments])
                val name = target.name(parameter)
                if (name != null) {
                    val values = substitutions ?: ArrayList<String>(2).also { substitutions = it }
                    values.add(name)
                    values.add(text(argumentSymbols, argument, argumentEnd))
                }
            }
            parameter = argumentEnd(target, parameter, target.matching[nameToken + 1]) + 1
            argument = argumentEnd(argumentSymbols, argument, argumentSymbols.matching[arguments]) + 1
        }
        val values = substitutions ?: return text(target, type, end)
        return buildString {
            var offset = target.ast.tokenPositions(type).start
            for (token in type until end) {
                var replacement: String? = null
                if (target.tag(token) == TokenTag.IDENTIFIER) {
                    var index = 0
                    while (index < values.size) {
                        if (target.matchesName(token, values[index])) {
                            replacement = values[index + 1]
                            break
                        }
                        index += 2
                    }
                }
                if (replacement != null) {
                    val span = target.ast.tokenPositions(token)
                    append(target.source, offset, span.start)
                    append(replacement)
                    offset = span.end
                }
            }
            append(target.source, offset, target.ast.tokenPositions(end - 1).end)
        }
    }

    private fun argumentEnd(symbols: ZigDeclarations, first: Int, close: Int): Int {
        var token = first
        while (token < close && symbols.tag(token) != TokenTag.COMMA) {
            token = if (symbols.matching[token] > token) symbols.matching[token] + 1 else token + 1
        }
        return token
    }

    private fun returnEnd(symbols: ZigDeclarations, nameToken: Int): Int {
        symbols.ast.forEachNode { node, tag, main, _, _ ->
            if (node and 255 == 0) ProgressManager.checkCanceled()
            if (main + 1 == nameToken && (tag == NodeTag.FN_PROTO || tag == NodeTag.FN_PROTO_ONE ||
                    tag == NodeTag.FN_PROTO_SIMPLE || tag == NodeTag.FN_PROTO_MULTI)) {
                return symbols.tokenAt(symbols.ast.nodePositions(node).end - 1) + 1
            }
        }
        return -1
    }

    private fun typeEnd(symbols: ZigDeclarations, first: Int): Int {
        var token = first
        while (token < symbols.ast.tokenCount()) {
            when (symbols.tag(token)) {
                TokenTag.COMMA, TokenTag.R_PAREN, TokenTag.R_BRACE, TokenTag.EQUAL, TokenTag.SEMICOLON,
                TokenTag.EOF -> return token
                else -> token = if (symbols.matching[token] > token) symbols.matching[token] + 1 else token + 1
            }
        }
        return token
    }

    private fun text(symbols: ZigDeclarations, first: Int, end: Int): String =
        if (end <= first) "" else symbols.source.subSequence(
            symbols.ast.tokenPositions(first).start, symbols.ast.tokenPositions(end - 1).end
        ).toString()

    private fun payload(type: String): String {
        val unwrapped = if (type.startsWith('(') && type.endsWith(')')) type.substring(1, type.length - 1) else type
        val bang = unwrapped.indexOf('!')
        return if (bang >= 0) unwrapped.substring(bang + 1).trim() else unwrapped
    }

    private fun stringByteLength(symbols: ZigDeclarations, token: Int): Int? {
        val span = symbols.ast.tokenPositions(token)
        val source = symbols.source
        var index = span.start + 1
        val end = span.end - 1
        var length = 0
        while (index < end) {
            var codepoint = source[index++].code
            if (codepoint == '\\'.code) {
                if (index >= end) return null
                when (source[index++]) {
                    'x' -> {
                        if (index + 2 > end || source[index].digitToIntOrNull(16) == null ||
                            source[index + 1].digitToIntOrNull(16) == null) return null
                        index += 2
                        length++
                        continue
                    }
                    'u' -> {
                        if (index >= end || source[index++] != '{') return null
                        codepoint = 0
                        var digits = 0
                        while (index < end && source[index] != '}') {
                            val digit = source[index++].digitToIntOrNull(16) ?: return null
                            if (++digits > 6) return null
                            codepoint = (codepoint shl 4) or digit
                        }
                        if (digits == 0 || index >= end || source[index++] != '}' ||
                            codepoint > 0x10ffff || codepoint in 0xd800..0xdfff) return null
                    }
                    'n', 'r', 't', '\\', '\'', '"' -> codepoint = 0
                    else -> return null
                }
            } else if (codepoint in 0xd800..0xdbff && index < end && source[index].isLowSurrogate()) {
                codepoint = Character.toCodePoint(codepoint.toChar(), source[index++])
            }
            length += when {
                codepoint < 0x80 -> 1
                codepoint < 0x800 -> 2
                codepoint < 0x10000 -> 3
                else -> 4
            }
        }
        return length
    }
}
