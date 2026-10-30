package com.zigrawr.zig.foreign.model

import com.zigrawr.lang.bin.MutBytes
import com.zigrawr.lang.primitives.PackedLong2
import com.zigrawr.zig.foreign.Layout
import com.zigrawr.zig.foreign.TokenPositions
import java.lang.foreign.MemoryLayout.PathElement.groupElement

private const val TOKENS_MAGIC = 0x5A52544B
private const val TOKENS_ABI_VERSION = 1
private val tokenizerHeaderSize = Layout.Data.zrTokenizerHeader.byteSize().toInt()
private val tokenizerMagicOffset = Layout.Data.zrTokenizerHeader.byteOffset(groupElement("magic")).toInt()
private val tokenizerAbiVersionOffset = Layout.Data.zrTokenizerHeader.byteOffset(groupElement("abiVersion")).toInt()
private val tokenizerSourceLengthOffset = Layout.Data.zrTokenizerHeader.byteOffset(groupElement("sourceLen")).toInt()
private val tokenizerTotalSizeOffset = Layout.Data.zrTokenizerHeader.byteOffset(groupElement("totalSize")).toInt()

@PublishedApi
internal val tokenizerCountOffset = Layout.Data.zrTokenizerHeader.byteOffset(groupElement("tokenCount")).toInt()

@PublishedApi
internal val tokenizerTagsOffset = Layout.Data.zrTokenizerHeader.byteOffset(groupElement("tokenTagsOffset")).toInt()

@PublishedApi
internal val tokenizerPositionsOffset =
    Layout.Data.zrTokenizerHeader.byteOffset(groupElement("tokenPositionsOffset")).toInt()

@JvmInline
value class Tokens(@PublishedApi internal val bytes: MutBytes) {

    init {
        require(bytes.size >= tokenizerHeaderSize)
        require(bytes.getPackedInt(tokenizerMagicOffset) == TOKENS_MAGIC)
        require(bytes.getPackedInt(tokenizerAbiVersionOffset) == TOKENS_ABI_VERSION)
        require(sourceLength() >= 0 && tokenCount() > 0)
        require(bytes.getPackedInt(tokenizerTotalSizeOffset) == bytes.size)
        checkSection(tokenizerTagsOffset, tagSize)
        checkSection(tokenizerPositionsOffset, tokenPositionsSize)
    }

    /** Source length in UTF-16 code units. */
    fun sourceLength(): Int = bytes.getPackedInt(tokenizerSourceLengthOffset)

    /** Includes the final, zero-length EOF token. */
    fun tokenCount(): Int = bytes.getPackedInt(tokenizerCountOffset)

    fun tokenTag(index: Int): TokenTag =
        TokenTag.entries[bytes[entryOffset(index, tokenizerTagsOffset, tagSize)].toInt() and 0xFF]

    /** Half-open UTF-16 range relative to the supplied input. */
    fun tokenPositions(index: Int): TokenPositions =
        TokenPositions(
            PackedLong2(
                bytes.getPackedLong(
                    entryOffset(
                        index,
                        tokenizerPositionsOffset,
                        tokenPositionsSize,
                    )
                )
            )
        )

    inline fun forEachToken(block: (tokenIndex: Int, tokenTag: TokenTag, positions: TokenPositions) -> Unit) {
        val count = tokenCount()
        val tagsOffset = bytes.getPackedInt(tokenizerTagsOffset)
        val positionsOffset = bytes.getPackedInt(tokenizerPositionsOffset)
        var index = 0
        while (index < count) {
            val tag = TokenTag.entries[bytes[tagsOffset + index * tagSize].toInt() and 0xFF]
            val positions =
                TokenPositions(PackedLong2(bytes.getPackedLong(positionsOffset + index * tokenPositionsSize)))
            block(index, tag, positions)
            index++
        }
    }

    private fun entryOffset(index: Int, offsetField: Int, stride: Int): Int {
        val count = tokenCount()
        if (index !in 0..<count) throw IndexOutOfBoundsException("Index $index out of bounds for size $count")
        return bytes.getPackedInt(offsetField) + index * stride
    }

    private fun checkSection(offsetField: Int, stride: Int) {
        val offset = bytes.getPackedInt(offsetField)
        require(offset >= tokenizerHeaderSize && offset.toLong() + tokenCount().toLong() * stride <= bytes.size)
    }
}
