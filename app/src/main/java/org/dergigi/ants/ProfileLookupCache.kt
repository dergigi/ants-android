package org.dergigi.ants

import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Short-lived, memory-only cache. Clear cannot be undone by an older in-flight lookup. */
internal class ProfileLookupCache<T>(
    private val capacity: Int,
    private val lifetime: (T) -> Long = { 300_000L },
    private val weight: (T) -> Long = { 1L },
    private val maxWeight: Long = 8_000_000L,
) {
    private data class Entry<T>(val value: T, val expires: Long, val weight: Long)
    private val entries = ConcurrentHashMap<String, Entry<T>>()
    // Fixed stripes avoid retaining a mutex for every name ever searched.
    private val locks = Array(32) { Mutex() }
    private val epoch = AtomicInteger()
    fun clear() { epoch.incrementAndGet(); entries.clear() }

    suspend fun get(key: String, load: suspend () -> T): T = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
        val now = SystemClock.elapsedRealtime()
        entries[key]?.takeIf { it.expires > now }?.let { return@withLock it.value }
        val revision = epoch.get()
        val value = load() // Exceptions and cancellations are not cached.
        val size = weight(value)
        val ttl = lifetime(value)
        if (revision == epoch.get() && size <= maxWeight && ttl > 0) {
            entries.entries.removeIf { it.value.expires <= now }
            entries[key] = Entry(value, SystemClock.elapsedRealtime() + ttl, size)
            while (entries.size > capacity || entries.values.sumOf { it.weight } > maxWeight) {
                val oldest = entries.minByOrNull { it.value.expires }?.key ?: break
                entries.remove(oldest)
            }
        }
        value
    }
}
