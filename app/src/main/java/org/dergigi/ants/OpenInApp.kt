package org.dergigi.ants

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

internal fun openInNostrApp(context: Context, event: Nip01Event) {
    val pointer = if (event.kind == 0) Nip19.npubEncode(event.pubkey)
        else Nip19.neventEncode(NeventPointer(event.id, author = event.pubkey, kind = event.kind))
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostr:$pointer"))
    @Suppress("DEPRECATION")
    val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (handlers.none { it.activityInfo.packageName != context.packageName && it.activityInfo.exported && it.activityInfo.enabled }) {
        Toast.makeText(context, "No compatible Nostr app installed.", Toast.LENGTH_SHORT).show()
        return
    }
    val excluded = handlers.filter { it.activityInfo.packageName == context.packageName }
        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toTypedArray()
    val chooser = Intent.createChooser(intent, "Open in app").apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }.onFailure {
        Toast.makeText(context, "Unable to open a Nostr app.", Toast.LENGTH_SHORT).show()
    }
}
