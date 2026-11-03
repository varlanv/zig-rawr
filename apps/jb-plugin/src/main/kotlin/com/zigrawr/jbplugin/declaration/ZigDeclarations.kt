package com.zigrawr.jbplugin.declaration

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.jbplugin.IdeProjectState
import com.zigrawr.jbplugin.lexer.decodeZigStringLiteral
import com.zigrawr.lang.primitives.IntList
import com.zigrawr.lang.primitives.LongList
import com.zigrawr.zig.ast.ParseMode
import com.zigrawr.zig.foreign.model.Ast
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag
import java.util.*

private const val DECLARATION_WORDS = 7
private const val PUBLIC = 1
private const val FIELD = 2
private const val CAPTURE_SHIFT = 2
private val declarationsKey = Key.create<ZigDeclarations>("zigrawr.declarations")

internal enum class ZigCaptureKind { ERROR, OPTIONAL, ELEMENT, INDEX }

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
    private val nameHeads: IntArray
    private val nameNext: IntArray
    private val functionRanges: LongArray
    private val branchExpressions: LongArray
    @Volatile
    private var expressionEnds = LongArray(0)

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
        functionRanges = functions.toLongArray()
        Arrays.sort(functionRanges)
        val records = IntList()
        val branches = LongList()
        ast.forEachNode { node, tag, main, first, second ->
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
                        add(
                            records, name, owner, scopeEnd(owner), if (isNamespace(owner)) 0 else end + 1,
                            value, if (tag(name + 1) == TokenTag.COLON) name + 2 else -1,
                            visibility(node, main)
                        )
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
                                tag(index) == TokenTag.KEYWORD_NOALIAS
                            ) index++
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
                        add(
                            records, main, owner, scopeEnd(owner), 0, -1,
                            if (tag(main + 1) == TokenTag.COLON) main + 2 else -1, PUBLIC or FIELD
                        )
                    }
                }

                NodeTag.CATCH -> addCapture(records, main + 1, nodeEnd(second), nodeStart(first), ZigCaptureKind.ERROR)

                NodeTag.IF_SIMPLE, NodeTag.IF, NodeTag.WHILE_SIMPLE, NodeTag.WHILE_CONT, NodeTag.WHILE -> {
                    val close = matching.getOrElse(main + 1) { -1 }
                    if (close < 0) return@forEachNode
                    val thenNode = when (tag) {
                        NodeTag.IF_SIMPLE, NodeTag.WHILE_SIMPLE -> second
                        NodeTag.IF -> extra(second)
                        else -> extra(second + 1)
                    }
                    val elseNode = when (tag) {
                        NodeTag.IF -> extra(second + 1)
                        NodeTag.WHILE -> extra(second + 2)
                        else -> -1
                    }
                    val thenEnd = nodeEnd(thenNode)
                    addCapture(records, close + 1, thenEnd, nodeStart(first), ZigCaptureKind.OPTIONAL)
                    if (tag(thenEnd) == TokenTag.KEYWORD_ELSE) {
                        branches.add((thenEnd.toLong() shl 32) or main.toLong())
                        addCapture(records, thenEnd + 1, nodeEnd(elseNode), nodeStart(first), ZigCaptureKind.ERROR)
                    }
                }

                NodeTag.FOR_SIMPLE, NodeTag.FOR -> {
                    val close = matching.getOrElse(main + 1) { -1 }
                    if (close < 0 || tag(close + 1) != TokenTag.PIPE) return@forEachNode
                    val inputCount = if (tag == NodeTag.FOR_SIMPLE) 1 else second and Int.MAX_VALUE
                    if (inputCount <= 0 || inputCount > tokenCount) return@forEachNode
                    val thenNode = if (tag == NodeTag.FOR_SIMPLE) second else extra(first + inputCount)
                    val end = nodeEnd(thenNode)
                    var capture = close + 2
                    var input = 0
                    while (capture < tokenCount && tag(capture) != TokenTag.PIPE && input < inputCount) {
                        if (tag(capture) == TokenTag.ASTERISK) capture++
                        if (tag(capture) != TokenTag.IDENTIFIER) break
                        val inputNode = if (tag == NodeTag.FOR_SIMPLE) first else extra(first + input)
                        val kind =
                            if (inputNode in 1 until ast.nodeCount() && ast.nodeTag(inputNode) == NodeTag.FOR_RANGE)
                                ZigCaptureKind.INDEX else ZigCaptureKind.ELEMENT
                        addCaptureName(records, capture, close + 1, end, nodeStart(inputNode), kind)
                        capture++
                        input++
                        if (tag(capture) != TokenTag.COMMA) break
                        capture++
                    }
                }

                else -> Unit
            }
        }
        declarations = records.toIntArray()
        branchExpressions = branches.toLongArray()
        Arrays.sort(branchExpressions)
        val count = declarations.size / DECLARATION_WORDS
        var capacity = 1
        while (capacity < count) capacity = capacity shl 1
        nameHeads = IntArray(capacity) { -1 }
        nameNext = IntArray(count)
        for (record in 0 until count) {
            if (record and 255 == 0) ProgressManager.checkCanceled()
            val index = record * DECLARATION_WORDS
            val token = declarations[index]
            val span = ast.tokenPositions(token)
            var hash = 0
            if (source[span.start] == '@') hash = name(token).hashCode() else {
                for (offset in span.start until span.end) hash = 31 * hash + source[offset].code
            }
            val bucket = nameBucket(hash)
            nameNext[record] = nameHeads[bucket]
            nameHeads[bucket] = index
        }
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
        var index = nameHeads[nameBucket(name.hashCode())]
        while (index >= 0) {
            ProgressManager.checkCanceled()
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
                    (best < 0 || declarations[index] > declarations[best])
                ) {
                    best = index
                    bestWidth = width
                }
            }
            index = nameNext[index / DECLARATION_WORDS]
        }
        return best
    }

    private fun nameBucket(hash: Int): Int = (hash xor (hash ushr 16)) and (nameHeads.size - 1)

    fun nameToken(declaration: Int): Int = declarations[declaration]
    fun valueToken(declaration: Int): Int = declarations[declaration + 4]
    fun typeToken(declaration: Int): Int = declarations[declaration + 5]

    fun ownerToken(token: Int): Int = owners.getOrElse(token) { -1 }

    fun branchExpression(token: Int): Int {
        var low = 0
        var high = branchExpressions.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if ((branchExpressions[middle] ushr 32).toInt() < token) low = middle + 1 else high = middle
        }
        return if (low < branchExpressions.size && (branchExpressions[low] ushr 32).toInt() == token)
            branchExpressions[low].toInt() else -1
    }

    fun functionToken(token: Int): Int {
        val found = Arrays.binarySearch(functionRanges, (token.toLong() shl 32) or 0xffffffffL)
        var index = if (found >= 0) found else -found - 2
        while (index >= 0) {
            val range = functionRanges[index--]
            if (token <= range.toInt()) return (range ushr 32).toInt()
        }
        return -1
    }

    fun isErrorName(token: Int): Boolean = tag(token) == TokenTag.IDENTIFIER &&
        (tag(token - 1) == TokenTag.PERIOD && tag(token - 2) == TokenTag.KEYWORD_ERROR ||
            tag(ownerToken(token) - 1) == TokenTag.KEYWORD_ERROR)

    fun tokenEnd(declaration: Int): Int = declarations[declaration + 2]

    fun captureKind(declaration: Int): ZigCaptureKind? =
        ZigCaptureKind.entries.getOrNull((declarations[declaration + 6] ushr CAPTURE_SHIFT) - 1)

    fun isField(declaration: Int): Boolean = declarations[declaration + 6] and FIELD != 0

    fun fieldCount(namespace: Int): Int {
        var count = 0
        var index = 0
        while (index < declarations.size) {
            if (index and 255 == 0) ProgressManager.checkCanceled()
            if (declarations[index + 1] == namespace && isField(index)) count++
            index += DECLARATION_WORDS
        }
        return count
    }

    fun expressionEnd(token: Int): Int {
        val cached = expressionEnds
        for (entry in cached) if ((entry ushr 32).toInt() == token) return entry.toInt()
        var end = -1
        ast.forEachNode { node, _, main, _, _ ->
            if (node and 255 == 0) ProgressManager.checkCanceled()
            if (main == token && nodeStart(node) == token) end = maxOf(end, nodeEnd(node))
        }
        expressionEnds = cached.copyOf(cached.size + 1).also {
            it[cached.size] = (token.toLong() shl 32) or (end.toLong() and 0xffffffffL)
        }
        return end
    }

    fun isFunction(declaration: Int): Boolean = tag(nameToken(declaration) - 1) == TokenTag.KEYWORD_FN

    fun functionReturnToken(declaration: Int): Int {
        if (!isFunction(declaration)) return -1
        val paren = nameToken(declaration) + 1
        if (tag(paren) != TokenTag.L_PAREN || matching[paren] < 0) return -1
        var token = matching[paren] + 1
        while (tag(token) == TokenTag.KEYWORD_ALIGN || tag(token) == TokenTag.KEYWORD_ADDRSPACE ||
            tag(token) == TokenTag.KEYWORD_LINKSECTION || tag(token) == TokenTag.KEYWORD_CALLCONV
        ) {
            if (tag(token + 1) != TokenTag.L_PAREN || matching[token + 1] < 0) return -1
            token = matching[token + 1] + 1
        }
        return if (tag(token) == TokenTag.BANG) token + 1 else token
    }

    fun enclosingNamespace(token: Int): Int {
        var owner = owners[token]
        while (owner >= 0 && !namespaces[owner]) owner = owners[owner]
        return owner
    }

    fun namespacePath(token: Int): String? {
        val names = ArrayList<String>()
        var owner = enclosingNamespace(token)
        while (owner >= 0) {
            var nameToken = -1
            var index = 0
            while (index < declarations.size) {
                var value = declarations[index + 4]
                while (tag(value) == TokenTag.KEYWORD_PACKED || tag(value) == TokenTag.KEYWORD_EXTERN) value++
                when (tag(value)) {
                    TokenTag.KEYWORD_STRUCT, TokenTag.KEYWORD_UNION, TokenTag.KEYWORD_ENUM, TokenTag.KEYWORD_OPAQUE ->
                        if (containerBrace(value) == owner) {
                            nameToken = declarations[index]
                            break
                        }

                    else -> Unit
                }
                index += DECLARATION_WORDS
            }
            if (nameToken < 0) return null
            names.add(name(nameToken) ?: return null)
            owner = enclosingNamespace(owner)
        }
        return names.asReversed().joinToString(".")
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
        return decodeZigStringLiteral(source, span.start + prefix, span.end)
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

    private fun extra(index: Int): Int = if (index in 0 until ast.extraDataCount()) ast.extraData(index) else -1

    private fun nodeStart(node: Int): Int =
        if (node in 1 until ast.nodeCount()) tokenAt(ast.nodePositions(node).start) else -1

    private fun nodeEnd(node: Int): Int =
        if (node in 1 until ast.nodeCount()) tokenAt(ast.nodePositions(node).end - 1) + 1 else -1

    private fun addCapture(records: IntList, pipe: Int, end: Int, value: Int, kind: ZigCaptureKind) {
        if (tag(pipe) != TokenTag.PIPE) return
        val name = if (tag(pipe + 1) == TokenTag.ASTERISK) pipe + 2 else pipe + 1
        if (tag(name + 1) == TokenTag.PIPE) addCaptureName(records, name, pipe, end, value, kind)
    }

    private fun addCaptureName(records: IntList, name: Int, start: Int, end: Int, value: Int, kind: ZigCaptureKind) {
        if (tag(name) != TokenTag.IDENTIFIER || end <= name || matchesName(name, "_")) return
        add(records, name, start, end, name + 1, value, -1, (kind.ordinal + 1) shl CAPTURE_SHIFT)
    }

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

    private fun add(
        records: IntList, name: Int, start: Int, end: Int, visibleFrom: Int,
        value: Int, type: Int, flags: Int
    ) {
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
            val cache = file.project.service<IdeProjectState>()
            val cached = cache.cached(file, declarationsKey)
            if (cached != null && cached.stamp == stamp) return cached
            val source = document?.immutableCharSequence ?: file.viewProvider.contents
            val ast = Ast(IdeProcessState.getInstance().bindings.ast.parseChars(source, ParseMode.Zig))
            val result = ZigDeclarations(stamp, source, ast)
            cache.cache(file, declarationsKey, result)
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
