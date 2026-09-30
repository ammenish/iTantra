package com.mirage.itantra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.TransportType

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editableCallsign by remember(state.callsign) { mutableStateOf(state.callsign) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // â”€â”€ Top bar â”€â”€
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // â”€â”€ Callsign â”€â”€
        SectionHeader("DEVICE CALLSIGN")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp)
        ) {
            if (state.isEditingCallsign) {
                OutlinedTextField(
                    value = editableCallsign,
                    onValueChange = { editableCallsign = it },
                    label = { Text("Callsign") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            viewModel.updateCallsign(editableCallsign)
                            viewModel.toggleEditCallsign()
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = state.callsign,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Identifies this device on the network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = viewModel::toggleEditCallsign) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit callsign",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // â”€â”€ Language Selection â”€â”€
        SectionHeader("COMMUNICATION LANGUAGE")
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 4.dp)
        ) {
            val supportedLanguages = listOf(Language.ENGLISH, Language.HINDI, Language.MARATHI, Language.TAMIL)
            state.availableLanguages.forEach { language ->
                val isSupported = language in supportedLanguages
                LanguageItem(
                    language = language,
                    isSelected = language == state.selectedLanguage,
                    enabled = isSupported,
                    isDownloaded = state.downloadStatus[language] ?: false,
                    onClick = { 
                        if (isSupported) {
                            viewModel.selectLanguage(language) 
                        } else {
                            android.widget.Toast.makeText(context, "${language.displayName} will be available in future models.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // â”€â”€ Transport â”€â”€
        SectionHeader("TRANSPORT MODE")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 4.dp)
        ) {
            TransportItem("Wi-Fi Direct (Primary)", TransportType.WIFI_DIRECT, state.transportType) {
                viewModel.selectTransport(TransportType.WIFI_DIRECT)
            }
            TransportItem("Bluetooth RFCOMM (Fallback)", TransportType.BLUETOOTH_RFCOMM, state.transportType) {
                viewModel.selectTransport(TransportType.BLUETOOTH_RFCOMM)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // â”€â”€ About â”€â”€
        SectionHeader("ABOUT")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp)
        ) {
            InfoRow("Application", "iTantra v0.1.0-alpha")
            InfoRow("Team", "MIRAGE")
            InfoRow("Architecture", "SPEECH â†’ STT â†’ TEXT â†’ TTS â†’ SPEECH")
            InfoRow("Operation", "Fully Offline")
            InfoRow("Languages (v0.1)", "Hindi, English, Tamil, Gujarati, Marathi, Kannada, Telugu, Bengali")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun LanguageItem(language: Language, isSelected: Boolean, enabled: Boolean = true, isDownloaded: Boolean = false, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // We still want it to be clickable to show the Toast!
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(alpha)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (enabled) {
            if (isDownloaded) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                // You can animate this icon or use a CircularProgressIndicator if you wanted to be fancy,
                // but a simple download icon implies it's either needed or currently happening in background.
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Downloading",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TransportItem(label: String, type: TransportType, current: TransportType, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        RadioButton(
            selected = type == current,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
