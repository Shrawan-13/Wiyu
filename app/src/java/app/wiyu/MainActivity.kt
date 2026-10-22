package app.wiyu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.wiyu.data.DocFamily
import app.wiyu.data.FileEntry
import app.wiyu.data.FileTypes
import app.wiyu.data.Scanner
import app.wiyu.data.WiyuDb
import app.wiyu.data.WiyuRepo
import app.wiyu.data.WiyuSettings
import app.wiyu.designsystem.FamilyGlyph
import app.wiyu.designsystem.FolderIcon
import app.wiyu.designsystem.WiyuTheme
import app.wiyu.designsystem.WiyuTopBar
import app.wiyu.feature.browse.BrowseScreen
import app.wiyu.feature.browse.browseTitle
import app.wiyu.feature.details.FileDetailsSheet
import app.wiyu.feature.home.HomeScreen
import app.wiyu.feature.search.SearchScreen
import app.wiyu.feature.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single Compose host for the Wiyu shell.
 *
 * The document editor lives in [org.libreoffice.LibreOfficeMainActivity] and is
 * launched by Intent, so the LibreOfficeKit surface keeps its own window and
 * rendering pipeline.
 */
class MainActivity : ComponentActivity() {

    private lateinit var repo: WiyuRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = WiyuRepo.get(this)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            repo.migrateLegacyRecents()
            repo.startScan("launch")
        }
        setContent { WiyuApp(repo) }
    }
}

@Composable
private fun WiyuApp(repo: WiyuRepo) {
    val settings by repo.settings.flow.collectAsState()
    val dark = when (settings.themeMode) {
        WiyuSettings.THEME_LIGHT -> false
        WiyuSettings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    WiyuTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
        WiyuShell(repo)
    }
}

private const val TAB_HOME = 0
private const val TAB_BROWSE = 1
private const val TAB_SEARCH = 2

