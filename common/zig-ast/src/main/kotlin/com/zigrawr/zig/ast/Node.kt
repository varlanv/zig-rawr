package com.zigrawr.zig.ast

class NodeList {
    val tags: Array<Tag> = emptyArray()
    val mainTokens: IntArray = intArrayOf()
    val data: LongArray = longArrayOf()
    val dataTags: Array<Data.Tag> = emptyArray()
}

@JvmInline
value class For(val packed: Int) {

    // first 31 bits
    val inputs: Int get() = TODO()

    // last bit
    val hasElse: Boolean get() = TODO()
}

object Data {

    typealias TokenIndex = Int

    enum class Tag {
        node,
        opt_node,
        token,
        node_and_node,
        opt_node_and_opt_node,
        node_and_opt_node,
        opt_node_and_node,
        node_and_extra,
        extra_and_node,
        extra_and_opt_node,
        node_and_token,
        token_and_node,
        token_and_token,
        opt_node_and_token,
        opt_token_and_node,
        opt_token_and_opt_node,
        opt_token_and_opt_token,
        for_,
        extra_range,
    }

    @JvmInline
    value class Index(val index: Int) {

        companion object {
            val root = Index(0)
        }
    }

    @JvmInline
    value class OptionalTokenIndex(val index: Int) {

        companion object {
            val none = Index(Int.MAX_VALUE)
        }
    }


    @JvmInline
    value class OptionalIndex(val index: Int) {

        companion object {
            val root = Index(0)
            val none = Index(Int.MAX_VALUE)
        }
    }

    @JvmInline
    value class Token(val index: Int)

    @JvmInline
    value class NodeAndNode(val packed: Long) {

        val index1: Index get() = TODO()
        val index2: Index get() = TODO()
    }

    @JvmInline
    value class OptNodeAndOptNode(val packed: Long) {

        val index1: OptionalIndex get() = TODO()
        val index2: OptionalIndex get() = TODO()
    }

    @JvmInline
    value class NodeAndOptNode(val packed: Long) {

        val index1: Index get() = TODO()
        val index2: OptionalIndex get() = TODO()
    }

    @JvmInline
    value class OptNodeAndNode(val packed: Long) {

        val index1: OptionalIndex get() = TODO()
        val index2: Index get() = TODO()
    }

    @JvmInline
    value class NodeAndExtra(val packed: Long) {

        val index1: Index get() = TODO()
        val index2: TokenIndex get() = TODO()
    }

    @JvmInline
    value class ExtraAndOptNode(val packed: Long) {

        val index1: Index get() = TODO()
        val index2: OptionalIndex get() = TODO()
    }

    @JvmInline
    value class NodeAndToken(val packed: Long) {

        val index1: Index get() = TODO()
        val index2: TokenIndex get() = TODO()
    }

    @JvmInline
    value class TokenAndNode(val packed: Long) {

        val index1: TokenIndex get() = TODO()
        val index2: Index get() = TODO()
    }

    @JvmInline
    value class TokenAndToken(val packed: Long) {

        val index1: TokenIndex get() = TODO()
        val index2: TokenIndex get() = TODO()
    }

    @JvmInline
    value class OptNodeAndToken(val packed: Long) {

        val index1: OptionalIndex get() = TODO()
        val index2: TokenIndex get() = TODO()
    }

    @JvmInline
    value class OptTokenAndNode(val packed: Long) {

        val index1: OptionalTokenIndex get() = TODO()
        val index2: Index get() = TODO()
    }

    @JvmInline
    value class OptTokenAndOptNode(val packed: Long) {

        val index1: OptionalTokenIndex get() = TODO()
        val index2: OptionalIndex get() = TODO()
    }

    @JvmInline
    value class OptTokenAndOptToken(val packed: Long) {

        val index1: OptionalTokenIndex get() = TODO()
        val index2: OptionalTokenIndex get() = TODO()
    }

    @JvmInline
    value class DataFor(val packed: LongArray) {

        val index: TokenIndex get() = TODO()
        val nested: For get() = TODO()
    }
}
