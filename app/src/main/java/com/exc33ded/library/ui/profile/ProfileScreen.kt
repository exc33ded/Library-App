package com.exc33ded.library.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ponytail: stats/profile content arrives in Phase 5; this tab only hosts Settings for now.
@Composable
fun ProfileScreen(onOpenSettings: () -> Unit) = Column(Modifier.fillMaxSize().statusBarsPadding()) {
    ListItem(
        headlineContent = { Text("Settings") },
        leadingContent = { Icon(Icons.Filled.Settings, null) },
        modifier = Modifier.clickable(onClick = onOpenSettings),
    )
}
