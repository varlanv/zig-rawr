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

    companion object {

        val noop: Log = Log(sink = Sink.noop, enabled = false)
        val stderr: Log = Log(sink = Sink.stderr, enabled = false)
    }

    interface Sink {

        fun print(message: String)

        companion object {

            val noop: Sink = object : Sink {
                override fun print(message: String) {
                    // no-op
                }
            }
            val stderr: Sink = object : Sink {
                override fun print(message: String) {
                    System.err.println(message)
                }
            }
        }
    }
}
