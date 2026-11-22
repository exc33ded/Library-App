package com.exc33ded.library.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.exc33ded.library.profile.BIO_MAX
import com.exc33ded.library.profile.NAME_MAX
import com.exc33ded.library.profile.Profile
import com.exc33ded.library.profile.parseGoal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (name: String, bio: String, goal: Int?) -> Unit,
    onPickPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var bio by remember { mutableStateOf(profile.bio) }
    var goal by remember { mutableStateOf(profile.yearlyGoal?.toString() ?: "") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPickPhoto(uri) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(profile, Modifier.size(64.dp))
                OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("Choose photo")
                }
                if (profile.avatarPath != null) TextButton(onClick = onRemovePhoto) { Text("Remove") }
            }
            OutlinedTextField(
                name, { name = it.take(NAME_MAX) }, Modifier.fillMaxWidth(),
                label = { Text("Name") }, singleLine = true,
            )
            OutlinedTextField(
                bio, { bio = it.take(BIO_MAX) }, Modifier.fillMaxWidth(),
                label = { Text("Bio or favourite quote") }, supportingText = { Text("${bio.length}/$BIO_MAX") },
            )
            OutlinedTextField(
                goal, { goal = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(),
                label = { Text("Books to read this year") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(name, bio, parseGoal(goal)) }) { Text("Save") }
            }
        }
    }
}
