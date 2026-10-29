package com.zigrawr.zig.foreign.model

import com.zigrawr.lang.bin.MutBytes
import com.zigrawr.lang.primitives.PackedLong2
import com.zigrawr.zig.foreign.Layout
import com.zigrawr.zig.foreign.TokenPositions
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.ValueLayout

/**
 * *handshake* sanity check value to check correctness against native binary.
 */
const val AST_MAGIC = 0x5A524153

private const val AST_ABI_VERSION = 1

private val snapshotHeaderSize = Layout.Data.zrSnapshotHeader.byteSize().toInt()
private val headerMagicOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("magic")).toInt()
private val headerAbiVersionOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("abiVersion")).toInt()
private val headerModeOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("mode")).toInt()
private val headerSourceLengthOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("sourceLen")).toInt()
private val headerTokenCountOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("tokenCount")).toInt()
private val headerNodeCountOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("nodeCount")).toInt()
private val headerExtraDataCountOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("extraDataCount")).toInt()
private val headerErrorCountOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("errorCount")).toInt()
private val headerExtraDataOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("extraDataOffset")).toInt()
private val headerErrorsOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("errorsOffset")).toInt()
private val headerTotalSizeOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("totalSize")).toInt()

@PublishedApi
internal val headerTokenTagsOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("tokenTagsOffset")).toInt()

@PublishedApi
internal val headerTokenPositionsOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("tokenPositionsOffset")).toInt()

@PublishedApi
internal val headerNodeTagsOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("nodeTagsOffset")).toInt()

@PublishedApi
internal val headerNodeMainTokensOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("nodeMainTokensOffset")).toInt()

@PublishedApi
internal val headerNodePayloadsOffset =
    Layout.Data.zrSnapshotHeader.byteOffset(groupElement("nodePayloadsOffset")).toInt()

@PublishedApi
internal val tagSize = ValueLayout.JAVA_BYTE.byteSize().toInt()

@PublishedApi
internal val wordSize = ValueLayout.JAVA_INT.byteSize().toInt()

@PublishedApi
internal val tokenPositionsSize = Layout.Data.zrTokenPositions.byteSize().toInt()

@PublishedApi
internal val nodePayloadSize = Layout.Data.zrNodePayload.byteSize().toInt()

@PublishedApi
internal val nodePayloadFirstOffset =
    Layout.Data.zrNodePayload.byteOffset(groupElement("first")).toInt()

@PublishedApi
internal val nodePayloadSecondOffset =
    Layout.Data.zrNodePayload.byteOffset(groupElement("second")).toInt()

private val errorViewSize = Layout.Data.zrErrorView.byteSize().toInt()
private val errorTagOffset = Layout.Data.zrErrorView.byteOffset(groupElement("tag")).toInt()
private val errorTokenOffset = Layout.Data.zrErrorView.byteOffset(groupElement("token")).toInt()
private val errorFlagsOffset = Layout.Data.zrErrorView.byteOffset(groupElement("flags")).toInt()
private val errorExtraKindOffset =
    Layout.Data.zrErrorView.byteOffset(groupElement("extraKind")).toInt()
private val errorExtraValueOffset =
    Layout.Data.zrErrorView.byteOffset(groupElement("extraValue")).toInt()

