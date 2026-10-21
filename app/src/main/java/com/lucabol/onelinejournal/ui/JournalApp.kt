package com.lucabol.onelinejournal.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lucabol.onelinejournal.JournalViewModel
import com.lucabol.onelinejournal.core.JournalEntry
import com.lucabol.onelinejournal.core.JournalFormat
import com.lucabol.onelinejournal.core.MAX_ENTRY_LENGTH
import com.lucabol.onelinejournal.core.Prompts
import com.lucabol.onelinejournal.data.JournalState
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun OneLineTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFFD8EA1), onPrimary = Color(0xFF1A1A1A),
        background = Color(0xFF292929), onBackground = Color(0xFFDEDEDE),
        surface = Color(0xFF292929), onSurface = Color(0xFFDEDEDE),
        surfaceVariant = Color(0xFF343231), onSurfaceVariant = Color(0xFFB0B0B0),
        surfaceContainer = Color(0xFF2E2E2E), surfaceContainerHigh = Color(0xFF343231),
        surfaceContainerHighest = Color(0xFF3D3B3A), surfaceContainerLow = Color(0xFF292929),
        surfaceContainerLowest = Color(0xFF242424), surfaceTint = Color(0xFFFD8EA1),
        outline = Color(0xFF919191), outlineVariant = Color(0xFF474747),
        secondaryContainer = Color(0xFF474747), onSecondaryContainer = Color(0xFFDEDEDE),
        error = Color(0xFFFFB4AB), errorContainer = Color(0xFF5C1D1B), onErrorContainer = Color(0xFFFFDAD6),
    ) else lightColorScheme(
        primary = Color(0xFFB11F4B), onPrimary = Color.White,
        background = Color(0xFFFCFBF8), onBackground = Color(0xFF242424),
        surface = Color(0xFFFCFBF8), onSurface = Color(0xFF242424),
        surfaceVariant = Color(0xFFF7F4EF), onSurfaceVariant = Color(0xFF5C5C5C),
        surfaceContainer = Color(0xFFF7F4EF), surfaceContainerHigh = Color(0xFFF7F4EF),
        surfaceContainerHighest = Color(0xFFF0EDE8), surfaceContainerLow = Color(0xFFFCFBF8),
        surfaceContainerLowest = Color.White, surfaceTint = Color(0xFFB11F4B),
        outline = Color(0xFF6F6F6F), outlineVariant = Color(0xFFDEDEDE),
        secondaryContainer = Color(0xFFF1E8E8), onSecondaryContainer = Color(0xFF242424),
        error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
    val typography = Typography().let {
        it.copy(
            headlineLarge = it.headlineLarge.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Light),
            headlineMedium = it.headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light),
            titleLarge = it.titleLarge.copy(fontSize = 22.sp, lineHeight = 30.sp),
            bodyLarge = it.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
            bodyMedium = it.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp)),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun JournalApp(model: JournalViewModel, canUseLock: () -> Boolean) {
    val context = LocalContext.current
    val allEntries by model.entries.collectAsStateWithLifecycle()
    val state by model.journalState.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val rawDate by model.date.collectAsStateWithLifecycle()
    val date = LocalDate.parse(rawDate)
    val current = allEntries.firstOrNull { it.journalDate == rawDate }
    var text by rememberSaveable(rawDate, current?.revision) {
        mutableStateOf(model.draftText(rawDate, current?.revision, current?.takeIf { it.deletedAt == null }?.text.orEmpty()))
    }
    var variation by rememberSaveable(rawDate, current?.revision) {
        mutableIntStateOf(model.draftVariation(rawDate, current?.revision))
    }
    val prompt = current?.takeIf { it.deletedAt == null }?.promptText ?: Prompts.forDate(date, variation)
    val dirty = text != current?.takeIf { it.deletedAt == null }?.text.orEmpty()
    var settingsScreen by rememberSaveable { mutableStateOf(false) }
    var yearsScreen by rememberSaveable { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingWriter by remember { mutableStateOf(false) }
    var entryMenu by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmPlainExport by remember { mutableStateOf(false) }
    var encryptionDialog by remember { mutableStateOf(false) }
    var disableEncryption by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val writeScroll = rememberLazyListState()
    val yearsScroll = rememberLazyListState()
    val settingsScroll = rememberLazyListState()
    var notificationRevision by remember { mutableIntStateOf(0) }
    val notificationsAllowed = remember(notificationRevision, settingsScreen) { model.graph.reminders.allowed() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationRevision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun navigate(target: LocalDate, openWriter: Boolean = false) {
        if (target != date) {
            if (dirty) {
                pendingDate = target
                pendingWriter = openWriter
            } else {
                model.selectDate(target)
                if (openWriter) yearsScreen = false
            }
        } else if (openWriter) {
            yearsScreen = false
        }
    }

    LaunchedEffect(model.requestedDate) {
        model.requestedDate?.let {
            model.dateRequestHandled()
            settingsScreen = false
            yearsScreen = false
            navigate(it, openWriter = true)
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationRevision++
        if (!granted) model.message("Reminders are configured, but Android notifications are blocked. Enable them in notification settings.")
    }
    val authorization = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        model.authorizationResult(it.data)
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(model::importDocument)
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        if (it == null) model.cancelExport() else model.writeExport(it)
    }
    val exportText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        if (it == null) model.cancelExport() else model.writeExport(it)
    }
    LaunchedEffect(model.pendingAuthorization) {
        model.pendingAuthorization?.let {
            model.authorizationLaunched()
            authorization.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }
    LaunchedEffect(model.exportPrompt) {
        model.exportPrompt?.let {
            model.exportLaunched()
            if (it.readable) exportText.launch(it.name) else exportJson.launch(it.name)
        }
    }
    LaunchedEffect(model.notice) {
        model.notice?.let { snackbar.showSnackbar(it); model.clearNotice() }
    }
    BackHandler(settingsScreen) { settingsScreen = false; notificationRevision++ }
    BackHandler(!settingsScreen && yearsScreen) { yearsScreen = false }
    BackHandler(!settingsScreen && !yearsScreen && dirty) { confirmExit = true }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(title = { Text(if (settingsScreen) "Your journal" else "one line.",
                style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (settingsScreen) TextButton(onClick = { settingsScreen = false; notificationRevision++ }) { Text("Back") }
                },
                actions = {
                    if (!settingsScreen) TextButton(onClick = { settingsScreen = true }) { Text("Settings") }
                })
        },
        bottomBar = {
            if (!settingsScreen) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp)) {
                        HorizontalDivider()
                        TabRow(selectedTabIndex = if (yearsScreen) 1 else 0,
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = {}) {
                            listOf("Write", "Years").forEachIndexed { index, label ->
                                Tab(selected = yearsScreen == (index == 1),
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = {
                                        focus.clearFocus()
                                        keyboard?.hide()
                                        yearsScreen = index == 1
                                    },
                                    text = { Text(label) }, modifier = Modifier.testTag("tab-${label.lowercase()}"))
                            }
                        }
                        state?.let { journal ->
                            TextButton(onClick = { settingsScreen = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(backupSummary(journal), style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("journal-list"),
            state = if (settingsScreen) settingsScroll else if (yearsScreen) yearsScroll else writeScroll,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (model.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            model.error?.let { message ->
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(16.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            TextButton(onClick = model::clearError) { Text("Dismiss") }
                        }
                    }
                }
            }
            if (state == null) {
                item { Text("Opening your journal...") }
            } else if (!settingsScreen) {
                item {
                    DateNavigation(date, !model.busy, ::navigate)
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(top = if (yearsScreen) 8.dp else 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.format(DateTimeFormatter.ofPattern("d MMMM")),
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center, modifier = Modifier.testTag("journal-date").semantics { heading() })
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE, yyyy")),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, modifier = Modifier.testTag("journal-year"))
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(Modifier.width(32.dp), color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (!yearsScreen) {
                    item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(prompt, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            TextField(
                                value = text,
                                onValueChange = { input ->
                                    val line = input.replace(Regex("[\\r\\n]+"), " ")
                                    if (line.length <= MAX_ENTRY_LENGTH) {
                                        text = line
                                        model.keepDraft(rawDate, current?.revision, text, variation)
                                    } else model.message("A line can contain at most $MAX_ENTRY_LENGTH characters. Your existing draft was kept.")
                                },
                                placeholder = { Text("One thought worth keeping", style = MaterialTheme.typography.titleLarge) },
                                textStyle = MaterialTheme.typography.titleLarge,
                                minLines = 3,
                                maxLines = 6,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("journal-editor")
                                    .semantics { contentDescription = "Your journal line" },
                                enabled = !model.busy,
                            )
                            if (dirty || text.length >= 900) Text("${text.length} / $MAX_ENTRY_LENGTH",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                    model.save(date, text, prompt, current?.revision)
                                },
                                enabled = !model.busy && text.isNotBlank() && text.length <= MAX_ENTRY_LENGTH && dirty,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp),
                            ) { Text(if (current?.deletedAt == null && current != null) "Save changes" else "Save line") }
                            Text(
                                if (dirty) "Unsaved changes" else if (current != null && current.deletedAt == null)
                                    "Saved on this phone." else "A short sentence is enough.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            if (current == null || current.deletedAt != null) {
                                TextButton(onClick = {
                                    variation++
                                    model.keepDraft(rawDate, current?.revision, text, variation)
                                }, enabled = !model.busy) { Text("Another cue") }
                            } else {
                                Column {
                                    TextButton(onClick = { entryMenu = true }, enabled = !model.busy) { Text("Entry options") }
                                    DropdownMenu(expanded = entryMenu, onDismissRequest = { entryMenu = false }) {
                                        DropdownMenuItem(text = { Text("Delete line") },
                                            onClick = { entryMenu = false; deleteEntry = current })
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text("This day, through the years", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                    }
                    val memories = JournalFormat.onDay(allEntries, MonthDay.from(date))
                    if (memories.isEmpty()) item {
                        Text("Your past years will appear here. A short line today is a beginning.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(memories, key = { it.journalDate }) { entry ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !model.busy,
                            role = Role.Button, onClickLabel = "View or edit ${entry.journalDate}") {
                            navigate(LocalDate.parse(entry.journalDate), openWriter = true)
                        }.padding(vertical = 16.dp).testTag("year-${entry.journalDate.take(4)}"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.journalDate.take(4), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                        }
                    }
                    if (dirty) item {
                        Text("Your unsaved line is still in Write.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state!!.accountEmail != null && state!!.cloudMessage != "All changes backed up to Google Drive.") {
                    item {
                        TextButton(onClick = { settingsScreen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(state!!.cloudMessage, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                item {
                    Section("Daily writing reminder",
                        if (settings.remindersEnabled) "Around %02d:%02d".format(settings.reminderMinute / 60, settings.reminderMinute % 60)
                        else "Off") {
                        SettingSwitch("Daily reminder", settings.remindersEnabled, !model.busy) { enabled ->
                            model.reminders(enabled, settings.reminderMinute, settings.suppressCompleted)
                            if (enabled && Build.VERSION.SDK_INT >= 33 && !model.graph.reminders.allowed()) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        Text("Around %02d:%02d local time. Android battery settings can delay delivery."
                            .format(settings.reminderMinute / 60, settings.reminderMinute % 60))
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                model.reminders(settings.remindersEnabled, h * 60 + m, settings.suppressCompleted)
                            }, settings.reminderMinute / 60, settings.reminderMinute % 60, true).show()
                        }, enabled = !model.busy) { Text("Change time") }
                        SettingSwitch("Skip if today's line is already written", settings.suppressCompleted, !model.busy) {
                            model.reminders(settings.remindersEnabled, settings.reminderMinute, it)
                        }
                        Text(if (notificationsAllowed) "Android notifications are allowed." else "Android notifications are blocked.",
                            color = if (notificationsAllowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            }) { Text("Notification settings") }
                            OutlinedButton(onClick = model::testReminder, enabled = !model.busy && notificationsAllowed) { Text("Test reminder") }
                        }
                        Text("Reminders include a local writing cue, never an old journal entry. Force-stop and locked Android Private Space prevent normal reminders.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Section("Your Google Drive", backupSummary(state!!), initiallyExpanded = true) {
                        BackupStatus(state!!, settings.encryptionEnabled)
                        Text("Backups live in a visible OneLineJournal folder. The newest 30 snapshots are kept; each contains every year. One active writing phone only.")
                        if (state!!.accountEmail == null) {
                            Button(onClick = model::connect, enabled = !model.busy) { Text("Connect Google Drive") }
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = model::connect, enabled = !model.busy) { Text("Reconnect") }
                                OutlinedButton(onClick = model::backupNow,
                                    enabled = !model.busy && !state!!.restoreRequired) { Text("Back up now") }
                                OutlinedButton(onClick = model::listBackups, enabled = !model.busy) { Text("Restore from Drive") }
                                TextButton(onClick = { confirmDisconnect = true }, enabled = !model.busy) { Text("Disconnect") }
                            }
                        }
                        Text("First-time setup needs an Android OAuth client for this APK's package and signing SHA-1. Enable Drive API and use production consent status for lasting access. See README.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Section("Privacy and recovery",
                        "App lock ${if (settings.appLock) "on" else "off"} / Backup encryption ${if (settings.encryptionEnabled) "on" else "off"}") {
                        SettingSwitch("Lock when leaving the app", settings.appLock, !model.busy) {
                            if (!it || canUseLock()) model.setAppLock(it)
                            else model.message("Set up a device screen lock or supported biometric in Android Settings first.")
                        }
                        Text("Screenshots and app-switcher previews are blocked. App lock does not encrypt the local database.",
                            style = MaterialTheme.typography.bodySmall)
                        SettingSwitch("Encrypt new backup files", settings.encryptionEnabled, !model.busy) {
                            if (it) encryptionDialog = true else disableEncryption = true
                        }
                        Text("Encrypted backups use your recovery passphrase. Keep it outside this phone; Google and this app cannot recover it for you.")
                        if (settings.encryptionEnabled) TextButton(onClick = { encryptionDialog = true }, enabled = !model.busy) {
                            Text("Change recovery passphrase")
                        }
                        Text("Turning encryption on does not erase older plaintext exports or Drive snapshots. Disconnecting Drive does not delete its files.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Section("Export and import", "Backups, readable text, and local recovery") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { model.prepareExport(false) }, enabled = !model.busy) { Text("Export backup JSON") }
                            OutlinedButton(onClick = { confirmPlainExport = true }, enabled = !model.busy) { Text("Export readable text") }
                            OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                                enabled = !model.busy) { Text("Import backup") }
                            OutlinedButton(onClick = model::localRecovery, enabled = !model.busy) { Text("Local recovery copies") }
                        }
                        Text("JSON exports honor backup encryption. Readable text is unencrypted. Imports are validated and previewed; a local safety copy is kept before changes.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                item { Text("No ads, analytics, external AI, or journal server. Your phone and the Google Drive account you choose.",
                    style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    pendingDate?.let { target ->
        ConfirmDialog("Discard unsaved changes?", "Your draft for $date has not been saved.", "Discard and change date",
            onDismiss = { pendingDate = null }, onConfirm = {
                pendingDate = null
                model.selectDate(target)
                if (pendingWriter) yearsScreen = false
            })
    }
    if (confirmExit) ConfirmDialog("Leave without saving?", "Your current line has not been saved.", "Leave without saving",
        onDismiss = { confirmExit = false }, onConfirm = {
            confirmExit = false
            (context as? android.app.Activity)?.finish()
        })
    deleteEntry?.let { entry ->
        ConfirmDialog("Delete this line?", "Delete the entry for ${entry.journalDate}? Older backups and exports may retain the original.",
            "Delete line", onDismiss = { deleteEntry = null },
            onConfirm = { deleteEntry = null; model.delete(entry.journalDate, entry.revision) })
    }
    if (confirmDisconnect) ConfirmDialog("Disconnect Google Drive?",
        "Local entries and existing Drive backups will remain. Automatic uploads will stop.", "Disconnect",
        onDismiss = { confirmDisconnect = false }, onConfirm = { confirmDisconnect = false; model.disconnect() })
    if (confirmPlainExport) ConfirmDialog("Export unencrypted text?",
        "Anyone with access to the exported file can read your journal. Choose a private destination.", "Choose destination",
        onDismiss = { confirmPlainExport = false }, onConfirm = { confirmPlainExport = false; model.prepareExport(true) })
    if (disableEncryption) ConfirmDialog("Stop encrypting new backups?",
        "New cloud backups and JSON exports will be readable by the storage provider. Older encrypted backups still need their original passphrase.",
        "Use unencrypted backups", onDismiss = { disableEncryption = false },
        onConfirm = { disableEncryption = false; model.encryption(null) })
    if (encryptionDialog) PassphraseSetup(
        onDismiss = { encryptionDialog = false },
        onConfirm = { encryptionDialog = false; model.encryption(it) },
    )
    ImportDialogs(model)
}

@Composable
private fun DateNavigation(date: LocalDate, enabled: Boolean, navigate: (LocalDate, Boolean) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { navigate(date.minusDays(1), false) }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Previous day" }) {
            Text("\u2039", style = MaterialTheme.typography.headlineMedium)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = {
                DatePickerDialog(context, { _, y, m, d -> navigate(LocalDate.of(y, m + 1, d), false) },
                    date.year, date.monthValue - 1, date.dayOfMonth).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                    show()
                }
            }, enabled = enabled) { Text("Choose date", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (date != LocalDate.now()) TextButton(onClick = { navigate(LocalDate.now(), false) },
                enabled = enabled) { Text("Today") }
        }
        IconButton(onClick = { navigate(date.plusDays(1), false) }, enabled = enabled && date.isBefore(LocalDate.now()),
            modifier = Modifier.semantics { contentDescription = "Next day" }) {
            Text("\u203a", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

internal fun backupSummary(state: JournalState): String = when {
    state.accountEmail == null -> "On this phone only"
    state.restoreRequired -> "Drive needs attention. Review backups."
    state.generation > state.backedUpGeneration || state.lastBackupAt == null -> "Saved on phone. Drive backup pending."
    state.cloudMessage == "All changes backed up to Google Drive." -> "Drive backup up to date"
    else -> "Drive needs attention. View status."
}

@Composable
private fun BackupStatus(state: JournalState, encrypted: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(state.accountEmail ?: "On this phone only", fontWeight = FontWeight.SemiBold)
        Text(if (state.generation > state.backedUpGeneration && state.folderId != null && !state.restoreRequired)
            "Changes saved locally; Google Drive backup is pending. ${state.cloudMessage}" else state.cloudMessage)
        state.lastBackupAt?.let {
            val time = Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))
            Text("Last successful backup: $time", style = MaterialTheme.typography.bodySmall)
        }
        Text(if (encrypted) "New backup files: encrypted" else "New backup files: not end-to-end encrypted",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, summary: String, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
            onClickLabel = if (expanded) "Collapse $title" else "Expand $title") { expanded = !expanded }
            .padding(vertical = 12.dp).sizeIn(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (expanded) "\u2212" else "+", Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PassphraseSetup(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Protect future backups") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use at least 12 characters. Keep this recovery passphrase somewhere other than this phone.")
                PasswordField("Recovery passphrase", password) { password = it }
                PasswordField("Repeat passphrase", confirmation) { confirmation = it }
                Row {
                    Checkbox(acknowledged, onCheckedChange = { acknowledged = it })
                    Text("I understand that losing this passphrase can permanently lose my encrypted journal.")
                }
            }
        },
        confirmButton = { TextButton(enabled = password.length >= 12 && password == confirmation && acknowledged,
            onClick = { onConfirm(password); password = ""; confirmation = "" }) { Text("Enable encryption") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
}
