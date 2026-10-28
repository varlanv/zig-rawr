package com.zigrawr.zig.tokenizer

import com.zigrawr.lang.bin.asReadonly
import io.kotest.matchers.shouldBe

class TokenizerSpec : BaseSpec({

    should("recognize keywords") {
        testTokenize("test const else", Token.Tag.keyword_test, Token.Tag.keyword_const, Token.Tag.keyword_else)
    }

    should("skip a line comment before top-level comptime") {
        testTokenize("// line comment\ncomptime {}\n", Token.Tag.keyword_comptime, Token.Tag.l_brace, Token.Tag.r_brace)
    }

    should("recognize an unknown length pointer followed by a C pointer") {
        testTokenize(
            "[*]u8\n[*c]u8",
            Token.Tag.l_bracket,
            Token.Tag.asterisk,
            Token.Tag.r_bracket,
            Token.Tag.identifier,
            Token.Tag.l_bracket,
            Token.Tag.asterisk,
            Token.Tag.identifier,
            Token.Tag.r_bracket,
            Token.Tag.identifier,
        )
    }

    should("recognize character literals with hex escapes") {
        testTokenize("'\\x1b'", Token.Tag.char_literal)
        testTokenize("'\\x1'", Token.Tag.char_literal)
    }

    should("reject newlines in character literals") {
        testTokenize("'\n'", Token.Tag.invalid, Token.Tag.invalid)
    }

    should("reject newlines in string literals") {
        testTokenize("\"\n\"", Token.Tag.invalid, Token.Tag.invalid)
    }

    should("recognize literals with unicode escapes without validating the escapes") {
        testTokenize("'\\u{3}'", Token.Tag.char_literal)
        testTokenize("'\\u{01}'", Token.Tag.char_literal)
        testTokenize("'\\u{2a}'", Token.Tag.char_literal)
        testTokenize("'\\u{3f9}'", Token.Tag.char_literal)
        testTokenize("'\\u{6E09aBc1523}'", Token.Tag.char_literal)
        testTokenize("\"\\u{440}\"", Token.Tag.string_literal)

        testTokenize("'\\u'", Token.Tag.char_literal)
        testTokenize("'\\u{{'", Token.Tag.char_literal)
        testTokenize("'\\u{}'", Token.Tag.char_literal)
        testTokenize("'\\u{s}'", Token.Tag.char_literal)
        testTokenize("'\\u{2z}'", Token.Tag.char_literal)
        testTokenize("'\\u{4a'", Token.Tag.char_literal)

        testTokenize("'\\u0333'", Token.Tag.char_literal)
        testTokenize("'\\U0333'", Token.Tag.char_literal)
    }

    should("recognize a unicode code point in a character literal") {
        testTokenize("'💩'", Token.Tag.char_literal)
    }

    should("recognize a float literal with an e exponent") {
        testTokenize(
            "a = 4.94065645841246544177e-324;\n",
            Token.Tag.identifier,
            Token.Tag.equal,
            Token.Tag.number_literal,
            Token.Tag.semicolon,
        )
    }

    should("recognize a float literal with a p exponent") {
        testTokenize(
            "a = 0x1.a827999fcef32p+1022;\n",
            Token.Tag.identifier,
            Token.Tag.equal,
            Token.Tag.number_literal,
            Token.Tag.semicolon,
        )
    }

    should("recognize character literals") {
        testTokenize("'c'", Token.Tag.char_literal)
    }

    should("reject invalid token characters and unfinished character literals") {
        testTokenize("#", Token.Tag.invalid)
        testTokenize("`", Token.Tag.invalid)
        testTokenize("'c", Token.Tag.invalid)
        testTokenize("'", Token.Tag.invalid)
        testTokenize("''", Token.Tag.char_literal)
        testTokenize("'\n'", Token.Tag.invalid, Token.Tag.invalid)
    }

    should("reject invalid characters in literals and comments") {
        testTokenize("\"\u0000\"", Token.Tag.invalid)
        testTokenize("`\u0000`", Token.Tag.invalid)
        testTokenize("//\u0000", Token.Tag.invalid)
        testTokenize("//\u001f", Token.Tag.invalid)
        testTokenize("//\u007f", Token.Tag.invalid)
    }

    should("accept UTF-8 bytes in line comments") {
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xc2.toByte(), 0x80.toByte()))
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xf4.toByte(), 0x8f.toByte(), 0xbf.toByte(), 0xbf.toByte()))
    }

    should("skip line comments containing invalid UTF-8 bytes") {
        testTokenize(byteArrayOf(0x2f, 0x2f, 0x80.toByte()))
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xbf.toByte()))
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xf8.toByte()))
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xff.toByte()))
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xc2.toByte(), 0xc0.toByte()))
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xe0.toByte()))
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xf0.toByte()))
        testTokenize(byteArrayOf(0x2f, 0x2f, 0xf0.toByte(), 0x90.toByte(), 0x80.toByte(), 0xc0.toByte()))
    }

    should("skip line comments containing unicode newline code points") {
        testTokenize("//\u0084")
        testTokenize("//\u0085")
        testTokenize("//\u0086")
        testTokenize("//\u2027")
        testTokenize("//\u2028")
        testTokenize("//\u2029")
        testTokenize("//\u202a")
    }

    should("recognize string identifiers and builtin functions") {
        testTokenize(
            "const @\"if\" = @import(\"std\");",
            Token.Tag.keyword_const,
            Token.Tag.identifier,
            Token.Tag.equal,
            Token.Tag.builtin,
            Token.Tag.l_paren,
            Token.Tag.string_literal,
            Token.Tag.r_paren,
            Token.Tag.semicolon,
        )
    }

    should("recognize a double pipe followed by an equals sign") {
        testTokenize("||=", Token.Tag.pipe_pipe, Token.Tag.equal)
    }

    should("distinguish line comments from documentation comments") {
        testTokenize("//")
        testTokenize("// a / b")
        testTokenize("// /")
        testTokenize("/// a", Token.Tag.doc_comment)
        testTokenize("///", Token.Tag.doc_comment)
        testTokenize("////")
        testTokenize("//!", Token.Tag.container_doc_comment)
        testTokenize("//!!", Token.Tag.container_doc_comment)
    }

    should("recognize identifiers around a line comment") {
        testTokenize(
            "    Unexpected,\n    // another\n    Another,",
            Token.Tag.identifier,
            Token.Tag.comma,
            Token.Tag.identifier,
            Token.Tag.comma,
        )
    }

    should("skip a UTF-8 BOM") {
        testTokenize("\uFEFFa;\n", Token.Tag.identifier, Token.Tag.semicolon)
    }

    should("recognize pointer assignment") {
        testTokenize(
            "b.*=3;\n",
            Token.Tag.identifier,
            Token.Tag.period_asterisk,
            Token.Tag.equal,
            Token.Tag.number_literal,
            Token.Tag.semicolon,
        )
    }

    should("recognize range literals") {
        testTokenize("0...9", Token.Tag.number_literal, Token.Tag.ellipsis3, Token.Tag.number_literal)
        testTokenize("'0'...'9'", Token.Tag.char_literal, Token.Tag.ellipsis3, Token.Tag.char_literal)
        testTokenize("0x00...0x09", Token.Tag.number_literal, Token.Tag.ellipsis3, Token.Tag.number_literal)
        testTokenize("0b00...0b11", Token.Tag.number_literal, Token.Tag.ellipsis3, Token.Tag.number_literal)
        testTokenize("0o00...0o11", Token.Tag.number_literal, Token.Tag.ellipsis3, Token.Tag.number_literal)
    }

    should("recognize decimal number literals without validating their contents") {
        testTokenize("0", Token.Tag.number_literal)
        testTokenize("1", Token.Tag.number_literal)
        testTokenize("2", Token.Tag.number_literal)
        testTokenize("3", Token.Tag.number_literal)
        testTokenize("4", Token.Tag.number_literal)
        testTokenize("5", Token.Tag.number_literal)
        testTokenize("6", Token.Tag.number_literal)
        testTokenize("7", Token.Tag.number_literal)
        testTokenize("8", Token.Tag.number_literal)
        testTokenize("9", Token.Tag.number_literal)
        testTokenize("1..", Token.Tag.number_literal, Token.Tag.ellipsis2)
        testTokenize("0a", Token.Tag.number_literal)
        testTokenize("9b", Token.Tag.number_literal)
        testTokenize("1z", Token.Tag.number_literal)
        testTokenize("1z_1", Token.Tag.number_literal)
        testTokenize("9z3", Token.Tag.number_literal)

        testTokenize("0_0", Token.Tag.number_literal)
        testTokenize("0001", Token.Tag.number_literal)
        testTokenize("01234567890", Token.Tag.number_literal)
        testTokenize("012_345_6789_0", Token.Tag.number_literal)
        testTokenize("0_1_2_3_4_5_6_7_8_9_0", Token.Tag.number_literal)

        testTokenize("00_", Token.Tag.number_literal)
        testTokenize("0_0_", Token.Tag.number_literal)
        testTokenize("0__0", Token.Tag.number_literal)
        testTokenize("0_0f", Token.Tag.number_literal)
        testTokenize("0_0_f", Token.Tag.number_literal)
        testTokenize("0_0_f_00", Token.Tag.number_literal)
        testTokenize("1_,", Token.Tag.number_literal, Token.Tag.comma)

        testTokenize("0.0", Token.Tag.number_literal)
        testTokenize("1.0", Token.Tag.number_literal)
        testTokenize("10.0", Token.Tag.number_literal)
        testTokenize("0e0", Token.Tag.number_literal)
        testTokenize("1e0", Token.Tag.number_literal)
        testTokenize("1e100", Token.Tag.number_literal)
        testTokenize("1.0e100", Token.Tag.number_literal)
        testTokenize("1.0e+100", Token.Tag.number_literal)
        testTokenize("1.0e-100", Token.Tag.number_literal)
        testTokenize("1_0_0_0.0_0_0_0_0_1e1_0_0_0", Token.Tag.number_literal)

        testTokenize("1.", Token.Tag.number_literal, Token.Tag.period)
        testTokenize("1e", Token.Tag.number_literal)
        testTokenize("1.e100", Token.Tag.number_literal)
        testTokenize("1.0e1f0", Token.Tag.number_literal)
        testTokenize("1.0p100", Token.Tag.number_literal)
        testTokenize("1.0p-100", Token.Tag.number_literal)
        testTokenize("1.0p1f0", Token.Tag.number_literal)
        testTokenize("1.0_,", Token.Tag.number_literal, Token.Tag.comma)
        testTokenize("1_.0", Token.Tag.number_literal)
        testTokenize("1._", Token.Tag.number_literal)
        testTokenize("1.a", Token.Tag.number_literal)
        testTokenize("1.z", Token.Tag.number_literal)
        testTokenize("1._0", Token.Tag.number_literal)
        testTokenize("1.+", Token.Tag.number_literal, Token.Tag.period, Token.Tag.plus)
        testTokenize("1._+", Token.Tag.number_literal, Token.Tag.plus)
        testTokenize("1._e", Token.Tag.number_literal)
        testTokenize("1.0e", Token.Tag.number_literal)
        testTokenize("1.0e,", Token.Tag.number_literal, Token.Tag.comma)
        testTokenize("1.0e_", Token.Tag.number_literal)
        testTokenize("1.0e+_", Token.Tag.number_literal)
        testTokenize("1.0e-_", Token.Tag.number_literal)
        testTokenize("1.0e0_+", Token.Tag.number_literal, Token.Tag.plus)
    }

    should("recognize binary number literals without validating their contents") {
        testTokenize("0b0", Token.Tag.number_literal)
        testTokenize("0b1", Token.Tag.number_literal)
        testTokenize("0b2", Token.Tag.number_literal)
        testTokenize("0b3", Token.Tag.number_literal)
        testTokenize("0b4", Token.Tag.number_literal)
        testTokenize("0b5", Token.Tag.number_literal)
        testTokenize("0b6", Token.Tag.number_literal)
        testTokenize("0b7", Token.Tag.number_literal)
        testTokenize("0b8", Token.Tag.number_literal)
        testTokenize("0b9", Token.Tag.number_literal)
        testTokenize("0ba", Token.Tag.number_literal)
        testTokenize("0bb", Token.Tag.number_literal)
        testTokenize("0bc", Token.Tag.number_literal)
        testTokenize("0bd", Token.Tag.number_literal)
        testTokenize("0be", Token.Tag.number_literal)
        testTokenize("0bf", Token.Tag.number_literal)
        testTokenize("0bz", Token.Tag.number_literal)

        testTokenize("0b0000_0000", Token.Tag.number_literal)
        testTokenize("0b1111_1111", Token.Tag.number_literal)
        testTokenize("0b10_10_10_10", Token.Tag.number_literal)
        testTokenize("0b0_1_0_1_0_1_0_1", Token.Tag.number_literal)
        testTokenize("0b1.", Token.Tag.number_literal, Token.Tag.period)
        testTokenize("0b1.0", Token.Tag.number_literal)

        testTokenize("0B0", Token.Tag.number_literal)
        testTokenize("0b_", Token.Tag.number_literal)
        testTokenize("0b_0", Token.Tag.number_literal)
        testTokenize("0b1_", Token.Tag.number_literal)
        testTokenize("0b0__1", Token.Tag.number_literal)
        testTokenize("0b0_1_", Token.Tag.number_literal)
        testTokenize("0b1e", Token.Tag.number_literal)
        testTokenize("0b1p", Token.Tag.number_literal)
        testTokenize("0b1e0", Token.Tag.number_literal)
        testTokenize("0b1p0", Token.Tag.number_literal)
        testTokenize("0b1_,", Token.Tag.number_literal, Token.Tag.comma)
    }

    should("recognize octal number literals without validating their contents") {
        testTokenize("0o0", Token.Tag.number_literal)
        testTokenize("0o1", Token.Tag.number_literal)
        testTokenize("0o2", Token.Tag.number_literal)
        testTokenize("0o3", Token.Tag.number_literal)
        testTokenize("0o4", Token.Tag.number_literal)
        testTokenize("0o5", Token.Tag.number_literal)
        testTokenize("0o6", Token.Tag.number_literal)
        testTokenize("0o7", Token.Tag.number_literal)
        testTokenize("0o8", Token.Tag.number_literal)
        testTokenize("0o9", Token.Tag.number_literal)
        testTokenize("0oa", Token.Tag.number_literal)
        testTokenize("0ob", Token.Tag.number_literal)
        testTokenize("0oc", Token.Tag.number_literal)
        testTokenize("0od", Token.Tag.number_literal)
        testTokenize("0oe", Token.Tag.number_literal)
        testTokenize("0of", Token.Tag.number_literal)
        testTokenize("0oz", Token.Tag.number_literal)

        testTokenize("0o01234567", Token.Tag.number_literal)
        testTokenize("0o0123_4567", Token.Tag.number_literal)
        testTokenize("0o01_23_45_67", Token.Tag.number_literal)
        testTokenize("0o0_1_2_3_4_5_6_7", Token.Tag.number_literal)
        testTokenize("0o7.", Token.Tag.number_literal, Token.Tag.period)
        testTokenize("0o7.0", Token.Tag.number_literal)

        testTokenize("0O0", Token.Tag.number_literal)
        testTokenize("0o_", Token.Tag.number_literal)
        testTokenize("0o_0", Token.Tag.number_literal)
        testTokenize("0o1_", Token.Tag.number_literal)
        testTokenize("0o0__1", Token.Tag.number_literal)
        testTokenize("0o0_1_", Token.Tag.number_literal)
        testTokenize("0o1e", Token.Tag.number_literal)
        testTokenize("0o1p", Token.Tag.number_literal)
        testTokenize("0o1e0", Token.Tag.number_literal)
        testTokenize("0o1p0", Token.Tag.number_literal)
        testTokenize("0o_,", Token.Tag.number_literal, Token.Tag.comma)
    }

    should("recognize hexadecimal number literals without validating their contents") {
        testTokenize("0x0", Token.Tag.number_literal)
        testTokenize("0x1", Token.Tag.number_literal)
        testTokenize("0x2", Token.Tag.number_literal)
        testTokenize("0x3", Token.Tag.number_literal)
        testTokenize("0x4", Token.Tag.number_literal)
        testTokenize("0x5", Token.Tag.number_literal)
        testTokenize("0x6", Token.Tag.number_literal)
        testTokenize("0x7", Token.Tag.number_literal)
        testTokenize("0x8", Token.Tag.number_literal)
        testTokenize("0x9", Token.Tag.number_literal)
        testTokenize("0xa", Token.Tag.number_literal)
        testTokenize("0xb", Token.Tag.number_literal)
        testTokenize("0xc", Token.Tag.number_literal)
        testTokenize("0xd", Token.Tag.number_literal)
        testTokenize("0xe", Token.Tag.number_literal)
        testTokenize("0xf", Token.Tag.number_literal)
        testTokenize("0xA", Token.Tag.number_literal)
        testTokenize("0xB", Token.Tag.number_literal)
        testTokenize("0xC", Token.Tag.number_literal)
        testTokenize("0xD", Token.Tag.number_literal)
        testTokenize("0xE", Token.Tag.number_literal)
        testTokenize("0xF", Token.Tag.number_literal)
        testTokenize("0x0z", Token.Tag.number_literal)
        testTokenize("0xz", Token.Tag.number_literal)

        testTokenize("0x0123456789ABCDEF", Token.Tag.number_literal)
        testTokenize("0x0123_4567_89AB_CDEF", Token.Tag.number_literal)
        testTokenize("0x01_23_45_67_89AB_CDE_F", Token.Tag.number_literal)
        testTokenize("0x0_1_2_3_4_5_6_7_8_9_A_B_C_D_E_F", Token.Tag.number_literal)

        testTokenize("0X0", Token.Tag.number_literal)
        testTokenize("0x_", Token.Tag.number_literal)
        testTokenize("0x_1", Token.Tag.number_literal)
        testTokenize("0x1_", Token.Tag.number_literal)
        testTokenize("0x0__1", Token.Tag.number_literal)
        testTokenize("0x0_1_", Token.Tag.number_literal)
        testTokenize("0x_,", Token.Tag.number_literal, Token.Tag.comma)

        testTokenize("0x1.0", Token.Tag.number_literal)
        testTokenize("0xF.0", Token.Tag.number_literal)
        testTokenize("0xF.F", Token.Tag.number_literal)
        testTokenize("0xF.Fp0", Token.Tag.number_literal)
        testTokenize("0xF.FP0", Token.Tag.number_literal)
        testTokenize("0x1p0", Token.Tag.number_literal)
        testTokenize("0xfp0", Token.Tag.number_literal)
        testTokenize("0x1.0+0xF.0", Token.Tag.number_literal, Token.Tag.plus, Token.Tag.number_literal)

        testTokenize("0x1.", Token.Tag.number_literal, Token.Tag.period)
        testTokenize("0xF.", Token.Tag.number_literal, Token.Tag.period)
        testTokenize(
            "0x1.+0xF.",
            Token.Tag.number_literal,
            Token.Tag.period,
            Token.Tag.plus,
            Token.Tag.number_literal,
            Token.Tag.period
        )
        testTokenize("0xff.p10", Token.Tag.number_literal)

        testTokenize("0x0123456.789ABCDEF", Token.Tag.number_literal)
        testTokenize("0x0_123_456.789_ABC_DEF", Token.Tag.number_literal)
        testTokenize("0x0_1_2_3_4_5_6.7_8_9_A_B_C_D_E_F", Token.Tag.number_literal)
        testTokenize("0x0p0", Token.Tag.number_literal)
        testTokenize("0x0.0p0", Token.Tag.number_literal)
        testTokenize("0xff.ffp10", Token.Tag.number_literal)
        testTokenize("0xff.ffP10", Token.Tag.number_literal)
        testTokenize("0xffp10", Token.Tag.number_literal)
        testTokenize("0xff_ff.ff_ffp1_0_0_0", Token.Tag.number_literal)
        testTokenize("0xf_f_f_f.f_f_f_fp+1_000", Token.Tag.number_literal)
        testTokenize("0xf_f_f_f.f_f_f_fp-1_00_0", Token.Tag.number_literal)

        testTokenize("0x1e", Token.Tag.number_literal)
        testTokenize("0x1e0", Token.Tag.number_literal)
        testTokenize("0x1p", Token.Tag.number_literal)
        testTokenize("0xfp0z1", Token.Tag.number_literal)
        testTokenize("0xff.ffpff", Token.Tag.number_literal)
        testTokenize("0x0.p", Token.Tag.number_literal)
        testTokenize("0x0.z", Token.Tag.number_literal)
        testTokenize("0x0._", Token.Tag.number_literal)
        testTokenize("0x0_.0", Token.Tag.number_literal)
        testTokenize("0x0_.0.0", Token.Tag.number_literal, Token.Tag.period, Token.Tag.number_literal)
        testTokenize("0x0._0", Token.Tag.number_literal)
        testTokenize("0x0.0_", Token.Tag.number_literal)
        testTokenize("0x0_p0", Token.Tag.number_literal)
        testTokenize("0x0_.p0", Token.Tag.number_literal)
        testTokenize("0x0._p0", Token.Tag.number_literal)
        testTokenize("0x0.0_p0", Token.Tag.number_literal)
        testTokenize("0x0._0p0", Token.Tag.number_literal)
        testTokenize("0x0.0p_0", Token.Tag.number_literal)
        testTokenize("0x0.0p+_0", Token.Tag.number_literal)
        testTokenize("0x0.0p-_0", Token.Tag.number_literal)
        testTokenize("0x0.0p0_", Token.Tag.number_literal)
    }

    should("reject a multiline string literal with only one backslash") {
        testTokenize("x \\\n;", Token.Tag.identifier, Token.Tag.invalid, Token.Tag.semicolon)
    }

    should("reject invalid builtin identifiers") {
        testTokenize("@()", Token.Tag.invalid)
        testTokenize("@0()", Token.Tag.invalid)
    }

    should("reject unfinished escapes at the end of a literal") {
        testTokenize("\"\\", Token.Tag.invalid)
        testTokenize("'\\", Token.Tag.invalid)
        testTokenize("'\\u", Token.Tag.invalid)
    }

    should("recognize saturating operators") {
        testTokenize("<<", Token.Tag.angle_bracket_angle_bracket_left)
        testTokenize("<<|", Token.Tag.angle_bracket_angle_bracket_left_pipe)
        testTokenize("<<|=", Token.Tag.angle_bracket_angle_bracket_left_pipe_equal)

        testTokenize("*", Token.Tag.asterisk)
        testTokenize("*|", Token.Tag.asterisk_pipe)
        testTokenize("*|=", Token.Tag.asterisk_pipe_equal)

        testTokenize("+", Token.Tag.plus)
        testTokenize("+|", Token.Tag.plus_pipe)
        testTokenize("+|=", Token.Tag.plus_pipe_equal)

        testTokenize("-", Token.Tag.minus)
        testTokenize("-|", Token.Tag.minus_pipe)
        testTokenize("-|=", Token.Tag.minus_pipe_equal)
    }

    should("reject null bytes before the end of the source") {
        testTokenize("123 \u0000 456", Token.Tag.number_literal, Token.Tag.invalid)
        testTokenize("//\u0000", Token.Tag.invalid)
        testTokenize("\\\\\u0000", Token.Tag.invalid)
        testTokenize("\u0000", Token.Tag.invalid)
        testTokenize("// NUL\u0000\n", Token.Tag.invalid)
        testTokenize("///\u0000\n", Token.Tag.doc_comment, Token.Tag.invalid)
        testTokenize("/// NUL\u0000\n", Token.Tag.doc_comment, Token.Tag.invalid)
    }

    should("reject tabs and stray carriage returns in comments and multiline strings") {
        testTokenize("//\t", Token.Tag.invalid)
        testTokenize("// \t", Token.Tag.invalid)
        testTokenize("///\t", Token.Tag.invalid)
        testTokenize("/// \t", Token.Tag.invalid)
        testTokenize("//!\t", Token.Tag.invalid)
        testTokenize("//! \t", Token.Tag.invalid)

        testTokenize("//\r", Token.Tag.invalid)
        testTokenize("// \r", Token.Tag.invalid)
        testTokenize("///\r", Token.Tag.invalid)
        testTokenize("/// \r", Token.Tag.invalid)
        testTokenize("//\r ", Token.Tag.invalid)
        testTokenize("// \r ", Token.Tag.invalid)
        testTokenize("///\r ", Token.Tag.invalid)
        testTokenize("/// \r ", Token.Tag.invalid)
        testTokenize("//\r\n")
        testTokenize("// \r\n")
        testTokenize("///\r\n", Token.Tag.doc_comment)
        testTokenize("/// \r\n", Token.Tag.doc_comment)
        testTokenize("//!\r", Token.Tag.invalid)
        testTokenize("//! \r", Token.Tag.invalid)
        testTokenize("//!\r ", Token.Tag.invalid)
        testTokenize("//! \r ", Token.Tag.invalid)
        testTokenize("//!\r\n", Token.Tag.container_doc_comment)
        testTokenize("//! \r\n", Token.Tag.container_doc_comment)

        testTokenize("\\\\\r", Token.Tag.invalid)
        testTokenize("\\\\\r ", Token.Tag.invalid)
        testTokenize("\\\\ \r", Token.Tag.invalid)
        testTokenize("\\\\\t", Token.Tag.invalid)
        testTokenize("\\\\\t ", Token.Tag.invalid)
        testTokenize("\\\\ \t", Token.Tag.invalid)
        testTokenize("\\\\\r\n", Token.Tag.multiline_string_literal_line)

        testTokenize("\tpub\tswitch\t", Token.Tag.keyword_pub, Token.Tag.keyword_switch)
        testTokenize("\rpub\rswitch\r", Token.Tag.keyword_pub, Token.Tag.keyword_switch)
    }
})

private fun testTokenize(source: String, vararg expectedTokenTags: Token.Tag) {
    testTokenize(source.encodeToByteArray(), *expectedTokenTags)
}

private fun testTokenize(source: ByteArray, vararg expectedTokenTags: Token.Tag) {
    val tokenizer = Tokenizer(source.asReadonly())
    for (expectedTokenTag in expectedTokenTags) {
        val token = tokenizer.next()
        token.tag shouldBe expectedTokenTag
    }
    val lastToken = tokenizer.next()
    lastToken.tag shouldBe Token.Tag.eof
    lastToken.start.value shouldBe source.size
    lastToken.end.value shouldBe source.size
}
