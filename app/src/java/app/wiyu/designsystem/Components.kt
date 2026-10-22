package app.wiyu.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import app.wiyu.data.DirEntry
import app.wiyu.data.DocFamily
import app.wiyu.data.FileEntry
import app.wiyu.data.Thumbnails
import app.wiyu.util.formatBytes
import app.wiyu.util.formatRelativeTime

/**
 * File-family accents.
 *
 * These are deliberately *not* part of the Material colour scheme: five more
 * full tonal palettes would fight the user's dynamic colour. They are fixed
 * container/content pairs that hold AA contrast in both schemes, used only on
 * small glyph containers and category tiles.
 */
data class FamilyAccent(val container: Color, val content: Color)

private val lightAccents = mapOf(
    DocFamily.WRITER to FamilyAccent(Color(0xFFD5E3FF), Color(0xFF17497A)),
    DocFamily.CALC to FamilyAccent(Color(0xFFBFF0D0), Color(0xFF0C5132)),
    DocFamily.IMPRESS to FamilyAccent(Color(0xFFFFDFB0), Color(0xFF6A3C00)),
    DocFamily.DRAW to FamilyAccent(Color(0xFFE6DCFF), Color(0xFF4A2E8C)),
    DocFamily.PDF to FamilyAccent(Color(0xFFFFDAD6), Color(0xFF8C1D18)),
    DocFamily.OTHER to FamilyAccent(Color(0xFFE2E2E9), Color(0xFF44474E)),
)

private val darkAccents = mapOf(
    DocFamily.WRITER to FamilyAccent(Color(0xFF17497A), Color(0xFFD5E3FF)),
    DocFamily.CALC to FamilyAccent(Color(0xFF0F5132), Color(0xFFBFF0D0)),
    DocFamily.IMPRESS to FamilyAccent(Color(0xFF5C3A00), Color(0xFFFFDFB0)),
    DocFamily.DRAW to FamilyAccent(Color(0xFF3F2A78), Color(0xFFE6DCFF)),
    DocFamily.PDF to FamilyAccent(Color(0xFF6B1F1A), Color(0xFFFFDAD6)),
    DocFamily.OTHER to FamilyAccent(Color(0xFF3A3D44), Color(0xFFE2E2E9)),
)

@Composable
fun familyAccent(family: DocFamily): FamilyAccent {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return (if (dark) darkAccents else lightAccents).getValue(family)
}

/** Family mark drawn as vector strokes — no per-type drawable assets. */
@Composable
fun FamilyGlyph(family: DocFamily, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val accent = familyAccent(family)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(accent.container),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.5f)) { drawFamilyMark(family, accent.content) }
    }
}

@Composable
fun FolderGlyph(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val tint = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.5f)) { drawFolderMark(tint) }
    }
}

