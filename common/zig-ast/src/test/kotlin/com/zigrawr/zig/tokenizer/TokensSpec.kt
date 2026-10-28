package com.zigrawr.zig.tokenizer

import io.kotest.matchers.shouldBe

class TokensSpec : BaseSpec({

    should("preserve packed tokens while growing from zero capacity") {
        val tokens = Tokens(0)
        val expected = (0 until 32).map { index ->
            Token.pack(
                ZigSourcePosition(268_435_423 + index),
                ZigSourcePosition(268_435_424 + index),
                if (index % 2 == 0) Token.Tag.identifier else Token.Tag.keyword_const,
            )
        }

        expected.forEach(tokens::append)

        tokens.size shouldBe expected.size
        expected.forEachIndexed { index, token ->
            tokens.get(index).packed shouldBe token.packed
        }
    }
})
