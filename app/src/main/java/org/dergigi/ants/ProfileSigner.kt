package org.dergigi.ants

import org.json.JSONObject

internal data class EventSignRequest(val id: String, val event: Nip01Event, val packageName: String)
internal fun validatedSignedEvent(expected: Nip01Event, eventJson: String?, signature: String?): Nip01Event? = runCatching {
    val signed = if (!eventJson.isNullOrBlank()) Nip01Event.parse(JSONObject(eventJson))
        else signature?.let { expected.copy(sig = it) }
    signed?.takeIf { it.id == expected.id && it.pubkey == expected.pubkey && it.verify() }
}.getOrNull()
