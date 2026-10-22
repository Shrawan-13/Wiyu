package app.wiyu.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.libreoffice.BuildConfig
import app.wiyu.data.RootEntry
import app.wiyu.data.SettingsStore
import app.wiyu.data.WiyuRepo
import app.wiyu.data.WiyuSettings
import app.wiyu.designsystem.SectionHeader
import app.wiyu.util.formatDateTime
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repo: WiyuRepo,
    hasFullAccess: Boolean,
    modifier: Modifier = Modifier,
    onAddFolder: () -> Unit,
    onRemoveRoot: (RootEntry) -> Unit,
    onToggleFullAccess: (Boolean) -> Unit,
    onRebuildIndex: () -> Unit,
) {
    val settings by repo.settings.flow.collectAsState()
    val version by repo.version.collectAsState()
    val scope = rememberCoroutineScope()

    val roots by produceState(initialValue = emptyList<RootEntry>(), version) {
        value = repo.query { it.roots() }
    }
    val total by produceState(initialValue = 0, version) {
        value = repo.query { it.totalFiles() }
    }
    val byFamily by produceState(initialValue = emptyMap<app.wiyu.data.DocFamily, Int>(), version) {
        value = repo.query { it.countsByFamily() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "appearance") { SectionHeader("Appearance") }
        item(key = "theme") {
            SettingBlock(title = "Theme") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice("System", settings.themeMode == WiyuSettings.THEME_SYSTEM) {
                        repo.settings.setThemeMode(WiyuSettings.THEME_SYSTEM)
                    }
                    ThemeChoice("Light", settings.themeMode == WiyuSettings.THEME_LIGHT) {
                        repo.settings.setThemeMode(WiyuSettings.THEME_LIGHT)
                    }
                    ThemeChoice("Dark", settings.themeMode == WiyuSettings.THEME_DARK) {
                        repo.settings.setThemeMode(WiyuSettings.THEME_DARK)
                    }
                }
            }
        }
        item(key = "dynamic") {
            SettingRow(
                title = "Dynamic colour",
                subtitle = "Take the palette from your wallpaper (Android 12+)",
                trailing = {
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { repo.settings.setDynamicColor(it) },
                    )
                },
            )
        }

        item(key = "discovery") { SectionHeader("Discovery") }
        items_roots(roots, onRemoveRoot)
        item(key = "add-folder") {
            SettingRow(
                title = "Add a folder",
                subtitle = "Grant access to a folder with the system picker",
                onClick = onAddFolder,
            )
        }
        item(key = "full-access") {
            SettingRow(
                title = "Full device access",
                subtitle = if (hasFullAccess) {
                    "Granted — Wiyu can index every folder on this device"
                } else {
                    "Needed to find documents outside the folders you granted (Android 11+)"
                },
                trailing = {
                    Switch(checked = hasFullAccess, onCheckedChange = onToggleFullAccess)
                },
            )
        }

        item(key = "index") { SectionHeader("Index") }
        item(key = "index-stats") {
            SettingRow(
                title = "$total documents indexed",
                subtitle = byFamily.entries
                    .sortedByDescending { it.value }
                    .joinToString(" · ") { "${it.key.label}: ${it.value}" }
                    .ifEmpty { "Nothing indexed yet" },
            )
        }
        item(key = "last-scan") {
            SettingRow(
                title = "Last scan",
                subtitle = if (settings.lastScanAt == 0L) "Not scanned yet" else formatDateTime(settings.lastScanAt),
                onClick = { repo.startScan("manual") },
                trailing = { Text("Scan now", color = MaterialTheme.colorScheme.primary) },
            )
        }
        item(key = "rebuild") {
            SettingRow(
                title = "Rebuild index",
                subtitle = "Forget everything and scan again from scratch",
                onClick = {
                    scope.launch {
                        repo.query { it.clearIndex() }
                        repo.refresh()
                        repo.startScan("rebuild")
                    }
                },
            )
        }

        item(key = "about") { SectionHeader("About") }
        item(key = "version") {
            SettingRow(
                title = "Wiyu ${BuildConfig.VERSION_NAME}",
                subtitle = "Document engine: LibreOffice · build ${BuildConfig.BUILD_ID_SHORT.ifEmpty { "dev" }}",
            )
        }
        item(key = "licence") {
            SettingRow(
                title = "Open source licences",
                subtitle = "Wiyu is based on LibreOffice, licensed under the MPL 2.0. Full notices are in the app's assets.",
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items_roots(
    roots: List<RootEntry>,
    onRemoveRoot: (RootEntry) -> Unit,
) {
    items(roots.size, key = { "root:" + roots[it].uri }) { index ->
        val root = roots[index]
        SettingRow(
            title = root.label,
            subtitle = "Granted folder",
            trailing = {
                IconButton(onClick = { onRemoveRoot(root) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove ${root.label}")
                }
            },
        )
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth()
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingBlock(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        content()
    }
}
