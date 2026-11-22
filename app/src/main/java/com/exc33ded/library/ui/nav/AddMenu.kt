package com.exc33ded.library.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class AddAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddMenuOverlay(
    visible: Boolean,
    actions: List<AddAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(effects), exit = fadeOut(effects)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val n = actions.size
            actions.forEachIndexed { i, action ->
                // the item nearest the button (last in the list) appears first
                val delay = (n - 1 - i) * 40
                AnimatedVisibility(
                    visible,
                    enter = fadeIn(tween(180, delayMillis = delay)) +
                        scaleIn(
                            tween(320, delayMillis = delay, easing = FastOutSlowInEasing),
                            initialScale = 0.8f,
                            transformOrigin = TransformOrigin(0.5f, 1f),
                        ) +
                        slideInVertically(tween(320, delayMillis = delay, easing = FastOutSlowInEasing)) { it / 2 },
                    exit = fadeOut(effects) + scaleOut(targetScale = 0.9f),
                ) {
                    AddPill(action)
                }
            }
        }
    }
    BackHandler(enabled = visible, onBack = onDismiss)
}

@Composable
private fun AddPill(action: AddAction) {
    Surface(
        onClick = action.onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier.height(56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(action.label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddToggleButton(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    ToggleFloatingActionButton(
        checked = expanded,
        onCheckedChange = { onToggle() },
        modifier = modifier.semantics { contentDescription = if (expanded) "Close add menu" else "Add book" },
    ) {
        val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add } }
        Icon(icon, contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
    }
}
