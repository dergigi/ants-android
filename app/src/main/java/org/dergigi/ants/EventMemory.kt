package org.dergigi.ants

internal const val RESULT_MEMORY_BUDGET = 8L * 1024 * 1024
internal const val CONTEXT_MEMORY_BUDGET = 2L * 1024 * 1024
internal const val HISTORY_MEMORY_BUDGET = 8L * 1024 * 1024
internal const val RESULT_MEMORY_LIMIT = "Result memory limit reached"

/** Preserve display priority while bounding payloads as well as event counts. */
internal fun boundedEvents(events: List<Nip01Event>, budget: Long = RESULT_MEMORY_BUDGET, count: Int = 500): List<Nip01Event> {
    var retained = 0L
    return buildList {
        for (event in events) {
            if (size >= count) break
            if (retained + event.retainedBytes > budget) continue
            add(event)
            retained += event.retainedBytes
        }
    }
}

internal fun retainContextEvent(previous: Map<String, Nip01Event>, key: String, event: Nip01Event): Map<String, Nip01Event> {
    var bytes = 0L
    return buildMap {
        for ((id, value) in listOf(key to event) + previous.entries.map { it.key to it.value }) {
            if (id in this || size >= 100 || bytes + value.retainedBytes > CONTEXT_MEMORY_BUDGET) continue
            put(id, value)
            bytes += value.retainedBytes
        }
    }
}
