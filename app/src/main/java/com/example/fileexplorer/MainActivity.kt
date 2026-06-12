package com.example.fileexplorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ExplorerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen() {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Storage Roots
    val storageRoots = remember { getStorageRoots(context) }
    var currentPath by remember { mutableStateOf(storageRoots.firstOrNull()?.file ?: Environment.getExternalStorageDirectory()) }
    
    var files by remember { mutableStateOf(listOf<File>()) }
    var hasPermission by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog States
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            refreshFiles(currentPath) { files = it }
        }
    }

    LaunchedEffect(Unit) {
        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        if (status == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            refreshFiles(currentPath) { files = it }
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isEmpty()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val updateCurrentPath = { path: File ->
        currentPath = path
        refreshFiles(path) { files = it }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Storage Locations",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                storageRoots.forEach { root ->
                    NavigationDrawerItem(
                        label = { Text(root.name) },
                        selected = currentPath.absolutePath.startsWith(root.file.absolutePath),
                        onClick = {
                            scope.launch { drawerState.close() }
                            updateCurrentPath(root.file)
                        },
                        icon = { Icon(if (root.isSdCard) Icons.Default.SdCard else Icons.Default.Storage, null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                // Quick Access
                Text(
                    "Quick Access",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                listOf(
                    QuickAccessItem("Downloads", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Icons.Default.Download),
                    QuickAccessItem("Pictures", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), Icons.Default.Image),
                    QuickAccessItem("DCIM", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), Icons.Default.CameraAlt)
                ).forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.name) },
                        selected = currentPath == item.file,
                        onClick = {
                            scope.launch { drawerState.close() }
                            updateCurrentPath(item.file)
                        },
                        icon = { Icon(item.icon, null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    if (currentPath == storageRoots.firstOrNull()?.file) "Internal Storage"
                                    else if (storageRoots.any { it.isSdCard && currentPath.absolutePath.startsWith(it.file.absolutePath) }) "SD Card"
                                    else currentPath.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, "Menu")
                                }
                            },
                            actions = {
                                if (currentPath.parentFile != null && !storageRoots.any { it.file == currentPath }) {
                                    IconButton(onClick = { updateCurrentPath(currentPath.parentFile!!) }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up")
                                    }
                                }
                            }
                        )
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = {},
                            active = false,
                            onActiveChange = {},
                            placeholder = { Text("Search files...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { 
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {}
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, "New Folder")
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (!hasPermission) {
                    PermissionView(onGrant = { permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) })
                } else if (filteredFiles.isEmpty()) {
                    EmptyView(searchQuery.isNotEmpty())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredFiles, key = { it.absolutePath }) { file ->
                            FileItem(
                                file = file,
                                onOpen = {
                                    if (file.isDirectory) {
                                        updateCurrentPath(file)
                                        searchQuery = ""
                                    } else {
                                        openFile(context, file)
                                    }
                                },
                                onRename = { showRenameDialog = file },
                                onDelete = { showDeleteConfirm = file },
                                onShare = { shareFile(context, file) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs ... (keep existing dialogs and add share to menu)
    if (showCreateFolderDialog) {
        InputDialog(
            title = "New Folder",
            onAction = { name ->
                val newDir = File(currentPath, name)
                if (newDir.mkdir()) {
                    refreshFiles(currentPath) { files = it }
                } else {
                    Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show()
                }
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    showRenameDialog?.let { file ->
        InputDialog(
            title = "Rename",
            initialValue = file.name,
            onAction = { newName ->
                val dest = File(file.parentFile, newName)
                if (file.renameTo(dest)) {
                    refreshFiles(currentPath) { files = it }
                } else {
                    Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                }
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    showDeleteConfirm?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete") },
            text = { Text("Are you sure you want to delete ${file.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    if (file.deleteRecursively()) {
                        refreshFiles(currentPath) { files = it }
                    } else {
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                    showDeleteConfirm = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

data class StorageRoot(val name: String, val file: File, val isSdCard: Boolean)
data class QuickAccessItem(val name: String, val file: File, val icon: ImageVector)

@Composable
fun PermissionView(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Security, null, Modifier.size(72.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Access Required", style = MaterialTheme.typography.headlineSmall)
        Text(
            "We need storage access to manage your files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrant, shape = RoundedCornerShape(16.dp)) {
            Text("Allow Access")
        }
    }
}

@Composable
fun EmptyView(isSearch: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (isSearch) Icons.Default.SearchOff else Icons.Default.FolderOpen,
            null,
            Modifier.size(80.dp),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isSearch) "Matching files not found" else "This directory is empty",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(file: File, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    ListItem(
        headlineContent = {
            Text(
                file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (file.isDirectory) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(file.lastModified())), style = MaterialTheme.typography.labelSmall)
                if (!file.isDirectory) {
                    Text(" • ${formatSize(file.length())}", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        leadingContent = {
            val icon = getFileIcon(file)
            val color = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = { expanded = false; onShare() },
                        leadingIcon = { Icon(Icons.Default.Share, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { expanded = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = { expanded = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    )
                }
            }
        },
        modifier = Modifier
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { expanded = true }
            )
            .padding(vertical = 4.dp)
    )
}

private fun getStorageRoots(context: android.content.Context): List<StorageRoot> {
    val roots = mutableListOf<StorageRoot>()
    // Internal
    roots.add(StorageRoot("Internal Storage", Environment.getExternalStorageDirectory(), false))
    
    // SD Cards and other volumes
    val dirs = ContextCompat.getExternalFilesDirs(context, null)
    if (dirs.size > 1) {
        for (i in 1 until dirs.size) {
            val file = dirs[i] ?: continue
            val path = file.absolutePath
            val rootPath = path.split("/Android")[0]
            val rootFile = File(rootPath)
            if (rootFile.exists() && rootFile.canRead()) {
                roots.add(StorageRoot("SD Card ${if (dirs.size > 2) i else ""}", rootFile, true))
            }
        }
    }
    return roots
}

private fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun InputDialog(title: String, initialValue: String = "", onAction: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onAction(text) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun refreshFiles(dir: File, onDone: (List<File>) -> Unit) {
    val list = dir.listFiles()?.toList() ?: emptyList()
    onDone(list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun getFileIcon(file: File): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder
    val ext = file.extension.lowercase()
    return when {
        ext in listOf("jpg", "jpeg", "png", "gif", "webp") -> Icons.Default.Image
        ext in listOf("mp4", "mkv", "avi", "mov") -> Icons.Default.VideoFile
        ext in listOf("mp3", "wav", "ogg", "flac") -> Icons.Default.AudioFile
        ext in listOf("pdf", "doc", "docx", "txt", "rtf") -> Icons.Default.Description
        ext in listOf("zip", "rar", "7z", "tar") -> Icons.Default.Archive
        ext in listOf("apk") -> Icons.Default.Android
        else -> Icons.Default.InsertDriveFile
    }
}

private fun openFile(context: android.content.Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
