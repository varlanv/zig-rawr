package com.zigrawr.zig.tokenizer

import com.zigrawr.lang.bin.asReadonly
import io.kotest.matchers.shouldBe

class TokenizerSpec : BaseSpec({

    should("recognize keywords") {
        testTokenize("test const else", Tag.keyword_test, Tag.keyword_const, Tag.keyword_else)
    }

    should("skip a line comment before top-level comptime") {
        testTokenize("// line comment\ncomptime {}\n", Tag.keyword_comptime, Tag.l_brace, Tag.r_brace)
    }

    should("recognize an unknown length pointer followed by a C pointer") {
        testTokenize(
            "[*]u8\n[*c]u8",
            Tag.l_bracket,
            Tag.asterisk,
            Tag.r_bracket,
            Tag.identifier,
            Tag.l_bracket,
            Tag.asterisk,
            Tag.identifier,
            Tag.r_bracket,
            Tag.identifier,
        )
    }

    should("recognize character literals with hex escapes") {
        testTokenize("'\\x1b'", Tag.char_literal)
        testTokenize("'\\x1'", Tag.char_literal)
    }

    should("reject newlines in character literals") {
        testTokenize("'\n'", Tag.invalid, Tag.invalid)
    }

    should("reject newlines in string literals") {
        testTokenize("\"\n\"", Tag.invalid, Tag.invalid)
    }

    should("recognize literals with unicode escapes without validating the escapes") {
        testTokenize("'\\u{3}'", Tag.char_literal)
        testTokenize("'\\u{01}'", Tag.char_literal)
        testTokenize("'\\u{2a}'", Tag.char_literal)
        testTokenize("'\\u{3f9}'", Tag.char_literal)
        testTokenize("'\\u{6E09aBc1523}'", Tag.char_literal)
        testTokenize("\"\\u{440}\"", Tag.string_literal)

        testTokenize("'\\u'", Tag.char_literal)
        testTokenize("'\\u{{'", Tag.char_literal)
        testTokenize("'\\u{}'", Tag.char_literal)
        testTokenize("'\\u{s}'", Tag.char_literal)
        testTokenize("'\\u{2z}'", Tag.char_literal)
        testTokenize("'\\u{4a'", Tag.char_literal)

        testTokenize("'\\u0333'", Tag.char_literal)
        testTokenize("'\\U0333'", Tag.char_literal)
    }

    should("recognize a unicode code point in a character literal") {
        testTokenize("'💩'", Tag.char_literal)
    }

    should("recognize a float literal with an e exponent") {
        testTokenize(
            "a = 4.94065645841246544177e-324;\n",
            Tag.identifier,
            Tag.equal,
            Tag.number_literal,
            Tag.semicolon,
        )
    }

    should("recognize a float literal with a p exponent") {
        testTokenize(
            "a = 0x1.a827999fcef32p+1022;\n",
            Tag.identifier,
            Tag.equal,
            Tag.number_literal,
            Tag.semicolon,
        )
    }

    should("recognize character literals") {
        testTokenize("'c'", Tag.char_literal)
    }

    should("reject invalid token characters and unfinished character literals") {
        testTokenize("#", Tag.invalid)
        testTokenize("`", Tag.invalid)
        testTokenize("'c", Tag.invalid)
        testTokenize("'", Tag.invalid)
        testTokenize("''", Tag.char_literal)
        testTokenize("'\n'", Tag.invalid, Tag.invalid)
    }

    should("reject invalid characters in literals and comments") {
        testTokenize("\"\u0000\"", Tag.invalid)
        testTokenize("`\u0000`", Tag.invalid)
        testTokenize("//\u0000", Tag.invalid)
        testTokenize("//\u001f", Tag.invalid)
        testTokenize("//\u007f", Tag.invalid)
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
            Tag.keyword_const,
            Tag.identifier,
            Tag.equal,
            Tag.builtin,
            Tag.l_paren,
            Tag.string_literal,
            Tag.r_paren,
            Tag.semicolon,
        )
    }

    should("recognize a double pipe followed by an equals sign") {
        testTokenize("||=", Tag.pipe_pipe, Tag.equal)
    }

    should("distinguish line comments from documentation comments") {
        testTokenize("//")
        testTokenize("// a / b")
        testTokenize("// /")
        testTokenize("/// a", Tag.doc_comment)
        testTokenize("///", Tag.doc_comment)
        testTokenize("////")
        testTokenize("//!", Tag.container_doc_comment)
        testTokenize("//!!", Tag.container_doc_comment)
    }

    should("recognize identifiers around a line comment") {
        testTokenize(
            "    Unexpected,\n    // another\n    Another,",
            Tag.identifier,
            Tag.comma,
            Tag.identifier,
            Tag.comma,
        )
    }

    should("skip a UTF-8 BOM") {
        testTokenize("\uFEFFa;\n", Tag.identifier, Tag.semicolon)
    }

    should("recognize pointer assignment") {
        testTokenize(
            "b.*=3;\n",
            Tag.identifier,
            Tag.period_asterisk,
            Tag.equal,
            Tag.number_literal,
            Tag.semicolon,
        )
    }

    should("recognize range literals") {
        testTokenize("0...9", Tag.number_literal, Tag.ellipsis3, Tag.number_literal)
        testTokenize("'0'...'9'", Tag.char_literal, Tag.ellipsis3, Tag.char_literal)
        testTokenize("0x00...0x09", Tag.number_literal, Tag.ellipsis3, Tag.number_literal)
        testTokenize("0b00...0b11", Tag.number_literal, Tag.ellipsis3, Tag.number_literal)
        testTokenize("0o00...0o11", Tag.number_literal, Tag.ellipsis3, Tag.number_literal)
    }

    should("recognize decimal number literals without validating their contents") {
        testTokenize("0", Tag.number_literal)
        testTokenize("1", Tag.number_literal)
        testTokenize("2", Tag.number_literal)
        testTokenize("3", Tag.number_literal)
        testTokenize("4", Tag.number_literal)
        testTokenize("5", Tag.number_literal)
        testTokenize("6", Tag.number_literal)
        testTokenize("7", Tag.number_literal)
        testTokenize("8", Tag.number_literal)
        testTokenize("9", Tag.number_literal)
        testTokenize("1..", Tag.number_literal, Tag.ellipsis2)
        testTokenize("0a", Tag.number_literal)
        testTokenize("9b", Tag.number_literal)
        testTokenize("1z", Tag.number_literal)
        testTokenize("1z_1", Tag.number_literal)
        testTokenize("9z3", Tag.number_literal)

        testTokenize("0_0", Tag.number_literal)
        testTokenize("0001", Tag.number_literal)
        testTokenize("01234567890", Tag.number_literal)
        testTokenize("012_345_6789_0", Tag.number_literal)
        testTokenize("0_1_2_3_4_5_6_7_8_9_0", Tag.number_literal)

        testTokenize("00_", Tag.number_literal)
        testTokenize("0_0_", Tag.number_literal)
        testTokenize("0__0", Tag.number_literal)
        testTokenize("0_0f", Tag.number_literal)
        testTokenize("0_0_f", Tag.number_literal)
        testTokenize("0_0_f_00", Tag.number_literal)
        testTokenize("1_,", Tag.number_literal, Tag.comma)

        testTokenize("0.0", Tag.number_literal)
        testTokenize("1.0", Tag.number_literal)
        testTokenize("10.0", Tag.number_literal)
        testTokenize("0e0", Tag.number_literal)
        testTokenize("1e0", Tag.number_literal)
        testTokenize("1e100", Tag.number_literal)
        testTokenize("1.0e100", Tag.number_literal)
        testTokenize("1.0e+100", Tag.number_literal)
        testTokenize("1.0e-100", Tag.number_literal)
        testTokenize("1_0_0_0.0_0_0_0_0_1e1_0_0_0", Tag.number_literal)

        testTokenize("1.", Tag.number_literal, Tag.period)
        testTokenize("1e", Tag.number_literal)
        testTokenize("1.e100", Tag.number_literal)
        testTokenize("1.0e1f0", Tag.number_literal)
        testTokenize("1.0p100", Tag.number_literal)
        testTokenize("1.0p-100", Tag.number_literal)
        testTokenize("1.0p1f0", Tag.number_literal)
        testTokenize("1.0_,", Tag.number_literal, Tag.comma)
        testTokenize("1_.0", Tag.number_literal)
        testTokenize("1._", Tag.number_literal)
        testTokenize("1.a", Tag.number_literal)
        testTokenize("1.z", Tag.number_literal)
        testTokenize("1._0", Tag.number_literal)
        testTokenize("1.+", Tag.number_literal, Tag.period, Tag.plus)
        testTokenize("1._+", Tag.number_literal, Tag.plus)
        testTokenize("1._e", Tag.number_literal)
        testTokenize("1.0e", Tag.number_literal)
        testTokenize("1.0e,", Tag.number_literal, Tag.comma)
        testTokenize("1.0e_", Tag.number_literal)
        testTokenize("1.0e+_", Tag.number_literal)
        testTokenize("1.0e-_", Tag.number_literal)
        testTokenize("1.0e0_+", Tag.number_literal, Tag.plus)
    }

    should("recognize binary number literals without validating their contents") {
        testTokenize("0b0", Tag.number_literal)
        testTokenize("0b1", Tag.number_literal)
        testTokenize("0b2", Tag.number_literal)
        testTokenize("0b3", Tag.number_literal)
        testTokenize("0b4", Tag.number_literal)
        testTokenize("0b5", Tag.number_literal)
        testTokenize("0b6", Tag.number_literal)
        testTokenize("0b7", Tag.number_literal)
        testTokenize("0b8", Tag.number_literal)
        testTokenize("0b9", Tag.number_literal)
        testTokenize("0ba", Tag.number_literal)
        testTokenize("0bb", Tag.number_literal)
        testTokenize("0bc", Tag.number_literal)
        testTokenize("0bd", Tag.number_literal)
        testTokenize("0be", Tag.number_literal)
        testTokenize("0bf", Tag.number_literal)
        testTokenize("0bz", Tag.number_literal)

        testTokenize("0b0000_0000", Tag.number_literal)
        testTokenize("0b1111_1111", Tag.number_literal)
        testTokenize("0b10_10_10_10", Tag.number_literal)
        testTokenize("0b0_1_0_1_0_1_0_1", Tag.number_literal)
        testTokenize("0b1.", Tag.number_literal, Tag.period)
        testTokenize("0b1.0", Tag.number_literal)

        testTokenize("0B0", Tag.number_literal)
        testTokenize("0b_", Tag.number_literal)
        testTokenize("0b_0", Tag.number_literal)
        testTokenize("0b1_", Tag.number_literal)
        testTokenize("0b0__1", Tag.number_literal)
        testTokenize("0b0_1_", Tag.number_literal)
        testTokenize("0b1e", Tag.number_literal)
        testTokenize("0b1p", Tag.number_literal)
        testTokenize("0b1e0", Tag.number_literal)
        testTokenize("0b1p0", Tag.number_literal)
        testTokenize("0b1_,", Tag.number_literal, Tag.comma)
    }

    should("recognize octal number literals without validating their contents") {
        testTokenize("0o0", Tag.number_literal)
        testTokenize("0o1", Tag.number_literal)
        testTokenize("0o2", Tag.number_literal)
        testTokenize("0o3", Tag.number_literal)
        testTokenize("0o4", Tag.number_literal)
        testTokenize("0o5", Tag.number_literal)
        testTokenize("0o6", Tag.number_literal)
        testTokenize("0o7", Tag.number_literal)
        testTokenize("0o8", Tag.number_literal)
        testTokenize("0o9", Tag.number_literal)
        testTokenize("0oa", Tag.number_literal)
        testTokenize("0ob", Tag.number_literal)
        testTokenize("0oc", Tag.number_literal)
        testTokenize("0od", Tag.number_literal)
        testTokenize("0oe", Tag.number_literal)
        testTokenize("0of", Tag.number_literal)
        testTokenize("0oz", Tag.number_literal)

        testTokenize("0o01234567", Tag.number_literal)
        testTokenize("0o0123_4567", Tag.number_literal)
        testTokenize("0o01_23_45_67", Tag.number_literal)
        testTokenize("0o0_1_2_3_4_5_6_7", Tag.number_literal)
        testTokenize("0o7.", Tag.number_literal, Tag.period)
        testTokenize("0o7.0", Tag.number_literal)

        testTokenize("0O0", Tag.number_literal)
        testTokenize("0o_", Tag.number_literal)
        testTokenize("0o_0", Tag.number_literal)
        testTokenize("0o1_", Tag.number_literal)
        testTokenize("0o0__1", Tag.number_literal)
        testTokenize("0o0_1_", Tag.number_literal)
        testTokenize("0o1e", Tag.number_literal)
        testTokenize("0o1p", Tag.number_literal)
        testTokenize("0o1e0", Tag.number_literal)
        testTokenize("0o1p0", Tag.number_literal)
        testTokenize("0o_,", Tag.number_literal, Tag.comma)
    }

    should("recognize hexadecimal number literals without validating their contents") {
        testTokenize("0x0", Tag.number_literal)
        testTokenize("0x1", Tag.number_literal)
        testTokenize("0x2", Tag.number_literal)
        testTokenize("0x3", Tag.number_literal)
        testTokenize("0x4", Tag.number_literal)
        testTokenize("0x5", Tag.number_literal)
        testTokenize("0x6", Tag.number_literal)
        testTokenize("0x7", Tag.number_literal)
        testTokenize("0x8", Tag.number_literal)
        testTokenize("0x9", Tag.number_literal)
        testTokenize("0xa", Tag.number_literal)
        testTokenize("0xb", Tag.number_literal)
        testTokenize("0xc", Tag.number_literal)
        testTokenize("0xd", Tag.number_literal)
        testTokenize("0xe", Tag.number_literal)
        testTokenize("0xf", Tag.number_literal)
        testTokenize("0xA", Tag.number_literal)
        testTokenize("0xB", Tag.number_literal)
        testTokenize("0xC", Tag.number_literal)
        testTokenize("0xD", Tag.number_literal)
        testTokenize("0xE", Tag.number_literal)
        testTokenize("0xF", Tag.number_literal)
        testTokenize("0x0z", Tag.number_literal)
        testTokenize("0xz", Tag.number_literal)

        testTokenize("0x0123456789ABCDEF", Tag.number_literal)
        testTokenize("0x0123_4567_89AB_CDEF", Tag.number_literal)
        testTokenize("0x01_23_45_67_89AB_CDE_F", Tag.number_literal)
        testTokenize("0x0_1_2_3_4_5_6_7_8_9_A_B_C_D_E_F", Tag.number_literal)

        testTokenize("0X0", Tag.number_literal)
        testTokenize("0x_", Tag.number_literal)
        testTokenize("0x_1", Tag.number_literal)
        testTokenize("0x1_", Tag.number_literal)
        testTokenize("0x0__1", Tag.number_literal)
        testTokenize("0x0_1_", Tag.number_literal)
        testTokenize("0x_,", Tag.number_literal, Tag.comma)

        testTokenize("0x1.0", Tag.number_literal)
        testTokenize("0xF.0", Tag.number_literal)
        testTokenize("0xF.F", Tag.number_literal)
        testTokenize("0xF.Fp0", Tag.number_literal)
        testTokenize("0xF.FP0", Tag.number_literal)
        testTokenize("0x1p0", Tag.number_literal)
        testTokenize("0xfp0", Tag.number_literal)
        testTokenize("0x1.0+0xF.0", Tag.number_literal, Tag.plus, Tag.number_literal)

        testTokenize("0x1.", Tag.number_literal, Tag.period)
        testTokenize("0xF.", Tag.number_literal, Tag.period)
        testTokenize("0x1.+0xF.", Tag.number_literal, Tag.period, Tag.plus, Tag.number_literal, Tag.period)
        testTokenize("0xff.p10", Tag.number_literal)

        testTokenize("0x0123456.789ABCDEF", Tag.number_literal)
        testTokenize("0x0_123_456.789_ABC_DEF", Tag.number_literal)
        testTokenize("0x0_1_2_3_4_5_6.7_8_9_A_B_C_D_E_F", Tag.number_literal)
        testTokenize("0x0p0", Tag.number_literal)
        testTokenize("0x0.0p0", Tag.number_literal)
        testTokenize("0xff.ffp10", Tag.number_literal)
        testTokenize("0xff.ffP10", Tag.number_literal)
        testTokenize("0xffp10", Tag.number_literal)
        testTokenize("0xff_ff.ff_ffp1_0_0_0", Tag.number_literal)
        testTokenize("0xf_f_f_f.f_f_f_fp+1_000", Tag.number_literal)
        testTokenize("0xf_f_f_f.f_f_f_fp-1_00_0", Tag.number_literal)

        testTokenize("0x1e", Tag.number_literal)
        testTokenize("0x1e0", Tag.number_literal)
        testTokenize("0x1p", Tag.number_literal)
        testTokenize("0xfp0z1", Tag.number_literal)
        testTokenize("0xff.ffpff", Tag.number_literal)
        testTokenize("0x0.p", Tag.number_literal)
        testTokenize("0x0.z", Tag.number_literal)
        testTokenize("0x0._", Tag.number_literal)
        testTokenize("0x0_.0", Tag.number_literal)
        testTokenize("0x0_.0.0", Tag.number_literal, Tag.period, Tag.number_literal)
        testTokenize("0x0._0", Tag.number_literal)
        testTokenize("0x0.0_", Tag.number_literal)
        testTokenize("0x0_p0", Tag.number_literal)
        testTokenize("0x0_.p0", Tag.number_literal)
        testTokenize("0x0._p0", Tag.number_literal)
        testTokenize("0x0.0_p0", Tag.number_literal)
        testTokenize("0x0._0p0", Tag.number_literal)
        testTokenize("0x0.0p_0", Tag.number_literal)
        testTokenize("0x0.0p+_0", Tag.number_literal)
        testTokenize("0x0.0p-_0", Tag.number_literal)
        testTokenize("0x0.0p0_", Tag.number_literal)
    }

    should("reject a multiline string literal with only one backslash") {
        testTokenize("x \\\n;", Tag.identifier, Tag.invalid, Tag.semicolon)
    }

    should("reject invalid builtin identifiers") {
        testTokenize("@()", Tag.invalid)
        testTokenize("@0()", Tag.invalid)
    }

    should("reject unfinished escapes at the end of a literal") {
        testTokenize("\"\\", Tag.invalid)
        testTokenize("'\\", Tag.invalid)
        testTokenize("'\\u", Tag.invalid)
    }

    should("recognize saturating operators") {
        testTokenize("<<", Tag.angle_bracket_angle_bracket_left)
        testTokenize("<<|", Tag.angle_bracket_angle_bracket_left_pipe)
        testTokenize("<<|=", Tag.angle_bracket_angle_bracket_left_pipe_equal)

        testTokenize("*", Tag.asterisk)
        testTokenize("*|", Tag.asterisk_pipe)
        testTokenize("*|=", Tag.asterisk_pipe_equal)

        testTokenize("+", Tag.plus)
        testTokenize("+|", Tag.plus_pipe)
        testTokenize("+|=", Tag.plus_pipe_equal)

        testTokenize("-", Tag.minus)
        testTokenize("-|", Tag.minus_pipe)
        testTokenize("-|=", Tag.minus_pipe_equal)
    }

    should("reject null bytes before the end of the source") {
        testTokenize("123 \u0000 456", Tag.number_literal, Tag.invalid)
        testTokenize("//\u0000", Tag.invalid)
        testTokenize("\\\\\u0000", Tag.invalid)
        testTokenize("\u0000", Tag.invalid)
        testTokenize("// NUL\u0000\n", Tag.invalid)
        testTokenize("///\u0000\n", Tag.doc_comment, Tag.invalid)
        testTokenize("/// NUL\u0000\n", Tag.doc_comment, Tag.invalid)
    }

    should("reject tabs and stray carriage returns in comments and multiline strings") {
        testTokenize("//\t", Tag.invalid)
        testTokenize("// \t", Tag.invalid)
        testTokenize("///\t", Tag.invalid)
        testTokenize("/// \t", Tag.invalid)
        testTokenize("//!\t", Tag.invalid)
        testTokenize("//! \t", Tag.invalid)

        testTokenize("//\r", Tag.invalid)
        testTokenize("// \r", Tag.invalid)
        testTokenize("///\r", Tag.invalid)
        testTokenize("/// \r", Tag.invalid)
        testTokenize("//\r ", Tag.invalid)
        testTokenize("// \r ", Tag.invalid)
        testTokenize("///\r ", Tag.invalid)
        testTokenize("/// \r ", Tag.invalid)
        testTokenize("//\r\n")
        testTokenize("// \r\n")
        testTokenize("///\r\n", Tag.doc_comment)
        testTokenize("/// \r\n", Tag.doc_comment)
        testTokenize("//!\r", Tag.invalid)
        testTokenize("//! \r", Tag.invalid)
        testTokenize("//!\r ", Tag.invalid)
        testTokenize("//! \r ", Tag.invalid)
        testTokenize("//!\r\n", Tag.container_doc_comment)
        testTokenize("//! \r\n", Tag.container_doc_comment)

        testTokenize("\\\\\r", Tag.invalid)
        testTokenize("\\\\\r ", Tag.invalid)
        testTokenize("\\\\ \r", Tag.invalid)
        testTokenize("\\\\\t", Tag.invalid)
        testTokenize("\\\\\t ", Tag.invalid)
        testTokenize("\\\\ \t", Tag.invalid)
        testTokenize("\\\\\r\n", Tag.multiline_string_literal_line)

        testTokenize("\tpub\tswitch\t", Tag.keyword_pub, Tag.keyword_switch)
        testTokenize("\rpub\rswitch\r", Tag.keyword_pub, Tag.keyword_switch)
    }
})

private fun testTokenize(source: String, vararg expectedTokenTags: Tag) {
    testTokenize(source.encodeToByteArray(), *expectedTokenTags)
}

private fun testTokenize(source: ByteArray, vararg expectedTokenTags: Tag) {
    val tokenizer = Tokenizer(source.asReadonly(), 0)
    for (expectedTokenTag in expectedTokenTags) {
        val token = tokenizer.next()
        token.tag shouldBe expectedTokenTag
    }
    val lastToken = tokenizer.next()
    lastToken.tag shouldBe Tag.eof
    lastToken.start.value shouldBe source.size
    lastToken.end.value shouldBe source.size
}
