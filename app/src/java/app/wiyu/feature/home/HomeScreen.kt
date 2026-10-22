package app.wiyu.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wiyu.data.DocFamily
import app.wiyu.data.FileEntry
import app.wiyu.data.RootEntry
import app.wiyu.data.ScanState
import app.wiyu.data.WiyuRepo
import app.wiyu.designsystem.EmptyState
import app.wiyu.designsystem.FamilyGlyph
import app.wiyu.designsystem.FileRow
import app.wiyu.designsystem.SectionHeader
import app.wiyu.designsystem.familyAccent
import app.wiyu.util.dateGroup
import app.wiyu.util.formatRelativeTime

@Composable
fun HomeScreen(
    repo: WiyuRepo,
    modifier: Modifier = Modifier,
    onOpen: (FileEntry) -> Unit,
    onOpenFile: () -> Unit,
    onAddFolder: () -> Unit,
    onMore: (FileEntry) -> Unit,
    onSeeFamily: (DocFamily) -> Unit,
) {
    val version by repo.version.collectAsState()
    val scan by repo.scan.collectAsState()

    val recent by produceState(initialValue = emptyList<FileEntry>(), version) {
        value = repo.query { it.recent(24) }
    }
    val counts by produceState(initialValue = emptyMap<DocFamily, Int>(), version) {
        value = repo.query { it.countsByFamily() }
    }
    val total by produceState(initialValue = 0, version) {
        value = repo.query { it.totalFiles() }
    }
    val roots by produceState(initialValue = emptyList<RootEntry>(), version) {
        value = repo.query { it.roots() }
    }

    val continueList = remember(recent) { recent.filter { it.lastOpened > 0 }.take(6) }
    val grouped = remember(recent) {
        var last: String? = null
        recent.take(18).map { entry ->
            val group = dateGroup(entry.lastOpened.takeIf { it > 0 } ?: entry.modified)
            val header = if (group != last) {
                last = group
                group
            } else null
            entry to header
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (scan.running) {
            item(key = "scan") { ScanProgressCard(scan) }
        }

        if (total == 0 && !scan.running) {
            item(key = "empty") {
                Column {
                    SectionHeader(title = "Get started")
                    EmptyState(
                        title = "No documents yet",
                        body = if (roots.isEmpty()) {
                            "Wiyu shows the documents in folders you choose. Nothing leaves your device and you can revoke access at any time."
                        } else {
                            "No supported documents were found in the folders you granted. Add another folder, or open a file directly."
                        },
                        primaryLabel = "Add a folder",
                        onPrimary = onAddFolder,
                        secondaryLabel = "Open a file",
                        onSecondary = onOpenFile,
                    )
                }
            }
        } else {
            item(key = "hero") { HeroHeader(total) }
            if (continueList.isNotEmpty()) {
                item(key = "continue-header") { SectionHeader("Continue") }
                item(key = "continue") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueList, key = { it.id }) { entry ->
                            ContinueCard(entry = entry, onClick = { onOpen(entry) })
                        }
                    }
                }
            }

            item(key = "types-header") { SectionHeader("By type") }
            item(key = "types") { FamilyTiles(counts, onSeeFamily) }

            item(key = "recent-header") { SectionHeader("Recent") }
            if (grouped.isEmpty()) {
                item(key = "recent-empty") {
                    EmptyState(
                        title = "Nothing opened yet",
                        body = "Documents you open will appear here, newest first.",
                        primaryLabel = "Open a file",
                        onPrimary = onOpenFile,
                    )
                }
            } else {
                items(grouped, key = { it.first.id }) { pair ->
                    val entry = pair.first
                    val header = pair.second
                    Column {
                        if (header != null) {
                            Text(
                                text = header.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        FileRow(
                            entry = entry,
                            onClick = { onOpen(entry) },
                            onMoreClick = { onMore(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanProgressCard(scan: ScanState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (scan.indexed > 0) "Indexing ${scan.indexed} documents" else "Looking for documents",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ContinueCard(entry: FileEntry, onClick: () -> Unit) {
    val accent = familyAccent(entry.family)
    Card(
        modifier = Modifier
            .width(232.dp)
            .height(132.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = accent.container),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FamilyGlyph(entry.family, size = 30.dp)
                Spacer(Modifier.weight(1f))
                Text(
                    text = entry.extension.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.content,
                )
            }
            Column {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatRelativeTime(entry.lastOpened),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent.content,
                )
            }
        }
    }
}

@Composable
private fun FamilyTiles(counts: Map<DocFamily, Int>, onSeeFamily: (DocFamily) -> Unit) {
    val families = listOf(DocFamily.WRITER, DocFamily.CALC, DocFamily.IMPRESS, DocFamily.DRAW)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        families.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { family ->
                    FamilyTile(
                        family = family,
                        count = counts[family] ?: 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onSeeFamily(family) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyTile(family: DocFamily, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val accent = familyAccent(family)
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = accent.container),
    ) {
        Column(Modifier.padding(14.dp)) {
            FamilyGlyph(family, size = 32.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = family.label,
                style = MaterialTheme.typography.titleSmall,
                color = accent.content,
            )
            Text(
                text = if (count == 0) "No files" else "$count file${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = accent.content,
            )
        }
    }
}

@Composable
private fun HeroHeader(total: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = "Your documents",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (total == 0) "Indexing your folders…" else "$total documents indexed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
