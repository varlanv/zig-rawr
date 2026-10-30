package com.zigrawr.zig.foreign

import com.zigrawr.lang.bin.Bytes
import com.zigrawr.lang.bin.MutBytes
import com.zigrawr.zig.ast.ParseMode
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

// todo while efficient, this implementation lead to 2 threadlocal states in a single file
//      before starting refactoring, need to prepare benchmarks and establish baseline
private val utf8Buffers = ThreadLocal.withInitial(::Utf8Buffers)

private inline fun <T> useUtfBuffers(block: (Utf8Buffers) -> T): T {
    val cached = utf8Buffers.get()
    // in theory, custom CharSequence can reenter parsing while we copy its characters.
    // extremely unlikely, but doesn't cost anything to check
    val buffers = if (cached.inUse) Utf8Buffers() else cached
    buffers.inUse = true
    try {
        return block(buffers)
    } finally {
        buffers.inUse = false
    }
}

/**
 * Zig AST and tokenizer bindings facade.
 * Each thread owns a native state with a reusable arena. Calls return independent snapshots.
 * [close] releases every native state created by this instance.
 * Parsing may run concurrently. Call [close] only after all parses have completed.
 */
class Bindings(private val handles: Handles) {
    private val state = State(handles)
    val ast = Ast(state)
    val tokens = Tokens(state)

    class Ast internal constructor(internal val state: State) {

        fun parseAtPath(path: Path): MutBytes {
            val str = path.toString()
            val mode = if (str.endsWith(".zig")) {
                ParseMode.Zig
            } else if (str.endsWith(".zon")) {
                ParseMode.Zon
            } else {
                error("Only .zig and .zon files are supported")
            }

            return state.use { mem ->
                FileChannel.open(path, StandardOpenOption.READ).use { channel ->
                    val fileSize = channel.size()
                    require(fileSize < Int.MAX_VALUE) { "Source file is too large" }
                    var input = allocateInput(state.handles, mem, fileSize)
                    var buffer = input.asByteBuffer()

                    while (true) {
                        if (!buffer.hasRemaining()) {
                            val sourceLen = buffer.position().toLong()
                            val nextCapacity = minOf(buffer.capacity().toLong() * 2, Int.MAX_VALUE.toLong())
                            require(nextCapacity > buffer.capacity()) { "Source file is too large" }
                            val expanded = allocateInput(state.handles, mem, nextCapacity - 1)
                            expanded.asSlice(0, sourceLen).copyFrom(input.asSlice(0, sourceLen))
                            input = expanded
                            buffer = input.asByteBuffer()
                            buffer.position(sourceLen.toInt())
                        }
                        if (channel.read(buffer) < 0) break
                    }

                    val sourceLen = buffer.position().toLong()
                    input.set(ValueLayout.JAVA_BYTE, sourceLen, 0.toByte())
                    parseNative(mem, input, sourceLen, mode)
                }
            }
        }

        fun parseBytes(source: Bytes, mode: ParseMode): MutBytes {
            return state.useBytes(source) { mem, input, sourceLen ->
                parseNative(mem, input, sourceLen, mode)
            }
        }

        fun parseChars(source: CharSequence, mode: ParseMode): MutBytes {
            return state.useChars(source, 0, source.length) { mem, input, sourceLen ->
                parseNative(mem, input, sourceLen, mode)
            }
        }

        private fun parseNative(
            mem: MemorySegment,
            input: MemorySegment,
            sourceLen: Long,
            mode: ParseMode
        ): MutBytes {
            val result = state.handles.zrParseAst(mem, input, sourceLen, mode.nativeValue) as MemorySegment
            return copyResult(result, Layout.Data.zrSnapshotHeader.byteSize())
        }
    }

    class Tokens internal constructor(private val state: State) {

        /**
         * Tokenizes a standalone input range. The range must start at a lexical boundary;
         * returned token positions are relative to [startOffset], in UTF-16 code units.
         */
        fun parseChars(source: CharSequence, startOffset: Int = 0, endOffset: Int = source.length): MutBytes {
            return state.useChars(source, startOffset, endOffset) { mem, input, sourceLen ->
                parseNative(mem, input, sourceLen)
            }
        }

        fun parseBytes(source: Bytes): MutBytes {
            return state.useBytes(source) { mem, input, sourceLen ->
                parseNative(mem, input, sourceLen)
            }
        }

