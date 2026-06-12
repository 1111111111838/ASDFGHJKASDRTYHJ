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
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
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

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Explorer 32", fontWeight = FontWeight.Bold)
                            Text(
                                currentPath.absolutePath.replace(Environment.getExternalStorageDirectory().absolutePath, "Storage"),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentPath != Environment.getExternalStorageDirectory()) {
                            IconButton(onClick = { 
                                currentPath.parentFile?.let { updateCurrentPath(it) }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search files...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateFolderDialog = true }) {
                Icon(Icons.Default.CreateNewFolder, "New Folder")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Security, null, Modifier.size(64.dp), Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text("Permission Required")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }) {
                        Text("Grant Permission")
                    }
                }
            } else if (filteredFiles.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(64.dp), Color.Gray.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text("No files found", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            onDelete = { showDeleteConfirm = file }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(file: File, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    ListItem(
        headlineContent = { 
            Text(
                file.name, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal
            ) 
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(file.lastModified())), fontSize = 12.sp)
                if (!file.isDirectory) {
                    Text(" • ${formatSize(file.length())}", fontSize = 12.sp)
                }
            }
        },
        leadingContent = {
            val icon = getFileIcon(file)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { expanded = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
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
    )
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
