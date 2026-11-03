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

private const val MAX_RESOLUTION_DEPTH = 64
private const val SLICE = 1
private const val ARRAY = 2
private const val MANY_POINTER = 3
private const val TYPE_INFO = 1
private const val CONTAINER_INFO = 2
private const val FIELD_NAMES = 3
private const val FIELD_ENUM = 4

private class Binding(
    val symbols: ZigDeclarations,
    val parameter: Int,
    val file: PsiFile,
    val values: ZigDeclarations,
    val token: Int,
    val parent: Binding?,
    val reflected: Namespace? = null,
    val valueBindings: Binding? = null,
)

private class Declaration(
    val file: PsiFile,
    val symbols: ZigDeclarations,
    val index: Int,
    val property: Int = 0,
    val owner: Namespace? = null,
    val bindings: Binding? = owner?.bindings,
) {
    fun nameToken(): Int = if (index < 0) -index - 1 else symbols.nameToken(index)
}

private class Namespace(
    val file: PsiFile,
    val symbols: ZigDeclarations,
    val scope: Int,
    val sequence: Int = 0,
    val element: Namespace? = null,
    val origin: Declaration? = null,
    val bindings: Binding? = null,
    val sequenceToken: Int = -1,
    val reflection: Int = 0,
) {
    fun withOrigin(declaration: Declaration): Namespace =
        Namespace(file, symbols, scope, sequence, element, declaration, bindings, sequenceToken, reflection)

    fun withReflection(kind: Int): Namespace =
        Namespace(file, symbols, scope, sequence, element, origin, bindings, sequenceToken, kind)
}

internal enum class ZigResolvedKind { FIELD, STATIC_FUNCTION, INSTANCE_FUNCTION }

