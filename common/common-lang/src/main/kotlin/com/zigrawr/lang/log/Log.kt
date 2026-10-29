package com.zigrawr.lang.log

/**
 * Very primitive wrapper for logging.
 * Is not meant to be general high-performant logging implementation.
 */
class Log(val sink: Sink, val enabled: Boolean) {

    inline fun debug(message: () -> String) {
        if (enabled) {
            sink.print(message())
        }
    }

    interface Sink {

        fun print(message: String)

        companion object {

            val noop: Sink = object : Sink {
                override fun print(message: String) {
                    // no-op
                }
            }
        }
    }
}
