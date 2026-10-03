package org.dergigi.ants

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull

/** Deliver every received update, but allow the UI to breathe between bursts. */
internal fun Flow<RelayUpdate>.batched(): Flow<List<RelayUpdate>> = flow {
    coroutineScope {
        val input = this@batched.buffer(8).produceIn(this)
        try {
            var closed = false
            while (!closed) {
                val first = input.receiveCatching().getOrNull() ?: break
                val batch = mutableListOf(first)
                withTimeoutOrNull(100) {
                    while (batch.size < 16) {
                        val next = input.receiveCatching().getOrNull()
                        if (next == null) { closed = true; break }
                        batch.add(next)
                    }
                }
                emit(batch)
            }
        } finally { input.cancel() }
    }
}
