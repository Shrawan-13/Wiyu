# Wiyu UI Plan

Status: proposal · Target: first public build
Scope: the app-facing UI, file discovery, search, saving and system
integration. The document engine (LibreOfficeKit, tiling, editing) is **not**
in scope and is treated as a black box.

---

## 1. Goals

1. A shell that feels like a modern Android document app: instant, calm,
   unmistakably Material 3, and pleasant with one hand.
2. Files are *found*, not hunted: the app proactively surfaces documents on the
   device, with honest permission UX.
3. Search that answers in under a frame at typical corpus sizes.
4. Saving is boring: one tap, format preserved, recoverable, never destroys data.
5. Wiyu is the obvious choice when opening a supported document anywhere in
   Android — appearing in "Open with", the share sheet, and file managers.

Non-goals for this cycle: replacing the editor UI, cloud sync, collaboration,
desktop-class window management, and a full-blown file manager (no move/copy
across volumes, no archives).

## 2. What exists today (constraints we must respect)

| Fact | Where | Consequence |
| --- | --- | --- |
| Recents are a `;`-delimited string in SharedPreferences | `ui/LibreOfficeUIActivity.java:216` | No metadata, no timestamps, no robust pagination. Replaced by Room. |
| Viewer copies `content://` into a cache temp file, then loads by path | `LibreOfficeMainActivity.java:334` | Save-back is a copy-out step; engine cannot write `content://` directly. |
| Save = `.uno:Save` to the temp file, then copy back to the origin URI | `LibreOfficeMainActivity.java:~356` | Autosave/conflict handling must live in the copy-back layer. |
| "Save As" always writes ODF, ignoring the picked format | `LibreOfficeMainActivity.saveDocumentAs(Uri)` | Format-preserving save is new work (engine already supports `saveAs(path, format, opts)`). |
| Supported types are duplicated in 3 places (manifest, `FileUtilities.mExtnMap`, `LibreOfficeUIActivity.SUPPORTED_MIME_TYPES`) | e.g. comments say "keep in sync" | Single registry + CI check. |
| No `application/pdf`, no `text/plain` intent filter | `AndroidManifest.xml` | Wiyu cannot be chosen to open PDFs or `.txt`. Gap to close. |
| `ThumbnailCreator` renders *page* thumbnails via LOKit | `ThumbnailCreator.java` | File thumbnails need a new pipeline (SAF/MediaStore first, LOKit fallback). |
| Java-only, Views, `android.builtInKotlin=false` | `gradle.properties` | New UI needs Kotlin + Compose enabled (build change, CI-validated). |
| `minSdk 21`, `target/compileSdk 36`, AGP 9.4.1, Gradle 9.6 | build files | Compose and M3 are fine; dynamic color requires API 31+, predictive back 33+. |

## 3. Decisions

| Area | Decision | Why |
| --- | --- | --- |
| UI toolkit | **Jetpack Compose + Material 3** for the whole shell | Motion (spring specs, shared transitions), adaptive layouts, M3 components, far less custom view code. |
| Editor | Keep `LibreOfficeMainActivity` (Views + GL `SurfaceView`) untouched, launched by Intent | It is a hard-won, high-performance render surface; rewriting it buys nothing. |
| Navigation | `navigation-compose`, three top-level destinations + editor | Small graph, predictable back stack, predictive back support. |
| Data | Room (index + recents), DataStore (settings), WorkManager (indexing, thumbnails) | Standard, testable, no server. |
| Lists | Paging 3 + LazyColumn with stable keys and `contentType` | 10k-file folders without jank. |
| Images | Coil with thumbnail cache (LRU, 100 MB) | Correct memory behaviour, cancellable requests. |
| DI | Manual `AppGraph` (constructor injection via ViewModel factories) | Avoids KSP/Hilt build cost while the app is one module; revisit at module split. |
| Modules | Single `:app` + future `:core`/`:feature:*`; package layout maps 1:1 to those | Don't pay modularization costs before the UI exists. |

