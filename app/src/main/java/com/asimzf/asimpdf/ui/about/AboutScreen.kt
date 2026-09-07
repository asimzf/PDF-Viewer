package com.asimzf.asimpdf.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asimzf.asimpdf.BuildConfig
import com.asimzf.asimpdf.ui.common.AppIcons
import com.asimzf.asimpdf.ui.common.SectionCard

/** Version, the privacy promise, and the licences of everything we build on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = "Back")
                    }
                },
                title = { Text("About asimPDF") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = "asimPDF ${BuildConfig.VERSION_NAME}") {
                Text(
                    "A PDF viewer and editor that works entirely on this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SectionCard(
                title = "Privacy",
                subtitle = "Nothing you open ever leaves this device"
            ) {
                Bullet("asimPDF asks for no permissions at all.")
                Bullet(
                    "It does not hold the internet permission, so it cannot send or " +
                        "receive anything over a network."
                )
                Bullet("There are no ads, no analytics, no accounts and no tracking.")
                Bullet(
                    "Documents you open are copied into the app's private storage while " +
                        "you edit them, and that copy is deleted when it goes stale."
                )
                Bullet(
                    "Files reach the app only through the system file picker, or when " +
                        "you share a PDF to it yourself."
                )
            }

            SectionCard(
                title = "Open source",
                subtitle = "asimPDF is released under the MIT licence"
            ) {
                Licence(
                    name = "PdfBox-Android",
                    detail = "Apache License 2.0 — the PDF engine behind every edit"
                )
                Licence(
                    name = "Apache PDFBox",
                    detail = "Apache License 2.0 — the library PdfBox-Android is ported from"
                )
                Licence(
                    name = "Bouncy Castle",
                    detail = "Bouncy Castle Licence (MIT style) — encryption support"
                )
                Licence(
                    name = "AndroidX and Jetpack Compose",
                    detail = "Apache License 2.0 — the user interface toolkit"
                )
                Text(
                    "Page rendering uses Android's own PdfRenderer, part of the operating " +
                        "system.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text(
        "•  $text",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun Licence(name: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
