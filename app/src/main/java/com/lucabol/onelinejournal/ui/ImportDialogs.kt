package com.lucabol.onelinejournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucabol.onelinejournal.JournalViewModel
import com.lucabol.onelinejournal.core.ConflictPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ImportDialogs(model: JournalViewModel) {
    model.folderChoices?.let { folders ->
        AlertDialog(onDismissRequest = model::cancelFolder, title = { Text("Choose your Drive folder") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(folders, key = { it.id }) { folder ->
                        Column(Modifier.clickable(enabled = !model.busy) { model.chooseFolder(folder) }.padding(12.dp)) {
                            Text(folder.name)
                            Text(folder.id, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = model::cancelFolder) { Text("Cancel") } })
    }
    model.backupChoices?.let { backups ->
        AlertDialog(onDismissRequest = model::cancelBackups, title = { Text("Restore a Drive backup") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (backups.isEmpty()) item { Text("No backup snapshots are available yet.") }
                    items(backups, key = { it.fileId }) { backup ->
                        Column(Modifier.clickable(enabled = !model.busy) { model.chooseBackup(backup) }.padding(12.dp)) {
                            Text(Instant.parse(backup.createdAt).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss")))
                            Text("Generation ${backup.generation}; ${if (backup.encrypted) "encrypted" else "unencrypted"}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = model::cancelBackups) { Text("Cancel") } })
    }
    model.recoveryChoices?.let { files ->
        AlertDialog(onDismissRequest = model::cancelRecovery, title = { Text("Local recovery copies") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (files.isEmpty()) item { Text("A safety copy is created before an import changes existing entries.") }
                    items(files, key = { it.name }) { file ->
                        Text(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss")),
                            Modifier.clickable(enabled = !model.busy) { model.chooseRecovery(file) }.padding(14.dp))
                    }
                }
            }, confirmButton = { TextButton(onClick = model::cancelRecovery) { Text("Cancel") } })
    }
    model.lockedImport?.let { source ->
        var password by remember(source) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!model.busy) model.cancelImport() }, title = { Text("Unlock encrypted backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter the recovery passphrase used when this backup was created.")
                    PasswordField("Recovery passphrase", password) { password = it }
                    model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !model.busy && password.isNotEmpty(),
                onClick = { model.unlockImport(password) }) { Text("Preview backup") } },
            dismissButton = { TextButton(enabled = !model.busy, onClick = model::cancelImport) { Text("Cancel") } })
    }
    model.importPlan?.let { plan ->
        var policy by remember(plan) { mutableStateOf<ConflictPolicy?>(null) }
        var replaceAll by remember(plan) { mutableStateOf(false) }
        var confirmed by remember(plan) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!model.busy) model.cancelImport() },
            title = { Text(if (plan.cloud == null) "Preview imported journal" else "Restore and use this phone") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        val active = plan.snapshot.entries.filter { it.deletedAt == null }
                        Text("${active.size} written dates; ${plan.snapshot.entries.size - active.size} deletion records.")
                        Text("Range: ${active.minOfOrNull { it.journalDate } ?: "empty"} to ${active.maxOfOrNull { it.journalDate } ?: "empty"}")
                        Text("${plan.preview.newDates} new dates; ${plan.preview.conflictDates.size} conflicting dates.")
                        if (plan.cloud != null) Text("This phone becomes the active writer. Stop writing on the old phone. Automatic backups will resume.")
                    }
                    item {
                        Row {
                            Checkbox(replaceAll, onCheckedChange = { replaceAll = it; confirmed = false })
                            Text("Replace all local entries instead of merging.")
                        }
                    }
                    if (!replaceAll && plan.preview.conflictDates.isNotEmpty()) {
                        item {
                            Row(Modifier.clickable { policy = ConflictPolicy.KEEP_LOCAL }) {
                                RadioButton(policy == ConflictPolicy.KEEP_LOCAL, onClick = { policy = ConflictPolicy.KEEP_LOCAL })
                                Text("Keep this phone's version on conflicting dates")
                            }
                            Row(Modifier.clickable { policy = ConflictPolicy.USE_BACKUP }) {
                                RadioButton(policy == ConflictPolicy.USE_BACKUP, onClick = { policy = ConflictPolicy.USE_BACKUP })
                                Text("Use the backup's version on conflicting dates")
                            }
                        }
                        items(plan.preview.conflictDates) { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    item {
                        Row {
                            Checkbox(confirmed, onCheckedChange = { confirmed = it })
                            Text("Apply these changes. A local safety copy will be kept.")
                        }
                        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !model.busy && confirmed &&
                    (replaceAll || plan.preview.conflictDates.isEmpty() || policy != null),
                    onClick = { model.applyImport(policy ?: ConflictPolicy.KEEP_LOCAL, replaceAll) }) { Text("Apply restore") }
            },
            dismissButton = { TextButton(enabled = !model.busy, onClick = model::cancelImport) { Text("Cancel") } })
    }
}
