package app.wiyu.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.wiyu.data.DirEntry
import app.wiyu.data.DocFamily
import app.wiyu.data.FileEntry
import app.wiyu.data.RootEntry
import app.wiyu.data.WiyuDb
import app.wiyu.data.WiyuRepo
import app.wiyu.designsystem.DirRow
import app.wiyu.designsystem.EmptyState
import app.wiyu.designsystem.FileRow

@Composable
fun BrowseScreen(
    repo: WiyuRepo,
    path: String,
    familyFilter: DocFamily?,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onOpen: (FileEntry) -> Unit,
    onMore: (FileEntry) -> Unit,
    onAddFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onClearFamilyFilter: () -> Unit,
) {
    val version by repo.version.collectAsState()
    val dirs by produceState(initialValue = emptyList<DirEntry>(), version, path) {
        value = repo.query { it.subdirs(path) }
    }
    val files by produceState(initialValue = emptyList<FileEntry>(), version, path) {
        value = repo.query { it.children(path) }
    }
    val roots by produceState(initialValue = emptyList<RootEntry>(), version) {
        value = repo.query { it.roots() }
    }
    val total by produceState(initialValue = 0, version) {
        value = repo.query { it.totalFiles() }
    }
    val visibleFiles = remember(files, familyFilter) {
        if (familyFilter == null) files else files.filter { it.family == familyFilter }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (roots.isNotEmpty()) {
            item(key = "roots") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        FilterChip(
                            selected = path.isEmpty(),
                            onClick = { onNavigate("") },
                            label = { Text("All") },
                        )
                    }
                    items(roots, key = { it.uri }) { root ->
                        FilterChip(
                            selected = path == root.label,
                            onClick = { onNavigate(root.label) },
                            label = { Text(root.label) },
                        )
                    }
                }
            }
        }

        if (total == 0) {
            item(key = "empty") {
                EmptyState(
                    title = "Nothing indexed yet",
                    body = "Add a folder so Wiyu can show the documents inside it, or open a file directly.",
                    primaryLabel = "Add a folder",
                    onPrimary = onAddFolder,
                    secondaryLabel = "Open a file",
                    onSecondary = onOpenFile,
                )
            }
            return@LazyColumn
        }

        if (familyFilter != null) {
            item(key = "filter") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = onClearFamilyFilter,
                            label = { Text(familyFilter.label) },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear filter",
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
        }

        if (path.isNotEmpty()) {
            item(key = "subtitle") {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }

        if (dirs.isEmpty() && visibleFiles.isEmpty()) {
            item(key = "folder-empty") {
                EmptyState(
                    title = if (familyFilter != null) "No ${familyFilter.label.lowercase()}" else "This folder is empty",
                    body = if (familyFilter != null) {
                        "No ${familyFilter.label.lowercase()} in this folder."
                    } else {
                        "Wiyu only lists documents it can open."
                    },
                )
            }
        }

        items(dirs, key = { "dir:" + it.path }) { dir ->
            DirRow(dir = dir, onClick = { onNavigate(dir.path) })
        }

        items(visibleFiles, key = { "file:" + it.uri }) { entry ->
            FileRow(
                entry = entry,
                onClick = { onOpen(entry) },
                onMoreClick = { onMore(entry) },
            )
        }
    }
}

/** Shown in the top bar as a subtitle so deep paths stay readable. */
fun browseTitle(path: String): String =
    if (path.isEmpty()) "Browse" else path.substringAfterLast('/')

fun browseSubtitle(path: String): String? =
    if (path.isEmpty()) null else WiyuDb.parentOf(path).ifEmpty { null }
