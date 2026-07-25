package com.bb8.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bb8.app.data.SavedMacro
import com.bb8.app.ui.components.PrimaryActionButton
import com.bb8.app.ui.components.SecondaryActionButton
import com.bb8.app.ui.theme.Bb8Orange
import com.bb8.app.ui.theme.SpaceBorder
import com.bb8.app.ui.theme.SpacePanel
import com.bb8.app.ui.theme.TextMuted
import com.bb8.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    macros: List<SavedMacro>,
    isRecording: Boolean,
    isPlaying: Boolean,
    recordedStepCount: Int,
    onBack: () -> Unit,
    onToggleRecord: () -> Unit,
    onSaveRecording: (String) -> Unit,
    onPlayMacro: (SavedMacro) -> Unit,
    onUploadMacro: (SavedMacro) -> Unit,
    onDeleteMacro: (SavedMacro) -> Unit,
    onStopPlayback: () -> Unit,
    onAddSampleMacro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Macro editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Record drive + LED actions, then play on-host or upload bytecode to the droid (Sphero V1 macro VM).",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryActionButton(
                    text = if (isRecording) "Stop recording ($recordedStepCount)" else "Record",
                    onClick = onToggleRecord,
                    modifier = Modifier.weight(1f),
                )
                SecondaryActionButton(
                    text = "Save",
                    onClick = { onSaveRecording("Macro ${macros.size + 1}") },
                    modifier = Modifier.weight(1f),
                )
            }

            if (isPlaying) {
                SecondaryActionButton(text = "Stop playback", onClick = onStopPlayback)
            }

            SecondaryActionButton(text = "Add sample square patrol", onClick = onAddSampleMacro)

            Text(
                text = "SAVED MACROS",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold,
            )

            if (macros.isEmpty()) {
                Text(
                    text = "No macros yet. Record while driving or add the sample.",
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(macros, key = { it.id }) { macro ->
                        MacroCard(
                            macro = macro,
                            onPlay = { onPlayMacro(macro) },
                            onUpload = { onUploadMacro(macro) },
                            onDelete = { onDeleteMacro(macro) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroCard(
    macro: SavedMacro,
    onPlay: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SpacePanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = macro.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${macro.steps.size} steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Bb8Orange)
            }
            IconButton(onClick = onUpload) {
                Icon(Icons.Default.Upload, contentDescription = "Upload to droid", tint = TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted)
            }
        }
    }
}
