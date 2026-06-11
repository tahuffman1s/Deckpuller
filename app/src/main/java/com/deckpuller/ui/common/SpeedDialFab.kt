package com.deckpuller.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One entry in a [SpeedDialFab]; [icon] is a slot so brand logos (Image) work too. */
data class SpeedDialAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
)

/**
 * Right-anchored expandable speed-dial FAB, matching the pull screen's actions menu.
 * Tapping the main button reveals labelled mini-FABs reading inward from the right edge.
 */
@Composable
fun SpeedDialFab(
    actions: List<SpeedDialAction>,
    collapsedIcon: ImageVector,
    collapsedDescription: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expanded) {
            actions.forEach { action ->
                Row(
                    modifier = Modifier.clickable { expanded = false; action.onClick() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                    ) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    SmallFloatingActionButton(onClick = { expanded = false; action.onClick() }) {
                        action.icon()
                    }
                }
            }
        }
        FloatingActionButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else collapsedIcon,
                contentDescription = if (expanded) "Close actions" else collapsedDescription,
            )
        }
    }
}
