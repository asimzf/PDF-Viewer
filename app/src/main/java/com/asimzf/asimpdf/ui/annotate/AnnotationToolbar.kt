package com.asimzf.asimpdf.ui.annotate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asimzf.asimpdf.model.AnnotationTool
import com.asimzf.asimpdf.model.ShapeKind
import com.asimzf.asimpdf.ui.AnnotationState
import com.asimzf.asimpdf.ui.common.AppIcons
import com.asimzf.asimpdf.ui.common.ColorPicker
import com.asimzf.asimpdf.ui.common.LabeledSlider

/** The markup controls that sit under the page while editing. */
@Composable
fun AnnotationToolbar(
    state: AnnotationState,
    onToolSelected: (AnnotationTool) -> Unit,
    onStateChange: ((AnnotationState) -> AnnotationState) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnnotationTool.entries.forEach { tool ->
                    FilterChip(
                        selected = state.tool == tool,
                        onClick = { onToolSelected(tool) },
                        label = { Text(tool.label) }
                    )
                }
            }

            AnimatedVisibility(visible = state.tool.hasStyleOptions()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorPicker(
                        selected = state.color,
                        onSelect = { color -> onStateChange { it.copy(color = color) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.tool == AnnotationTool.INK || state.tool == AnnotationTool.SHAPE) {
                        LabeledSlider(
                            label = "Thickness",
                            value = state.strokeWidth,
                            range = 1f..16f,
                            onValueChange = { value ->
                                onStateChange { it.copy(strokeWidth = value) }
                            },
                            valueLabel = "${state.strokeWidth.toInt()} pt"
                        )
                    }
                    if (state.tool == AnnotationTool.TEXT) {
                        LabeledSlider(
                            label = "Text size",
                            value = state.fontSize,
                            range = 8f..48f,
                            onValueChange = { value ->
                                onStateChange { it.copy(fontSize = value) }
                            },
                            valueLabel = "${state.fontSize.toInt()} pt"
                        )
                    }
                    if (state.tool == AnnotationTool.SHAPE) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            ShapeKind.entries.forEach { kind ->
                                FilterChip(
                                    selected = state.shape == kind,
                                    onClick = { onStateChange { it.copy(shape = kind) } },
                                    label = { Text(kind.label) }
                                )
                            }
                            Text("Filled", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = state.filled,
                                onCheckedChange = { checked ->
                                    onStateChange { it.copy(filled = checked) }
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(AppIcons.Undo, contentDescription = "Undo markup")
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(AppIcons.Redo, contentDescription = "Redo markup")
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.hasPending) {
                        AssistChip(
                            onClick = onDiscard,
                            label = { Text("Discard") },
                            leadingIcon = { Icon(AppIcons.Close, contentDescription = null) }
                        )
                    }
                }
                AssistChip(
                    onClick = onApply,
                    enabled = state.hasPending,
                    label = { Text("Apply to PDF") },
                    leadingIcon = { Icon(AppIcons.Check, contentDescription = null) }
                )
            }
        }
    }
}

private fun AnnotationTool.hasStyleOptions(): Boolean = when (this) {
    AnnotationTool.NONE, AnnotationTool.ERASER, AnnotationTool.REDACT,
    AnnotationTool.SIGNATURE, AnnotationTool.IMAGE -> false

    else -> true
}
