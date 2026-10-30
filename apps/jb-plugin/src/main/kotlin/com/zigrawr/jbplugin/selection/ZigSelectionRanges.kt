package com.zigrawr.jbplugin.selection

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.zigrawr.lang.primitives.LongList
import com.zigrawr.lang.primitives.PackedLong2
import com.zigrawr.lang.primitives.stableEmptyIntArray
import com.zigrawr.zig.foreign.model.Ast
import com.zigrawr.zig.foreign.model.NodeTag
import com.zigrawr.zig.foreign.model.TokenTag
import java.util.*

/** Immutable ranges for one source version, shared by expand and shrink selection. */
internal class ZigSelectionRanges private constructor(
    private val sourceLength: Int,
    // High word: start. Low word: inverted end. Sorts by start ascending, end descending.
    private val ranges: LongArray,
) {
    // A balanced interval tree over the sorted ranges avoids scanning the file on each keypress.
    private val maximumEnds = buildSubtreeMaximumEnds(ranges)

    fun at(offset: Int): List<TextRange> {
        if (offset !in 0..sourceLength || ranges.isEmpty()) return emptyList()
        val matches = LongList()
        collectContainingRanges(offset, matches)
        val count = matches.size
        if (count == 0) return emptyList()
        val sorted = matches.backingArray()
        Arrays.sort(sorted, 0, count)
        val result = ArrayList<TextRange>(count)
        for (index in 0 until count) {
            val match = PackedLong2(sorted[index])
            result.add(TextRange(match.v1, match.v1 + match.v2))
        }
        return result
    }

    private fun collectContainingRanges(offset: Int, result: LongList) {
        val pendingBounds = LongArray(Int.SIZE_BITS - Integer.numberOfLeadingZeros(ranges.size))
        var pendingCount = 1
        pendingBounds[0] = PackedLong2.pack(0, ranges.size).packed
        while (pendingCount > 0) {
            val bounds = PackedLong2(pendingBounds[--pendingCount])
            val low = bounds.v1
            val high = bounds.v2
            val middle = (low + high) ushr 1
            if (maximumEnds[middle] < offset) continue
            val range = ranges[middle]
            val start = (range ushr 32).toInt()
            if (start <= offset) {
                val end = range.toInt().inv()
                // High word: length. Low word: start. Matches the final selection ordering.
                if (end >= offset) result.addPacked(start, end - start)
                if (middle + 1 < high) {
                    pendingBounds[pendingCount++] = PackedLong2.pack(middle + 1, high).packed
                }
            }
            if (low < middle) pendingBounds[pendingCount++] = PackedLong2.pack(low, middle).packed
        }
    }

    companion object {
        private fun buildSubtreeMaximumEnds(ranges: LongArray): IntArray {
            if (ranges.isEmpty()) {
                return stableEmptyIntArray
            }
            val maximumEnds = IntArray(ranges.size)
            val pendingBounds = LongArray(Int.SIZE_BITS - Integer.numberOfLeadingZeros(ranges.size))
            var depth = 1
            pendingBounds[0] = PackedLong2.pack(0, ranges.size).packed
            while (depth > 0) {
                val bounds = PackedLong2(pendingBounds[depth - 1])
                val low = bounds.v1
                val high = bounds.v2
                val middle = (low + high) ushr 1
                val leftMiddle = (low + middle) ushr 1
                val rightMiddle = (middle + 1 + high) ushr 1
                // Every stored end is positive; zero marks a subtree that is not built yet.
                if (low < middle && maximumEnds[leftMiddle] == 0) {
                    pendingBounds[depth++] = PackedLong2.pack(low, middle).packed
                    continue
                }
                if (middle + 1 < high && maximumEnds[rightMiddle] == 0) {
                    pendingBounds[depth++] = PackedLong2.pack(middle + 1, high).packed
                    continue
                }
                var maximum = ranges[middle].toInt().inv()
                if (low < middle) maximum = maxOf(maximum, maximumEnds[leftMiddle])
                if (middle + 1 < high) maximum = maxOf(maximum, maximumEnds[rightMiddle])
                maximumEnds[middle] = maximum
                depth--
            }
            return maximumEnds
        }

        fun build(ast: Ast, source: CharSequence): ZigSelectionRanges {
            val sourceLen = source.length
            require(ast.sourceLength() == sourceLen)
            val candidates = RangeBuilder(ast, source).build()
            val ranges = candidates.backingArray()
            val count = candidates.size
            Arrays.sort(ranges, 0, count)
            var uniqueCount = 0
            for (index in 0 until count) {
                if (index and 255 == 0) ProgressManager.checkCanceled()
                val range = ranges[index]
                if (uniqueCount == 0 || ranges[uniqueCount - 1] != range) ranges[uniqueCount++] = range
            }
            // Transfer ownership, trimming spare capacity and discarded duplicates from the snapshot.
            val compacted = if (uniqueCount == ranges.size) ranges else ranges.copyOf(uniqueCount)
            return ZigSelectionRanges(sourceLen, compacted)
        }
    }
}