        private fun parseNative(mem: MemorySegment, input: MemorySegment, sourceLen: Long): MutBytes {
            val result = state.handles.zrParseTokens(mem, input, sourceLen) as MemorySegment
            return copyResult(result, Layout.Data.zrTokenizerHeader.byteSize())
        }
    }

    internal class State(internal val handles: Handles) {
        internal val states = ConcurrentLinkedQueue<MemorySegment>()
        internal val closed = AtomicBoolean()
        internal val threadState = ThreadLocal.withInitial {
            val state = handles.zrStartSession() as MemorySegment
            check(state != MemorySegment.NULL) { "Failed to initialize native parser" }
            states.add(state)
            state
        }

        inline fun <T> use(block: (MemorySegment) -> T): T {
            check(!closed.get()) { "Bindings are closed" }
            val state = threadState.get()
            try {
                return block(state)
            } finally {
                handles.zrFree(state)
            }
        }

        inline fun <T> useBytes(source: Bytes, block: (MemorySegment, MemorySegment, Long) -> T): T {
            return use { mem ->
                val sourceLen = source.size.toLong()
                val input = allocateInput(handles, mem, sourceLen)
                Bytes.unsafe {
                    useInternal(source) {
                        input.asSlice(0, sourceLen).copyFrom(MemorySegment.ofArray(it))
                    }
                }
                block(mem, input, sourceLen)
            }
        }
    }

    fun close() {
        if (state.closed.compareAndSet(false, true)) {
            while (true) {
                val state = state.states.poll() ?: break
                try {
                    handles.zrCloseSession.invokeWithArguments(state)
                } catch (_: Exception) {
                    // nothing to do here
                }
            }
            state.threadState.remove()
        }
    }
}

private inline fun <T> Bindings.State.useChars(
    source: CharSequence,
    startOffset: Int,
    endOffset: Int,
    block: (MemorySegment, MemorySegment, Long) -> T,
): T {
    require(startOffset in 0..endOffset && endOffset <= source.length)
    return useUtfBuffers { buffers ->
        val encoded = buffers.encode(source, startOffset, endOffset)
        use { mem ->
            val sourceLen = encoded.remaining().toLong()
            val input = allocateInput(handles, mem, sourceLen)
            input.asSlice(0, sourceLen).copyFrom(MemorySegment.ofBuffer(encoded))
            block(mem, input, sourceLen)
        }
    }
}

private fun copyResult(pointer: MemorySegment, minimumSize: Long): MutBytes {
    val result = pointer.reinterpret(Layout.Data.zrParseResult.byteSize())
    val status = result.get(ValueLayout.JAVA_INT, ValueLayout.ADDRESS.byteSize() + ValueLayout.JAVA_LONG.byteSize())
    check(status == 0) { "Native parse failed with status $status" }
    val output = result.get(ValueLayout.ADDRESS, 0)
    val size = result.get(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS.byteSize())
    check(output != MemorySegment.NULL) { "Native snapshot is null" }
    check(size in minimumSize..Int.MAX_VALUE.toLong()) { "Invalid native snapshot size: $size" }
    return MutBytes(output.reinterpret(size).toArray(ValueLayout.JAVA_BYTE))
}

private fun allocateInput(handles: Handles, mem: MemorySegment, sourceLen: Long): MemorySegment {
    val input = handles.zrAllocInput(mem, sourceLen) as MemorySegment
    check(input != MemorySegment.NULL) { "Failed to allocate native input" }
    return input.reinterpret(sourceLen + 1)
}

private class Utf8Buffers {
    var inUse = false
    private val encoder = StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var chars = CharBuffer.allocate(0)
    private var bytes = ByteBuffer.allocate(0)

    fun encode(source: CharSequence, startOffset: Int, endOffset: Int): ByteBuffer {
        val length = endOffset - startOffset
        if (length > chars.capacity()) {
            val capacity = maxOf(
                length.toLong(),
                minOf(chars.capacity().toLong() * 2, Int.MAX_VALUE / 3L),
            ).toInt()
            val byteCapacity = Math.multiplyExact(capacity, 3)
            val newChars = CharBuffer.allocate(capacity)
            val newBytes = ByteBuffer.allocate(byteCapacity)
            chars = newChars
            bytes = newBytes
        }

        val array = chars.array()
        for (index in 0 until length) array[index] = source[startOffset + index]
        chars.clear().limit(length)
        bytes.clear()
        encoder.reset()
        check(encoder.encode(chars, bytes, true).isUnderflow)
        check(encoder.flush(bytes).isUnderflow)
        return bytes.flip()
    }
}
