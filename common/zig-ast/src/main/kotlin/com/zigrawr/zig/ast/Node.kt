package com.zigrawr.zig.ast

class Node(
    val tag: Tag,
    val mainToken: Int,
    val dataType: DataType,
    val data: Data,
) {

    @JvmInline
    value class Data private constructor(private val packed: Long) {

    }

    enum class DataType {
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
}