### Build changes required (phase 0)

- Enable Kotlin (`kotlin-android` + Compose compiler plugin, or AGP 9 built-in Kotlin).
- Add: Compose BOM, `material3`, `material3-adaptive`, `activity-compose`,
  `navigation-compose`, `lifecycle-viewmodel-compose`, `room-ktx` + KSP,
  `paging-compose`, `work-runtime-ktx`, `datastore-preferences`, `coil-compose`,
  `androidx.documentfile`.
- Add `androidx.profileinstaller` and a baseline profile in phase 6.
- CI unchanged otherwise: debug stays arm64-only, engine bundle untouched.

---

## 4. Information architecture

```
Launcher ──► Home ──────────┬──► Editor (Intent)  ──► [back] Home
              │             ├──► File details (bottom sheet)
              │             └──► Permissions / onboarding (modal)
              ├──► Browse ──┴──► Editor / details / multi-select actions
              ├──► Search ──────► Editor / details
              └──► Settings ────► About, licenses, theme, indexing
```

Top-level destinations: **Home**, **Browse**, **Search**. Settings is reached
from the Home top app bar (avatar-less, single account-free app) and is a normal
destination. The editor is a full-screen, immersive surface outside the shell
nav graph.

### Adaptive layout

| Window width | Navigation | Content |
| --- | --- | --- |
| < 600dp (compact) | `NavigationBar` (3 items), FAB | Single pane, bottom sheets |
| 600–839dp (medium) | `NavigationRail` | List, sheets become side panels |
| ≥ 840dp (expanded) | Permanent `NavigationDrawer` | List–detail: file list + preview/metadata pane |

Predictive back: system back previews Home when leaving Editor; back from a
top-level destination never traps the user.

---

## 5. Design system

### 5.1 Color

- **Dynamic color** (Material You) on API 31+, from the user's wallpaper.
- **Fallback seed**: a Wiyu brand seed (cool blue) run through
  `material-color-utilities` to produce full light/dark/contrast schemes — no
  hand-picked hex for surfaces.
- **File-type accents** are *derived*, not hardcoded: each family gets a seed
  hue, and container/content pairs are generated per light/dark scheme so
  contrast stays valid. Used sparingly (icon container, category card tint,
  selected filter chip) — never as large fills.

| Family | Seed hue | Used for |
| --- | --- | --- |
| Writer (text) | blue | icon container, category tile |
| Calc (sheets) | green | icon container, category tile |
| Impress (slides) | amber/orange | icon container, category tile |
| Draw (vector) | violet | icon container, category tile |
| PDF | red | icon container, read-only badge |
| Other/plain | neutral | low-emphasis tiles |

### 5.2 Type

M3 type scale only: `displaySmall` for the Home greeting, `headlineSmall` for
section titles, `titleMedium` for file names, `bodyMedium` for metadata,
`labelLarge` for actions. No custom fonts in phase 1; Inter/Roboto system stack
keeps the APK lean and the script coverage broad (the app ships ~50 locales).

### 5.3 Shape & elevation

M3 shape scale: `extraSmall` 4dp (chips), `small` 8dp (list rows), `medium`
12dp (cards), `large` 16dp (sheets, FAB menu), `extraLarge` 28dp (hero cards).
Prefer **tonal elevation** over shadows; shadows only for the FAB and modal
sheets.

### 5.4 Motion

| Interaction | Spec |
| --- | --- |
| Top-level destination change | Shared axis X, `emphasizedDecelerate` 300 ms |
| File → details | Container transform from row/card, 350 ms |
| Open → Editor | Fade-through (shell fades out, editor fades in), 220 ms, no shared element (GL surface) |
| FAB menu | Spring (`dampingRatio 0.8`, `stiffness 380`), staggered 30 ms |
| List items entering | Fade + 8dp rise, 180 ms, stagger capped at 4 items |
| Saved confirmation | Snackbar slide-in, tonal surface, action "Show file" |
| Scroll | Top app bar collapses via `TopAppBarScrollBehavior`, no bounce |

