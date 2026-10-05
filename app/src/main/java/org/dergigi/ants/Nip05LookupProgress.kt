package org.dergigi.ants

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Search-scoped counter: concurrent verification requests keep the indicator active until all finish. */
internal class Nip05LookupProgress(private val onChange: (Boolean) -> Unit) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<Nip05LookupProgress>
    private var active = 0
    @Synchronized fun start() { active++; onChange(true) }
    @Synchronized fun finish() { active--; onChange(active > 0) }
}
