package com.zigrawr.zig.ast

import com.zigrawr.lang.bin.Bytes
import com.zigrawr.zig.tokenizer.Token
import com.zigrawr.zig.tokenizer.Tokenizer
import com.zigrawr.zig.tokenizer.Tokens

class Ast {

    companion object {

        fun parse(input: Bytes): Ast {
            val tokenizer = Tokenizer(input)
            val estimatedTokenCount = input.size / 8;
            val tokens = Tokens(estimatedTokenCount)

            while (true) {
                val token = tokenizer.next()
                tokens.append(token)
                if (token.tag == Token.Tag.eof) {
                    break
                }
            }
            return parseTokens(input, tokens, true)
        }

        fun parseTokens(input: Bytes, tokens: Tokens, recover: Boolean): Ast {


            TODO()
        }
    }
}
