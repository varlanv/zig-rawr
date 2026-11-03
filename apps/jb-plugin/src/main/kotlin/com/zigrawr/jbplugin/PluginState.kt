package com.zigrawr.jbplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.zigrawr.jbplugin.log.IntellijLog
import com.zigrawr.zig.foreign.Bindings
import com.zigrawr.zig.foreign.BundledBindingsLoader
import java.lang.foreign.Arena
import java.util.WeakHashMap

private val log = IntellijLog.asInternalLog()

@Service(Service.Level.APP)
class IdeProcessState : Disposable {

    val arena: Arena = Arena.ofShared()
    val bindings: Bindings = try {
        Bindings(context(log) { BundledBindingsLoader.load(arena) })
    } catch (e: Throwable) {
        arena.close()
        throw e
    }

    override fun dispose() {
        try {
            bindings.close()
        } finally {
            arena.close()
        }
    }

    companion object {

        @JvmStatic
        fun getInstance(): IdeProcessState = service()
    }
}

@Service(Service.Level.PROJECT)
class IdeProjectState : Disposable {
    private val snapshots = WeakHashMap<Any, MutableMap<Key<*>, Any>>()
    private var disposed = false

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cached(holder: Any, key: Key<T>): T? = snapshots[holder]?.get(key) as T?

    @Synchronized
    fun <T : Any> cache(holder: Any, key: Key<T>, value: T) {
        if (!disposed) snapshots.getOrPut(holder) { HashMap() }[key] = value
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        snapshots.clear()
    }
}
