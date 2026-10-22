package app.wiyu.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wiyu.data.FileEntry
import app.wiyu.designsystem.FamilyGlyph
import app.wiyu.util.formatBytes
import app.wiyu.util.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsSheet(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveCopy: () -> Unit,
    onDelete: () -> Unit,
    onOpenWith: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FamilyGlyph(entry.family, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            entry.family.label,
                            entry.extension.uppercase().ifEmpty { null },
                            formatBytes(entry.size),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(entry.family.label)
                if (entry.isTemplate) Tag("Template")
                if (!entry.family.let { it == app.wiyu.data.DocFamily.PDF }) Tag("Editable") else Tag("View only")
            }

            Spacer(Modifier.height(16.dp))
            InfoLine("Location", entry.path)
            if (entry.modified > 0) InfoLine("Modified", formatDateTime(entry.modified))
            if (entry.lastOpened > 0) InfoLine("Last opened", formatDateTime(entry.lastOpened))

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
                FilledTonalButton(onClick = onSaveCopy, modifier = Modifier.weight(1f)) { Text("Save a copy") }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenWith) { Text("Open with another app") }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
