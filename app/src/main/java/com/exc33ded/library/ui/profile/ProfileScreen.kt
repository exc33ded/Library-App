package com.exc33ded.library.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.exc33ded.library.profile.Profile
import com.exc33ded.library.profile.ProfileStats
import com.exc33ded.library.profile.initials
import java.io.File

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val profile = LocalProfile.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(profile, Modifier.size(88.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.ifBlank { "Your name" },
                    style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (profile.bio.isNotBlank())
                    Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalButton(onClick = { editing = true }) { Text("Edit profile") }
        GoalCard(stats.readThisYear, profile.yearlyGoal) { editing = true }
        StatsCard(stats)
        ListItem(
            headlineContent = { Text("Settings") },
            leadingContent = { Icon(Icons.Filled.Settings, null) },
            modifier = Modifier.clickable(onClick = onOpenSettings),
        )
    }
    if (editing) EditProfileSheet(
        profile = profile,
        onDismiss = { editing = false },
        onSave = { n, b, g -> vm.save(n, b, g); editing = false },
        onPickPhoto = vm::setAvatar,
        onRemovePhoto = vm::removeAvatar,
    )
}

@Composable
fun Avatar(profile: Profile, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        val path = profile.avatarPath
        if (path != null) {
            AsyncImage(File(path), "Profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initials(profile.name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun GoalCard(readThisYear: Int, goal: Int?, onSetGoal: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (goal == null) {
            Column(Modifier.weight(1f)) {
                Text("Reading goal", style = MaterialTheme.typography.titleMedium)
                Text("Set a goal for this year", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSetGoal) { Text("Set goal") }
        } else {
            val fraction = (readThisYear.toFloat() / goal).coerceIn(0f, 1f)
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Column {
                Text("$readThisYear of $goal books this year", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (readThisYear >= goal) "Goal reached!" else "${goal - readThisYear} to go",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(s: ProfileStats) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Stats", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(s.read, "Read"); Stat(s.reading, "Reading"); Stat(s.want, "Want"); Stat(s.dnf, "DNF")
        }
        Text(
            "${s.pagesRead} pages read" + (s.averageRating?.let { " · average rating ${"%.1f".format(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Stat(n: Int, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("$n", style = MaterialTheme.typography.headlineSmall)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
