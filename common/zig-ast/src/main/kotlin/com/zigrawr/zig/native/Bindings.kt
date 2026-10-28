package com.zigrawr.zig.native

import com.zigrawr.lang.bin.Bytes
import com.zigrawr.lang.bin.MutBytes
import com.zigrawr.zig.ast.ParseMode
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class Bindings(private val handles: Handles) {

    val state = handles.zrInit() as MemorySegment

    fun parseAtPath(path: Path): MutBytes {
        val str = path.toString()
        val mode = if (str.endsWith(".zig")) {
            ParseMode.Zig
        } else if (str.endsWith(".zon")) {
            ParseMode.Zon
        } else {
            error("Only .zig and .zon files are supported")
        }

        return Arena.ofConfined().use { arena ->
            FileChannel.open(path, StandardOpenOption.READ).use { channel ->
                val fileSize = channel.size()
                require(fileSize < Int.MAX_VALUE) { "Source file is too large" }
                var input = arena.allocate(fileSize + 1, 1)
                var buffer = input.asByteBuffer()

                while (true) {
                    if (!buffer.hasRemaining()) {
                        val sourceLen = buffer.position().toLong()
                        val nextCapacity = minOf(buffer.capacity().toLong() * 2, Int.MAX_VALUE.toLong())
                        require(nextCapacity > buffer.capacity()) { "Source file is too large" }
                        val expanded = arena.allocate(nextCapacity, 1)
                        expanded.asSlice(0, sourceLen).copyFrom(input.asSlice(0, sourceLen))
                        input = expanded
                        buffer = input.asByteBuffer()
                        buffer.position(sourceLen.toInt())
                    }
                    if (channel.read(buffer) < 0) break
                }

                val sourceLen = buffer.position().toLong()
                input.set(ValueLayout.JAVA_BYTE, sourceLen, 0.toByte())
                parseNative(arena, input, sourceLen, mode)
            }
        }
    }

    fun parseBytes(source: Bytes, mode: ParseMode): MutBytes {
        return Arena.ofConfined().use { arena ->
            val sourceLen = source.size.toLong()
            val input = arena.allocate(sourceLen + 1, 1)
            Bytes.unsafe {
                useInternal(source) {
                    input.asSlice(0, sourceLen).copyFrom(MemorySegment.ofArray(it))
                }
            }
            input.set(ValueLayout.JAVA_BYTE, sourceLen, 0.toByte())
            parseNative(arena, input, sourceLen, mode)
        }
    }

    fun parseChars(source: CharSequence, mode: ParseMode): MutBytes {
        return Arena.ofConfined().use { arena ->
            val capacity = Math.addExact(Math.multiplyExact(source.length, 3), 1)
            val input = arena.allocate(capacity.toLong(), 1)
            val inputBuffer = input.asByteBuffer()
            val encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            check(encoder.encode(CharBuffer.wrap(source), inputBuffer, true).isUnderflow)
            check(encoder.flush(inputBuffer).isUnderflow)
            val sourceLen = inputBuffer.position().toLong()
            input.set(ValueLayout.JAVA_BYTE, sourceLen, 0.toByte())
            parseNative(arena, input, sourceLen, mode)
        }
    }

    private fun parseNative(arena: Arena, input: MemorySegment, sourceLen: Long, mode: ParseMode): MutBytes {
        val required = arena.allocate(ValueLayout.JAVA_LONG)
        val first = handles.zrParse(
            state, input, sourceLen, mode.nativeValue,
            MemorySegment.NULL, 0L, required,
        ) as Int
        check(first == 2)

        val size = required.get(ValueLayout.JAVA_LONG, 0)
        check(size <= Int.MAX_VALUE)
        val output = arena.allocate(size, 8)

        val second = handles.zrParse(
            state, input, sourceLen, mode.nativeValue,
            output, size, required,
        ) as Int
        check(second == 0)

        return MutBytes(output.toArray(ValueLayout.JAVA_BYTE))
    }

    fun close() {
        handles.zrClose.invokeWithArguments(state)
    }
}