@JvmInline
value class Ast(@PublishedApi internal val bytes: MutBytes) {

    init {
        require(bytes.size >= snapshotHeaderSize)
        require(word(headerMagicOffset) == AST_MAGIC)
        require(word(headerAbiVersionOffset) == AST_ABI_VERSION)
        require(word(headerModeOffset) in Mode.entries.indices)
        require(word(headerSourceLengthOffset) >= 0)
        require(word(headerTotalSizeOffset) == bytes.size)
        checkSection(headerTokenTagsOffset, headerTokenCountOffset, tagSize)
        checkSection(headerTokenPositionsOffset, headerTokenCountOffset, tokenPositionsSize)
        checkSection(headerNodeTagsOffset, headerNodeCountOffset, tagSize)
        checkSection(headerNodeMainTokensOffset, headerNodeCountOffset, wordSize)
        checkSection(headerNodePayloadsOffset, headerNodeCountOffset, nodePayloadSize)
        checkSection(headerExtraDataOffset, headerExtraDataCountOffset, wordSize)
        checkSection(headerErrorsOffset, headerErrorCountOffset, errorViewSize)
    }

    /**
     * `nodeIndex` is the node index, `nodeTag` its kind, and `tokenIndex` its main token index.
     * `payloadWord0` and `payloadWord1` are raw values interpreted according to `nodeTag`.
     * Inlined so that caller stays on stack.
     */
    inline fun forEachNode(block: (nodeIndex: Int, nodeTag: NodeTag, tokenIndex: Int, payloadWord0: Int, payloadWord1: Int) -> Unit) {
        var index = 0
        val count = nodeCount()
        val tagsOffset = bytes.getPackedInt(headerNodeTagsOffset)
        val mainTokensOffset = bytes.getPackedInt(headerNodeMainTokensOffset)
        val payloadsOffset = bytes.getPackedInt(headerNodePayloadsOffset)
        while (index < count) {
            val tag = NodeTag.entries[bytes[tagsOffset + index * tagSize].toInt() and 0xFF]
            val mainToken = bytes.getPackedInt(mainTokensOffset + index * wordSize)
            val payloadOffset = payloadsOffset + index * nodePayloadSize
            val payloadWord0 = bytes.getPackedInt(payloadOffset + nodePayloadFirstOffset)
            val payloadWord1 = bytes.getPackedInt(payloadOffset + nodePayloadSecondOffset)
            block(index, tag, mainToken, payloadWord0, payloadWord1)
            index++
        }
    }

    /**
     * `tokenIndex` is the token index, `tokenTag` its kind, and `positions` its half-open UTF-16 range.
     * Inlined so that caller stays on stack.
     */
    inline fun forEachToken(block: (tokenIndex: Int, tokenTag: TokenTag, positions: TokenPositions) -> Unit) {
        var index = 0
        val count = tokenCount()
        val tagsOffset = bytes.getPackedInt(headerTokenTagsOffset)
        val positionsOffset = bytes.getPackedInt(headerTokenPositionsOffset)
        while (index < count) {
            val tag = TokenTag.entries[bytes[tagsOffset + index * tagSize].toInt() and 0xFF]
            val positions = TokenPositions(
                PackedLong2(
                    bytes.getPackedLong(positionsOffset + index * tokenPositionsSize)
                )
            )
            block(index, tag, positions)
            index++
        }
    }

    fun mode(): Mode = Mode.entries[word(headerModeOffset)]

    /** Source length in UTF-16 code units. */
    fun sourceLength(): Int = word(headerSourceLengthOffset)

    fun tokenCount(): Int = word(headerTokenCountOffset)

    fun nodeCount(): Int = word(headerNodeCountOffset)

    fun extraDataCount(): Int = word(headerExtraDataCountOffset)

    fun errorCount(): Int = word(headerErrorCountOffset)

    fun tokenTag(index: Int): TokenTag {
        val entries = TokenTag.entries
        return entries[bytes[entryOffset(
            index,
            headerTokenCountOffset,
            headerTokenTagsOffset,
            tagSize
        )].toInt() and 0xFF]
    }

    /** Half-open token range relative to the parsed source, in UTF-16 code units. */
    fun tokenPositions(index: Int): TokenPositions =
        TokenPositions(
            PackedLong2(
                bytes.getPackedLong(
                    entryOffset(
                        index,
                        headerTokenCountOffset,
                        headerTokenPositionsOffset,
                        tokenPositionsSize
                    )
                )
            )
        )

    fun nodeTag(index: Int): NodeTag =
        NodeTag.entries[bytes[entryOffset(
            index,
            headerNodeCountOffset,
            headerNodeTagsOffset,
            tagSize
        )].toInt() and 0xFF]

    fun nodeMainToken(index: Int): Int =
        word(entryOffset(index, headerNodeCountOffset, headerNodeMainTokensOffset, wordSize))

    fun extraData(index: Int): Int =
        word(entryOffset(index, headerExtraDataCountOffset, headerExtraDataOffset, wordSize))

    fun errorTag(index: Int): ErrorTag =
        ErrorTag.entries[word(
            entryOffset(
                index,
                headerErrorCountOffset,
                headerErrorsOffset,
                errorViewSize
            ) + errorTagOffset
        )]

    fun errorToken(index: Int): Int =
        word(entryOffset(index, headerErrorCountOffset, headerErrorsOffset, errorViewSize) + errorTokenOffset)

    fun errorFlags(index: Int): Int =
        word(entryOffset(index, headerErrorCountOffset, headerErrorsOffset, errorViewSize) + errorFlagsOffset)

    fun errorIsNote(index: Int): Boolean = errorFlags(index) and 1 != 0

    fun errorTokenIsPrevious(index: Int): Boolean = errorFlags(index) and 2 != 0

    fun errorExtraKind(index: Int): ErrorExtraKind =
        ErrorExtraKind.entries[word(
            entryOffset(
                index,
                headerErrorCountOffset,
                headerErrorsOffset,
                errorViewSize
            ) + errorExtraKindOffset
        )]

    fun errorExtraValue(index: Int): Long =
        bytes.getPackedLong(
            entryOffset(
                index,
                headerErrorCountOffset,
                headerErrorsOffset,
                errorViewSize
            ) + errorExtraValueOffset
        )

    private fun word(offset: Int): Int = bytes.getPackedInt(offset)

    private fun entryOffset(index: Int, countField: Int, offsetField: Int, stride: Int): Int {
        val count = word(countField)
        if (index !in 0..<count) throw IndexOutOfBoundsException("Index $index out of bounds for size $count")
        return word(offsetField) + index * stride
    }

    private fun checkSection(offsetField: Int, countField: Int, stride: Int) {
        val offset = word(offsetField)
        val count = word(countField)
        require(offset >= snapshotHeaderSize && count >= 0 && offset.toLong() + count.toLong() * stride <= bytes.size)
    }
}
