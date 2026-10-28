package com.zigrawr.zig.tokenizer

import com.zigrawr.lang.bin.Bytes
import com.zigrawr.lang.bin.HashedBytesSlice
import com.zigrawr.lang.bin.asReadonly
import com.zigrawr.lang.bin.toHashedSlice

/**
 * Represents a position in Zig sources.
 * It intentionally violates Zig spec which says Zig source file max size is effectively u32 - roughly, 4GB.
 * Java arrays are i32-indexed, and working on such big files is not in the scope anyway, intentional tradeoff
 * is to have max size that fits into 64 bits: first 28 bits for "start", next 28 bits for "end" last 8 bits for "tag".
 * As a result, maximum allowed size of Zig sources for this library is roughly 268MB.
 */
@JvmInline
value class ZigSourcePosition(val value: Int) {

    init {
        assert(value <= MAX)
        assert(value >= 0)
    }

    operator fun plus(other: Int): ZigSourcePosition {
        return ZigSourcePosition(value + other)
    }

    companion object {
        private const val MAX = 268_435_455
        val MAX_VALUE = ZigSourcePosition(MAX)
        val MIN_VALUE = ZigSourcePosition(0)
    }
}

/**
 * Packs start into bits 0..27, end into bits 28..55, and tag into bits 56..63.
 */