Respect `Settings.Global.ANIMATOR_DURATION_SCALE = 0` (reduce motion) by
substituting fades for transforms.

### 5.5 Iconography

Material Symbols Rounded, 24dp, optical size aligned to type scale. File icons
are a composed glyph: family symbol inside a tonal container, plus a badge for
template (`T`) and macro-enabled (`!`) documents.

---

## 6. Screens

### 6.1 Onboarding / permissions

Purpose: get the user to a populated Home in under 30 seconds, without lying
about what access is needed.

- Three-step pager (`HorizontalPager`, M3 indicators): *Why Wiyu wants to look*
  → *Add folders* → *Done*.
- "Add folders" launches `ACTION_OPEN_DOCUMENT_TREE`; each grant is persisted
  (`takePersistableUriPermission`) and shown as a chip with a remove action.
- On API ≤ 32, offer "Scan Downloads" behind `READ_EXTERNAL_STORAGE`.
- Optional "Full device access" (`MANAGE_EXTERNAL_STORAGE`) is **off by default**
  and explained as a power feature; see §8 for the policy caveat.
- Skip is always available; Home then shows an empty state with the same CTA.

### 6.2 Home

- **Large top app bar** ("Wiyu") with `TopAppBarScrollBehavior`; search icon and
  settings overflow.
- **Continue** — horizontally paged carousel of the 3 most recent documents as
  `extraLarge` cards with thumbnail, name, family chip, "12 min ago".
- **By type** — 4 category tiles (2×2 on compact, row on medium) with tinted
  containers, document counts, tap → Browse filtered by family.
- **Recent** — date-grouped list (Today / Yesterday / Last 7 days / Earlier),
  `ListItem` with thumbnail, name, path breadcrumb, relative time, overflow menu.
- **Empty state** — illustration-free, M3 tonal card: "No documents yet" + two
  actions (Add a folder, Open a file).
- **FAB** — "New" expands into an M3 FAB menu: Text document, Spreadsheet,
  Presentation, Drawing.
- **Indeterminate progress** strip while the indexer's first run is active.

Motion: carousel parallax on scroll, tiles scale 0.98 on press, list items fade
in on first composition only.

### 6.3 Browse

- Location selector as a leading `AssistChip` row: Internal storage, SD card,
  Downloads, each granted SAF folder, Favorites.
- Breadcrumb trail with overflow collapse for deep paths.
- Content: list (default) or grid toggle; `LazyColumn`/`LazyVerticalGrid` with
  Paging; sticky folder section then files.
- Sort/filter in a `ModalBottomSheet`: name, modified, size, type; folders
  first toggle.
- **Multi-select**: long-press enters contextual mode; the top app bar becomes a
  contextual bar ("3 selected") with Share, Delete, Move to folder (SAF only),
  Select all. Back exits selection, not the screen.
- Row affordances: thumbnail, name (2-line ellipsis), type chip, size + date,
  overflow (Open, Open with…, Share, Rename, Details, Delete).

### 6.4 Search

- Docked search bar (M3) at the top; focus expands into a full-screen search
  surface with the field pinned.
- Before typing: recent queries (chips) and "recently opened" suggestions.
- While typing: debounce 120 ms, `collectLatest` cancels in-flight queries,
  results stream in with the matched span highlighted.
- Filter row: type chips (All, Text, Sheets, Slides, Drawings, PDFs), date,
  location.
- Zero state distinguishes "no match" from "nothing indexed yet — add folders".
- Result rows behave like Browse rows; enter opens the editor.

### 6.5 File details (bottom sheet)

`ModalBottomSheet` with drag handle: large thumbnail, name (editable inline for
rename), path, size, modified, format family + template/macro badges, and a
2×2 action grid (Open, Share, Save a copy, Delete). "Open with another app"
uses `Intent.createChooser` with `FLAG_GRANT_READ_URI_PERMISSION`.

