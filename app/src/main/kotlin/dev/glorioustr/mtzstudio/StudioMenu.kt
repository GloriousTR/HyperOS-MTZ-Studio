package dev.glorioustr.mtzstudio

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.glorioustr.mtzstudio.composer.CompositionResult
import dev.glorioustr.mtzstudio.core.ComponentCategory
import dev.glorioustr.mtzstudio.library.LibraryTheme
import dev.glorioustr.mtzstudio.tester.RootThemeManagerUpdater
import dev.glorioustr.mtzstudio.tester.ThemeManagerInspector
import java.io.InputStream
import java.nio.file.Path

internal enum class StudioDestination(
    @StringRes val titleRes: Int,
    val category: ComponentCategory? = null,
) {
    HOME(R.string.dest_home),
    THEMES(R.string.dest_themes),
    WALLPAPERS(R.string.dest_wallpapers, ComponentCategory.WALLPAPER),
    RINGTONES(R.string.dest_ringtones, ComponentCategory.RINGTONE),
    FONTS(R.string.dest_fonts, ComponentCategory.FONT),
    LOCKSCREEN(R.string.dest_lockscreen, ComponentCategory.LOCKSCREEN),
    ICONS(R.string.dest_icons, ComponentCategory.ICONS),
    SYSTEM_UI(R.string.dest_system_ui, ComponentCategory.SYSTEM_UI),
    CONTACTS(R.string.dest_contacts, ComponentCategory.CONTACTS),
    MMS(R.string.dest_mms, ComponentCategory.MMS),
    SYSTEM_UI_PLUGIN(R.string.dest_system_ui_plugin, ComponentCategory.SYSTEM_UI_PLUGIN),
    LAUNCHER(R.string.dest_launcher, ComponentCategory.LAUNCHER),
    AOD(R.string.dest_aod, ComponentCategory.AOD),
    FRAMEWORK(R.string.dest_framework, ComponentCategory.FRAMEWORK),
    OTHER(R.string.dest_other, ComponentCategory.OTHER),
    PERSONALIZE(R.string.dest_personalize),
    APPEARANCE(R.string.dest_appearance),
    BACKUP(R.string.dest_backup),
    DIAGNOSTICS(R.string.dest_diagnostics),
    THEME_PROTECTION(R.string.dest_theme_protection),
    ABOUT(R.string.dest_about),
}

