package com.zigrawr.jbplugin.declaration

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.lang.primitives.IntList
import com.zigrawr.lang.primitives.LongList
import com.zigrawr.zig.ast.ParseMode
import com.zigrawr.zig.foreign.model.Ast
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag
import java.util.Arrays

private const val DECLARATION_WORDS = 7
private const val PUBLIC = 1
private const val FIELD = 2
private val declarationsKey = Key.create<ZigDeclarations>("zigrawr.declarations")

internal class ZigDeclarations private constructor(
    private val stamp: Long,
    val source: CharSequence,
    val ast: Ast,
) {
    private val tokenCount = ast.tokenCount()
    val matching = IntArray(tokenCount) { -1 }
    private val owners = IntArray(tokenCount) { -1 }
    private val namespaces = BooleanArray(tokenCount)
    private val declarations: IntArray

    init {
        buildDelimiters()
        val functions = LongList()
        ast.forEachNode { node, tag, main, _, _ ->
            if (node and 255 == 0) ProgressManager.checkCanceled()
            if (tag == NodeTag.FN_DECL) {
                functions.add((main.toLong() shl 32) or tokenAt(ast.nodePositions(node).end - 1).toLong())
            }
            if (tag.isContainer()) {
                val brace = containerBrace(main)
                if (brace >= 0) namespaces[brace] = true
            }
        }
        val functionRanges = functions.toLongArray()
        Arrays.sort(functionRanges)
        val records = IntList()
        ast.forEachNode { node, tag, main, _, _ ->
            if (node and 255 == 0) ProgressManager.checkCanceled()
            when (tag) {
                NodeTag.GLOBAL_VAR_DECL, NodeTag.LOCAL_VAR_DECL, NodeTag.SIMPLE_VAR_DECL, NodeTag.ALIGNED_VAR_DECL -> {
                    val name = main + 1
                    if (tag(name) == TokenTag.IDENTIFIER) {
                        val end = tokenAt(ast.nodePositions(node).end - 1)
                        var value = -1
                        var index = name + 1
                        while (index <= end) {
                            if (tag(index) == TokenTag.EQUAL) {
                                value = index + 1
                                break
                            }
                            index = nextExpressionToken(index)
                        }
                        val owner = owners[main]
                        add(records, name, owner, scopeEnd(owner), if (isNamespace(owner)) 0 else end + 1,
                            value, if (tag(name + 1) == TokenTag.COLON) name + 2 else -1,
                            visibility(node, main))
                    }
                }

                NodeTag.FN_PROTO_SIMPLE, NodeTag.FN_PROTO_MULTI, NodeTag.FN_PROTO_ONE, NodeTag.FN_PROTO -> {
                    val name = main + 1
                    val paren = if (tag(name) == TokenTag.IDENTIFIER) name + 1 else name
                    if (tag(paren) == TokenTag.L_PAREN && matching[paren] >= 0) {
                        val owner = owners[main]
                        if (tag(name) == TokenTag.IDENTIFIER) {
                            add(records, name, owner, scopeEnd(owner), 0, -1, -1, visibility(node, main))
                        }
                        val found = Arrays.binarySearch(functionRanges, (main.toLong() shl 32) or 0xffffffffL)
                        val functionIndex = if (found >= 0) found else -found - 2
                        val end = if (functionIndex >= 0 && (functionRanges[functionIndex] ushr 32).toInt() == main) {
                            functionRanges[functionIndex].toInt()
                        } else tokenAt(ast.nodePositions(node).end - 1)
                        var index = paren + 1
                        val close = matching[paren]
                        while (index < close) {
                            while (tag(index) == TokenTag.DOC_COMMENT || tag(index) == TokenTag.KEYWORD_COMPTIME ||
                                tag(index) == TokenTag.KEYWORD_NOALIAS) index++
                            if (tag(index) == TokenTag.IDENTIFIER && tag(index + 1) == TokenTag.COLON) {
                                add(records, index, main, end, index + 1, -1, index + 2, 0)
                            }
                            while (index < close && tag(index) != TokenTag.COMMA) index = nextExpressionToken(index)
                            index++
                        }
                    }
                }

                NodeTag.CONTAINER_FIELD, NodeTag.CONTAINER_FIELD_INIT, NodeTag.CONTAINER_FIELD_ALIGN -> {
                    if (tag(main) == TokenTag.IDENTIFIER) {
                        val owner = owners[main]
                        add(records, main, owner, scopeEnd(owner), 0, -1,
                            if (tag(main + 1) == TokenTag.COLON) main + 2 else -1, PUBLIC or FIELD)
                    }
                }

                else -> Unit
            }
        }
        declarations = records.toIntArray()
    }

    fun tokenAt(offset: Int): Int {
        var low = 0
        var high = tokenCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (ast.tokenPositions(middle).start <= offset) low = middle + 1 else high = middle
        }
        return low - 1
    }

    fun tag(token: Int): TokenTag = if (token in 0 until tokenCount) ast.tokenTag(token) else TokenTag.EOF

    fun text(token: Int): String {
        val span = ast.tokenPositions(token)
        return source.subSequence(span.start, span.end).toString()
    }

    fun name(token: Int): String? = if (tag(token) != TokenTag.IDENTIFIER) null else {
        val span = ast.tokenPositions(token)
        if (source[span.start] == '@') string(token, 1) else text(token)
    }

    fun find(name: String, token: Int, namespace: Int? = null, publicOnly: Boolean = false): Int {
        var best = -1
        var bestWidth = Int.MAX_VALUE
        var index = 0
        while (index < declarations.size) {
            if (index and 255 == 0) ProgressManager.checkCanceled()
            val start = declarations[index + 1]
            val end = declarations[index + 2]
            val flags = declarations[index + 6]
            val visible = if (namespace != null) start == namespace else {
                token == declarations[index] ||
                    (flags and FIELD == 0 && token > start && token < end && token >= declarations[index + 3])
            }
            if (visible && (!publicOnly || flags and PUBLIC != 0) && matchesName(declarations[index], name)) {
                val width = end - start
                if (width < bestWidth || width == bestWidth &&
                    (best < 0 || declarations[index] > declarations[best])) {
                    best = index
                    bestWidth = width
                }
            }
            index += DECLARATION_WORDS
        }
        return best
    }

    fun nameToken(declaration: Int): Int = declarations[declaration]
    fun valueToken(declaration: Int): Int = declarations[declaration + 4]
    fun typeToken(declaration: Int): Int = declarations[declaration + 5]

    fun enclosingNamespace(token: Int): Int {
        var owner = owners[token]
        while (owner >= 0 && !namespaces[owner]) owner = owners[owner]
        return owner
    }

    fun containerBrace(token: Int): Int {
        var index = token + 1
        while (index < tokenCount) {
            when (tag(index)) {
                TokenTag.L_BRACE -> return index
                TokenTag.L_PAREN, TokenTag.L_BRACKET -> {
                    if (matching[index] < 0) return -1
                    index = matching[index]
                }
                TokenTag.SEMICOLON, TokenTag.EOF -> return -1
                else -> Unit
            }
            index++
        }
        return -1
    }

    fun string(token: Int, prefix: Int = 0): String? {
        val span = ast.tokenPositions(token)
        var index = span.start + prefix
        val end = span.end - 1
        if (index >= end || source[index++] != '"' || source[end] != '"') return null
        val result = StringBuilder(end - index)
        while (index < end) {
            val char = source[index++]
            if (char != '\\') {
                result.append(char)
                continue
            }
            if (index >= end) return null
            when (val escaped = source[index++]) {
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                '\\', '"', '\'' -> result.append(escaped)
                'x' -> {
                    val bytes = ByteArray((end - index) / 4 + 1)
                    var count = 0
                    while (true) {
                        if (index + 2 > end) return null
                        val first = source[index++].digitToIntOrNull(16) ?: return null
                        val second = source[index++].digitToIntOrNull(16) ?: return null
                        bytes[count++] = ((first shl 4) or second).toByte()
                        if (index + 2 >= end || source[index] != '\\' || source[index + 1] != 'x') break
                        index += 2
                    }
                    result.append(String(bytes, 0, count, Charsets.UTF_8))
                }
                'u' -> {
                    if (index >= end || source[index++] != '{') return null
                    var codepoint = 0
                    var digits = 0
                    while (index < end && source[index] != '}') {
                        val digit = source[index++].digitToIntOrNull(16) ?: return null
                        if (++digits > 6) return null
                        codepoint = (codepoint shl 4) or digit
                    }
                    if (digits == 0 || index >= end || source[index++] != '}' ||
                        codepoint > 0x10ffff || codepoint in 0xd800..0xdfff) return null
                    result.appendCodePoint(codepoint)
                }
                else -> return null
            }
        }
        return result.toString()
    }

    private fun buildDelimiters() {
        var stack = IntArray(16)
        var depth = 0
        var owner = -1
        for (token in 0 until tokenCount) {
            if (token and 255 == 0) ProgressManager.checkCanceled()
            owners[token] = owner
            when (tag(token)) {
                TokenTag.L_PAREN, TokenTag.L_BRACKET, TokenTag.L_BRACE -> {
                    if (depth == stack.size) stack = stack.copyOf(depth * 2)
                    stack[depth++] = token
                    if (tag(token) == TokenTag.L_BRACE) owner = token
                }
                TokenTag.R_PAREN, TokenTag.R_BRACKET, TokenTag.R_BRACE -> {
                    val expected = when (tag(token)) {
                        TokenTag.R_PAREN -> TokenTag.L_PAREN
                        TokenTag.R_BRACKET -> TokenTag.L_BRACKET
                        else -> TokenTag.L_BRACE
                    }
                    if (depth > 0 && tag(stack[depth - 1]) == expected) {
                        val opener = stack[--depth]
                        matching[opener] = token
                        matching[token] = opener
                        if (expected == TokenTag.L_BRACE) owner = owners[opener]
                    }
                }
                else -> Unit
            }
        }
    }

    private fun nextExpressionToken(token: Int): Int =
        if (matching[token] > token) matching[token] + 1 else token + 1

    private fun isNamespace(owner: Int): Boolean = owner < 0 || namespaces[owner]
    private fun scopeEnd(owner: Int): Int = if (owner >= 0 && matching[owner] >= 0) matching[owner] else tokenCount

    private fun visibility(node: Int, main: Int): Int {
        var token = tokenAt(ast.nodePositions(node).start)
        while (token < main) {
            if (tag(token++) == TokenTag.KEYWORD_PUB) return PUBLIC
        }
        return 0
    }

    fun matchesName(token: Int, name: String): Boolean {
        val span = ast.tokenPositions(token)
        if (source[span.start] == '@') return string(token, 1) == name
        return span.end - span.start == name.length && source.regionMatches(span.start, name, 0, name.length)
    }

    private fun add(records: IntList, name: Int, start: Int, end: Int, visibleFrom: Int,
                    value: Int, type: Int, flags: Int) {
        records.ensureCapacity(DECLARATION_WORDS)
        records.addAssumeCapacity(name)
        records.addAssumeCapacity(start)
        records.addAssumeCapacity(end)
        records.addAssumeCapacity(visibleFrom)
        records.addAssumeCapacity(value)
        records.addAssumeCapacity(type)
        records.addAssumeCapacity(flags)
    }

    companion object {
        fun get(file: PsiFile): ZigDeclarations {
            val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
            val stamp = document?.modificationStamp ?: file.modificationStamp
            val cached = file.getUserData(declarationsKey)
            if (cached != null && cached.stamp == stamp) return cached
            val source = document?.immutableCharSequence ?: file.viewProvider.contents
            val ast = Ast(IdeProcessState.getInstance().bindings.ast.parseChars(source, ParseMode.Zig))
            val result = ZigDeclarations(stamp, source, ast)
            file.putUserData(declarationsKey, result)
            return result
        }
    }
}

private fun NodeTag.isContainer(): Boolean = when (this) {
    NodeTag.CONTAINER_DECL, NodeTag.CONTAINER_DECL_TRAILING,
    NodeTag.CONTAINER_DECL_TWO, NodeTag.CONTAINER_DECL_TWO_TRAILING,
    NodeTag.CONTAINER_DECL_ARG, NodeTag.CONTAINER_DECL_ARG_TRAILING,
    NodeTag.TAGGED_UNION, NodeTag.TAGGED_UNION_TRAILING,
    NodeTag.TAGGED_UNION_TWO, NodeTag.TAGGED_UNION_TWO_TRAILING,
    NodeTag.TAGGED_UNION_ENUM_TAG, NodeTag.TAGGED_UNION_ENUM_TAG_TRAILING -> true
    else -> false
}