@JvmInline
value class Token internal constructor(val packed: Long) {

    /**
     * Token Tag.
     */
    val tag: Tag
        get() {
            val idx = (packed ushr 56).toInt()
            return Tag.entries[idx]
        }


    /**
     * Position where token starts.
     */
    val start: ZigSourcePosition
        get() {
            return ZigSourcePosition((packed and POSITION_MASK).toInt())
        }

    /**
     * Position where token ends.
     */
    val end: ZigSourcePosition
        get() {
            return ZigSourcePosition(((packed ushr 28) and POSITION_MASK).toInt())
        }


    companion object {

        const val POSITION_MASK = 0x0FFF_FFFFL

        fun pack(start: ZigSourcePosition, end: ZigSourcePosition, tag: Tag): Token {
            val startBits = start.value.toLong() and POSITION_MASK
            val endBits = (end.value.toLong() and POSITION_MASK) shl 28
            val tagBits = tag.ordinal.toLong() shl 56
            return Token(startBits or endBits or tagBits)
        }

        fun unpackStart(start: ZigSourcePosition): Long = start.value.toLong() and POSITION_MASK
    }

    // Tags enumeration ported from Zig compiler as-is https://codeberg.org/ziglang/zig/src/tag/0.17.0/lib/std/zig/tokenizer.zig
    enum class Tag(text: String?, symbol: String? = null) {


        invalid(null, "invalid token"),
        identifier(null, "an identifier"),
        string_literal(null, "a string literal"),
        multiline_string_literal_line(null, "a multiline string literal"),
        char_literal(null, "a character literal"),
        eof(null, "EOF"),
        builtin(null, "a builtin function"),
        bang("!"),
        pipe("|"),
        pipe_pipe("||"),
        pipe_equal("|="),
        equal("="),
        equal_equal("=="),
        equal_angle_bracket_right("=>"),
        bang_equal("!="),
        l_paren("("),
        r_paren(")"),
        semicolon(";"),
        percent("%"),
        percent_equal("%="),
        l_brace("{"),
        r_brace("}"),
        l_bracket("["),
        r_bracket("]"),
        period("."),
        period_asterisk("*"),
        ellipsis2(".."),
        ellipsis3("..."),
        caret("^"),
        caret_equal("^="),
        plus("+"),
        plus_plus("++"),
        plus_equal("+="),
        plus_percent("+%"),
        plus_percent_equal("+%="),
        plus_pipe("+|"),
        plus_pipe_equal("+|="),
        minus("-"),
        minus_equal("-="),
        minus_percent("-%"),
        minus_percent_equal("-%="),
        minus_pipe("-|"),
        minus_pipe_equal("-|="),
        asterisk("*"),
        asterisk_equal("*="),
        asterisk_percent("*%"),
        asterisk_percent_equal("*%="),
        asterisk_pipe("*|"),
        asterisk_pipe_equal("*|="),
        arrow("->"),
        colon(":"),
        slash("/"),
        slash_equal("/="),
        comma(","),
        ampersand("&"),
        ampersand_equal("&="),
        question_mark("?"),
        angle_bracket_left("<"),
        angle_bracket_left_equal("<="),
        angle_bracket_angle_bracket_left("<<"),
        angle_bracket_angle_bracket_left_equal("<<="),
        angle_bracket_angle_bracket_left_pipe("<<|"),
        angle_bracket_angle_bracket_left_pipe_equal("<<|="),
        angle_bracket_right(">"),
        angle_bracket_right_equal(">="),
        angle_bracket_angle_bracket_right(">>"),
        angle_bracket_angle_bracket_right_equal(">>="),
        tilde("~"),
        number_literal(null, "a number literal"),
        doc_comment(null, "a document comment"),
        container_doc_comment(null, "a document comment"),
        keyword_addrspace("addrspace"),
        keyword_align("align"),
        keyword_allowzero("allowzero"),
        keyword_and("and"),
        keyword_anyframe("anyframe"),
        keyword_anytype("anytype"),
        keyword_asm("asm"),
        keyword_break("break"),
        keyword_callconv("callconv"),
        keyword_catch("catch"),
        keyword_comptime("comptime"),
        keyword_const("const"),
        keyword_continue("continue"),
        keyword_defer("defer"),
        keyword_else("else"),
        keyword_enum("enum"),
        keyword_errdefer("errdefer"),
        keyword_error("error"),
        keyword_export("export"),
        keyword_extern(""),
        keyword_fn("fn"),
        keyword_for("for"),
        keyword_if("if"),
        keyword_inline("inline"),
        keyword_noalias("noalias"),
        keyword_noinline("noinline"),
        keyword_nosuspend("nosuspend"),
        keyword_opaque("opaque"),
        keyword_or("or"),
        keyword_orelse("orelse"),
        keyword_packed("packed"),
        keyword_pub("pub"),
        keyword_resume("resume"),
        keyword_return("return"),
        keyword_linksection("linksection"),
        keyword_struct("struct"),
        keyword_suspend("suspend"),
        keyword_switch("switch"),
        keyword_test("test"),
        keyword_threadlocal("threadlocal"),
        keyword_try("try"),
        keyword_union("union"),
        keyword_unreachable("unreachable"),
        keyword_var("var"),
        keyword_volatile("volatile"),
        keyword_while("while");

        val bytes: Bytes? = text?.encodeToByteArray()?.asReadonly()

        init {
            if (bytes != null) {
//            assert(bytes.bytes.isNotBlank()) todo
            }
        }

        companion object {

            private val keywords: Map<HashedBytesSlice, Tag>

            init {
                require(Tag.entries.size <= UByte.MAX_VALUE.toInt() + 1) {
                    "Token tags must fit in 8 bits"
                }
                val stringMap: Map<String, Tag> = mapOf(
                    "addrspace" to keyword_addrspace,
                    "align" to keyword_align,
                    "allowzero" to keyword_allowzero,
                    "and" to keyword_and,
                    "anyframe" to keyword_anyframe,
                    "anytype" to keyword_anytype,
                    "asm" to keyword_asm,
                    "break" to keyword_break,
                    "callconv" to keyword_callconv,
                    "catch" to keyword_catch,
                    "comptime" to keyword_comptime,
                    "const" to keyword_const,
                    "continue" to keyword_continue,
                    "defer" to keyword_defer,
                    "else" to keyword_else,
                    "enum" to keyword_enum,
                    "errdefer" to keyword_errdefer,
                    "error" to keyword_error,
                    "export" to keyword_export,
                    "extern" to keyword_extern,
                    "fn" to keyword_fn,
                    "for" to keyword_for,
                    "if" to keyword_if,
                    "inline" to keyword_inline,
                    "noalias" to keyword_noalias,
                    "noinline" to keyword_noinline,
                    "nosuspend" to keyword_nosuspend,
                    "opaque" to keyword_opaque,
                    "or" to keyword_or,
                    "orelse" to keyword_orelse,
                    "packed" to keyword_packed,
                    "pub" to keyword_pub,
                    "resume" to keyword_resume,
                    "return" to keyword_return,
                    "linksection" to keyword_linksection,
                    "struct" to keyword_struct,
                    "suspend" to keyword_suspend,
                    "switch" to keyword_switch,
                    "test" to keyword_test,
                    "threadlocal" to keyword_threadlocal,
                    "try" to keyword_try,
                    "union" to keyword_union,
                    "unreachable" to keyword_unreachable,
                    "var" to keyword_var,
                    "volatile" to keyword_volatile,
                    "while" to keyword_while,
                )
                keywords = stringMap.mapKeysTo(HashMap.newHashMap(Tag.entries.size)) { it.key.toHashedSlice() }
            }

            fun getKeyword(slice: HashedBytesSlice): Tag? = keywords[slice]
        }
    }
}