### 6.6 Settings

M3 preference groups in a `LazyColumn` (`ListItem` + `Switch`/`Slider`):

- **Appearance** — theme mode (system/light/dark), dynamic color toggle (31+),
  contrast level.
- **Documents** — default save location, default format for new documents,
  autosave interval, keep recovery snapshots (N days).
- **Discovery** — granted folders, exclude folders, full-device access, "Rebuild
  index" with progress.
- **About** — version, engine build, licenses, notices (existing `R.raw`
  strings and About dialog content migrate here).

Existing preference keys used by the editor (`ENABLE_EXPERIMENTAL_PREFS_KEY`,
etc.) must be preserved so the editor keeps working.

### 6.7 Editor handoff

- Launch via an explicit `EditorRequest` (uri, mime, mode, read-only, source).
- Optimistic transition: shell shows a tonal scrim for ≤ 220 ms, then the editor
  fades in. No spinner unless load exceeds 500 ms.
- Error surfaces return to the shell with the reason ("This file is
  password-protected", "Permission expired — pick the file again").
- Read-only files show a persistent banner in the editor (existing behaviour)
  plus a "Save a copy" affordance in the shell.

---

## 7. File type distribution

One canonical registry replaces the three duplicated lists:

```kotlin
object FileTypes {
    val all: List<DocType>          // 39 MIME types today + pdf, txt
    fun byMime(mime: String): DocType?
    fun byExtension(ext: String): DocType?
    fun byUri(resolver, uri): DocType   // MIME first, display-name extension second
}
```

`DocType` fields: `mime[]`, `extensions[]`, `family` (writer/calc/impress/draw/
pdf/other), `labelRes`, `icon`, `accentSeed`, `canView`, `canEdit`,
`isTemplate`, `isMacroEnabled`, `saveFormat` (LOKit filter name).

### 7.1 Coverage matrix

| Family | Formats (view + edit) | View-only | Notes |
| --- | --- | --- | --- |
| Writer | ODT, OTT, FODT, DOC, DOT, DOCX, DOTX, RTF, TXT, MD, ABW, WPS, MS Works | — | `.txt` and `text/plain` are **missing today**; add. |
| Calc | ODS, OTS, FODS, XLS, XLT, XLSX, XLTX, CSV, TSV | — | CSV needs `.csv` suffix staging (already handled). |
| Impress | ODP, OTP, FODP, PPT, POT, PPTX, POTX, PPS, PPSX, KEY | — | Keynote import is best-effort. |
| Draw | ODG, OTG, FODG, VSD, VSDX, VSTX, PUB, PMD, SVG, EMF, WMF, SVM | — | Vector formats open in Draw. |
| PDF | — | PDF | Import is supported by the engine; expose as viewer-first, with an explicit "Edit as drawing" power action. **Not registered today.** |
| Other | — | — | `application/octet-stream` fallback only for `file://` + extension patterns. |

### 7.2 How types are distributed in the UI

- **Home** — four family tiles with counts; "Other" is not a tile but appears in
  Browse/Search filters.
- **Browse** — icon + type chip per row; filter sheet groups by family.
- **Search** — filter chips per family, plus Templates and Macro-enabled.
- **New document** — FAB menu offers exactly the four families; default format
  is ODF unless the user changes it.
- **Empty states** — family-specific copy when a filter yields nothing.

### 7.3 Keeping the manifest honest

Add a unit test that parses the **merged** manifest and asserts:

1. every `FileTypes` MIME appears in the VIEW filter;
2. every VIEW MIME resolves to a `DocType`;
3. no filter references a MIME the engine cannot open;
4. `application/pdf` and `text/plain` are present.

This makes the "keep in sync" comment obsolete and prevents regressions in CI.

---

## 8. Discovery and permissions

Android's storage model does not allow silent, whole-device document indexing on
modern versions. The plan is layered and explicit.

### 8.1 Permission matrix

| API level | Mechanism | Permission | Coverage |
| --- | --- | --- | --- |
| 21–28 | `MediaStore.Files` + `File` walk | `READ_EXTERNAL_STORAGE` (runtime) | Near-complete |
| 21+ | SAF tree (`ACTION_OPEN_DOCUMENT_TREE`) | none (user-granted, persisted) | Whatever the user grants |
| 29–32 | `MediaStore.Downloads` / `.Files` | `READ_EXTERNAL_STORAGE` | Downloads + shared storage |
| 33+ | SAF tree | none | User-granted folders only |
| 30+ | `MANAGE_EXTERNAL_STORAGE` | special app access | Whole primary volume; **Play policy restricted** |

**Default posture**: SAF-first. Ask for one "Documents" folder grant during
onboarding; everything else is additive.

**Play Store caveat**: `MANAGE_EXTERNAL_STORAGE` is only approved for file
managers, backup tools and similar. A document editor is usually rejected.
Therefore: keep it behind an explicit, non-blocking setting, and ship builds
without it on Play. Sideloaded/F-Droid builds may enable it.

### 8.2 Indexing pipeline

```
Trigger (first run, folder grant, onResume, 6-hourly WorkManager)
  └─► Crawler (SAF DocumentsContract / MediaStore cursor)
        ├─ batch 200 entries, checkpointed in Room
        ├─ filter by FileTypes (MIME, then extension)
        └─ skip excluded folders, honour MAX_DEPTH
  └─► Upsert into `files` (uri, name, path, mime, family, size, modified, source)
  └─► FTS row (name, path tokens)
  └─► Thumbnail worker (bounded concurrency = 2)
```

Rules:

- Never block UI; all crawling on `Dispatchers.IO` in a foreground-triggered
  WorkManager job with progress (`setProgress`) surfaced as a thin bar.
- Incremental: skip entries whose `modified` is unchanged; delete rows whose
  document no longer resolves (`SecurityException` → mark stale, then prune).
- Permission revocation is detected lazily: on `SecurityException`, mark the
  grant dead and prompt re-grant instead of crashing.
- Thumbnails: SAF/MediaStore thumbnail first; LOKit page-1 render only for files
  the user opened, cached in `cacheDir/thumbs`, LRU 100 MB.

### 8.3 What the user sees

- First run: "Wiyu can show the documents in folders you choose." with one
  primary action.
- A persistent "Indexing 1 240 files…" chip on Home (dismissible, resumes on
  next run).
- Never more than one permission dialog per app session.

---

## 9. Search

### 9.1 Data model

```sql
CREATE VIRTUAL TABLE files_fts USING fts4(
    name, path, keywords, tokenize=unicode61
);
-- rowid == files.id
```

- Query: `name:prefix*` plus optional family/date filters applied in SQL.
- Ranking: `matchStrength * 1.0 + recencyBoost(0.3) + openFrequencyBoost(0.2)`,
  computed in SQL so the cursor comes back ordered.
- Debounce 120 ms; cancel previous query; return `LIMIT 50` with keyset
  pagination for "load more".

### 9.2 Performance targets

| Corpus | Target |
| --- | --- |
| 10 000 files | first result < 60 ms after debounce |
| 50 000 files | < 120 ms |
| Keystroke → frame | no main-thread SQL; no work > 4 ms |

Search over document *content* is explicitly deferred (needs full-text
extraction through the engine); the UI reserves a "Inside documents" toggle for
a later release.

---

## 10. Saving

### 10.1 Session model

```kotlin
data class DocumentSession(
    val sourceUri: Uri,        // where it came from (may be read-only)
    val workingFile: File,     // cache copy the engine edits
    val mime: String,
    val writable: Boolean,
    val lastRemoteModified: Long?,
)
```

### 10.2 Behaviours

| Action | Behaviour |
| --- | --- |
| Save | Engine `.uno:Save` → copy working file back via `openOutputStream(uri, "wt")`. Verify byte count; on failure keep the document open and offer "Save a copy". |
| Save As | `ACTION_CREATE_DOCUMENT` with the **current** format family preselected (fixes the ODF-only bug); format conversion is an explicit choice with a loss-warning dialog. |
| Save a copy | Always available, even for read-only sources. |
| Autosave | 30 s after the last engine modification + on `onStop`; debounced; never while a save is in flight. |
| Recovery | Working file snapshot every save and every 5 min while dirty; crash/restart offers "Recover document". |
| Conflict | Compare remote `COLUMN_LAST_MODIFIED` before write-back; if changed → dialog: Overwrite / Save copy / Reload. |
| Read-only | No silent write-back; banner in editor, "Save a copy" in shell. |
| Export | PDF export preserved (existing menu path), surfaced in the shell's share flow too. |

### 10.3 UX

- Primary action in the editor toolbar stays "Save"; success shows a snackbar
  with **Show file** (reveals it in Browse), not a toast.
- Failures are actionable dialogs, never toasts: reason + retry + alternative.
- A single "unsaved changes" guard on back/close, with Save / Discard / Cancel.

---

## 11. System integration ("Open with Wiyu")

### 11.1 Intent filters (generated/verified from `FileTypes`)

| Action | Categories | Data | Purpose |
| --- | --- | --- | --- |
| `VIEW` | DEFAULT | 39+ MIMEs, `content`, `file` | "Open with", file managers, mail attachments |
| `EDIT` | DEFAULT | same | Editors offering "edit with" |
| `SEND` | DEFAULT | same MIMEs + `octet-stream` | Share sheet, single file |
| `SEND_MULTIPLE` | DEFAULT | same | Share sheet, multi-select |
| `VIEW` (mime `application/pdf`) | DEFAULT | `content`, `file` | PDFs (**new**) |
| `VIEW` (mime `text/plain`) | DEFAULT | `content`, `file` | `.txt` (**new**) |

Implementation notes:

- `android:exported="true"` (already), no `pathPattern` dependence for `content`
  URIs — providers do not reliably expose extensions.
- `application/octet-stream` only for `scheme="file"` with extension patterns,
  to avoid claiming every unknown binary in the system.
- Handle `ClipData` with multiple items: open the first, queue the rest in an
  "Incoming" tray on Home (one snackbar, no dialogs).
- Persist URI permissions on entry (`takePersistableUriPermission` when the
  grant is persistable) so recents keep working after a reboot.
- `ACTION_VIEW` arriving while the app is already open reuses the task and
  replaces the editor instance (single-top per document).

### 11.2 Verification

A CI unit test asserts registry ↔ merged-manifest parity (§7.3). A manual
device matrix covers: Gmail attachment, Drive, Files by Google, Solid Explorer,
Telegram, Slack, downloads notification, and "Open with" from a `file://`
source.

---

## 12. Performance budget

| Metric | Target | How |
| --- | --- | --- |
| Cold start (TTID) | < 1.2 s on Pixel 6a-class | Shell only constructs Compose; LOKit init stays behind the editor |
| Warm start | < 400 ms | Saved state, no index scan on launch |
| First frame of Home | < 100 ms with data | Preloaded recents from Room, no `NOTIFY` storms |
| Janky frames | < 1% @120 Hz, < 3% @60 Hz | Macrobenchmark `FrameTimingMetric` |
| Search latency | < 120 ms @50k files | FTS + off-main-thread |
| Folder scroll | 0 dropped frames @10k items | Paging 3, stable keys, fixed row heights |
| Shell APK growth | < 2.5 MB | R8, no unused Compose artifacts, vector icons only |
| Thumbnail memory | < 16 MB | Coil sized requests + LRU disk cache |

Guardrails: baseline profile shipped in phase 6, `StrictMode` in debug, Compose
strong skipping (Kotlin 2.x default), `Immutable`/`Stable` annotations on UI
models, no `AndroidView` inside lazy lists.

---

## 13. Accessibility, i18n, RTL

- 48×48dp minimum targets; content descriptions on every icon-only button.
- Contrast AA by construction (M3 roles), verified with a contrast test on the
  generated schemes.
- TalkBack: file rows announce name, family, size, date, and selection state as
  one node; bottom sheets trap focus and restore it on dismiss.
- Dynamic type up to 200%: no fixed heights for text rows.
- Reduce motion honoured (§5.4).
- RTL: start/end padding everywhere; icons that imply direction are mirrored.
- New strings in English first; reuse existing `values-*` translations where
  copy is unchanged; never machine-translate.

---

## 14. Target code layout

```
app/src/main/java/org/libreoffice/          (existing engine + editor, untouched)
app/src/main/kotlin/app/wiyu/
  WiyuApp.kt                  Application wiring, AppGraph
  MainActivity.kt             single Compose host (launcher)
  navigation/                 NavHost, destinations, transitions
  designsystem/
    theme/                    Color, Type, Shape, Motion, WiyuTheme
    component/                FileRow, FamilyTile, SectionHeader, EmptyState, …
  feature/
    home/                     HomeScreen, HomeViewModel, components
    browse/                   BrowseScreen, BrowseViewModel, selection
    search/                   SearchScreen, SearchViewModel
    details/                  FileDetailsSheet
    settings/                 SettingsScreen
    onboarding/               PermissionPager
  data/
    file/                     FileTypes, DocumentHandle, DocumentRepository
    index/                    Room entities/DAO/FTS, Crawler, IndexWorker
    recents/                  RecentStore (migrates legacy prefs once)
    prefs/                    DataStore settings
    save/                     SaveCoordinator, conflict detection, recovery
  editorbridge/               EditorRequest, launcher, result contract
  util/                       formatters, thumbnail cache
```

Legacy pieces removed when their replacement ships: `LibreOfficeUIActivity`,
`PageView`, `RecentFilesAdapter`, the `RECENT_DOCUMENTS_KEY` preference handling.
`SettingsActivity` is replaced but its preference **keys** are preserved.

---

## 15. Phases

| Phase | Deliverable | Acceptance | CI |
| --- | --- | --- | --- |
| **0. Foundation** | Kotlin/Compose enabled, `WiyuTheme`, nav skeleton, `FileTypes` registry + manifest parity test, Room schema v1 | App launches to a placeholder Home; parity test fails if a MIME is added on one side only | Green, APK unchanged size class |
| **1. Home** | Recents (Room, legacy prefs migrated), Continue carousel, family tiles, FAB menu, empty state | Opens legacy recents; new opens recorded from the editor's entry point; 60/120 Hz clean | Green + screenshot tests |
| **2. Browse + discovery** | SAF grants, crawler, incremental index, thumbnails, sort/filter, multi-select | 5 000-file folder scrolls without jank; revocation handled | Green |
| **3. Search** | FTS, debounce, filters, highlights, zero states | < 120 ms on 50k rows; no main-thread SQL | Green + perf test |
| **4. Saving** | Session model, format-preserving Save As, autosave, recovery, conflict dialog | Interrupted save never loses data; read-only source never written | Green |
| **5. Open with** | Filter fixes (PDF, text), SEND/SEND_MULTIPLE, multi-item intake, deep verification | Manual intent matrix passes on 4 devices/API levels | Green |
| **6. Polish** | Motion pass, adaptive layouts, a11y audit, baseline profile, settings | Jank budget met; TalkBack pass; tablet list–detail | Green + macrobenchmark |
| **7. Cleanup** | Delete legacy browser/recents/settings, docs update | No dead code paths; README screenshots | Green |

Each phase ships behind the existing launcher, so `main` is always installable.

---

## 16. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Play policy rejection for `MANAGE_EXTERNAL_STORAGE` | Distribution | Off by default, Play build without it, SAF primary path |
| Indexing large volumes on low-end devices | Battery/heat | Batched, checkpoints, cancellable, 6-hourly minimum |
| Engine format drift (engine 26.2.6.3 vs master Java) | Crashes | Pin engine bundle version; smoke test per bundle bump |
| Compose + existing Java/GL editor integration | Startup/jank | Editor stays a separate Activity; no Compose in the GL path |
| Legacy preference keys colliding | Editor settings lost | Keep keys; migration test |
| URI permission expiry (SAF grants revoked, app data restored) | Broken recents | Detect on open, mark stale, re-grant flow |

## 17. Open questions

1. Should PDF be view-only or expose "Edit as drawing" in the first release?
2. Default new-document format: ODF only, or remember the last used format?
3. Do we want a Favorites concept in v1, or is "Recent" enough?
4. Tablet list–detail: preview with a real page render (costly) or metadata only?
5. Should the shell offer "import from another app" batch flow in v1?

---

## 18. Implementation status

What actually shipped, and where it differs from the plan above. The differences
are deliberate: each one trades a dependency for something the app can do itself
in less code.

### Shipped

| Area | Implementation |
| --- | --- |
| Shell | Compose `ComponentActivity` (`app.wiyu.MainActivity`), M3 theme with dynamic colour on Android 12+, hand-rolled tab state |
| Navigation | Home / Browse / Search bottom bar, Settings as a pushed screen, `BackHandler` for path and sheet dismissal |
| Index | `SQLiteOpenHelper` (`WiyuDb`), FTS4 shadow table for names and paths, `dirs` table rebuilt from files after each scan |
| Discovery | SAF tree walk via `DocumentsContract`, filesystem walk with all-files access, `MediaStore` fallback |
| Search | Debounced (120 ms) FTS4 prefix query, family filters, match highlighting, result count + elapsed ms |
| Browse | Indexed virtual tree, root chips, family filter chip, empty states |
| Details | Modal bottom sheet: open, save a copy, share, delete, open with another app, metadata |
| Saving | Format-preserving `saveDocumentAs` driven by `FileTypes`, `file://` write-back, `ACTION_CREATE_DOCUMENT` copies |
| Integration | `VIEW`/`EDIT`/`PICK`/`SEND`/`SEND_MULTIPLE`, `content` and `file` schemes, `ACTION_SEND` stream intake in the editor |
| Guard rails | `FileTypesManifestTest` (manifest ↔ registry parity), run in CI before the APK is uploaded |

### Deliberately different from the plan

| Planned | Landed | Why |
| --- | --- | --- |
| Room + KSP | `SQLiteOpenHelper` + FTS4 | No annotation processor in the build; the schema is six queries deep, not worth codegen risk |
| Hilt | Manual `WiyuRepo.get(context)` singleton | One repository, no graph to inject |
| navigation-compose | Tab + path state in the shell | Three tabs and one push destination |
| Paging 3 | Chunked `LIMIT` queries | The list is a local index, not a remote feed |
| Coil | `ThumbnailCache` (`LruCache`, 16 MB) + `ContentResolver.loadThumbnail` | One thumbnail source, no extra dependency |
| WorkManager period scan | In-app coroutine scan on launch, on grant, and on demand | Avoids a battery-hungry background job; the index refreshes whenever the user is in the app |
| Autosave + recovery (phase 4) | Not implemented | The editor's own save path is untouched; interrupted saves are out of scope for the shell |
| Multi-select (phase 2) | Single-item details sheet | Every action is reachable per file; batch operations can follow once the index has selection state |
| Tablet list–detail (phase 6) | Single-pane, adaptive padding | The editor is a separate full-screen activity, so a preview pane would only show metadata |
| Baseline profile / macrobenchmark (phase 6) | Not added | Needs a device farm; the shell renders a flat list over a local cursor |

### Known follow-ups

1. Remove `org.libreoffice.ui.LibreOfficeUIActivity` (and its layouts) once the
   `NEW_*_STRING_KEY` constants move to `EditorBridge`.
2. Localise the new strings (currently English; the engine keeps its ~50 locales).
3. Device verification of the intent matrix and a TalkBack pass.