private fun DrawScope.drawFamilyMark(family: DocFamily, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val page = CornerRadius(w * 0.12f, w * 0.12f)
    when (family) {
        DocFamily.WRITER -> {
            drawRoundRect(color, Offset(w * 0.12f, 0f), Size(w * 0.76f, h), page, style = stroke)
            for (i in 0..2) {
                val y = h * (0.3f + i * 0.2f)
                drawLine(color, Offset(w * 0.32f, y), Offset(w * 0.68f, y), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
            }
        }
        DocFamily.CALC -> {
            drawRoundRect(color, Offset(w * 0.05f, 0f), Size(w * 0.9f, h), page, style = stroke)
            drawLine(color, Offset(w * 0.05f, h * 0.34f), Offset(w * 0.95f, h * 0.34f), strokeWidth = w * 0.1f)
            drawLine(color, Offset(w * 0.45f, h * 0.34f), Offset(w * 0.45f, h), strokeWidth = w * 0.1f)
        }
        DocFamily.IMPRESS -> {
            drawRoundRect(color, Offset(0f, h * 0.02f), Size(w, h * 0.62f), page, style = stroke)
            drawLine(color, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.5f, h * 0.95f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.28f, h * 0.95f), Offset(w * 0.72f, h * 0.95f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
        }
        DocFamily.DRAW -> {
            drawCircle(color, radius = w * 0.2f, center = Offset(w * 0.28f, h * 0.28f), style = stroke)
            val path = Path().apply {
                moveTo(w * 0.3f, h * 0.95f)
                lineTo(w * 0.98f, h * 0.3f)
                lineTo(w * 0.66f, h * 0.02f)
                lineTo(0f, h * 0.62f)
                close()
            }
            drawPath(path, color, style = stroke)
        }
        DocFamily.PDF -> {
            drawRoundRect(color, Offset(w * 0.12f, 0f), Size(w * 0.76f, h), page, style = stroke)
            drawRoundRect(
                color,
                Offset(w * 0.28f, h * 0.5f),
                Size(w * 0.44f, h * 0.3f),
                CornerRadius(w * 0.06f, w * 0.06f),
            )
        }
        DocFamily.OTHER -> {
            drawRoundRect(color, Offset(w * 0.12f, 0f), Size(w * 0.76f, h), page, style = stroke)
            drawLine(color, Offset(w * 0.32f, h * 0.5f), Offset(w * 0.68f, h * 0.5f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawFolderMark(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(w * 0.02f, h * 0.85f)
        lineTo(w * 0.02f, h * 0.22f)
        lineTo(w * 0.4f, h * 0.22f)
        lineTo(w * 0.5f, h * 0.36f)
        lineTo(w * 0.98f, h * 0.36f)
        lineTo(w * 0.98f, h * 0.85f)
        close()
    }
    drawPath(path, color, style = stroke)
}

/** Folder outline in the current content colour, for navigation items. */
@Composable
fun FolderIcon(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val tint = LocalContentColor.current
    Canvas(modifier.size(size)) { drawFolderMark(tint) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiyuTopBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun rememberThumbnail(uri: String, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, sizePx) {
        value = Thumbnails.load(context, uri, sizePx)?.asImageBitmap()
    }
    return bitmap
}

/** Thumbnail if the provider has one, family glyph otherwise. */
@Composable
private fun FileVisual(entry: FileEntry, size: Dp = 44.dp) {
    val thumb = rememberThumbnail(entry.uri, (size.value * 3).toInt().coerceAtLeast(96))
    if (thumb != null) {
        Image(
            bitmap = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)),
        )
    } else {
        FamilyGlyph(entry.family, size = size)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    entry: FileEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null,
    selected: Boolean = false,
    trailingText: String? = null,
    highlight: String? = null,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileVisual(entry)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (highlight.isNullOrBlank()) {
                    AnnotatedString(entry.name)
                } else {
                    highlighted(entry.name, highlight, MaterialTheme.typography.bodyLarge)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = trailingText ?: buildList {
                    add(entry.family.label)
                    if (entry.size > 0) add(formatBytes(entry.size))
                    val time = formatRelativeTime(entry.lastOpened.takeIf { it > 0 } ?: entry.modified)
                    if (time.isNotEmpty()) add(time)
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
            }
        }
    }
}

@Composable
fun DirRow(dir: DirEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderGlyph()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = dir.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (dir.fileCount == 0) "Empty" else "${dir.fileCount} file${if (dir.fileCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (primaryLabel != null && onPrimary != null) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPrimary) { Text(primaryLabel) }
                    if (secondaryLabel != null && onSecondary != null) {
                        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                    }
                }
            }
        }
    }
}

/** Renders [text] with the matched [query] span emphasised (search results). */
@Composable
fun highlighted(text: String, query: String, style: androidx.compose.ui.text.TextStyle): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return AnnotatedString(text)
    val index = text.indexOf(trimmed, ignoreCase = true)
    if (index < 0) return AnnotatedString(text)
    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    return buildAnnotatedString {
        append(text.substring(0, index))
        withStyle(highlight) { append(text.substring(index, index + trimmed.length)) }
        append(text.substring(index + trimmed.length))
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
