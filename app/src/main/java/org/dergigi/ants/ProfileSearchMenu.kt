package org.dergigi.ants

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// Same order and alias spelling as the web app's public/replacements.txt.
private val profileSearchAliases = listOf(
    "profile", "tweet", "repost", "reaction", "image", "video", "picture", "media", "file",
    "patch", "issue", "report", "zap", "nutzap", "highlight", "blogpost", "article", "longform",
    "muted", "pin", "bookmark", "code", "followpack",
)

@Composable
internal fun ProfileSearchMenu(author: String, onSearch: (String) -> Unit) {
    profileSearchAliases.forEach { alias ->
        val keyword = "is:$alias"
        DropdownMenuItem(text = { Text(keyword) }, enabled = kindAliases.getValue(alias).all { it in renderedKinds },
            onClick = { onSearch("$keyword by:$author") })
    }
    // Mentions target the profile, rather than events authored by it.
    DropdownMenuItem(text = { Text("mentions:") }, onClick = { onSearch("mentions:$author") })
    DropdownMenuItem(text = { Text("kind:1111") }, onClick = { onSearch("kind:1111 by:$author") })
}
