package pl.zse.bydgoszcz.elektron.presentation.timetable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

/** Bounded sharing: evicted entries survive only while an existing page is collecting them. */
internal class SharedWeekCache<K, V>(private val parent: CoroutineScope, private val limit: Int,
    private val source: (K) -> Flow<V>) {
    private val lock = Any()
    private val entries = LinkedHashMap<K, Entry>(16, 0.75f, true)
    private inner class Entry(val key: K) {
        val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
        var collectors = 0
        var retired = false
        val shared = source(key).shareIn(scope, SharingStarted.WhileSubscribed(0, 0), replay = 1)
        val result: Flow<V> = flow {
            val acquired = synchronized(lock) {
                if (retired && collectors == 0) false else { collectors++; true }
            }
            if (!acquired) emitAll(get(key))
            else try { emitAll(shared) } finally {
                synchronized(lock) { collectors--; if (retired && collectors == 0) scope.cancel() }
            }
        }
    }
    fun get(key: K): Flow<V> = synchronized(lock) {
        val entry = entries.getOrPut(key) { Entry(key) }
        while (entries.size > limit) {
            val oldest = entries.entries.iterator().next()
            entries.remove(oldest.key)
            oldest.value.retired = true
            if (oldest.value.collectors == 0) oldest.value.scope.cancel()
        }
        entry.result
    }
}