internal object ZigDeclarationResolver {
    fun semanticKind(file: PsiFile, symbols: ZigDeclarations, token: Int): ZigResolvedKind? {
        val declaration = identifier(file, symbols, token, 0) ?: return null
        if (declaration.property != 0 || declaration.symbols.isField(declaration.index)) return ZigResolvedKind.FIELD
        val function = function(declaration, 0) ?: return null
        var first = function.symbols.nameToken(function.index) + 2
        val close = function.symbols.matching[first - 1]
        if (close < 0) return null
        while (function.symbols.tag(first) == TokenTag.DOC_COMMENT ||
            function.symbols.tag(first) == TokenTag.KEYWORD_COMPTIME ||
            function.symbols.tag(first) == TokenTag.KEYWORD_NOALIAS
        ) first++
        if (first >= close) return ZigResolvedKind.STATIC_FUNCTION
        val type = if (function.symbols.tag(first + 1) == TokenTag.COLON) first + 2 else first
        val receiver = expression(function.file, function.symbols, type, 0, close - 1)
        return if (receiver != null && receiver.sequence == 0) {
            ZigResolvedKind.INSTANCE_FUNCTION
        } else ZigResolvedKind.STATIC_FUNCTION
    }

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
        if (symbols.isErrorName(token)) {
            val scope = symbols.functionToken(token)
            val ranges = ArrayList<TextRange>()
            symbols.ast.forEachToken { index, tag, span ->
                if (index and 255 == 0) ProgressManager.checkCanceled()
                if (tag == TokenTag.IDENTIFIER && symbols.matchesName(index, name) && symbols.isErrorName(index) &&
                    symbols.functionToken(index) == scope
                ) ranges.add(TextRange(span.start, span.end))
            }
            return ranges
        }
        val target = identifier(file, symbols, token, 0) ?: return emptyList()
        val ranges = ArrayList<TextRange>()
        symbols.ast.forEachToken { index, tag, span ->
            if (index and 255 == 0) ProgressManager.checkCanceled()
            if (tag != TokenTag.IDENTIFIER || !symbols.matchesName(index, name)) return@forEachToken
            val candidate = identifier(file, symbols, index, 0) ?: return@forEachToken
            if (candidate.file == target.file && candidate.symbols === target.symbols && candidate.index == target.index &&
                candidate.property == target.property
            ) {
                if ((target.property != 0 || name == "len" || name == "ptr") &&
                    !sameOrigin(candidate.owner?.origin, target.owner?.origin)
                ) return@forEachToken
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
            declaration.symbols.ast.tokenPositions(declaration.nameToken()).start
        return declaration.file.findElementAt(targetOffset)
    }

    private fun sameOrigin(left: Declaration?, right: Declaration?, depth: Int = 0): Boolean {
        if (left == null || right == null) return left == right
        if (depth >= MAX_RESOLUTION_DEPTH) return false
        return left.file == right.file && left.symbols === right.symbols && left.index == right.index &&
            left.property == right.property && sameOrigin(left.owner?.origin, right.owner?.origin, depth + 1)
    }

    fun propertyType(file: PsiFile, offset: Int): String? {
        val symbols = ZigDeclarations.get(file)
        val token = symbols.tokenAt(offset)
        val declaration = identifier(file, symbols, token, 0) ?: return null
        if (declaration.property == 1) return "usize"
        if (declaration.property != 2) return null
        val receiver = declaration.owner ?: return null
        val first = receiver.sequenceToken
        if (first < 0 || receiver.symbols.tag(first) == TokenTag.STRING_LITERAL) return "[*:0]const u8"
        val close = receiver.symbols.matching[first]
        if (close < 0) return null
        var end = close + 1
        while (end < receiver.symbols.ast.tokenCount()) {
            when (receiver.symbols.tag(end)) {
                TokenTag.COMMA, TokenTag.SEMICOLON, TokenTag.EQUAL, TokenTag.R_PAREN, TokenTag.R_BRACE,
                TokenTag.L_BRACE, TokenTag.EOF -> break

                else -> Unit
            }
            end = if (receiver.symbols.matching[end] > end) receiver.symbols.matching[end] + 1 else end + 1
        }
        if (end <= close + 1) return null
        val sentinel = if (receiver.symbols.tag(first + 1) == TokenTag.COLON) {
            receiver.symbols.source.subSequence(
                receiver.symbols.ast.tokenPositions(first + 1).start, receiver.symbols.ast.tokenPositions(close).start
            ).toString()
        } else ""
        val element = receiver.symbols.source.subSequence(
            receiver.symbols.ast.tokenPositions(close + 1).start, receiver.symbols.ast.tokenPositions(end - 1).end
        )
        return "[*$sentinel]$element"
    }

    private fun identifier(
        file: PsiFile, symbols: ZigDeclarations, token: Int, depth: Int, bindings: Binding? = null
    ): Declaration? {
        ProgressManager.checkCanceled()
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        val name = symbols.name(token) ?: return null
        if (symbols.tag(token - 1) == TokenTag.PERIOD) {
            val receiver = if (implicitMember(symbols, token)) {
                expected(file, symbols, token - 1, depth + 1, bindings)
            } else receiver(file, symbols, token - 2, depth + 1, bindings)
            if (receiver == null) return null
            return member(receiver, name, file)
        }
        val index = symbols.find(name, token)
        return if (index < 0) null else Declaration(file, symbols, index, bindings = bindings)
    }

    private fun member(receiver: Namespace, name: String, source: PsiFile): Declaration? {
        if (receiver.reflection != 0 && receiver.reflection != FIELD_ENUM) return null
        if (receiver.sequence != 0) {
            val property = when {
                name == "len" && (receiver.sequence == SLICE || receiver.sequence == ARRAY) -> 1
                name == "ptr" && receiver.sequence == SLICE -> 2
                else -> return null
            }
            val origin = receiver.origin ?: return null
            return Declaration(origin.file, origin.symbols, origin.index, property, receiver)
        }
        val index = receiver.symbols.find(name, 0, receiver.scope, receiver.file != source)
        if (index >= 0 && receiver.reflection == FIELD_ENUM && !receiver.symbols.isField(index)) return null
        return if (index < 0) null else Declaration(receiver.file, receiver.symbols, index, owner = receiver)
    }

    private fun receiver(
        file: PsiFile, symbols: ZigDeclarations, token: Int, depth: Int, bindings: Binding? = null
    ): Namespace? {
        if (depth >= MAX_RESOLUTION_DEPTH || token < 0) return null
        return when (symbols.tag(token)) {
            TokenTag.IDENTIFIER -> {
                val declaration = identifier(file, symbols, token, depth + 1, bindings) ?: return null
                namespace(declaration, depth + 1)
            }

            TokenTag.R_PAREN -> {
                val open = symbols.matching[token]
                if (open < 0) return null
                if (symbols.tag(open - 1) == TokenTag.BUILTIN) expression(
                    file,
                    symbols,
                    open - 1,
                    depth + 1,
                    token,
                    bindings
                )
                else if (symbols.tag(open - 1) == TokenTag.IDENTIFIER) {
                    val declaration = identifier(file, symbols, open - 1, depth + 1, bindings) ?: return null
                    functionResult(declaration, depth + 1, file, symbols, open, callerBindings = bindings)
                } else expression(
                    file,
                    symbols,
                    open + 1,
                    depth + 1,
                    token - 1, bindings
                )
            }

            TokenTag.STRING_LITERAL -> Namespace(
                file, symbols, -1, SLICE, origin = Declaration(file, symbols, -token - 1), sequenceToken = token
            )

            TokenTag.PERIOD_ASTERISK -> receiver(file, symbols, token - 1, depth + 1, bindings)

            TokenTag.R_BRACKET -> {
                val open = symbols.matching[token]
                if (open < 0) return null
                val sequence = receiver(file, symbols, open - 1, depth + 1, bindings) ?: return null
                indexed(sequence, symbols, open, token)
            }

            else -> null
        }
    }

    private fun function(declaration: Declaration, depth: Int): Declaration? {
        ProgressManager.checkCanceled()
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        val symbols = declaration.symbols
        if (symbols.isFunction(declaration.index)) return declaration
        if (symbols.isField(declaration.index)) return null
        var token = symbols.valueToken(declaration.index)
        if (token < 0) return null
        if (symbols.tag(token) == TokenTag.L_PAREN) token++
        if (symbols.tag(token) == TokenTag.BUILTIN && symbols.text(token) == "@import" &&
            symbols.tag(token + 1) == TokenTag.L_PAREN
        ) token = symbols.matching[token + 1]
        while (token >= 0 && symbols.tag(token + 1) == TokenTag.PERIOD && symbols.tag(token + 2) == TokenTag.IDENTIFIER) {
            token += 2
        }
        if (token < 0 || symbols.tag(token + 1) == TokenTag.L_PAREN) return null
        val target = identifier(declaration.file, symbols, token, depth + 1, declaration.bindings) ?: return null
        return function(target, depth + 1)
    }

    private fun functionResult(
        declaration: Declaration, depth: Int,
        source: PsiFile = declaration.file, values: ZigDeclarations = declaration.symbols, arguments: Int = -1,
        evaluate: Boolean = false,
        callerBindings: Binding? = null,
    ): Namespace? {
        val function = function(declaration, depth + 1) ?: return null
        val symbols = function.symbols
        val bindings = callBindings(function, declaration, source, values, arguments, depth, callerBindings)
        val type = symbols.functionReturnToken(function.index)
        if (type < 0) return null
        if (evaluate || symbols.matchesName(type, "type")) {
            val body = functionBody(symbols, type)
            if (body < 0) return null
            return returnedValue(
                function.file,
                symbols,
                body + 1,
                symbols.matching[body],
                depth + 1,
                bindings,
                evaluate
            )
        }
        return expression(function.file, symbols, type, depth + 1, bindings = bindings)
    }

    private fun callBindings(
        function: Declaration, declaration: Declaration, source: PsiFile, values: ZigDeclarations,
        arguments: Int, depth: Int, callerBindings: Binding?,
    ): Binding? {
        val symbols = function.symbols
        var bindings = function.bindings
        if (arguments >= 0) {
            var parameter = symbols.nameToken(function.index) + 2
            val close = symbols.matching[parameter - 1]
            var argument = arguments + 1
            val argumentEnd = values.matching[arguments]
            var first = true
            while (parameter < close && argument < argumentEnd) {
                var comptime = false
                while (symbols.tag(parameter) == TokenTag.KEYWORD_COMPTIME ||
                    symbols.tag(parameter) == TokenTag.KEYWORD_NOALIAS || symbols.tag(parameter) == TokenTag.DOC_COMMENT
                ) {
                    if (symbols.tag(parameter) == TokenTag.KEYWORD_COMPTIME) comptime = true
                    parameter++
                }
                if (symbols.tag(parameter) != TokenTag.IDENTIFIER || symbols.tag(parameter + 1) != TokenTag.COLON) break
                val receiver = if (first && declaration.owner?.origin != null &&
                    values.tag(arguments - 2) == TokenTag.PERIOD
                ) expression(function.file, symbols, parameter + 2, depth + 1, close - 1, bindings) else null
                if (receiver == null || receiver.sequence != 0) {
                    if (comptime || symbols.matchesName(parameter + 2, "type")) {
                        bindings = Binding(
                            symbols,
                            parameter,
                            source,
                            values,
                            argument,
                            bindings,
                            valueBindings = callerBindings
                        )
                    }
                    argument = afterExpression(values, argument, argumentEnd)
                    if (values.tag(argument) == TokenTag.COMMA) argument++
                }
                parameter = afterExpression(symbols, parameter + 2, close)
                if (symbols.tag(parameter) == TokenTag.COMMA) parameter++
                first = false
            }
        }
        return bindings
    }

    private fun namespace(declaration: Declaration, depth: Int, evaluate: Boolean = false): Namespace? {
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        if (declaration.property != 0 || declaration.index < 0) return null
        val symbols = declaration.symbols
        when (symbols.captureKind(declaration.index)) {
            ZigCaptureKind.ERROR, ZigCaptureKind.INDEX -> return null
            ZigCaptureKind.ELEMENT -> {
                val input = expression(
                    declaration.file, symbols, symbols.valueToken(declaration.index), depth + 1,
                    bindings = declaration.bindings
                )
                return input?.element?.withOrigin(declaration)
            }

            else -> Unit
        }
        val type = symbols.typeToken(declaration.index)
        if (!evaluate && type >= 0 && symbols.name(type) != "type") {
            return expression(declaration.file, symbols, type, depth + 1, bindings = declaration.bindings)
                ?.withOrigin(declaration)
        }
        val value = symbols.valueToken(declaration.index)
        return if (value < 0) null else expression(
            declaration.file, symbols, value, depth + 1, bindings = declaration.bindings, evaluate = evaluate
        )?.withOrigin(declaration)
    }

    private fun expression(
        file: PsiFile, symbols: ZigDeclarations, first: Int, depth: Int,
        last: Int = Int.MAX_VALUE, bindings: Binding? = null, evaluate: Boolean = false,
    ): Namespace? {
        ProgressManager.checkCanceled()
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        if (first < 0) return null
        var token = payloadStart(symbols, first, last)
        while (symbols.tag(token) == TokenTag.ASTERISK || symbols.tag(token) == TokenTag.QUESTION_MARK ||
            symbols.tag(token) == TokenTag.KEYWORD_CONST || symbols.tag(token) == TokenTag.KEYWORD_VOLATILE ||
            symbols.tag(token) == TokenTag.KEYWORD_TRY || symbols.tag(token) == TokenTag.AMPERSAND ||
            symbols.tag(token) == TokenTag.KEYWORD_PACKED || symbols.tag(token) == TokenTag.KEYWORD_EXTERN
        ) token++
        while (symbols.tag(token) == TokenTag.KEYWORD_ALIGN || symbols.tag(token) == TokenTag.KEYWORD_ADDRSPACE) {
            if (symbols.tag(token + 1) != TokenTag.L_PAREN || symbols.matching[token + 1] < 0) return null
            token = symbols.matching[token + 1] + 1
        }
        if (symbols.tag(token) == TokenTag.L_BRACKET) {
            val close = symbols.matching[token]
            if (close < 0) return null
            val kind = when (symbols.tag(token + 1)) {
                TokenTag.R_BRACKET, TokenTag.COLON -> SLICE
                TokenTag.ASTERISK -> MANY_POINTER
                else -> ARRAY
            }
            val element = expression(file, symbols, close + 1, depth + 1, last, bindings)
            return Namespace(file, symbols, -1, kind, element, bindings = bindings, sequenceToken = token)
        }
        var result = when (symbols.tag(token)) {
            TokenTag.IDENTIFIER -> {
                val bound = binding(symbols, token, bindings)
                if (bound != null) {
                    bound.reflected ?: expression(
                        bound.file,
                        bound.values,
                        bound.token,
                        depth + 1,
                        bindings = bound.valueBindings,
                        evaluate = evaluate
                    )
                } else {
                    val declaration = identifier(file, symbols, token, depth + 1, bindings) ?: return null
                    if (symbols.tag(token + 1) == TokenTag.L_PAREN) {
                        val close = symbols.matching[token + 1]
                        if (close < 0 || close > last) return null
                        val result =
                            functionResult(declaration, depth + 1, file, symbols, token + 1, evaluate, bindings)
                        token = close
                        result
                    } else namespace(declaration, depth + 1, evaluate)
                }
            }

            TokenTag.BUILTIN -> when (symbols.text(token)) {
                "@import" -> {
                    val imported = importedFile(file, symbols, token + 2) ?: return null
                    Namespace(imported, ZigDeclarations.get(imported), -1)
                }

                "@This" -> Namespace(file, symbols, symbols.enclosingNamespace(token), bindings = bindings)
                "@typeInfo" -> expression(file, symbols, token + 2, depth + 1, bindings = bindings)?.withReflection(
                    TYPE_INFO
                )

                "@Enum" -> {
                    val end = symbols.matching.getOrElse(token + 1) { -1 }
                    if (end < 0) return null
                    val mode = afterExpression(symbols, token + 2, end) + 1
                    val names = afterExpression(symbols, mode, end) + 1
                    if (names >= end) return null
                    val fields = expression(file, symbols, names, depth + 1, end - 1, bindings, true)
                    if (fields == null) return null
                    if (fields.reflection != FIELD_NAMES) return null
                    fields.withReflection(FIELD_ENUM)
                }

                "@FieldType" -> {
                    val owner = expression(file, symbols, token + 2, depth + 1, bindings = bindings) ?: return null
                    val comma = afterExpression(symbols, token + 2, symbols.matching[token + 1])
                    val name = fieldName(symbols, comma + 1, bindings) ?: return null
                    val field = member(owner, name, file) ?: return null
                    namespace(field, depth + 1)
                }

                else -> null
            }

            TokenTag.KEYWORD_STRUCT, TokenTag.KEYWORD_UNION, TokenTag.KEYWORD_ENUM, TokenTag.KEYWORD_OPAQUE -> {
                val brace = symbols.containerBrace(token)
                if (brace < 0) null else Namespace(file, symbols, brace, bindings = bindings)
            }

            TokenTag.L_PAREN -> expression(
                file,
                symbols,
                token + 1,
                depth + 1,
                symbols.matching[token] - 1,
                bindings,
                evaluate
            )

            TokenTag.STRING_LITERAL -> receiver(file, symbols, token, depth + 1, bindings)
            TokenTag.KEYWORD_SWITCH -> typeSwitch(file, symbols, token, depth + 1, bindings, evaluate)
            else -> null
        } ?: return null
        if (symbols.tag(token) == TokenTag.BUILTIN && symbols.tag(token + 1) == TokenTag.L_PAREN) {
            token = symbols.matching[token + 1]
        } else if (symbols.tag(token) == TokenTag.L_PAREN) token = symbols.matching[token]
        while (token >= 0 && token + 1 <= last) {
            if (symbols.tag(token + 1) == TokenTag.L_BRACKET) {
                val close = symbols.matching[token + 1]
                if (close < 0 || close > last) return null
                result = indexed(result, symbols, token + 1, close) ?: return null
                token = close
                continue
            }
            if (symbols.tag(token + 1) != TokenTag.PERIOD || symbols.tag(token + 2) != TokenTag.IDENTIFIER ||
                token + 2 > last
            ) break
            val name = symbols.name(token + 2) ?: return null
            if (result.reflection == TYPE_INFO && name == containerKind(result)) {
                result = result.withReflection(CONTAINER_INFO)
                token += 2
                continue
            }
            if (result.reflection == CONTAINER_INFO && name == "field_names") {
                result = result.withReflection(FIELD_NAMES)
                token += 2
                continue
            }
            val declaration = member(result, name, file) ?: return null
            token += 2
            result = (if (symbols.tag(token + 1) == TokenTag.L_PAREN) {
                val close = symbols.matching[token + 1]
                if (close < 0 || close > last) return null
                val result = functionResult(declaration, depth + 1, file, symbols, token + 1, evaluate, bindings)
                token = close
                result
            } else namespace(declaration, depth + 1, evaluate)) ?: return null
        }
        return result
    }

    private fun indexed(sequence: Namespace, symbols: ZigDeclarations, open: Int, close: Int): Namespace? {
        if (sequence.sequence == 0) return null
        var token = open + 1
        while (token < close) {
            if (symbols.tag(token) == TokenTag.ELLIPSIS2) {
                return Namespace(
                    sequence.file, sequence.symbols, -1, SLICE, sequence.element,
                    sequence.origin, sequence.bindings, sequence.sequenceToken
                )
            }
            token = if (symbols.matching[token] > token) symbols.matching[token] + 1 else token + 1
        }
        return sequence.element
    }

    private fun binding(symbols: ZigDeclarations, token: Int, bindings: Binding?): Binding? {
        val name = symbols.name(token) ?: return null
        var current = bindings
        while (current != null) {
            if (current.symbols === symbols && symbols.matchesName(current.parameter, name)) {
                val record = symbols.find(name, token)
                if (record >= 0 && symbols.nameToken(record) == current.parameter || record < 0 && current.reflected != null) return current
            }
            current = current.parent
        }
        return null
    }

    private fun afterExpression(symbols: ZigDeclarations, first: Int, end: Int): Int {
        var token = first
        while (token < end && symbols.tag(token) != TokenTag.COMMA) {
            if (token and 255 == 0) ProgressManager.checkCanceled()
            token = if (symbols.matching[token] > token) symbols.matching[token] + 1 else token + 1
        }
        return token
    }

    private fun fieldName(symbols: ZigDeclarations, token: Int, bindings: Binding?, depth: Int = 0): String? {
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        return when (symbols.tag(token)) {
            TokenTag.STRING_LITERAL -> symbols.string(token)
            TokenTag.PERIOD -> symbols.name(token + 1)
            TokenTag.BUILTIN -> if (symbols.text(token) == "@tagName") fieldName(
                symbols,
                token + 2,
                bindings,
                depth + 1
            ) else null

            TokenTag.IDENTIFIER -> {
                val bound = binding(symbols, token, bindings) ?: return null
                fieldName(bound.values, bound.token, bound.valueBindings, depth + 1)
            }

            else -> null
        }
    }

    private fun typeSwitch(
        file: PsiFile, symbols: ZigDeclarations, token: Int, depth: Int, bindings: Binding?,
        evaluate: Boolean = false,
    ): Namespace? {
        val branch = typeBranch(file, symbols, token, depth, bindings) ?: return null
        var first = branch.first
        var context = bindings
        if (symbols.tag(first) == TokenTag.PIPE && symbols.tag(first + 2) == TokenTag.PIPE) {
            context =
                Binding(symbols, first + 1, file, symbols, -1, bindings, branch.type.withReflection(CONTAINER_INFO))
            first += 3
        }
        return expression(file, symbols, first, depth + 1, branch.end - 1, context, evaluate)
    }

    private class TypeBranch(val type: Namespace, val first: Int, val end: Int)

    private fun typeBranch(
        file: PsiFile, symbols: ZigDeclarations, token: Int, depth: Int, bindings: Binding?,
    ): TypeBranch? {
        if (depth >= MAX_RESOLUTION_DEPTH || symbols.tag(token + 2) != TokenTag.BUILTIN ||
            symbols.text(token + 2) != "@typeInfo"
        ) return null
        val type = expression(file, symbols, token + 4, depth + 1, bindings = bindings) ?: return null
        val name = containerKind(type) ?: return null
        val close = symbols.matching.getOrElse(token + 1) { -1 }
        if (close < 0 || symbols.tag(close + 1) != TokenTag.L_BRACE) return null
        val end = symbols.matching[close + 1]
        var branch = close + 2
        var matches = false
        var fallback = -1
        var isFallback = false
        while (branch < end) {
            if (symbols.tag(branch) == TokenTag.PERIOD && symbols.matchesName(branch + 1, name)) matches = true
            if (symbols.tag(branch) == TokenTag.KEYWORD_ELSE) isFallback = true
            if (symbols.tag(branch) == TokenTag.EQUAL_ANGLE_BRACKET_RIGHT) {
                val next = afterExpression(symbols, branch + 1, end)
                if (matches) return TypeBranch(type, branch + 1, next)
                if (isFallback) fallback = branch + 1
                branch = next + 1
                matches = false
                isFallback = false
            } else branch = if (symbols.matching[branch] > branch) symbols.matching[branch] + 1 else branch + 1
        }
        return if (fallback < 0) null else TypeBranch(type, fallback, afterExpression(symbols, fallback, end))
    }

    private fun containerKind(type: Namespace): String? {
        var keyword = type.scope - 1
        while (keyword >= 0 && type.symbols.tag(keyword) != TokenTag.KEYWORD_STRUCT &&
            type.symbols.tag(keyword) != TokenTag.KEYWORD_ENUM && type.symbols.tag(keyword) != TokenTag.KEYWORD_UNION
        ) {
            if (type.symbols.tag(keyword) == TokenTag.SEMICOLON || type.symbols.tag(keyword) == TokenTag.EQUAL) return null
            keyword--
        }
        if (keyword < 0) return null
        return type.symbols.text(keyword)
    }

    private fun functionBody(symbols: ZigDeclarations, first: Int): Int {
        var token = first
        while (token < symbols.ast.tokenCount()) {
            when (symbols.tag(token)) {
                TokenTag.L_BRACE -> if (symbols.tag(token - 1) != TokenTag.KEYWORD_ERROR) return token
                TokenTag.SEMICOLON, TokenTag.EOF -> return -1
                else -> Unit
            }
            token = if (symbols.matching[token] > token) symbols.matching[token] + 1 else token + 1
        }
        return -1
    }

    private fun returnedValue(
        file: PsiFile, symbols: ZigDeclarations, first: Int, end: Int, depth: Int,
        bindings: Binding?, evaluate: Boolean,
    ): Namespace? {
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        var token = first
        while (token < end) {
            ProgressManager.checkCanceled()
            when (symbols.tag(token)) {
                TokenTag.KEYWORD_RETURN -> return expression(
                    file,
                    symbols,
                    token + 1,
                    depth + 1,
                    end - 1,
                    bindings,
                    evaluate
                )

                TokenTag.KEYWORD_IF -> {
                    val close = symbols.matching.getOrElse(token + 1) { -1 }
                    if (close < 0) return null
                    val truth = condition(file, symbols, token + 2, close, depth + 1, bindings)
                    if (truth == null) return null
                    val next = symbols.expressionEnd(token)
                    if (next <= token) return null
                    if (truth) {
                        token = close + 1
                        if (symbols.tag(token) == TokenTag.L_BRACE) {
                            return returnedValue(
                                file,
                                symbols,
                                token + 1,
                                symbols.matching[token],
                                depth + 1,
                                bindings,
                                evaluate
                            )
                        }
                        continue
                    }
                    var afterThen = symbols.expressionEnd(close + 1)
                    if (symbols.tag(afterThen) == TokenTag.SEMICOLON) afterThen++
                    if (symbols.tag(afterThen) == TokenTag.KEYWORD_ELSE) {
                        token = afterThen + 1
                        if (symbols.tag(token) == TokenTag.L_BRACE) {
                            return returnedValue(
                                file,
                                symbols,
                                token + 1,
                                symbols.matching[token],
                                depth + 1,
                                bindings,
                                evaluate
                            )
                        }
                    } else token = next
                    continue
                }

                TokenTag.KEYWORD_SWITCH -> {
                    val branch = typeBranch(file, symbols, token, depth + 1, bindings)
                    if (branch == null) return null
                    if (symbols.tag(branch.first) != TokenTag.L_BRACE || symbols.matching[branch.first] != branch.first + 1) return null
                    val close = symbols.matching[token + 1]
                    token = symbols.matching[close + 1] + 1
                    continue
                }

                TokenTag.KEYWORD_CONST, TokenTag.KEYWORD_VAR -> {
                    while (token < end && symbols.tag(token) != TokenTag.SEMICOLON) {
                        token = if (symbols.matching[token] > token) symbols.matching[token] + 1 else token + 1
                    }
                }

                TokenTag.SEMICOLON -> Unit
                else -> return null
            }
            token++
        }
        return null
    }

    private fun condition(
        file: PsiFile, symbols: ZigDeclarations, first: Int, end: Int, depth: Int, bindings: Binding?,
    ): Boolean? {
        if (first + 1 == end && symbols.tag(first) == TokenTag.IDENTIFIER) {
            val span = symbols.ast.tokenPositions(first)
            if (span.end - span.start == 4 && symbols.source.regionMatches(span.start, "true", 0, 4)) return true
            if (span.end - span.start == 5 && symbols.source.regionMatches(span.start, "false", 0, 5)) return false
        }
        var comparison = first
        while (comparison < end && symbols.tag(comparison) != TokenTag.EQUAL_EQUAL &&
            symbols.tag(comparison) != TokenTag.BANG_EQUAL
        ) comparison++
        if (comparison + 2 != end || symbols.tag(comparison + 1) != TokenTag.NUMBER_LITERAL ||
            symbols.tag(comparison - 2) != TokenTag.PERIOD || !symbols.matchesName(comparison - 1, "len")
        ) return null
        val value = expression(file, symbols, first, depth + 1, comparison - 3, bindings, true)
        if (value == null) return null
        if (value.reflection != FIELD_NAMES) return null
        val number = symbols.text(comparison + 1).toIntOrNull() ?: return null
        val equal = value.symbols.fieldCount(value.scope) == number
        return if (symbols.tag(comparison) == TokenTag.EQUAL_EQUAL) equal else !equal
    }

    private fun implicitMember(symbols: ZigDeclarations, token: Int): Boolean = when (symbols.tag(token - 2)) {
        TokenTag.IDENTIFIER, TokenTag.R_PAREN, TokenTag.R_BRACKET, TokenTag.STRING_LITERAL,
        TokenTag.PERIOD_ASTERISK -> false

        else -> true
    }

    private fun initializer(
        file: PsiFile, symbols: ZigDeclarations, brace: Int, depth: Int, bindings: Binding?,
    ): Namespace? = if (symbols.tag(brace - 1) == TokenTag.PERIOD) {
        expected(file, symbols, brace - 1, depth + 1, bindings)
    } else receiver(file, symbols, brace - 1, depth + 1, bindings)

    private fun expected(
        file: PsiFile, symbols: ZigDeclarations, first: Int, depth: Int, bindings: Binding?,
    ): Namespace? {
        ProgressManager.checkCanceled()
        if (depth >= MAX_RESOLUTION_DEPTH) return null
        if (symbols.tag(first) == TokenTag.PERIOD && symbols.tag(first + 1) == TokenTag.IDENTIFIER) {
            var after = first + 2
            while (symbols.tag(after) == TokenTag.R_PAREN) {
                val open = symbols.matching[after]
                if (open < 0 || open >= first) break
                when (symbols.tag(open - 1)) {
                    TokenTag.IDENTIFIER, TokenTag.BUILTIN, TokenTag.R_PAREN, TokenTag.R_BRACKET -> break
                    else -> after++
                }
            }
            if (symbols.tag(after) == TokenTag.EQUAL_EQUAL || symbols.tag(after) == TokenTag.BANG_EQUAL) {
                return expression(file, symbols, after + 1, depth + 1, bindings = bindings)
            }
        }
        val owner = symbols.ownerToken(first)
        if (symbols.tag(first + 2) == TokenTag.EQUAL &&
            (symbols.tag(first - 1) == TokenTag.L_BRACE || symbols.tag(first - 1) == TokenTag.COMMA)
        ) return initializer(file, symbols, owner, depth + 1, bindings)
        if (owner >= 0 && symbols.tag(owner - 1) == TokenTag.R_PAREN) {
            val open = symbols.matching[owner - 1]
            if (open > 0 && symbols.tag(open - 1) == TokenTag.KEYWORD_SWITCH) {
                var labels = true
                var token = owner + 1
                while (token < first) {
                    when (symbols.tag(token)) {
                        TokenTag.EQUAL_ANGLE_BRACKET_RIGHT -> labels = false
                        TokenTag.COMMA -> labels = true
                        else -> Unit
                    }
                    token = if (symbols.matching[token] > token) symbols.matching[token] + 1 else token + 1
                }
                if (labels) return expression(file, symbols, open + 1, depth + 1, owner - 2, bindings)
            }
        }
        var token = first - 1
        while (token >= 0) {
            when (symbols.tag(token)) {
                TokenTag.KEYWORD_RETURN -> {
                    val function = symbols.functionToken(token)
                    val name = symbols.name(function + 1) ?: return null
                    val declaration = symbols.find(name, function + 1)
                    if (declaration < 0) return null
                    return functionResult(Declaration(file, symbols, declaration, bindings = bindings), depth + 1)
                }

                TokenTag.EQUAL -> return receiver(file, symbols, token - 1, depth + 1, bindings)
                TokenTag.EQUAL_EQUAL, TokenTag.BANG_EQUAL ->
                    return receiver(file, symbols, token - 1, depth + 1, bindings)

                TokenTag.KEYWORD_ELSE -> {
                    val branch = symbols.branchExpression(token)
                    return if (branch < 0) null else expected(file, symbols, branch, depth + 1, bindings)
                }

                TokenTag.L_BRACE -> {
                    if (symbols.tag(token - 1) == TokenTag.PERIOD || symbols.tag(token - 1) == TokenTag.IDENTIFIER) {
                        val type = initializer(file, symbols, token, depth + 1, bindings) ?: return null
                        var field = token + 1
                        var selected = -1
                        while (field < first) {
                            if (symbols.tag(field) == TokenTag.PERIOD && symbols.tag(field + 2) == TokenTag.EQUAL) {
                                selected = field + 1
                            }
                            field = if (symbols.matching[field] > field) symbols.matching[field] + 1 else field + 1
                        }
                        val name = symbols.name(selected) ?: return null
                        return member(type, name, file)?.let { namespace(it, depth + 1) }
                    }
                    if (symbols.tag(token - 1) == TokenTag.R_PAREN) {
                        val open = symbols.matching[token - 1]
                        if (open > 0 && symbols.tag(open - 1) == TokenTag.KEYWORD_SWITCH) {
                            return expected(file, symbols, open - 1, depth + 1, bindings)
                        }
                    }
                    return null
                }

                TokenTag.L_PAREN -> {
                    when (symbols.tag(token - 1)) {
                        TokenTag.KEYWORD_IF, TokenTag.KEYWORD_SWITCH, TokenTag.KEYWORD_WHILE -> return null
                        TokenTag.IDENTIFIER, TokenTag.BUILTIN, TokenTag.R_PAREN, TokenTag.R_BRACKET -> Unit
                        else -> return expected(file, symbols, token, depth + 1, bindings)
                    }
                    val callee = identifier(file, symbols, token - 1, depth + 1, bindings) ?: return null
                    val function = function(callee, depth + 1) ?: return null
                    var argument = token + 1
                    var index = 0
                    while (argument < first) {
                        val next = afterExpression(symbols, argument, symbols.matching[token])
                        if (next >= first) break
                        argument = next + 1
                        index++
                    }
                    val parameters = function.symbols
                    var parameter = parameters.nameToken(function.index) + 2
                    if (callee.owner?.origin != null) {
                        val receiverType = if (parameters.tag(parameter + 1) == TokenTag.COLON) {
                            expression(
                                function.file,
                                parameters,
                                parameter + 2,
                                depth + 1,
                                bindings = function.bindings
                            )
                        } else null
                        if (receiverType != null && receiverType.sequence == 0) index++
                    }
                    repeat(index) {
                        parameter = afterExpression(
                            parameters,
                            parameter,
                            parameters.matching[parameters.nameToken(function.index) + 1]
                        ) + 1
                    }
                    while (parameters.tag(parameter) == TokenTag.KEYWORD_COMPTIME ||
                        parameters.tag(parameter) == TokenTag.KEYWORD_NOALIAS
                    ) parameter++
                    if (parameters.tag(parameter + 1) != TokenTag.COLON) return null
                    return expression(function.file, parameters, parameter + 2, depth + 1, bindings = function.bindings)
                }

                TokenTag.SEMICOLON, TokenTag.R_BRACE -> return null
                TokenTag.R_PAREN, TokenTag.R_BRACKET -> {
                    val open = symbols.matching[token]
                    if (open < 0) return null
                    if (symbols.tag(open - 1) == TokenTag.KEYWORD_IF) {
                        return expected(file, symbols, open - 1, depth + 1, bindings)
                    }
                    token = open
                }

                else -> Unit
            }
            token--
        }
        return null
    }

    private fun payloadStart(symbols: ZigDeclarations, first: Int, last: Int): Int {
        var token = first
        while (token <= last && token < symbols.ast.tokenCount()) {
            when (symbols.tag(token)) {
                TokenTag.BANG -> return token + 1
                TokenTag.L_PAREN, TokenTag.L_BRACKET -> {
                    val close = symbols.matching[token]
                    if (close < 0) return first
                    token = close
                }

                TokenTag.L_BRACE -> {
                    if (symbols.tag(token - 1) != TokenTag.KEYWORD_ERROR) return first
                    val close = symbols.matching[token]
                    if (close < 0) return first
                    token = close
                }

                TokenTag.COMMA, TokenTag.SEMICOLON, TokenTag.EQUAL, TokenTag.R_PAREN, TokenTag.R_BRACE,
                TokenTag.KEYWORD_CATCH, TokenTag.KEYWORD_ORELSE, TokenTag.EOF -> return first

                else -> Unit
            }
            token++
        }
        return first
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
