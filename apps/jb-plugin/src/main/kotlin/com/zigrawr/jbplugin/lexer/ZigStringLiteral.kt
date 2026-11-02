package com.zigrawr.jbplugin.lexer

internal fun decodeZigStringLiteral(source: CharSequence, startOffset: Int, endOffset: Int): String? {
    var index = startOffset
    val end = endOffset - 1
    if (index >= end || source[index++] != '"' || source[end] != '"') return null
    val result = StringBuilder(end - index)
    while (index < end) {
        val char = source[index++]
        if (char != '\\') {
            result.append(char)
            continue
        }
        if (index >= end) return null
        when (val escaped = source[index++]) {
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            '\\', '"', '\'' -> result.append(escaped)
            'x' -> {
                val bytes = ByteArray((end - index) / 4 + 1)
                var count = 0
                while (true) {
                    if (index + 2 > end) return null
                    val first = source[index++].digitToIntOrNull(16) ?: return null
                    val second = source[index++].digitToIntOrNull(16) ?: return null
                    bytes[count++] = ((first shl 4) or second).toByte()
                    if (index + 2 >= end || source[index] != '\\' || source[index + 1] != 'x') break
                    index += 2
                }
                result.append(String(bytes, 0, count, Charsets.UTF_8))
            }
            'u' -> {
                if (index >= end || source[index++] != '{') return null
                var codepoint = 0
                var digits = 0
                while (index < end && source[index] != '}') {
                    val digit = source[index++].digitToIntOrNull(16) ?: return null
                    if (++digits > 6) return null
                    codepoint = (codepoint shl 4) or digit
                }
                if (digits == 0 || index >= end || source[index++] != '}' ||
                    codepoint > 0x10ffff || codepoint in 0xd800..0xdfff) return null
                result.appendCodePoint(codepoint)
            }
            else -> return null
        }
    }
    return result.toString()
}