private class RangeBuilder(private val ast: Ast, private val text: CharSequence) {
    private val tokenCount = ast.tokenCount()
    private val ranges = LongList()

    // AST positions already have end in the high word and start in the low word.
    private val nodeStartsByEnd = LongArray((ast.nodeCount() - 1).coerceAtLeast(0))
    private var nodeEndCount = 0
    private var nodeEndIndex = 0
    private val blockOpeners = BooleanArray(tokenCount)

    fun build(): LongList {
        ast.forEachNodePosition { node, tag, span ->
            if (node == 0) return@forEachNodePosition
            if (node and 255 == 0) ProgressManager.checkCanceled()
            val spanStart = span.start
            val spanEnd = span.end
            add(spanStart, spanEnd)
            addWholeLine(spanStart, spanEnd)
            nodeStartsByEnd[node - 1] = span.packed.packed
            when (tag) {
                NodeTag.BLOCK, NodeTag.BLOCK_SEMICOLON, NodeTag.BLOCK_TWO, NodeTag.BLOCK_TWO_SEMICOLON ->
                    blockOpeners[ast.nodeMainToken(node)] = true

                NodeTag.FN_DECL, NodeTag.TEST_DECL,
                NodeTag.GLOBAL_VAR_DECL, NodeTag.LOCAL_VAR_DECL, NodeTag.SIMPLE_VAR_DECL, NodeTag.ALIGNED_VAR_DECL,
                NodeTag.CONTAINER_FIELD, NodeTag.CONTAINER_FIELD_INIT, NodeTag.CONTAINER_FIELD_ALIGN,
                    -> addDocumentation(spanStart, spanEnd)

                else -> Unit
            }
        }

        Arrays.sort(nodeStartsByEnd)
        for (index in nodeStartsByEnd.indices) {
            if (index and 255 == 0) ProgressManager.checkCanceled()
            val span = nodeStartsByEnd[index]
            // Sorting by end then start puts the minimum start first for each end.
            if (nodeEndCount == 0 || (span ushr 32) != (nodeStartsByEnd[nodeEndCount - 1] ushr 32)) {
                nodeStartsByEnd[nodeEndCount++] = span
            }
        }

        // High word: opener token. Low word: first token of the current item.
        var delimiters = LongArray(minOf(16, tokenCount))
        var depth = 0
        var previousTokenEnd = 0
        ast.forEachToken { index, tag, span ->
            if (index == tokenCount - 1) return@forEachToken
            if (index and 255 == 0) ProgressManager.checkCanceled()
            val spanStart = span.start
            val spanEnd = span.end
            add(spanStart, spanEnd)
            when (tag) {
                TokenTag.L_PAREN, TokenTag.L_BRACKET, TokenTag.L_BRACE -> {
                    if (depth == delimiters.size) delimiters = delimiters.copyOf(maxOf(8, depth * 2))
                    delimiters[depth++] = PackedLong2.pack(index + 1, index).packed
                }

                TokenTag.COMMA -> if (depth > 0) {
                    val frame = PackedLong2(delimiters[depth - 1])
                    addItem(frame.v2, frame.v1, index, spanEnd)
                    delimiters[depth - 1] = PackedLong2.pack(index + 1, frame.v2).packed
                }

                TokenTag.R_PAREN, TokenTag.R_BRACKET, TokenTag.R_BRACE -> {
                    val expected = when (tag) {
                        TokenTag.R_PAREN -> TokenTag.L_PAREN
                        TokenTag.R_BRACKET -> TokenTag.L_BRACKET
                        else -> TokenTag.L_BRACE
                    }
                    while (depth > 0) {
                        depth--
                        val frame = PackedLong2(delimiters[depth])
                        val matches = ast.tokenTag(frame.v2) == expected
                        addItem(frame.v2, frame.v1, index)
                        addDelimited(frame.v2, spanStart, if (matches) spanEnd else spanStart)
                        if (matches) break
                    }
                }

                TokenTag.SEMICOLON -> if (index > 0) {
                    val start = nodeStartByEnd(previousTokenEnd)
                    val completeStart = nodeStartByEnd(spanEnd)
                    if (start >= 0 && (completeStart !in 0..start)) {
                        add(start, spanEnd)
                        addWholeLine(start, spanEnd)
                    }
                }

                TokenTag.MULTILINE_STRING_LITERAL_LINE -> add(spanStart + 2, spanEnd)
                TokenTag.STRING_LITERAL, TokenTag.CHAR_LITERAL, TokenTag.IDENTIFIER, TokenTag.INVALID -> {
                    val quote = when {
                        text[spanStart] == '"' || text[spanStart] == '\'' -> spanStart
                        text[spanStart] == '@' && spanStart + 1 < spanEnd && text[spanStart + 1] == '"' -> spanStart + 1
                        else -> -1
                    }
                    if (quote >= 0) add(quote + 1, spanEnd - if (tag == TokenTag.INVALID) 0 else 1)
                }

                else -> Unit
            }
            previousTokenEnd = spanEnd
        }
        while (depth > 0) {
            depth--
            val frame = PackedLong2(delimiters[depth])
            addItem(frame.v2, frame.v1, tokenCount - 1)
            addDelimited(frame.v2, text.length, text.length)
        }
        add(0, text.length)
        return ranges
    }

