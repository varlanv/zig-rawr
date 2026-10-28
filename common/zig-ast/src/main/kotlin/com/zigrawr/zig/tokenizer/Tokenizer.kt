package com.zigrawr.zig.tokenizer

import com.zigrawr.lang.bin.Bytes
import com.zigrawr.lang.bin.BytesSlice
import com.zigrawr.lang.bin.HashedBytesSlice

private fun hasUtf8Bom(bytes: Bytes): Boolean {
    return bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
}

class Tokenizer(
    private val buffer: Bytes,
    index: Int,
) {

    private var index: ZigSourcePosition

    init {
        if (hasUtf8Bom(buffer)) {
            this.index = ZigSourcePosition(index + 3)
        } else {
            this.index = ZigSourcePosition(index)
        }
    }

    fun next(): Token {
        var state = State.start
        var tag = Tag.invalid
        val end: ZigSourcePosition
        var start = index
        while (true) {
            when (state) {
                State.start -> {
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value == buffer.size) {
                                return Token.pack(index, index, Tag.eof)
                            } else {
                                state = State.invalid
                                continue
                            }
                        }

                        ' '.code.toByte(), '\n'.code.toByte(), '\t'.code.toByte(), '\r'.code.toByte() -> {
                            start = index + 1
                            index = start
                            continue
                        }

                        '"'.code.toByte() -> {
                            tag = Tag.string_literal
                            state = State.string_literal
                            continue
                        }

                        '\''.code.toByte() -> {
                            tag = Tag.char_literal
                            state = State.char_literal
                            continue
                        }

                        '_'.code.toByte(),
                        in 'a'.code.toByte()..'z'.code.toByte(),
                        in 'A'.code.toByte()..'Z'.code.toByte() -> {
                            tag = Tag.identifier
                            state = State.identifier
                            continue
                        }

                        '@'.code.toByte() -> {
                            state = State.saw_at_sign
                            continue
                        }

                        '='.code.toByte() -> {
                            state = State.equal
                            continue
                        }

                        '!'.code.toByte() -> {
                            state = State.bang
                            continue
                        }

                        '|'.code.toByte() -> {
                            state = State.pipe
                            continue
                        }

                        '('.code.toByte() -> {
                            tag = Tag.l_paren
                            index += 1
                        }

                        ')'.code.toByte() -> {
                            tag = Tag.r_paren
                            index += 1
                        }

                        '['.code.toByte() -> {
                            tag = Tag.l_bracket
                            index += 1
                        }

                        ']'.code.toByte() -> {
                            tag = Tag.r_bracket
                            index += 1
                        }

                        ';'.code.toByte() -> {
                            tag = Tag.semicolon
                            index += 1
                        }

                        ','.code.toByte() -> {
                            tag = Tag.comma
                            index += 1
                        }

                        '?'.code.toByte() -> {
                            tag = Tag.question_mark
                            index += 1
                        }

                        ':'.code.toByte() -> {
                            tag = Tag.colon
                            index += 1
                        }

                        '%'.code.toByte() -> {
                            state = State.percent
                            continue
                        }

                        '*'.code.toByte() -> {
                            state = State.asterisk
                            continue
                        }

                        '+'.code.toByte() -> {
                            state = State.plus
                            continue
                        }

                        '<'.code.toByte() -> {
                            state = State.angle_bracket_left
                            continue
                        }

                        '>'.code.toByte() -> {
                            state = State.angle_bracket_right
                            continue
                        }

                        '^'.code.toByte() -> {
                            state = State.caret
                            continue
                        }

                        '\\'.code.toByte() -> {
                            tag = Tag.multiline_string_literal_line
                            state = State.backslash
                            continue
                        }

                        '{'.code.toByte() -> {
                            tag = Tag.l_brace
                            index += 1
                        }

                        '}'.code.toByte() -> {
                            tag = Tag.r_brace
                            index += 1
                        }

                        '~'.code.toByte() -> {
                            tag = Tag.tilde
                            index += 1
                        }

                        '.'.code.toByte() -> {
                            state = State.period
                            continue
                        }

                        '-'.code.toByte() -> {
                            state = State.minus
                            continue
                        }

                        '/'.code.toByte() -> {
                            state = State.slash
                            continue
                        }

                        '&'.code.toByte() -> {
                            state = State.ampersand
                            continue
                        }

                        in '0'.code.toByte()..'9'.code.toByte() -> {
                            tag = Tag.number_literal
                            index += 1
                            state = State.int
                            continue
                        }

                        else -> {
                            state = State.invalid
                            continue
                        }
                    }
                }

                State.expect_newline -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value == buffer.size) {
                                tag = Tag.invalid
                            } else {
                                state = State.invalid
                                continue
                            }
                        }

                        '\n'.code.toByte() -> {
                            index += 1
                            start = index
                            state = State.start
                            continue
                        }

                        else -> {
                            state = State.invalid
                            continue
                        }
                    }
                }

                State.invalid -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value == buffer.size) {
                                tag = Tag.invalid
                            } else {
                                state = State.invalid
                                continue
                            }
                        }

                        '\n'.code.toByte() -> tag = Tag.invalid

                        else -> {
                            state = State.invalid
                            continue
                        }
                    }
                }

                State.saw_at_sign -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte(), '\n'.code.toByte() -> tag = Tag.invalid
                        '"'.code.toByte() -> {
                            tag = Tag.identifier
                            state = State.string_literal
                            continue
                        }

                        '_'.code.toByte(),
                        in 'a'.code.toByte()..'z'.code.toByte(),
                        in 'A'.code.toByte()..'Z'.code.toByte() -> {
                            tag = Tag.builtin
                            state = State.builtin
                            continue
                        }

                        else -> {
                            state = State.invalid
                            continue
                        }
                    }
                }

                State.ampersand -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.ampersand_equal
                            index += 1
                        }

                        else -> tag = Tag.ampersand
                    }
                }

                State.asterisk -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.asterisk_equal
                            index += 1
                        }

                        '%'.code.toByte() -> {
                            state = State.asterisk_percent
                            continue
                        }

                        '|'.code.toByte() -> {
                            state = State.asterisk_pipe
                            continue
                        }

                        else -> tag = Tag.asterisk
                    }
                }

                State.asterisk_percent -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.asterisk_percent_equal
                            index += 1
                        }

                        else -> tag = Tag.asterisk_percent
                    }
                }

                State.asterisk_pipe -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.asterisk_pipe_equal
                            index += 1
                        }

                        else -> tag = Tag.asterisk_pipe
                    }
                }

                State.percent -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.percent_equal
                            index += 1
                        }

                        else -> tag = Tag.percent
                    }
                }

                State.plus -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.plus_equal
                            index += 1
                        }

                        '+'.code.toByte() -> {
                            tag = Tag.plus_plus
                            index += 1
                        }

                        '%'.code.toByte() -> {
                            state = State.plus_percent
                            continue
                        }

                        '|'.code.toByte() -> {
                            state = State.plus_pipe
                            continue
                        }

                        else -> tag = Tag.plus
                    }
                }

                State.plus_percent -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.plus_percent_equal
                            index += 1
                        }

                        else -> tag = Tag.plus_percent
                    }
                }

                State.plus_pipe -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.plus_pipe_equal
                            index += 1
                        }

                        else -> tag = Tag.plus_pipe
                    }
                }

                State.caret -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.caret_equal
                            index += 1
                        }

                        else -> tag = Tag.caret
                    }
                }

                State.identifier -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '_'.code.toByte(),
                        in 'a'.code.toByte()..'z'.code.toByte(),
                        in 'A'.code.toByte()..'Z'.code.toByte(),
                        in '0'.code.toByte()..'9'.code.toByte() -> {
                            state = State.identifier
                            continue
                        }

                        else -> {
                            val ident = HashedBytesSlice(BytesSlice(buffer, start.value, index.value - start.value))
                            val keyword = Tag.getKeyword(ident)
                            if (keyword != null) {
                                tag = keyword
                            }
                        }
                    }
                }

                State.builtin -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '_'.code.toByte(),
                        in 'a'.code.toByte()..'z'.code.toByte(),
                        in 'A'.code.toByte()..'Z'.code.toByte(),
                        in '0'.code.toByte()..'9'.code.toByte() -> {
                            state = State.builtin
                            continue
                        }

                        else -> {}
                    }
                }

                State.backslash -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> tag = Tag.invalid
                        '\\'.code.toByte() -> {
                            state = State.multiline_string_literal_line
                            continue
                        }

                        '\n'.code.toByte() -> tag = Tag.invalid
                        else -> {
                            state = State.invalid
                            continue
                        }
                    }
                }

                State.string_literal -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value != buffer.size) {
                                state = State.invalid
                                continue
                            } else {
                                tag = Tag.invalid
                            }
                        }

                        '\n'.code.toByte() -> tag = Tag.invalid
                        '\\'.code.toByte() -> {
                            state = State.string_literal_backslash
                            continue
                        }

                        '"'.code.toByte() -> index += 1
                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            state = State.string_literal
                            continue
                        }
                    }
                }

                State.string_literal_backslash -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte(), '\n'.code.toByte() -> tag = Tag.invalid
                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            state = State.string_literal
                            continue
                        }
                    }
                }

                State.char_literal -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value != buffer.size) {
                                state = State.invalid
                                continue
                            } else {
                                tag = Tag.invalid
                            }
                        }

                        '\n'.code.toByte() -> tag = Tag.invalid
                        '\\'.code.toByte() -> {
                            state = State.char_literal_backslash
                            continue
                        }

                        '\''.code.toByte() -> index += 1
                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            state = State.char_literal
                            continue
                        }
                    }
                }

                State.char_literal_backslash -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value != buffer.size) {
                                state = State.invalid
                                continue
                            } else {
                                tag = Tag.invalid
                            }
                        }

                        '\n'.code.toByte() -> tag = Tag.invalid
                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            state = State.char_literal
                            continue
                        }
                    }
                }

                State.multiline_string_literal_line -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value != buffer.size) {
                                state = State.invalid
                                continue
                            }
                        }

                        '\n'.code.toByte() -> {}
                        '\r'.code.toByte() -> {
                            if (index.value + 1 == buffer.size || buffer[index.value + 1] != '\n'.code.toByte()) {
                                state = State.invalid
                                continue
                            }
                        }

                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x0c.toByte(),
                        in 0x0e.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            state = State.multiline_string_literal_line
                            continue
                        }
                    }
                }

                State.bang -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.bang_equal
                            index += 1
                        }

                        else -> tag = Tag.bang
                    }
                }

                State.pipe -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.pipe_equal
                            index += 1
                        }

                        '|'.code.toByte() -> {
                            tag = Tag.pipe_pipe
                            index += 1
                        }

                        else -> tag = Tag.pipe
                    }
                }

                State.equal -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.equal_equal
                            index += 1
                        }

                        '>'.code.toByte() -> {
                            tag = Tag.equal_angle_bracket_right
                            index += 1
                        }

                        else -> tag = Tag.equal
                    }
                }

                State.minus -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '>'.code.toByte() -> {
                            tag = Tag.arrow
                            index += 1
                        }

                        '='.code.toByte() -> {
                            tag = Tag.minus_equal
                            index += 1
                        }

                        '%'.code.toByte() -> {
                            state = State.minus_percent
                            continue
                        }

                        '|'.code.toByte() -> {
                            state = State.minus_pipe
                            continue
                        }

                        else -> tag = Tag.minus
                    }
                }

                State.minus_percent -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.minus_percent_equal
                            index += 1
                        }

                        else -> tag = Tag.minus_percent
                    }
                }

                State.minus_pipe -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.minus_pipe_equal
                            index += 1
                        }

                        else -> tag = Tag.minus_pipe
                    }
                }

                State.angle_bracket_left -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '<'.code.toByte() -> {
                            state = State.angle_bracket_angle_bracket_left
                            continue
                        }

                        '='.code.toByte() -> {
                            tag = Tag.angle_bracket_left_equal
                            index += 1
                        }

                        else -> tag = Tag.angle_bracket_left
                    }
                }

                State.angle_bracket_angle_bracket_left -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.angle_bracket_angle_bracket_left_equal
                            index += 1
                        }

                        '|'.code.toByte() -> {
                            state = State.angle_bracket_angle_bracket_left_pipe
                            continue
                        }

                        else -> tag = Tag.angle_bracket_angle_bracket_left
                    }
                }

                State.angle_bracket_angle_bracket_left_pipe -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.angle_bracket_angle_bracket_left_pipe_equal
                            index += 1
                        }

                        else -> tag = Tag.angle_bracket_angle_bracket_left_pipe
                    }
                }

                State.angle_bracket_right -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '>'.code.toByte() -> {
                            state = State.angle_bracket_angle_bracket_right
                            continue
                        }

                        '='.code.toByte() -> {
                            tag = Tag.angle_bracket_right_equal
                            index += 1
                        }

                        else -> tag = Tag.angle_bracket_right
                    }
                }

                State.angle_bracket_angle_bracket_right -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '='.code.toByte() -> {
                            tag = Tag.angle_bracket_angle_bracket_right_equal
                            index += 1
                        }

                        else -> tag = Tag.angle_bracket_angle_bracket_right
                    }
                }

                State.period -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '.'.code.toByte() -> {
                            state = State.period_2
                            continue
                        }

                        '*'.code.toByte() -> {
                            tag = Tag.period_asterisk
                            index += 1
                        }

                        else -> tag = Tag.period
                    }
                }

                State.period_2 -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '.'.code.toByte() -> {
                            tag = Tag.ellipsis3
                            index += 1
                        }

                        else -> tag = Tag.ellipsis2
                    }
                }

                State.slash -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '/'.code.toByte() -> {
                            state = State.line_comment_start
                            continue
                        }

                        '='.code.toByte() -> {
                            tag = Tag.slash_equal
                            index += 1
                        }

                        else -> tag = Tag.slash
                    }
                }

                State.line_comment_start -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value != buffer.size) {
                                state = State.invalid
                                continue
                            } else {
                                return Token.pack(index, index, Tag.eof)
                            }
                        }

                        '!'.code.toByte() -> {
                            tag = Tag.container_doc_comment
                            state = State.doc_comment
                            continue
                        }

                        '\n'.code.toByte() -> {
                            index += 1
                            start = index
                            state = State.start
                            continue
                        }

                        '/'.code.toByte() -> {
                            state = State.doc_comment_start
                            continue
                        }

                        '\r'.code.toByte() -> {
                            state = State.expect_newline
                            continue
                        }

                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x0c.toByte(),
                        in 0x0e.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            state = State.line_comment
                            continue
                        }
                    }
                }

                State.doc_comment_start -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte(), '\n'.code.toByte() -> tag = Tag.doc_comment
                        '\r'.code.toByte() -> {
                            if (index.value + 1 < buffer.size && buffer[index.value + 1] == '\n'.code.toByte()) {
                                tag = Tag.doc_comment
                            } else {
                                state = State.invalid
                                continue
                            }
                        }

                        '/'.code.toByte() -> {
                            state = State.line_comment
                            continue
                        }

                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x0c.toByte(),
                        in 0x0e.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            tag = Tag.doc_comment
                            state = State.doc_comment
                            continue
                        }
                    }
                }

                State.line_comment -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte() -> {
                            if (index.value != buffer.size) {
                                state = State.invalid
                                continue
                            } else {
                                return Token.pack(index, index, Tag.eof)
                            }
                        }

                        '\n'.code.toByte() -> {
                            index += 1
                            start = index
                            state = State.start
                            continue
                        }

                        '\r'.code.toByte() -> {
                            state = State.expect_newline
                            continue
                        }

                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x0c.toByte(),
                        in 0x0e.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            state = State.line_comment
                            continue
                        }
                    }
                }

                State.doc_comment -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        0.toByte(), '\n'.code.toByte() -> {}
                        '\r'.code.toByte() -> {
                            if (index.value + 1 == buffer.size || buffer[index.value + 1] != '\n'.code.toByte()) {
                                state = State.invalid
                                continue
                            }
                        }

                        in 0x01.toByte()..0x09.toByte(),
                        in 0x0b.toByte()..0x0c.toByte(),
                        in 0x0e.toByte()..0x1f.toByte(),
                        0x7f.toByte() -> {
                            state = State.invalid
                            continue
                        }

                        else -> {
                            state = State.doc_comment
                            continue
                        }
                    }
                }

                State.int -> {
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '.'.code.toByte() -> {
                            state = State.int_period
                            continue
                        }

                        '_'.code.toByte(),
                        in 'a'.code.toByte()..'d'.code.toByte(),
                        in 'f'.code.toByte()..'o'.code.toByte(),
                        in 'q'.code.toByte()..'z'.code.toByte(),
                        in 'A'.code.toByte()..'D'.code.toByte(),
                        in 'F'.code.toByte()..'O'.code.toByte(),
                        in 'Q'.code.toByte()..'Z'.code.toByte(),
                        in '0'.code.toByte()..'9'.code.toByte() -> {
                            index += 1
                            state = State.int
                            continue
                        }

                        'e'.code.toByte(), 'E'.code.toByte(), 'p'.code.toByte(), 'P'.code.toByte() -> {
                            state = State.int_exponent
                            continue
                        }

                        else -> {}
                    }
                }

                State.int_exponent -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '-'.code.toByte(), '+'.code.toByte() -> {
                            index += 1
                            state = State.float
                            continue
                        }

                        else -> {
                            state = State.int
                            continue
                        }
                    }
                }

                State.int_period -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '_'.code.toByte(),
                        in 'a'.code.toByte()..'d'.code.toByte(),
                        in 'f'.code.toByte()..'o'.code.toByte(),
                        in 'q'.code.toByte()..'z'.code.toByte(),
                        in 'A'.code.toByte()..'D'.code.toByte(),
                        in 'F'.code.toByte()..'O'.code.toByte(),
                        in 'Q'.code.toByte()..'Z'.code.toByte(),
                        in '0'.code.toByte()..'9'.code.toByte() -> {
                            index += 1
                            state = State.float
                            continue
                        }

                        'e'.code.toByte(), 'E'.code.toByte(), 'p'.code.toByte(), 'P'.code.toByte() -> {
                            state = State.float_exponent
                            continue
                        }

                        else -> index = ZigSourcePosition(index.value - 1)
                    }
                }

                State.float -> {
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '_'.code.toByte(),
                        in 'a'.code.toByte()..'d'.code.toByte(),
                        in 'f'.code.toByte()..'o'.code.toByte(),
                        in 'q'.code.toByte()..'z'.code.toByte(),
                        in 'A'.code.toByte()..'D'.code.toByte(),
                        in 'F'.code.toByte()..'O'.code.toByte(),
                        in 'Q'.code.toByte()..'Z'.code.toByte(),
                        in '0'.code.toByte()..'9'.code.toByte() -> {
                            index += 1
                            state = State.float
                            continue
                        }

                        'e'.code.toByte(), 'E'.code.toByte(), 'p'.code.toByte(), 'P'.code.toByte() -> {
                            state = State.float_exponent
                            continue
                        }

                        else -> {}
                    }
                }

                State.float_exponent -> {
                    index += 1
                    when (if (index.value == buffer.size) 0.toByte() else buffer[index.value]) {
                        '-'.code.toByte(), '+'.code.toByte() -> {
                            index += 1
                            state = State.float
                            continue
                        }

                        else -> {
                            state = State.float
                            continue
                        }
                    }
                }
            }
            end = index
            break
        }
        return Token.pack(start, end, tag)
    }

    enum class State {
        start,
        expect_newline,
        identifier,
        builtin,
        string_literal,
        string_literal_backslash,
        multiline_string_literal_line,
        char_literal,
        char_literal_backslash,
        backslash,
        equal,
        bang,
        pipe,
        minus,
        minus_percent,
        minus_pipe,
        asterisk,
        asterisk_percent,
        asterisk_pipe,
        slash,
        line_comment_start,
        line_comment,
        doc_comment_start,
        doc_comment,
        int,
        int_exponent,
        int_period,
        float,
        float_exponent,
        ampersand,
        caret,
        percent,
        plus,
        plus_percent,
        plus_pipe,
        angle_bracket_left,
        angle_bracket_angle_bracket_left,
        angle_bracket_angle_bracket_left_pipe,
        angle_bracket_right,
        angle_bracket_angle_bracket_right,
        period,
        period_2,
        saw_at_sign,
        invalid,
    }
}
