package org.dergigi.ants

import okhttp3.OkHttpClient
import java.util.concurrent.Executors

/** Outlives a cleared ViewModel; TLS socket close can perform network writes. */
internal object NetworkCleanup {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ants-network-cleanup").apply { isDaemon = true }
    }

    fun close(client: OkHttpClient) {
        // Capture only the client, not an Activity or ViewModel. A viewModelScope
        // launch would already be cancelled by the time onCleared is invoked.
        executor.execute {
            try {
                client.dispatcher.cancelAll()
            } finally {
                client.connectionPool.evictAll()
            }
        }
    }
}