@Composable
internal fun HomeMenuScreen(
    importExpanded: Boolean,
    importing: Boolean,
    rootlessMode: Boolean = false,
    themeManagerInspector: ThemeManagerInspector,
    themeManagerUpdater: RootThemeManagerUpdater,
    openInput: (Uri) -> InputStream?,
    onToggleImport: () -> Unit,
    onAddMtz: () -> Unit,
    onNavigate: (StudioDestination) -> Unit,
    showThemeManagerVersionTool: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        MenuSpec(
            StudioDestination.THEMES,
            Icons.Filled.ColorLens,
            Color(0xFFFFB52E),
        ),
        MenuSpec(StudioDestination.PERSONALIZE, Icons.Filled.Tune, Color(0xFF8054E8)),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rootlessMode) {
            item {
                StudioCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MenuIconBox(Icons.Filled.Lock, Color(0xFF2E7D32))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.rootless_mode_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.rootless_mode_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        item {
            StudioCard(Modifier.fillMaxWidth()) {
                MenuHeader(
                    icon = Icons.Filled.Add,
                    color = Color(0xFF26A69A),
                    title = stringResource(R.string.mtz_import_title),
                    subtitle = stringResource(R.string.mtz_import_home_subtitle),
                    expanded = importExpanded,
                    onClick = onToggleImport,
                )
                AnimatedVisibility(importExpanded) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.mtz_import_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(enabled = !importing, onClick = onAddMtz) {
                            Text(if (importing) stringResource(R.string.mtz_import_btn_importing) else stringResource(R.string.mtz_import_btn_select))
                        }
                        if (showThemeManagerVersionTool) {
                            ThemeManagerCompatibilityCard(
                                inspector = themeManagerInspector,
                                updater = themeManagerUpdater,
                                openInput = openInput,
                            )
                        }
                    }
                }
            }
        }

        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column {
                    rows.forEachIndexed { index, row ->
                        MenuRow(row, onClick = { onNavigate(row.destination) })
                        if (index != rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
internal fun StudioOverlayMenu(
    showThemeProtection: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (StudioDestination) -> Unit,
) {
    val items = listOf(
        OverlayMenuItem(
            destination = StudioDestination.APPEARANCE,
            icon = Icons.Filled.Palette,
            color = Color(0xFF5C6BC0),
            descriptionRes = R.string.overlay_appearance_desc,
        ),
        OverlayMenuItem(
            destination = StudioDestination.BACKUP,
            icon = Icons.Filled.CloudUpload,
            color = Color(0xFF3F7FD9),
            descriptionRes = R.string.overlay_backup_desc,
        ),
        OverlayMenuItem(
            destination = StudioDestination.DIAGNOSTICS,
            icon = Icons.Filled.MonitorHeart,
            color = Color(0xFF607D8B),
            descriptionRes = R.string.overlay_diagnostics_desc,
        ),
        OverlayMenuItem(
            destination = StudioDestination.THEME_PROTECTION,
            icon = Icons.Filled.Security,
            color = Color(0xFF2E7D32),
            descriptionRes = R.string.overlay_theme_protection_desc,
        ),
        OverlayMenuItem(
            destination = StudioDestination.ABOUT,
            icon = Icons.Filled.Info,
            color = Color(0xFF7E57C2),
            descriptionRes = R.string.overlay_about_desc,
        ),
    ).filter { showThemeProtection || it.destination != StudioDestination.THEME_PROTECTION }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss)
                .padding(start = 12.dp, end = 24.dp, top = 48.dp, bottom = 36.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            StudioCard(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clickable(enabled = false) {},
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.menu_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.menu_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items.forEach { item ->
                        OverlayMenuCard(item = item, onClick = { onNavigate(item.destination) })
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.menu_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayMenuCard(item: OverlayMenuItem, onClick: () -> Unit) {
    StudioCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MenuIconBox(item.icon, item.color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(item.destination.titleRes),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(item.descriptionRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ThemesScreen(
    themes: List<LibraryTheme>,
    activeThemeId: String?,
    deviceImportStatus: String,
    deviceImportRunning: Boolean,
    onOpenDeviceThemePicker: () -> Unit,
    onShowAllDeviceThemes: () -> Unit,
    catalogError: String? = null,
    onRetryCatalog: () -> Unit = {},
    showDeviceImport: Boolean = true,
    nativeCatalogMode: Boolean = false,
    rootlessMode: Boolean = false,
    onApplyTheme: (LibraryTheme) -> Unit,
    onDeleteTheme: (LibraryTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteTheme by remember { mutableStateOf<LibraryTheme?>(null) }
    var detailsTheme by remember { mutableStateOf<LibraryTheme?>(null) }
    val galleryThemes = themes.filter(LibraryTheme::isThemeGalleryItem)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rootlessMode) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StudioCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.rootless_mode_desc),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (showDeviceImport) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DeviceImportCard(
                    title = stringResource(R.string.device_theme_import_title),
                    description = stringResource(R.string.device_theme_import_desc),
                    status = deviceImportStatus,
                    buttonText = stringResource(R.string.device_theme_import_button),
                    running = deviceImportRunning,
                    onImport = onOpenDeviceThemePicker,
                )
            }
        }
        if (nativeCatalogMode) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DeviceImportCard(
                    title = stringResource(R.string.catalog_full_library_title),
                    description = stringResource(R.string.catalog_full_library_desc),
                    status = deviceImportStatus,
                    buttonText = stringResource(R.string.catalog_full_library_button),
                    running = deviceImportRunning,
                    onImport = onShowAllDeviceThemes,
                )
            }
        }
        if (nativeCatalogMode && catalogError != null && !deviceImportRunning) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    EmptyState(stringResource(R.string.catalog_error_title), catalogError)
                    TextButton(onClick = onRetryCatalog) { Text(stringResource(R.string.catalog_retry)) }
                }
            }
        }
        if (galleryThemes.isEmpty() && (!nativeCatalogMode || (!deviceImportRunning && catalogError == null))) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    stringResource(if (!nativeCatalogMode) R.string.empty_themes_title else R.string.catalog_empty_title),
                    stringResource(if (!nativeCatalogMode) R.string.empty_themes_desc else R.string.catalog_empty_desc),
                )
            }
        }
        gridItems(galleryThemes, key = { it.id.value }) { theme ->
            ThemeGalleryCard(
                theme = theme,
                isActive = theme.id.value == activeThemeId,
                onOpenDetails = { detailsTheme = theme },
                onApplyTheme = onApplyTheme,
                onDeleteTheme = { pendingDeleteTheme = it },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(20.dp)) }
    }

    detailsTheme?.let { theme ->
        ThemeDetailsDialog(
            theme = theme,
            isActive = theme.id.value == activeThemeId,
            onDismiss = { detailsTheme = null },
            onApply = {
                detailsTheme = null
                onApplyTheme(theme)
            },
            onDelete = {
                detailsTheme = null
                pendingDeleteTheme = theme
            },
        )
    }

    pendingDeleteTheme?.let { themeToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTheme = null },
            title = { Text(stringResource(R.string.delete_theme_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_theme_dialog_desc,
                        themeToDelete.archive.metadata?.name ?: themeToDelete.displayName,
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTheme(themeToDelete)
                        pendingDeleteTheme = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTheme = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun CategoryScreen(
    destination: StudioDestination,
    themes: List<LibraryTheme>,
    selections: MutableMap<ComponentCategory, UiSelection>,
    customHomeWallpaperUri: String? = null,
    customLockWallpaperUri: String? = null,
    onPickHomeWallpaper: () -> Unit = {},
    onRemoveHomeWallpaper: () -> Unit = {},
    onPickLockWallpaper: () -> Unit = {},
    onRemoveLockWallpaper: () -> Unit = {},
    deviceImportStatus: String = "",
    deviceImportRunning: Boolean = false,
    onImportActiveFont: () -> Unit = {},
    showDeviceFontImport: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val category = requireNotNull(destination.category)
    val sources = themes.flatMap { theme ->
        theme.archive.components.filter { it.category == category }.map { theme to it }
    }
    val previewOnlyThemes = themes.filter {
        dev.glorioustr.mtzstudio.core.ThemeVisualPolicy.isPreviewOnly(it.archive.components, it.archive.entries, category)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.category_select_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (category == ComponentCategory.FONT && showDeviceFontImport) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DeviceImportCard(
                    title = stringResource(R.string.device_font_import_title),
                    description = stringResource(R.string.device_font_import_desc),
                    status = deviceImportStatus,
                    buttonText = stringResource(R.string.device_font_import_button),
                    running = deviceImportRunning,
                    onImport = onImportActiveFont,
                )
            }
        }

        // Custom Wallpaper Upload Card for Wallpaper Category
        if (category == ComponentCategory.WALLPAPER) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StudioCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.custom_home_wallpaper_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.custom_home_wallpaper_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (customHomeWallpaperUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp, 90.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                ) {
                                    UriImagePreview(
                                        uri = Uri.parse(customHomeWallpaperUri),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(R.string.custom_home_wallpaper_selected),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = onPickHomeWallpaper) {
                                            Text(stringResource(R.string.btn_pick_gallery_image))
                                        }
                                        OutlinedButton(onClick = onRemoveHomeWallpaper) {
                                            Text(stringResource(R.string.btn_remove_custom_wallpaper))
                                        }
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = onPickHomeWallpaper,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_pick_gallery_image))
                            }
                        }
                    }
                }
            }
        }

        // Custom Wallpaper Upload Card for Lockscreen Category
        if (category == ComponentCategory.LOCKSCREEN) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StudioCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.custom_lock_wallpaper_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.custom_lock_wallpaper_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (customLockWallpaperUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp, 90.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                ) {
                                    UriImagePreview(
                                        uri = Uri.parse(customLockWallpaperUri),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(R.string.custom_lock_wallpaper_selected),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = onPickLockWallpaper) {
                                            Text(stringResource(R.string.btn_pick_gallery_image))
                                        }
                                        OutlinedButton(onClick = onRemoveLockWallpaper) {
                                            Text(stringResource(R.string.btn_remove_custom_wallpaper))
                                        }
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = onPickLockWallpaper,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_pick_gallery_image))
                            }
                        }
                    }
                }
            }
        }

        gridItems(previewOnlyThemes, key = { "preview-only:${it.id.value}" }) { theme ->
            val selected = selections[category]
            val checked = selected?.themeId == theme.id && selected.useDefault
            StudioCard(Modifier.fillMaxWidth().clickable {
                if (checked) selections.remove(category)
                else selections[category] = UiSelection(theme.id, category, "", useDefault = true)
            }) {
                Column {
                    ThemePreview(theme = theme, category = category, modifier = Modifier.fillMaxWidth().aspectRatio(0.68f))
                    Text(theme.archive.metadata?.name ?: theme.displayName, modifier = Modifier.padding(10.dp),
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Checkbox(checked = checked, onCheckedChange = { enabled ->
                        if (enabled) selections[category] = UiSelection(theme.id, category, "", useDefault = true)
                        else if (checked) selections.remove(category)
                    })
                    Text(stringResource(R.string.component_use_default), modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (sources.isEmpty() && previewOnlyThemes.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    stringResource(R.string.empty_category_title),
                    stringResource(R.string.empty_category_desc),
                )
            }
        }
        gridItems(sources, key = { (theme, component) -> "${theme.id.value}:${component.rootPath}" }) { (theme, component) ->
            val selected = selections[category]
            val checked = selected?.themeId == theme.id && selected.rootPath == component.rootPath
            StudioCard(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (checked) selections.remove(category)
                    else selections[category] = UiSelection(theme.id, category, component.rootPath)
                },
            ) {
                Column {
                    Box {
                        ThemePreview(
                            theme = theme,
                            category = category,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
                        )
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { enabled ->
                                if (enabled) selections[category] = UiSelection(theme.id, category, component.rootPath)
                                else if (checked) selections.remove(category)
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            theme.archive.metadata?.name ?: theme.displayName,
                            fontWeight = FontWeight.Bold,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
internal fun PersonalizeScreen(
    themes: List<LibraryTheme>,
    selections: MutableMap<ComponentCategory, UiSelection>,
    compositionName: String,
    compositionMakerName: String,
    operationRunning: Boolean,
    lastResult: CompositionResult?,
    status: String,
    baseThemeId: String? = null,
    onSelectBaseTheme: (LibraryTheme?) -> Unit = {},
    customHomeWallpaperUri: String? = null,
    customLockWallpaperUri: String? = null,
    onPickHomeWallpaper: () -> Unit = {},
    onRemoveHomeWallpaper: () -> Unit = {},
    onPickLockWallpaper: () -> Unit = {},
    onRemoveLockWallpaper: () -> Unit = {},
    onCompositionNameChange: (String) -> Unit,
    onCompositionMakerNameChange: (String) -> Unit,
    onCompose: () -> Unit,
    onShare: (Path) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBaseThemeDialog by remember { mutableStateOf(false) }
    var quickPickCategory by remember { mutableStateOf<ComponentCategory?>(null) }
    val baseTheme = baseThemeId?.let { id -> themes.firstOrNull { it.id.value == id } }
    val baseMakerName = baseTheme?.archive?.metadata?.let { metadata ->
        metadata.designer?.takeIf(String::isNotBlank)
            ?: metadata.author?.takeIf(String::isNotBlank)
    }

    val availableCategories = dev.glorioustr.mtzstudio.core.ThemeVisualPolicy.personalizationCategories
    val visibleSelections = selections.values.filter { it.category.isPersonalizationOption() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Base Theme (Ana Tema) Selection Card
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudioCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.base_theme_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.base_theme_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (baseTheme != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp, 76.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                ) {
                                    ThemePreview(
                                        theme = baseTheme,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        baseTheme.archive.metadata?.name ?: baseTheme.displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { showBaseThemeDialog = true }) {
                                            Text(stringResource(R.string.base_theme_change_btn))
                                        }
                                        OutlinedButton(onClick = { onSelectBaseTheme(null) }) {
                                            Text(stringResource(R.string.base_theme_clear_btn))
                                        }
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = { showBaseThemeDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.base_theme_select_btn))
                            }
                        }
                    }
                }
            }
        }

        items(availableCategories.chunked(2)) { rowCategories ->
            Row(Modifier.fillMaxWidth()) {
                rowCategories.forEach { category ->
                    val selected = selections[category]
                    val selectedTheme = selected?.let { sel ->
                        themes.firstOrNull { theme ->
                            theme.id == sel.themeId && if (sel.useDefault) {
                                dev.glorioustr.mtzstudio.core.ThemeVisualPolicy.isPreviewOnly(theme.archive.components, theme.archive.entries, category)
                            } else theme.archive.components.any { it.category == category && it.rootPath == sel.rootPath }
                        }
                    }
                    PersonalizeTile(
                        icon = categoryIcon(category),
                        title = stringResource(categoryLabelRes(category)),
                        selectedTheme = selectedTheme,
                        sourceName = if (selected?.useDefault == true) selectedTheme?.let {
                            dev.glorioustr.mtzstudio.core.ThemeVisualPolicy.defaultSourceName(it.archive, category)
                        } else null,
                        category = category,
                        onClick = { quickPickCategory = category },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Custom Wallpapers in 2 Columns matching the grid above
        item {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.custom_wallpaper_section_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    CustomWallpaperTile(
                        icon = Icons.Default.Image,
                        title = stringResource(R.string.custom_home_wallpaper_title),
                        uriString = customHomeWallpaperUri,
                        baseTheme = baseTheme,
                        lockScreen = false,
                        onPick = onPickHomeWallpaper,
                        onRemove = onRemoveHomeWallpaper,
                        modifier = Modifier.weight(1f),
                    )
                    CustomWallpaperTile(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.custom_lock_wallpaper_title),
                        uriString = customLockWallpaperUri,
                        baseTheme = baseTheme,
                        lockScreen = true,
                        onPick = onPickLockWallpaper,
                        onRemove = onRemoveLockWallpaper,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Save Theme Card directly below
        item {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                StudioCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.save_theme_as),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedTextField(
                            value = compositionName,
                            onValueChange = onCompositionNameChange,
                            label = { Text(stringResource(R.string.theme_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = compositionMakerName,
                            onValueChange = onCompositionMakerNameChange,
                            label = { Text(stringResource(R.string.theme_maker_name_label)) },
                            placeholder = baseMakerName?.let { maker ->
                                { Text(maker) }
                            },
                            supportingText = {
                                Text(
                                    baseMakerName?.let { maker ->
                                        stringResource(R.string.theme_maker_inherited, maker)
                                    } ?: stringResource(R.string.theme_maker_inherited_unknown),
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (status.isNotBlank()) {
                            Text(status, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val canCompose = !operationRunning && compositionName.isNotBlank() && (baseTheme != null || visibleSelections.any { !it.useDefault } || customHomeWallpaperUri != null || customLockWallpaperUri != null)
                            Button(
                                enabled = canCompose,
                                onClick = onCompose,
                            ) { Text(stringResource(R.string.save_theme_as)) }
                            lastResult?.let { result ->
                                OutlinedButton(onClick = { onShare(result.output) }) {
                                    Text(stringResource(R.string.action_share))
                                }
                            }
                        }
                        lastResult?.let { result ->
                            Text(
                                "${result.output.fileName}\nSHA-256 ${result.outputSha256}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showBaseThemeDialog) {
        HorizontalThemePickerDialog(
            title = stringResource(R.string.base_theme_dialog_title),
            description = stringResource(R.string.base_theme_picker_desc),
            choices = themes.filter(LibraryTheme::isThemeGalleryItem).map { theme ->
                ThemeChoice(theme = theme)
            },
            selectedKey = baseTheme?.id?.value,
            onSelect = { choice ->
                onSelectBaseTheme(choice.theme)
                showBaseThemeDialog = false
            },
            onClear = if (baseTheme != null) {
                {
                    onSelectBaseTheme(null)
                    showBaseThemeDialog = false
                }
            } else null,
            onDismiss = { showBaseThemeDialog = false },
        )
    }

    quickPickCategory?.let { category ->
        val current = selections[category]
        val choices = themes.mapNotNull { theme ->
            val component = theme.archive.components.firstOrNull { it.category == category }
            val specificPreviews = dev.glorioustr.mtzstudio.core.ThemeVisualPolicy
                .categoryPreviewPaths(theme.archive.entries, category)
            if (component == null && specificPreviews.isEmpty()) return@mapNotNull null
            ThemeChoice(theme = theme, category = category, rootPath = component?.rootPath)
        }
        HorizontalThemePickerDialog(
            title = stringResource(R.string.category_quick_picker_title, stringResource(categoryLabelRes(category))),
            description = stringResource(R.string.category_quick_picker_desc),
            choices = choices,
            selectedKey = current?.let {
                if (it.useDefault) "${it.themeId.value}:default:${category.name}" else "${it.themeId.value}:${it.rootPath}"
            },
            onSelect = { choice ->
                selections[category] = UiSelection(choice.theme.id, category, choice.rootPath.orEmpty(), useDefault = choice.useDefault)
                quickPickCategory = null
            },
            onClear = if (current != null) {
                {
                    selections.remove(category)
                    quickPickCategory = null
                }
            } else null,
            onDismiss = { quickPickCategory = null },
        )
    }
}

private data class ThemeChoice(
    val theme: LibraryTheme,
    val category: ComponentCategory? = null,
    val rootPath: String? = null,
) {
    val key: String get() = rootPath?.let { "${theme.id.value}:$it" }
        ?: category?.let { "${theme.id.value}:default:${it.name}" } ?: theme.id.value
    val useDefault: Boolean get() = category != null && rootPath == null
    val displayName: String get() = category?.takeIf { useDefault }?.let {
        dev.glorioustr.mtzstudio.core.ThemeVisualPolicy.defaultSourceName(theme.archive, it)
    } ?: theme.archive.metadata?.name ?: theme.displayName
    val specificPreviews: List<String> get() = category?.let {
        dev.glorioustr.mtzstudio.core.ThemeVisualPolicy.categoryPreviewPaths(theme.archive.entries, it)
    }.orEmpty()
}

@Composable
private fun HorizontalThemePickerDialog(
    title: String,
    description: String,
    choices: List<ThemeChoice>,
    selectedKey: String?,
    onSelect: (ThemeChoice) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var inspecting by remember { mutableStateOf<ThemeChoice?>(null) }
    inspecting?.let { choice ->
        Dialog(onDismissRequest = { inspecting = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxWidth(0.96f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(choice.displayName,
                        modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                    if (choice.useDefault) Text(stringResource(R.string.component_use_default_desc),
                        modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(choice.specificPreviews, key = { it }) { path ->
                            ThemePreview(theme = choice.theme, category = choice.category, previewPath = path,
                                modifier = Modifier.width(260.dp).heightIn(max = 480.dp).aspectRatio(0.5f))
                        }
                    }
                    TextButton(onClick = { inspecting = null }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (choices.isEmpty()) {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        EmptyState(
                            stringResource(R.string.empty_category_title),
                            stringResource(R.string.empty_category_desc),
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(choices, key = ThemeChoice::key) { choice ->
                            val checked = choice.key == selectedKey
                            Surface(
                                modifier = Modifier.width(184.dp).clickable { onSelect(choice) },
                                shape = RoundedCornerShape(20.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = if (checked) 6.dp else 1.dp,
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(248.dp)
                                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                                    ) {
                                        ThemePreview(
                                            theme = choice.theme,
                                            category = choice.category,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { enabled -> if (enabled) onSelect(choice) },
                                            modifier = Modifier.align(Alignment.TopEnd),
                                        )
                                    }
                                    Text(
                                        choice.displayName,
                                        modifier = Modifier.padding(12.dp),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        minLines = 2,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (choice.useDefault) Text(
                                        stringResource(R.string.component_use_default),
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (choice.specificPreviews.isNotEmpty()) TextButton(
                                        onClick = { inspecting = choice },
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    ) { Text(stringResource(R.string.component_view_previews, choice.specificPreviews.size)) }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.weight(1f))
                    onClear?.let { clear ->
                        OutlinedButton(onClick = clear) { Text(stringResource(R.string.base_theme_clear_btn)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackupRestoreScreen(
    themeCount: Int,
    status: String,
    cloudAccount: CloudAccount,
    onSelectGoogleDrive: () -> Unit,
    onConnectCloud: (CloudAccount) -> Unit,
    onDisconnectCloud: () -> Unit,
    onBackupCloud: () -> Unit,
    onRestoreCloud: () -> Unit,
    onBackupLocal: () -> Unit,
    onRestoreLocal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showConnectDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_backup), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Backup, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_restore), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                )
            }
        }

        if (selectedTab == 0) {
            // ==================== YEDEKLEME ====================
            item {
                StudioCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (cloudAccount.isConnected) Icons.Filled.CloudDone else Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.section_cloud_storage),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    if (cloudAccount.isConnected) {
                                        stringResource(R.string.cloud_connected_as, cloudAccount.accountName, cloudAccount.provider.displayName)
                                    } else {
                                        stringResource(R.string.cloud_not_connected)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cloudAccount.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Text(
                            stringResource(R.string.cloud_backup_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        if (cloudAccount.isConnected) {
                            cloudAccount.lastBackupTime?.let { time ->
                                Text(
                                    stringResource(R.string.cloud_last_backup, time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = onBackupCloud,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_backup_to_cloud))
                            }
                            OutlinedButton(
                                onClick = onDisconnectCloud,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.btn_disconnect_cloud))
                            }
                        } else {
                            Button(
                                onClick = { showConnectDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_connect_cloud))
                            }
                        }
                    }
                }
            }

            item {
                StudioCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = Color(0xFFFFA000),
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                stringResource(R.string.section_local_storage),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.local_backup_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onBackupLocal,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        ) {
                            Text(stringResource(R.string.btn_backup_to_local))
                        }
                    }
                }
            }
        } else {
            // ==================== GERİ YÜKLEME ====================
            item {
                StudioCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (cloudAccount.isConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.section_cloud_storage),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    if (cloudAccount.isConnected) {
                                        stringResource(R.string.cloud_connected_as, cloudAccount.accountName, cloudAccount.provider.displayName)
                                    } else {
                                        stringResource(R.string.cloud_not_connected)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cloudAccount.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Text(
                            stringResource(R.string.cloud_restore_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        if (cloudAccount.isConnected) {
                            cloudAccount.lastBackupTime?.let { time ->
                                Text(
                                    stringResource(R.string.cloud_last_backup, time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } ?: Text(
                                stringResource(R.string.cloud_no_previous_backup),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = onRestoreCloud,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_restore_from_cloud))
                            }
                        } else {
                            Button(
                                onClick = { showConnectDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_connect_cloud))
                            }
                        }
                    }
                }
            }

            item {
                StudioCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = Color(0xFFFFA000),
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                stringResource(R.string.section_local_storage),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.local_restore_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onRestoreLocal,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        ) {
                            Text(stringResource(R.string.btn_restore_from_local))
                        }
                    }
                }
            }
        }

        item {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showConnectDialog) {
        CloudConnectDialog(
            currentAccount = cloudAccount,
            onDismiss = { showConnectDialog = false },
            onSelectGoogleDrive = {
                showConnectDialog = false
                onSelectGoogleDrive()
            },
            onConnectWebDav = {
                showConnectDialog = false
                onConnectCloud(it)
            },
        )
    }
}

@Composable
private fun CloudConnectDialog(
    currentAccount: CloudAccount,
    onDismiss: () -> Unit,
    onSelectGoogleDrive: () -> Unit,
    onConnectWebDav: (CloudAccount) -> Unit,
) {
    var isWebDavMode by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(currentAccount.serverUrl) }
    var username by remember { mutableStateOf(if (currentAccount.provider == CloudProvider.WEBDAV) currentAccount.accountName else "") }
    var password by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onDismiss)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            StudioCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {},
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (isWebDavMode) stringResource(R.string.cloud_provider_webdav) else stringResource(R.string.cloud_dialog_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )

                    if (!isWebDavMode) {
                        Text(
                            stringResource(R.string.cloud_dialog_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Google Drive Option Card
                        StudioCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectGoogleDrive()
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Cloud,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.cloud_provider_google),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(R.string.cloud_google_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // WebDAV Option Card
                        StudioCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isWebDavMode = true },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.cloud_provider_webdav),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(R.string.cloud_webdav_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(onClick = onDismiss) {
                                Text(stringResource(R.string.cloud_btn_cancel))
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text(stringResource(R.string.cloud_server_url_label)) },
                            placeholder = { Text("https://cloud.example.com/remote.php/webdav") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.cloud_username_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.cloud_password_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            OutlinedButton(onClick = { isWebDavMode = false }) {
                                Text(stringResource(R.string.cloud_btn_cancel))
                            }
                            Button(
                                onClick = {
                                    val account = CloudAccount(
                                        provider = CloudProvider.WEBDAV,
                                        accountName = if (username.isBlank()) "nextcloud.user" else username.trim(),
                                        serverUrl = serverUrl.trim(),
                                        isConnected = true,
                                    )
                                    onConnectWebDav(account)
                                },
                            ) {
                                Text(stringResource(R.string.cloud_btn_connect))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThemeProtectionScreen(
    state: ThemeProtectionState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onOpenFramework: () -> Unit,
    onRestartDevice: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MenuIconBox(
                            if (state.fullyActive) Icons.Filled.VerifiedUser else Icons.Filled.Security,
                            if (state.fullyActive) Color(0xFF2E7D32) else Color(0xFFF57C00),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.theme_protection_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (state.fullyActive) {
                                    stringResource(R.string.theme_protection_active)
                                } else {
                                    stringResource(R.string.theme_protection_inactive)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.theme_protection_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(R.string.theme_protection_status_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    ProtectionStep(
                        ready = state.serviceConnected,
                        title = stringResource(R.string.theme_protection_framework),
                        detail = if (state.serviceConnected) {
                            listOfNotNull(state.frameworkName, state.frameworkVersion, state.apiVersion?.let { "API $it" })
                                .joinToString(" · ")
                        } else stringResource(R.string.theme_protection_framework_missing),
                    )
                    ProtectionStep(
                        ready = state.themeManagerCompatible == true,
                        title = stringResource(R.string.theme_protection_compatibility),
                        detail = when (state.themeManagerCompatible) {
                            true -> stringResource(R.string.theme_protection_compatible)
                            false -> stringResource(
                                R.string.theme_protection_incompatible,
                                state.compatibilityDetail.orEmpty(),
                            )
                            null -> stringResource(R.string.theme_protection_checking)
                        },
                    )
                    ProtectionStep(
                        ready = state.scopesApproved,
                        title = stringResource(R.string.theme_protection_scope),
                        detail = if (state.scopesApproved) {
                            stringResource(R.string.theme_protection_scope_ready)
                        } else stringResource(R.string.theme_protection_scope_missing),
                    )
                    ProtectionStep(
                        ready = state.systemHookReady,
                        title = stringResource(R.string.theme_protection_system_hook),
                        detail = if (state.systemHookReady) {
                            stringResource(R.string.theme_protection_hook_ready)
                        } else stringResource(R.string.theme_protection_reboot_required),
                    )
                    ProtectionStep(
                        ready = state.themeManagerHookReady,
                        title = stringResource(R.string.theme_protection_manager_hook),
                        detail = if (state.themeManagerHookReady) {
                            stringResource(R.string.theme_protection_hook_ready)
                        } else stringResource(R.string.theme_protection_reboot_required),
                    )
                    state.error?.let { error ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        !state.serviceConnected -> Button(onClick = onOpenFramework, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.theme_protection_open_framework))
                        }
                        !state.scopesApproved -> Button(
                            enabled = !state.waitingForApproval && state.themeManagerCompatible != false,
                            onClick = onEnable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (state.waitingForApproval) stringResource(R.string.theme_protection_waiting_approval)
                                else stringResource(R.string.theme_protection_enable)
                            )
                        }
                        !state.fullyActive -> {
                            Text(
                                stringResource(R.string.theme_protection_activation_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = onOpenFramework, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.theme_protection_open_framework))
                            }
                            OutlinedButton(onClick = onRestartDevice, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.theme_protection_restart))
                            }
                        }
                    }
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.theme_protection_refresh))
                    }
                    if (state.scopesApproved) {
                        TextButton(onClick = onDisable, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.theme_protection_disable))
                        }
                    }
                }
            }
        }

        item {
            Text(
                stringResource(R.string.theme_protection_notice),
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProtectionStep(ready: Boolean, title: String, detail: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (ready) Icons.Filled.VerifiedUser else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ready) Color(0xFF2E7D32) else Color(0xFFF57C00),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AppearanceScreen(
    selected: AppAppearance,
    onSelect: (AppAppearance) -> Unit,
    contentStyle: AppContentStyle,
    onContentStyleSelect: (AppContentStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.appearance_color_mode_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column {
                    AppAppearance.entries.forEachIndexed { index, appearance ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(appearance) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RadioButton(
                                selected = selected == appearance,
                                onClick = { onSelect(appearance) },
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(appearance.titleRes), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(appearance.descriptionRes), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (index != AppAppearance.entries.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.appearance_content_style_title),
                modifier = Modifier.padding(top = 8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column {
                    AppContentStyle.entries.forEachIndexed { index, style ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onContentStyleSelect(style) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RadioButton(
                                selected = contentStyle == style,
                                onClick = { onContentStyleSelect(style) },
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(style.titleRes), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(style.descriptionRes), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (index != AppContentStyle.entries.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ThemeGalleryCard(
    theme: LibraryTheme,
    isActive: Boolean,
    onOpenDetails: () -> Unit,
    onApplyTheme: (LibraryTheme) -> Unit,
    onDeleteTheme: (LibraryTheme) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.58f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenDetails),
        ) {
            ThemePreview(
                theme = theme,
                purpose = ThemePreviewPurpose.GALLERY,
                modifier = Modifier.fillMaxSize(),
            )
            ThemeSourceBadge(
                theme = theme,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
            )
            if (isActive) {
                StatusBadge(
                    text = stringResource(R.string.theme_active_badge),
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                )
            }
        }
        Text(
            theme.archive.metadata?.name ?: theme.displayName,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onApplyTheme(theme) },
                modifier = Modifier.weight(1f).height(44.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.action_apply), maxLines = 1)
            }
            OutlinedIconButton(
                onClick = { onDeleteTheme(theme) },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ThemeDetailsDialog(
    theme: LibraryTheme,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = theme.archive.metadata?.name ?: theme.displayName
    val categories = theme.archive.components.asSequence()
        .map { it.category }
        .filter(ComponentCategory::isPersonalizationOption)
        .distinct()
        .toList()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(360.dp)) {
                        ThemePreview(
                            theme = theme,
                            purpose = ThemePreviewPurpose.GALLERY,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ThemeSourceBadge(
                            theme = theme,
                            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                        )
                        if (isActive) {
                            StatusBadge(
                                text = stringResource(R.string.theme_active_badge),
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                            )
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                        val maker = theme.archive.metadata?.let { metadata ->
                            metadata.designer?.takeIf(String::isNotBlank)
                                ?: metadata.author?.takeIf(String::isNotBlank)
                        }
                        maker?.let { author ->
                            Text(
                                stringResource(R.string.theme_designed_by, author),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        theme.archive.metadata?.description?.takeIf(String::isNotBlank)?.let { description ->
                            Text(
                                description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isActive) {
                            Text(
                                stringResource(R.string.theme_active_explanation),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (categories.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.theme_includes_title),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(categories) { category ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(categoryIcon(category), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(stringResource(categoryLabelRes(category)), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onApply, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(stringResource(R.string.action_apply))
                        }
                        OutlinedIconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSourceBadge(theme: LibraryTheme, modifier: Modifier = Modifier) {
    StatusBadge(
        text = stringResource(
            if (theme.includeInThemeGallery) R.string.theme_source_created else R.string.theme_source_imported,
        ),
        color = if (theme.includeInThemeGallery) Color(0xFF6A3FC8) else Color(0xFF1565C0),
        modifier = modifier,
    )
}

@Composable
private fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.94f),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        shadowElevation = 3.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AboutScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF7138F4).copy(alpha = 0.34f),
                                        Color(0xFF00B8F0).copy(alpha = 0.28f),
                                        Color(0xFF7138F4).copy(alpha = 0.18f),
                                    ),
                                ),
                            )
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                stringResource(R.string.about_eyebrow),
                                color = Color.White.copy(alpha = 0.88f),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
                                        RoundedCornerShape(30.dp),
                                    )
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                                    contentDescription = stringResource(R.string.app_name),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(
                            stringResource(R.string.about_app_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        stringResource(R.string.about_capabilities_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.about_capabilities_desc),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AboutCapabilityRow(
                        icon = Icons.Filled.Download,
                        color = Color(0xFF00897B),
                        title = stringResource(R.string.about_capability_import_title),
                        description = stringResource(R.string.about_capability_import_desc),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    AboutCapabilityRow(
                        icon = Icons.Filled.Tune,
                        color = Color(0xFF6A3FC8),
                        title = stringResource(R.string.about_capability_create_title),
                        description = stringResource(R.string.about_capability_create_desc),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    AboutCapabilityRow(
                        icon = Icons.Filled.VerifiedUser,
                        color = Color(0xFF1565C0),
                        title = stringResource(R.string.about_capability_apply_title),
                        description = stringResource(R.string.about_capability_apply_desc),
                    )
                }
            }
        }
        item {
            StudioCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MenuIconBox(Icons.Filled.Build, Color(0xFF4F46A5))
                        Column {
                            Text(
                                stringResource(R.string.about_repository_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.about_repository_badge),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.about_repository_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.about_repository_url),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Button(
                        onClick = { uriHandler.openUri(PROJECT_REPOSITORY_URL) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.about_open_github))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun AboutCapabilityRow(
    icon: ImageVector,
    color: Color,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MenuIconBox(icon, color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DeviceImportCard(
    title: String,
    description: String,
    status: String,
    buttonText: String,
    running: Boolean,
    onImport: () -> Unit,
) {
    StudioCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MenuIconBox(Icons.Filled.Download, Color(0xFF26A69A))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(status, style = MaterialTheme.typography.labelSmall)
            Button(
                enabled = !running,
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) stringResource(R.string.device_import_working) else buttonText)
            }
        }
    }
}

@Composable
private fun MenuHeader(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MenuIconBox(icon, color)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuRow(spec: MenuSpec, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MenuIconBox(spec.icon, spec.color)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(spec.destination.titleRes),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuIconBox(icon: ImageVector, color: Color) {
    val iconTint = if (color.luminance() > 0.179f) Color(0xFF111111) else Color.White
    Box(
        modifier = Modifier.size(46.dp).background(color, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PersonalizeTile(
    icon: ImageVector,
    title: String,
    selectedTheme: LibraryTheme?,
    sourceName: String? = null,
    category: ComponentCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notSelectedText = stringResource(R.string.personalize_choose_theme)
    val subtitle = sourceName ?: selectedTheme?.archive?.metadata?.name ?: selectedTheme?.displayName ?: notSelectedText
    val hasPreview = selectedTheme != null && hasThemePreview(selectedTheme, category)
    StudioCard(
        modifier = modifier.padding(4.dp).height(80.dp).clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hasPreview && selectedTheme != null) {
                Box(
                    modifier = Modifier
                        .size(46.dp, 60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    ThemePreview(
                        theme = selectedTheme,
                        category = category,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedTheme != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CustomWallpaperTile(
    icon: ImageVector,
    title: String,
    uriString: String?,
    baseTheme: LibraryTheme?,
    lockScreen: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasBaseWallpaper = baseTheme != null && hasThemeWallpaper(baseTheme, lockScreen)
    StudioCard(
        modifier = modifier
            .padding(4.dp)
            .height(80.dp)
            .clickable(onClick = onPick),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (uriString != null || hasBaseWallpaper) {
                Box(
                    modifier = Modifier
                        .size(46.dp, 60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    if (uriString != null) {
                        UriImagePreview(
                            uri = Uri.parse(uriString),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (baseTheme != null) {
                        ThemeWallpaperPreview(
                            theme = baseTheme,
                            lockScreen = lockScreen,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        uriString != null -> stringResource(R.string.custom_wallpaper_selected)
                        hasBaseWallpaper -> stringResource(R.string.custom_wallpaper_from_base)
                        else -> stringResource(R.string.btn_pick_gallery_image)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uriString != null || hasBaseWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (uriString != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.btn_remove_custom_wallpaper),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun DeviceThemePickerDialog(
    availableThemes: List<DeviceThemeSummary>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onImportSelected: (Set<String>) -> Unit,
) {
    var selectedIds by remember(availableThemes) {
        mutableStateOf(
            availableThemes.filterNot { it.isAlreadyImported }.map { it.localId }.toSet()
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.device_theme_select_dialog_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.device_theme_visual_picker_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.device_theme_scanning), style = MaterialTheme.typography.bodySmall)
                    }
                }
                } else if (availableThemes.isEmpty()) {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        EmptyState(
                            stringResource(R.string.empty_themes_title),
                            stringResource(R.string.device_import_idle),
                        )
                    }
                } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                selectedIds = availableThemes.map { it.localId }.toSet()
                            },
                        ) {
                            Text(stringResource(R.string.device_theme_select_all))
                        }
                        TextButton(
                            onClick = {
                                selectedIds = emptySet()
                            },
                        ) {
                            Text(stringResource(R.string.device_theme_deselect_all))
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(availableThemes) { theme ->
                            val isChecked = theme.localId in selectedIds
                            Surface(
                                modifier = Modifier
                                    .width(184.dp)
                                    .clickable {
                                        selectedIds = if (isChecked) {
                                            selectedIds - theme.localId
                                        } else {
                                            selectedIds + theme.localId
                                        }
                                    },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = if (isChecked) 6.dp else 1.dp,
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(248.dp)
                                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                                    ) {
                                        DeviceThemePreview(
                                            path = theme.previewPath,
                                            title = theme.title,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + theme.localId
                                                else selectedIds - theme.localId
                                            },
                                            modifier = Modifier.align(Alignment.TopEnd),
                                        )
                                        if (theme.isAlreadyImported) {
                                            StatusBadge(
                                                text = stringResource(R.string.device_theme_already_imported_badge),
                                                color = Color(0xFF1565C0),
                                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            theme.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            minLines = 2,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        theme.author?.takeIf(String::isNotBlank)?.let { author ->
                                            Text(
                                                author,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        enabled = !isLoading && selectedIds.isNotEmpty(),
                        onClick = { onImportSelected(selectedIds) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.device_theme_import_selected_btn, selectedIds.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    StudioCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class MenuSpec(
    val destination: StudioDestination,
    val icon: ImageVector,
    val color: Color,
)

private data class OverlayMenuItem(
    val destination: StudioDestination,
    val icon: ImageVector,
    val color: Color,
    @StringRes val descriptionRes: Int,
)

internal fun LibraryTheme.isThemeGalleryItem(): Boolean {
    if (includeInThemeGallery) return true
    val appearanceCategories = archive.components.asSequence()
        .map { it.category }
        .filter { it in THEME_GALLERY_CATEGORIES }
        .distinct()
        .count()
    return appearanceCategories >= 2
}

private val THEME_GALLERY_CATEGORIES = setOf(
    ComponentCategory.ICONS,
    ComponentCategory.LOCKSCREEN,
    ComponentCategory.WALLPAPER,
    ComponentCategory.SYSTEM_UI,
    ComponentCategory.CONTACTS,
    ComponentCategory.MMS,
    ComponentCategory.LAUNCHER,
    ComponentCategory.AOD,
)

private const val PROJECT_REPOSITORY_URL = "https://github.com/GloriousTR/HyperOS-MTZ-Studio"

internal fun destinationFor(category: ComponentCategory): StudioDestination = when (category) {
    ComponentCategory.ICONS -> StudioDestination.ICONS
    ComponentCategory.LOCKSCREEN -> StudioDestination.LOCKSCREEN
    ComponentCategory.WALLPAPER -> StudioDestination.WALLPAPERS
    ComponentCategory.FRAMEWORK -> StudioDestination.FRAMEWORK
    ComponentCategory.SYSTEM_UI -> StudioDestination.SYSTEM_UI
    ComponentCategory.CONTACTS -> StudioDestination.CONTACTS
    ComponentCategory.MMS -> StudioDestination.MMS
    ComponentCategory.SYSTEM_UI_PLUGIN -> StudioDestination.SYSTEM_UI_PLUGIN
    ComponentCategory.LAUNCHER -> StudioDestination.LAUNCHER
    ComponentCategory.AOD -> StudioDestination.AOD
    ComponentCategory.RINGTONE -> StudioDestination.RINGTONES
    ComponentCategory.FONT -> StudioDestination.FONTS
    ComponentCategory.OTHER -> StudioDestination.OTHER
}

internal fun ComponentCategory.isPersonalizationOption(): Boolean = when (this) {
    ComponentCategory.FRAMEWORK,
    ComponentCategory.SYSTEM_UI_PLUGIN,
    ComponentCategory.OTHER,
    ComponentCategory.RINGTONE,
    -> false
    else -> true
}

@StringRes
internal fun categoryLabelRes(category: ComponentCategory): Int = when (category) {
    ComponentCategory.ICONS -> R.string.category_icons
    ComponentCategory.LOCKSCREEN -> R.string.category_lockscreen
    ComponentCategory.WALLPAPER -> R.string.category_wallpaper
    ComponentCategory.FRAMEWORK -> R.string.category_framework
    ComponentCategory.SYSTEM_UI -> R.string.category_system_ui
    ComponentCategory.CONTACTS -> R.string.category_contacts
    ComponentCategory.MMS -> R.string.category_mms
    ComponentCategory.SYSTEM_UI_PLUGIN -> R.string.category_system_ui_plugin
    ComponentCategory.LAUNCHER -> R.string.category_launcher
    ComponentCategory.AOD -> R.string.category_aod
    ComponentCategory.RINGTONE -> R.string.category_ringtone
    ComponentCategory.FONT -> R.string.category_font
    ComponentCategory.OTHER -> R.string.category_other
}

private fun categoryIcon(category: ComponentCategory): ImageVector = when (category) {
    ComponentCategory.ICONS -> Icons.Filled.GridView
    ComponentCategory.LOCKSCREEN -> Icons.Filled.Lock
    ComponentCategory.WALLPAPER -> Icons.Filled.Image
    ComponentCategory.FRAMEWORK -> Icons.Filled.Build
    ComponentCategory.SYSTEM_UI -> Icons.Filled.Notifications
    ComponentCategory.CONTACTS -> Icons.Filled.Call
    ComponentCategory.MMS -> Icons.Filled.Sms
    ComponentCategory.SYSTEM_UI_PLUGIN -> Icons.Filled.Widgets
    ComponentCategory.LAUNCHER -> Icons.Filled.Home
    ComponentCategory.AOD -> Icons.Filled.Nightlight
    ComponentCategory.RINGTONE -> Icons.Filled.MusicNote
    ComponentCategory.FONT -> Icons.Filled.FontDownload
    ComponentCategory.OTHER -> Icons.Filled.Dashboard
}
