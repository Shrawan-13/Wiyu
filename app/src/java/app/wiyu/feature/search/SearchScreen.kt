package app.wiyu.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.wiyu.data.DocFamily
import app.wiyu.data.FileEntry
import app.wiyu.data.WiyuRepo
import app.wiyu.designsystem.EmptyState
import app.wiyu.designsystem.FileRow
import kotlinx.coroutines.delay

private val filterFamilies = listOf(
    DocFamily.WRITER,
    DocFamily.CALC,
    DocFamily.IMPRESS,
    DocFamily.DRAW,
    DocFamily.PDF,
)

@Composable
fun SearchScreen(
    repo: WiyuRepo,
    modifier: Modifier = Modifier,
    onOpen: (FileEntry) -> Unit,
    onMore: (FileEntry) -> Unit,
    onAddFolder: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var familyId by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<FileEntry>()) }
    var resultCount by remember { mutableStateOf(0) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var indexed by remember { mutableStateOf(0) }

    val version by repo.version.collectAsState()

    LaunchedEffect(Unit) {
        indexed = repo.query { it.totalFiles() }
    }
    LaunchedEffect(version) {
        indexed = repo.query { it.totalFiles() }
    }

    LaunchedEffect(query, familyId, version) {
        if (query.isBlank()) {
            results = emptyList()
            resultCount = 0
            elapsedMs = 0
            return@LaunchedEffect
        }
        // Debounce: a new keystroke cancels this coroutine before the wait ends.
        delay(120)
        val family = familyId.takeIf { it.isNotEmpty() }?.let { DocFamily.fromId(it) }
        val start = System.currentTimeMillis()
        val hits = repo.query { it.search(query, family, 60) }
        val count = repo.query { it.searchCount(query, family) }
        elapsedMs = System.currentTimeMillis() - start
        results = hits
        resultCount = count
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search documents") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = familyId.isEmpty(),
                    onClick = { familyId = "" },
                    label = { Text("All") },
                )
            }
            items(filterFamilies, key = { it.id }) { family ->
                FilterChip(
                    selected = familyId == family.id,
                    onClick = { familyId = if (familyId == family.id) "" else family.id },
                    label = { Text(family.label) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            when {
                indexed == 0 -> item(key = "no-index") {
                    EmptyState(
                        title = "Nothing to search yet",
                        body = "Add a folder so Wiyu can index the documents inside it.",
                        primaryLabel = "Add a folder",
                        onPrimary = onAddFolder,
                    )
                }

                query.isBlank() -> item(key = "hint") {
                    EmptyState(
                        title = "Search $indexed documents",
                        body = "Start typing a file name. Results are ranked by match, then by how recently you opened them.",
                    )
                }

                results.isEmpty() -> item(key = "no-match") {
                    EmptyState(
                        title = "No matches",
                        body = "Nothing matches \"$query\". Try fewer words, or clear the type filter.",
                    )
                }

                else -> {
                    item(key = "count") {
                        Text(
                            text = "$resultCount result${if (resultCount == 1) "" else "s"} · ${elapsedMs} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(results, key = { it.id }) { entry ->
                        FileRow(
                            entry = entry,
                            onClick = { onOpen(entry) },
                            onMoreClick = { onMore(entry) },
                            highlight = query,
                        )
                    }
                }
            }
        }
    }
}