@Composable
private fun WiyuShell(repo: WiyuRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    var tab by rememberSaveable { mutableStateOf(TAB_HOME) }
    var browsePath by rememberSaveable { mutableStateOf("") }
    var familyId by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<FileEntry?>(null) }
    var pendingCopy by remember { mutableStateOf<FileEntry?>(null) }
    var hasFullAccess by remember { mutableStateOf(Scanner.hasFullStorageAccess(context)) }

    val familyFilter = familyId.takeIf { it.isNotEmpty() }?.let { DocFamily.fromId(it) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (t: Throwable) {
                // Not every provider offers a persistable grant; opening still works.
            }
            val mime = context.contentResolver.getType(uri)
            val name = FileOps.displayName(context, uri)
            if (FileTypes.isSupported(name, mime)) {
                EditorBridge.openUri(context, uri, mime, name)
            } else {
                notify("Wiyu cannot open ${name ?: "that file"}")
            }
        }
    }

    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (t: Throwable) {
                notify("Could not keep access to that folder")
            }
            scope.launch {
                val label = repo.displayNameForTree(uri)
                repo.addRoot(uri, label)
                notify("Added $label")
            }
        }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            hasFullAccess = Scanner.hasFullStorageAccess(context)
            repo.startScan("storage permission granted")
        } else {
            notify("Without access, Wiyu only sees the folders you add")
        }
    }

    val manageStorage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasFullAccess = Scanner.hasFullStorageAccess(context)
        if (hasFullAccess) repo.startScan("full access granted") else notify("Full access was not granted")
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { target ->
        val source = pendingCopy
        pendingCopy = null
        if (target != null && source != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { FileOps.copy(context, source.uri, target) }
                notify(if (ok) "Saved a copy of ${source.name}" else "Could not save the copy")
            }
        }
    }

    val requestFullAccess: (Boolean) -> Unit = { wanted ->
        if (!wanted) {
            notify("Full access can be revoked in system settings")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            try {
                manageStorage.launch(intent)
            } catch (t: Throwable) {
                manageStorage.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun openEntry(entry: FileEntry) = EditorBridge.open(context, entry)

    fun shareEntry(entry: FileEntry) {
        val uri = Uri.parse(entry.uri)
        val mime = entry.mime ?: FileTypes.mimeFor(entry.name) ?: "*/*"
        if (uri.scheme == "content") {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share ${entry.name}"))
        } else {
            scope.launch {
                val staged = withContext(Dispatchers.IO) { FileOps.stageForShare(context, uri, entry.name) }
                if (staged == null) {
                    notify("Could not prepare the file for sharing")
                } else {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, staged)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share ${entry.name}"))
                }
            }
        }
    }

    val upIcon: (@Composable () -> Unit)? = if (browsePath.isNotEmpty()) {
        {
            IconButton(onClick = { browsePath = WiyuDb.parentOf(browsePath) }) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Up one level")
            }
        }
    } else {
        null
    }

    BackHandler(enabled = details != null || showSettings || browsePath.isNotEmpty()) {
        when {
            details != null -> details = null
            showSettings -> showSettings = false
            else -> browsePath = WiyuDb.parentOf(browsePath)
        }
    }

    Scaffold(
        topBar = {
            when {
                showSettings -> WiyuTopBar(
                    title = "Settings",
                    navigationIcon = {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                )

                tab == TAB_BROWSE -> WiyuTopBar(
                    title = browseTitle(browsePath),
                    navigationIcon = upIcon,
                    actions = {
                        IconButton(onClick = { openTree.launch(null) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add folder")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    },
                )

                tab == TAB_SEARCH -> WiyuTopBar(title = "Search")

                else -> WiyuTopBar(
                    title = "Wiyu",
                    actions = {
                        IconButton(onClick = { tab = TAB_SEARCH }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == TAB_HOME,
                        onClick = { tab = TAB_HOME },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = tab == TAB_BROWSE,
                        onClick = { tab = TAB_BROWSE },
                        icon = { FolderIcon() },
                        label = { Text("Browse") },
                    )
                    NavigationBarItem(
                        selected = tab == TAB_SEARCH,
                        onClick = { tab = TAB_SEARCH },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text("Search") },
                    )
                }
            }
        },
        floatingActionButton = {
            if (!showSettings && tab == TAB_HOME) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { expanded = true },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("New") },
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Open a file") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            onClick = {
                                expanded = false
                                openFile.launch(arrayOf("*/*"))
                            },
                        )
                        HorizontalDivider()
                        FileTypes.creatableFamilies.forEach { family ->
                            DropdownMenuItem(
                                text = { Text(family.label) },
                                leadingIcon = { FamilyGlyph(family, size = 24.dp) },
                                onClick = {
                                    expanded = false
                                    EditorBridge.newDocument(context, family)
                                },
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            showSettings -> SettingsScreen(
                repo = repo,
                hasFullAccess = hasFullAccess,
                modifier = Modifier.padding(padding),
                onAddFolder = { openTree.launch(null) },
                onRemoveRoot = { root -> repo.removeRoot(root.uri); notify("Removed ${root.label}") },
                onToggleFullAccess = requestFullAccess,
                onRebuildIndex = { repo.startScan("rebuild") },
            )

            tab == TAB_BROWSE -> BrowseScreen(
                repo = repo,
                path = browsePath,
                familyFilter = familyFilter,
                modifier = Modifier.padding(padding),
                onNavigate = { browsePath = it },
                onOpen = { openEntry(it) },
                onMore = { details = it },
                onAddFolder = { openTree.launch(null) },
                onOpenFile = { openFile.launch(arrayOf("*/*")) },
                onClearFamilyFilter = { familyId = "" },
            )

            tab == TAB_SEARCH -> SearchScreen(
                repo = repo,
                modifier = Modifier.padding(padding),
                onOpen = { openEntry(it) },
                onMore = { details = it },
                onAddFolder = { openTree.launch(null) },
            )

            else -> HomeScreen(
                repo = repo,
                modifier = Modifier.padding(padding),
                onOpen = { openEntry(it) },
                onOpenFile = { openFile.launch(arrayOf("*/*")) },
                onAddFolder = { openTree.launch(null) },
                onMore = { details = it },
                onSeeFamily = { family ->
                    familyId = family.id
                    browsePath = ""
                    tab = TAB_BROWSE
                },
                hasFullAccess = hasFullAccess,
                onRequestFullAccess = { requestFullAccess(true) },
            )
        }

        details?.let { entry ->
            FileDetailsSheet(
                entry = entry,
                onDismiss = { details = null },
                onOpen = {
                    details = null
                    openEntry(entry)
                },
                onShare = {
                    details = null
                    shareEntry(entry)
                },
                onSaveCopy = {
                    details = null
                    pendingCopy = entry
                    createDocument.launch(entry.name)
                },
                onDelete = {
                    details = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { FileOps.delete(context, Uri.parse(entry.uri)) }
                        if (ok) {
                            repo.query { it.removeFile(entry.uri) }
                            repo.refresh()
                            notify("Deleted ${entry.name}")
                        } else {
                            notify("Could not delete ${entry.name}")
                        }
                    }
                },
                onOpenWith = {
                    details = null
                    try {
                        context.startActivity(EditorBridge.openWithChooser(context, Uri.parse(entry.uri), entry.mime))
                    } catch (t: Throwable) {
                        notify("No app can open this file")
                    }
                },
            )
        }
    }
}
