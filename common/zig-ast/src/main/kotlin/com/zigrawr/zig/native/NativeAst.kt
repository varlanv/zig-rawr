package com.zigrawr.zig.native

import com.zigrawr.lang.bin.MutBytes

/**
 * *handshake* sanity check value to check correctness against native binary.
 */
const val AST_MAGIC = 0x5A524153

@JvmInline
value class NativeAst(@PublishedApi internal val bytes: MutBytes) {

    enum class Mode { Zig, Zon }

    enum class TokenTag {
        INVALID,
        IDENTIFIER,
        STRING_LITERAL,
        MULTILINE_STRING_LITERAL_LINE,
        CHAR_LITERAL,
        EOF,
        BUILTIN,
        BANG,
        PIPE,
        PIPE_PIPE,
        PIPE_EQUAL,
        EQUAL,
        EQUAL_EQUAL,
        EQUAL_ANGLE_BRACKET_RIGHT,
        BANG_EQUAL,
        L_PAREN,
        R_PAREN,
        SEMICOLON,
        PERCENT,
        PERCENT_EQUAL,
        L_BRACE,
        R_BRACE,
        L_BRACKET,
        R_BRACKET,
        PERIOD,
        PERIOD_ASTERISK,
        ELLIPSIS2,
        ELLIPSIS3,
        CARET,
        CARET_EQUAL,
        PLUS,
        PLUS_PLUS,
        PLUS_EQUAL,
        PLUS_PERCENT,
        PLUS_PERCENT_EQUAL,
        PLUS_PIPE,
        PLUS_PIPE_EQUAL,
        MINUS,
        MINUS_EQUAL,
        MINUS_PERCENT,
        MINUS_PERCENT_EQUAL,
        MINUS_PIPE,
        MINUS_PIPE_EQUAL,
        ASTERISK,
        ASTERISK_EQUAL,
        ASTERISK_PERCENT,
        ASTERISK_PERCENT_EQUAL,
        ASTERISK_PIPE,
        ASTERISK_PIPE_EQUAL,
        ARROW,
        COLON,
        SLASH,
        SLASH_EQUAL,
        COMMA,
        AMPERSAND,
        AMPERSAND_EQUAL,
        QUESTION_MARK,
        ANGLE_BRACKET_LEFT,
        ANGLE_BRACKET_LEFT_EQUAL,
        ANGLE_BRACKET_ANGLE_BRACKET_LEFT,
        ANGLE_BRACKET_ANGLE_BRACKET_LEFT_EQUAL,
        ANGLE_BRACKET_ANGLE_BRACKET_LEFT_PIPE,
        ANGLE_BRACKET_ANGLE_BRACKET_LEFT_PIPE_EQUAL,
        ANGLE_BRACKET_RIGHT,
        ANGLE_BRACKET_RIGHT_EQUAL,
        ANGLE_BRACKET_ANGLE_BRACKET_RIGHT,
        ANGLE_BRACKET_ANGLE_BRACKET_RIGHT_EQUAL,
        TILDE,
        NUMBER_LITERAL,
        DOC_COMMENT,
        CONTAINER_DOC_COMMENT,
        KEYWORD_ADDRSPACE,
        KEYWORD_ALIGN,
        KEYWORD_ALLOWZERO,
        KEYWORD_AND,
        KEYWORD_ANYFRAME,
        KEYWORD_ANYTYPE,
        KEYWORD_ASM,
        KEYWORD_BREAK,
        KEYWORD_CALLCONV,
        KEYWORD_CATCH,
        KEYWORD_COMPTIME,
        KEYWORD_CONST,
        KEYWORD_CONTINUE,
        KEYWORD_DEFER,
        KEYWORD_ELSE,
        KEYWORD_ENUM,
        KEYWORD_ERRDEFER,
        KEYWORD_ERROR,
        KEYWORD_EXPORT,
        KEYWORD_EXTERN,
        KEYWORD_FN,
        KEYWORD_FOR,
        KEYWORD_IF,
        KEYWORD_INLINE,
        KEYWORD_NOALIAS,
        KEYWORD_NOINLINE,
        KEYWORD_NOSUSPEND,
        KEYWORD_OPAQUE,
        KEYWORD_OR,
        KEYWORD_ORELSE,
        KEYWORD_PACKED,
        KEYWORD_PUB,
        KEYWORD_RESUME,
        KEYWORD_RETURN,
        KEYWORD_LINKSECTION,
        KEYWORD_STRUCT,
        KEYWORD_SUSPEND,
        KEYWORD_SWITCH,
        KEYWORD_TEST,
        KEYWORD_THREADLOCAL,
        KEYWORD_TRY,
        KEYWORD_UNION,
        KEYWORD_UNREACHABLE,
        KEYWORD_VAR,
        KEYWORD_VOLATILE,
        KEYWORD_WHILE,
    }

    enum class NodeTag {
        ROOT,
        TEST_DECL,
        GLOBAL_VAR_DECL,
        LOCAL_VAR_DECL,
        SIMPLE_VAR_DECL,
        ALIGNED_VAR_DECL,
        ERRDEFER,
        DEFER,
        CATCH,
        FIELD_ACCESS,
        UNWRAP_OPTIONAL,
        EQUAL_EQUAL,
        BANG_EQUAL,
        LESS_THAN,
        GREATER_THAN,
        LESS_OR_EQUAL,
        GREATER_OR_EQUAL,
        ASSIGN_MUL,
        ASSIGN_DIV,
        ASSIGN_MOD,
        ASSIGN_ADD,
        ASSIGN_SUB,
        ASSIGN_SHL,
        ASSIGN_SHL_SAT,
        ASSIGN_SHR,
        ASSIGN_BIT_AND,
        ASSIGN_BIT_XOR,
        ASSIGN_BIT_OR,
        ASSIGN_MUL_WRAP,
        ASSIGN_ADD_WRAP,
        ASSIGN_SUB_WRAP,
        ASSIGN_MUL_SAT,
        ASSIGN_ADD_SAT,
        ASSIGN_SUB_SAT,
        ASSIGN,
        ASSIGN_DESTRUCTURE,
        MERGE_ERROR_SETS,
        MUL,
        DIV,
        MOD,
        MUL_WRAP,
        MUL_SAT,
        ADD,
        SUB,
        ARRAY_CAT,
        ADD_WRAP,
        SUB_WRAP,
        ADD_SAT,
        SUB_SAT,
        SHL,
        SHL_SAT,
        SHR,
        BIT_AND,
        BIT_XOR,
        BIT_OR,
        ORELSE,
        BOOL_AND,
        BOOL_OR,
        BOOL_NOT,
        NEGATION,
        BIT_NOT,
        NEGATION_WRAP,
        ADDRESS_OF,
        TRY,
        OPTIONAL_TYPE,
        ARRAY_TYPE,
        ARRAY_TYPE_SENTINEL,
        PTR_TYPE_ALIGNED,
        PTR_TYPE_SENTINEL,
        PTR_TYPE,
        PTR_TYPE_BIT_RANGE,
        SLICE_OPEN,
        SLICE,
        SLICE_SENTINEL,
        DEREF,
        ARRAY_ACCESS,
        ARRAY_INIT_ONE,
        ARRAY_INIT_ONE_COMMA,
        ARRAY_INIT_DOT_TWO,
        ARRAY_INIT_DOT_TWO_COMMA,
        ARRAY_INIT_DOT,
        ARRAY_INIT_DOT_COMMA,
        ARRAY_INIT,
        ARRAY_INIT_COMMA,
        STRUCT_INIT_ONE,
        STRUCT_INIT_ONE_COMMA,
        STRUCT_INIT_DOT_TWO,
        STRUCT_INIT_DOT_TWO_COMMA,
        STRUCT_INIT_DOT,
        STRUCT_INIT_DOT_COMMA,
        STRUCT_INIT,
        STRUCT_INIT_COMMA,
        CALL_ONE,
        CALL_ONE_COMMA,
        CALL,
        CALL_COMMA,
        SWITCH,
        SWITCH_COMMA,
        SWITCH_CASE_ONE,
        SWITCH_CASE_INLINE_ONE,
        SWITCH_CASE,
        SWITCH_CASE_INLINE,
        SWITCH_RANGE,
        WHILE_SIMPLE,
        WHILE_CONT,
        WHILE,
        FOR_SIMPLE,
        FOR,
        FOR_RANGE,
        IF_SIMPLE,
        IF,
        SUSPEND,
        RESUME,
        CONTINUE,
        BREAK,
        RETURN,
        FN_PROTO_SIMPLE,
        FN_PROTO_MULTI,
        FN_PROTO_ONE,
        FN_PROTO,
        FN_DECL,
        ANYFRAME_TYPE,
        ANYFRAME_LITERAL,
        CHAR_LITERAL,
        NUMBER_LITERAL,
        UNREACHABLE_LITERAL,
        IDENTIFIER,
        ENUM_LITERAL,
        STRING_LITERAL,
        MULTILINE_STRING_LITERAL,
        GROUPED_EXPRESSION,
        BUILTIN_CALL_TWO,
        BUILTIN_CALL_TWO_COMMA,
        BUILTIN_CALL,
        BUILTIN_CALL_COMMA,
        ERROR_SET_DECL,
        CONTAINER_DECL,
        CONTAINER_DECL_TRAILING,
        CONTAINER_DECL_TWO,
        CONTAINER_DECL_TWO_TRAILING,
        CONTAINER_DECL_ARG,
        CONTAINER_DECL_ARG_TRAILING,
        TAGGED_UNION,
        TAGGED_UNION_TRAILING,
        TAGGED_UNION_TWO,
        TAGGED_UNION_TWO_TRAILING,
        TAGGED_UNION_ENUM_TAG,
        TAGGED_UNION_ENUM_TAG_TRAILING,
        CONTAINER_FIELD_INIT,
        CONTAINER_FIELD_ALIGN,
        CONTAINER_FIELD,
        COMPTIME,
        NOSUSPEND,
        BLOCK_TWO,
        BLOCK_TWO_SEMICOLON,
        BLOCK,
        BLOCK_SEMICOLON,
        ASM_SIMPLE,
        ASM,
        ASM_OUTPUT,
        ASM_INPUT,
        ERROR_VALUE,
        ERROR_UNION,
    }

    enum class ErrorTag {
        CHAINED_COMPARISON_OPERATORS,
        DECL_BETWEEN_FIELDS,
        EXPECTED_BLOCK,
        EXPECTED_BLOCK_OR_ASSIGNMENT,
        EXPECTED_BLOCK_OR_EXPR,
        EXPECTED_BLOCK_OR_FIELD,
        EXPECTED_CONTAINER_MEMBERS,
        EXPECTED_EXPR,
        EXPECTED_EXPR_OR_ASSIGNMENT,
        EXPECTED_EXPR_OR_VAR_DECL,
        EXPECTED_FN,
        EXPECTED_INLINABLE,
        EXPECTED_LABELABLE,
        EXPECTED_PARAM_LIST,
        EXPECTED_PREFIX_EXPR,
        EXPECTED_PRIMARY_TYPE_EXPR,
        EXPECTED_PUB_ITEM,
        EXPECTED_RETURN_TYPE,
        EXPECTED_SEMI_OR_ELSE,
        EXPECTED_SEMI_OR_LBRACE,
        EXPECTED_STATEMENT,
        EXPECTED_SUFFIX_OP,
        EXPECTED_TYPE_EXPR,
        EXPECTED_VAR_DECL,
        EXPECTED_VAR_DECL_OR_FN,
        EXPECTED_LOOP_PAYLOAD,
        EXPECTED_CONTAINER,
        EXTERN_FN_BODY,
        EXTRA_ADDRSPACE_QUALIFIER,
        EXTRA_ALIGN_QUALIFIER,
        PTR_MOD_ON_ARRAY_CHILD_TYPE,
        INVALID_BIT_RANGE,
        SAME_LINE_DOC_COMMENT,
        UNATTACHED_DOC_COMMENT,
        TEST_DOC_COMMENT,
        COMPTIME_DOC_COMMENT,
        VARARGS_NONFINAL,
        EXPECTED_SEMI_AFTER_DECL,
        EXPECTED_SEMI_AFTER_STMT,
        EXPECTED_COMMA_AFTER_FIELD,
        EXPECTED_COMMA_AFTER_ARG,
        EXPECTED_COMMA_AFTER_PARAM,
        EXPECTED_COMMA_AFTER_INITIALIZER,
        EXPECTED_COMMA_AFTER_SWITCH_PRONG,
        EXPECTED_COMMA_AFTER_FOR_OPERAND,
        EXPECTED_COMMA_AFTER_CAPTURE,
        EXPECTED_INITIALIZER,
        MISMATCHED_BINARY_OP_WHITESPACE,
        INVALID_AMPERSAND_AMPERSAND,
        C_STYLE_CONTAINER,
        EXPECTED_VAR_CONST,
        WRONG_EQUAL_VAR_DECL,
        VAR_CONST_DECL,
        AMBIGUOUS_OPERATOR_PRECEDENCE,
        PREVIOUS_AMBIGUOUS_OPERATOR,
        HINT_OPERATOR_PRECEDENCE_LEFT,
        HINT_OPERATOR_PRECEDENCE_RIGHT,
        ILLEGAL_CHAINED_OPERATORS,
        ZIG_STYLE_CONTAINER,
        PREVIOUS_FIELD,
        NEXT_FIELD,
        EXPECTED_TOKEN,
        INVALID_BYTE,
    }

    enum class ErrorExtraKind { None, ExpectedToken, InvalidByte }

    init {
        require(bytes.size >= 64)
        require(bytes.getPackedInt(0) == AST_MAGIC)
        require(bytes.getPackedInt(4) == 2)
        require(bytes.getPackedInt(8) in 0..1)
        require(bytes.getPackedInt(12) >= 0)
        require(bytes.getPackedInt(60) == bytes.size)
        checkSection(32, 16, 1)
        checkSection(36, 16, 4)
        checkSection(40, 20, 1)
        checkSection(44, 20, 4)
        checkSection(48, 20, 8)
        checkSection(52, 24, 4)
        checkSection(56, 28, 24)
    }

    /**
     * `nodeIndex` is the node index, `nodeTag` its kind, and `tokenIndex` its main token index.
     * `payloadWord0` and `payloadWord1` are raw values interpreted according to `nodeTag`.
     * Inlined so that caller stays on stack.
     */
    inline fun forEachNode(block: (nodeIndex: Int, nodeTag: NodeTag, tokenIndex: Int, payloadWord0: Int, payloadWord1: Int) -> Unit) {
        var index = 0
        val count = nodeCount()
        val tagsOffset = bytes.getPackedInt(40)
        val mainTokensOffset = bytes.getPackedInt(44)
        val payloadsOffset = bytes.getPackedInt(48)
        while (index < count) {
            val tag = NodeTag.entries[bytes[tagsOffset + index].toInt() and 0xFF]
            val mainToken = bytes.getPackedInt(mainTokensOffset + index * 4)
            val payloadOffset = payloadsOffset + index * 8
            val payloadWord0 = bytes.getPackedInt(payloadOffset)
            val payloadWord1 = bytes.getPackedInt(payloadOffset + 4)
            block(index, tag, mainToken, payloadWord0, payloadWord1)
            index++
        }
    }

    /**
     * `tokenIndex` is the token index, `tokenTag` its kind, and `startByte` its UTF-8 byte offset.
     * Inlined so that caller stays on stack.
     */
    inline fun forEachToken(block: (tokenIndex: Int, tokenTag: TokenTag, startByte: Int) -> Unit) {
        var index = 0
        val count = tokenCount()
        val tagsOffset = bytes.getPackedInt(32)
        val startsOffset = bytes.getPackedInt(36)
        while (index < count) {
            val tag = TokenTag.entries[bytes[tagsOffset + index].toInt() and 0xFF]
            val start = bytes.getPackedInt(startsOffset + index * 4)
            block(index, tag, start)
            index++
        }
    }

    fun mode(): Mode = Mode.entries[word(8)]

    fun sourceLength(): Int = word(12)

    fun tokenCount(): Int = word(16)

    fun nodeCount(): Int = word(20)

    fun extraDataCount(): Int = word(24)

    fun errorCount(): Int = word(28)

    fun tokenTag(index: Int): TokenTag = TokenTag.entries[bytes[entryOffset(index, 16, 32, 1)].toInt() and 0xFF]

    fun tokenStart(index: Int): Int = word(entryOffset(index, 16, 36, 4))

    fun nodeTag(index: Int): NodeTag = NodeTag.entries[bytes[entryOffset(index, 20, 40, 1)].toInt() and 0xFF]

    fun nodeMainToken(index: Int): Int = word(entryOffset(index, 20, 44, 4))

    fun extraData(index: Int): Int = word(entryOffset(index, 24, 52, 4))

    fun errorTag(index: Int): ErrorTag = ErrorTag.entries[word(entryOffset(index, 28, 56, 24))]

    fun errorToken(index: Int): Int = word(entryOffset(index, 28, 56, 24) + 4)

    fun errorFlags(index: Int): Int = word(entryOffset(index, 28, 56, 24) + 8)

    fun errorIsNote(index: Int): Boolean = errorFlags(index) and 1 != 0

    fun errorTokenIsPrevious(index: Int): Boolean = errorFlags(index) and 2 != 0

    fun errorExtraKind(index: Int): ErrorExtraKind = ErrorExtraKind.entries[word(entryOffset(index, 28, 56, 24) + 12)]

    fun errorExtraValue(index: Int): Long = bytes.getPackedLong(entryOffset(index, 28, 56, 24) + 16)

    private fun word(offset: Int): Int = bytes.getPackedInt(offset)

    private fun entryOffset(index: Int, countField: Int, offsetField: Int, stride: Int): Int {
        val count = word(countField)
        if (index !in 0..<count) throw IndexOutOfBoundsException("Index $index out of bounds for size $count")
        return word(offsetField) + index * stride
    }

    private fun checkSection(offsetField: Int, countField: Int, stride: Int) {
        val offset = word(offsetField)
        val count = word(countField)
        require(offset >= 64 && count >= 0 && offset.toLong() + count.toLong() * stride <= bytes.size)
    }
}
