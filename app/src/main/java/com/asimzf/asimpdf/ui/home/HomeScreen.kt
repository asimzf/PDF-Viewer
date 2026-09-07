package com.asimzf.asimpdf.ui.home

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asimzf.asimpdf.data.RecentDocument
import com.asimzf.asimpdf.ui.common.AppIcons

/** Landing screen: open something, start a tool, or pick up where you left off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recents: List<RecentDocument>,
    onOpen: () -> Unit,
    onOpenRecent: (Uri) -> Unit,
    onForgetRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onImagesToPdf: () -> Unit,
    onMerge: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("asimPDF", fontWeight = FontWeight.Bold)
                        Text(
                            "Read, edit and organise PDFs — entirely on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (recents.isNotEmpty()) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AppIcons.More, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear recent list") },
                                onClick = {
                                    menuOpen = false
                                    onClearRecents()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PrimaryAction(
                    title = "Open a PDF",
                    subtitle = "Pick a document from this phone or tablet",
                    icon = AppIcons.Folder,
                    onClick = onOpen
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction(
                        title = "Images to PDF",
                        icon = AppIcons.Image,
                        modifier = Modifier.weight(1f),
                        onClick = onImagesToPdf
                    )
                    QuickAction(
                        title = "Merge PDFs",
                        icon = AppIcons.Layers,
                        modifier = Modifier.weight(1f),
                        onClick = onMerge
                    )
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (recents.isEmpty()) "No documents yet" else "Recent",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (recents.isEmpty()) {
                item {
                    Text(
                        "Documents you open show up here. asimPDF never uploads anything: " +
                            "it has no internet permission at all.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(recents, key = { it.uri }) { recent ->
                RecentRow(
                    recent = recent,
                    onOpen = { onOpenRecent(Uri.parse(recent.uri)) },
                    onForget = { onForgetRecent(recent.uri) }
                )
            }
        }
    }
}

@Composable
private fun PrimaryAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RecentRow(
    recent: RecentDocument,
    onOpen: () -> Unit,
    onForget: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Pdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recent.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                val details = buildString {
                    if (recent.pageCount > 0) {
                        append("${recent.pageCount} page${if (recent.pageCount == 1) "" else "s"}")
                    }
                    if (recent.sizeBytes > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(Formatter.formatShortFileSize(context, recent.sizeBytes))
                    }
                    if (recent.lastOpened > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(
                            DateUtils.getRelativeTimeSpanString(
                                recent.lastOpened,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            )
                        )
                    }
                }
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onForget) {
                Icon(AppIcons.Close, contentDescription = "Remove from recents")
            }
        }
    }
}