    private fun add(start: Int, end: Int) {
        if (start in 0..<end && end <= text.length) ranges.addPacked(end.inv(), start)
    }

    private fun nodeStartByEnd(end: Int): Int {
        // Token ends are queried in source order. Keep an equal end available for repeated lookups.
        while (nodeEndIndex < nodeEndCount && (nodeStartsByEnd[nodeEndIndex] ushr 32).toInt() < end) {
            nodeEndIndex++
        }
        if (nodeEndIndex == nodeEndCount) return -1
        val span = PackedLong2(nodeStartsByEnd[nodeEndIndex])
        return if (span.v2 == end) span.v1 else -1
    }

    private fun addDelimited(opener: Int, closingStart: Int, closingEnd: Int) {
        val span = ast.tokenPositions(opener)
        add(span.start, closingEnd)
        var start = span.end
        var end = closingStart
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        add(start, end)
    }

    private fun addItem(opener: Int, first: Int, end: Int, commaEnd: Int = -1) {
        if (first >= end || blockOpeners[opener]) return
        val start = ast.tokenPositions(first).start
        add(start, ast.tokenPositions(end - 1).end)
        if (commaEnd >= 0) {
            add(start, commaEnd)
            addWholeLine(start, commaEnd)
        }
    }

    private fun addDocumentation(start: Int, end: Int) {
        var low = 0
        var high = tokenCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (ast.tokenPositions(middle).start < start) low = middle + 1 else high = middle
        }
        var index = low - 1
        var docStart = start
        while (index >= 0 && ast.tokenTag(index) == TokenTag.DOC_COMMENT) {
            val previous = ast.tokenPositions(index--)
            if ((previous.end until docStart).any { !text[it].isWhitespace() }) break
            docStart = previous.start
        }
        if (docStart != start) {
            add(docStart, end)
            addWholeLine(docStart, end)
        }
    }

    private fun addWholeLine(start: Int, end: Int) {
        var lineStart = start
        while (lineStart > 0 && (text[lineStart - 1] == ' ' || text[lineStart - 1] == '\t')) lineStart--
        if (lineStart > 0 && text[lineStart - 1] != '\n') return
        var lineEnd = end
        val length = text.length
        while (lineEnd < length && (text[lineEnd] == ' ' || text[lineEnd] == '\t')) lineEnd++
        if (lineEnd + 1 < length && text[lineEnd] == '/' && text[lineEnd + 1] == '/') {
            while (lineEnd < length && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
        }
        if (lineEnd < length && text[lineEnd] == '\r') lineEnd++
        if (lineEnd < length && text[lineEnd] == '\n') lineEnd++
        else if (lineEnd < length) return
        add(lineStart, lineEnd)
    }
}
