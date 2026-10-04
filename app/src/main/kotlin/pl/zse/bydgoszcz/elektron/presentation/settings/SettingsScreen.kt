package pl.zse.bydgoszcz.elektron.presentation.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.width
import pl.zse.bydgoszcz.elektron.crash.openContactEmail
import pl.zse.bydgoszcz.elektron.crash.ReportDialog
import pl.zse.bydgoszcz.elektron.presentation.common.revealWhen
import androidx.compose.foundation.layout.Box
import pl.zse.bydgoszcz.elektron.presentation.common.UpdateActions
import pl.zse.bydgoszcz.elektron.presentation.common.BackgroundWork
import pl.zse.bydgoszcz.elektron.crash.CrashReport
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import pl.zse.bydgoszcz.elektron.presentation.common.GroupedSection
import pl.zse.bydgoszcz.elektron.presentation.common.LargeTitleBar
import pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls
import pl.zse.bydgoszcz.elektron.presentation.common.pressable

private const val REPO_URL = "https://github.com/stasolejnik/elektron"
private const val PRIVACY_URL = "https://github.com/stasolejnik/elektron/blob/main/PRYWATNOSC.md"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenGroups: () -> Unit = {},
    onOpenSubjects: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    // Tryb dewelopera.
    val devMode by viewModel.devMode.collectAsStateWithLifecycle()
    val notesViewModel: pl.zse.bydgoszcz.elektron.presentation.timetable.LessonNotesViewModel = hiltViewModel()
    val notesState by notesViewModel.state.collectAsStateWithLifecycle()
    var showNotes by remember { mutableStateOf(false) }
    if (showNotes) pl.zse.bydgoszcz.elektron.presentation.timetable.LessonNotesLibrary(
        notesState.notes.filter { it.classId == state.selectedClassId }, notesViewModel) { showNotes = false }
    val noteError by notesViewModel.settingsError.collectAsStateWithLifecycle()
    val devMessage by viewModel.devMessage.collectAsStateWithLifecycle()
    val toastContext = LocalContext.current
    var toast by remember { mutableStateOf<Toast?>(null) }
    val showToast: (String) -> Unit = { text ->
        toast?.cancel()
        toast = Toast.makeText(toastContext, text, Toast.LENGTH_SHORT).also { it.show() }
    }
    LaunchedEffect(noteError) { noteError?.let { showToast(it); notesViewModel.settingsError.value = null } }
    LaunchedEffect(devMessage) {
        devMessage?.let { showToast(it); viewModel.consumeDevMessage() }
    }
    var logoTaps by remember { mutableIntStateOf(0) }
    var lastLogoTap by remember { mutableLongStateOf(0L) }
    val onLogoTap: () -> Unit = {
        val t = SystemClock.uptimeMillis()
        logoTaps = if (t - lastLogoTap < 700) logoTaps + 1 else 1
        lastLogoTap = t
        if (!devMode && logoTaps >= 5) {
            viewModel.setDevMode(true)
            logoTaps = 0
            showToast("Tryb dewelopera włączony.")
        }
    }
    var confirmCrash by remember { mutableStateOf(false) }
    if (confirmCrash) {
        AlertDialog(
            onDismissRequest = { confirmCrash = false },
            title = { Text("Symulować awarię?") },
            text = { Text("Aplikacja zamknie się z błędem. Po ponownym uruchomieniu pojawi się okno raportu (kopiowanie logów, e-mail, GitHub).") },
            confirmButton = { TextButton(onClick = { confirmCrash = false; viewModel.devCrash() }) { Text("Zamknij z błędem") } },
            dismissButton = { TextButton(onClick = { confirmCrash = false }) { Text("Anuluj") } }
        )
    }

    val feedbackReport by viewModel.feedbackReport.collectAsStateWithLifecycle()
    feedbackReport?.let { report ->
        ReportDialog(
            title = "Zgłoś problem",
            intro = "Opisz w wiadomości, co nie działa. Raport techniczny poniżej zostanie dołączony.",
            report = report,
            issueUrl = CrashReport.feedbackUrl(
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.FLAVOR,
                Build.VERSION.RELEASE ?: "?", Build.VERSION.SDK_INT, "${Build.MANUFACTURER} ${Build.MODEL}"
            ),
            onDismiss = viewModel::closeFeedback
        )
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val ctx = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LargeTitleBar(title = "Ustawienia", scrollBehavior = scrollBehavior) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).revealWhen(state.ready),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                GroupedSection("Wygląd") {
                    SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val options = listOf(ThemeMode.SYSTEM to "Auto", ThemeMode.LIGHT to "Jasny", ThemeMode.DARK to "Ciemny")
                        options.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                icon = {}
                            ) { Text(label) }
                        }
                    }
                    // Kolory akcentu zawsze widoczne. Przy kolorach z tapety żaden nie jest zaznaczony,
                    // a wybór koloru wyłącza kolory z tapety (dawniej wybór się chował).
                    RowDivider()
                    val wallpaper = state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    AccentPicker(state.accent, wallpaperActive = wallpaper) {
                        viewModel.setAccent(it)
                        if (wallpaper) viewModel.setDynamicColor(false)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        RowDivider()
                        SwitchRow("Kolory z tapety", state.dynamicColor) { viewModel.setDynamicColor(it) }
                    }
                }
            }

            item {
                val look = state.look
                GroupedSection("Wygląd planu") {
                    ActionRow("Nazwy i kolory przedmiotów", trailingChevron = true, onClick = onOpenSubjects)
                    RowDivider()
                    SwitchRow("Pokazuj salę", look.showRoom) { viewModel.setTimetableLook(look.copy(showRoom = it)) }
                    RowDivider()
                    SwitchRow("Pokazuj nauczyciela", look.showTeacher) { viewModel.setTimetableLook(look.copy(showTeacher = it)) }
                }
            }

            item {
                GroupedSection("Strona główna") {
                    SwitchRow("Następna lekcja", state.showNextLesson) { viewModel.setShowNextLesson(it) }
                    RowDivider()
                    SwitchRow("Zastępstwa", state.showSubstitutions) { viewModel.setShowSubstitutions(it) }
                    RowDivider()
                    SwitchRow("Ogłoszenia", state.showAnnouncements) { viewModel.setShowAnnouncements(it) }
                }
            }

            item { WidgetsSection(state.widgetLook) { viewModel.setWidgetLook(it) } }

            item { StartScreenSection(state.startScreen) { viewModel.setStartScreen(it) } }

            item {
                GroupedSection("Powiadomienia") {
                    SwitchRow("Nowe zastępstwa", state.notifSubs) { viewModel.setNotifSubs(it) }
                    RowDivider()
                    SwitchRow("Nowe ogłoszenia", state.notifAnn) { viewModel.setNotifAnn(it) }
                }
            }

            item {
                val reminderStatus by viewModel.reminderStatus.collectAsStateWithLifecycle()
                RemindersSection(state.reminder, reminderStatus, viewModel::refreshReminderStatus) { viewModel.setReminder(it) }
            }

            item {
                NoteRemindersSection(notesState.enabled, notesState.ready && !notesState.error, notesState.timing, notesViewModel::setReminderTiming, notesViewModel::refreshReminders, notesViewModel::setReminders) { showNotes = true }
            }

            item { QuietHoursSection(state.quiet) { viewModel.setQuietHours(it) } }

            item {
                // Stan sprawdzany przy każdym powrocie na ekran - użytkownik zmienia go
                // w ustawieniach systemu i wraca tutaj.
                var unrestricted by remember { mutableStateOf(BackgroundWork.isUnrestricted(ctx)) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) unrestricted = BackgroundWork.isUnrestricted(ctx)
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                GroupedSection(
                    "Działanie w tle",
                    footer = if (unrestricted)
                        "Widżety i powiadomienia działają bez ograniczeń."
                    else
                        "Telefon może usypiać aplikację w tle - widżety przestają się wtedy odświeżać, " +
                            "a powiadomienia przychodzą z opóźnieniem. Ustaw eLektron na „Bez ograniczeń”."
                ) {
                    if (unrestricted) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Optymalizacja baterii", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text("Wyłączona", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        ActionRow("Wyłącz optymalizację baterii", trailingChevron = true) {
                            BackgroundWork.openSettings(ctx)
                        }
                        RowDivider()
                        ActionRow("Poradnik dla Twojego telefonu", trailingIcon = true) {
                            SafeUrls.open(ctx, BackgroundWork.GUIDE_URL)
                        }
                    }
                }
            }

            item { ClassSection(state.classes, state.selectedClassId) { viewModel.setClass(it) } }

            item {
                GroupedSection("Grupy zajęciowe", footer = "Wybierz swoje grupy (np. językowe, zajęcia praktyczne) - w planie i na stronie głównej zobaczysz tylko swoje lekcje.") {
                    ActionRow("Wybierz swoje grupy", trailingChevron = true, onClick = onOpenGroups)
                }
            }

            item {
                var confirmReset by remember { mutableStateOf(false) }
                GroupedSection("Dane", footer = "Pobiera plan, zastępstwa i ogłoszenia od nowa. Klasa, grupy i ustawienia zostają.") {
                    ActionRow("Wyczyść dane podręczne", destructive = true) { confirmReset = true }
                }
                if (confirmReset) {
                    AlertDialog(
                        onDismissRequest = { confirmReset = false },
                        title = { Text("Wyczyścić dane podręczne?") },
                        text = { Text("Lokalna kopia planu, zastępstw i ogłoszeń zostanie usunięta i pobrana od nowa ze strony szkoły.") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.resetCache(); confirmReset = false }) {
                                Text("Wyczyść", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Anuluj") } }
                    )
                }
            }

            // Aktualizacje (GitHub Releases) - osobna sekcja tuż nad "O aplikacji"; nie w wersji F-Droid.
            if (BuildConfig.UPDATE_CHECK) {
                item {
                    GroupedSection("Aktualizacje") {
                        ActionRow("Sprawdź aktualizacje") { viewModel.checkForUpdates() }
                        when (val st = updateStatus) {
                            SettingsViewModel.UpdateStatus.Idle -> Unit
                            SettingsViewModel.UpdateStatus.Checking -> UpdateNote("Sprawdzam…")
                            SettingsViewModel.UpdateStatus.UpToDate -> UpdateNote("Masz najnowszą wersję (${BuildConfig.VERSION_NAME}).")
                            SettingsViewModel.UpdateStatus.Failed -> UpdateNote("Nie udało się sprawdzić - brak połączenia z GitHubem.")
                            is SettingsViewModel.UpdateStatus.Available -> {
                                UpdateNote("Dostępna wersja ${st.update.versionName}.")
                                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                    UpdateActions(st.update, onLater = null)
                                }
                            }
                        }
                    }
                }
            }

            item {
                GroupedSection("O aplikacji") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onLogoTap
                                )
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("eLektron", style = MaterialTheme.typography.titleLarge)
                        Text("Wersja ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(aboutText(linkColor),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    RowDivider()
                    ActionRow("Kod źródłowy na GitHubie", trailingIcon = true) {
                        SafeUrls.open(ctx, REPO_URL)
                    }
                    RowDivider()
                    // Raport z logami: kopiowanie, e-mail do dewelopera albo zgłoszenie na GitHubie.
                    ActionRow("Zgłoś problem") { viewModel.openFeedback() }
                    RowDivider()
                    ActionRow(CrashReport.CONTACT_EMAIL, trailingIcon = true) {
                        if (!openContactEmail(ctx)) SafeUrls.open(ctx, REPO_URL)
                    }
                    RowDivider()
                    ActionRow("Polityka prywatności", trailingIcon = true) {
                        SafeUrls.open(ctx, PRIVACY_URL)
                    }
                }
            }

            if (devMode) {
                item {
                    GroupedSection(
                        "Tryb dewelopera",
                        footer = "Symulacje są widoczne tylko na tym telefonie: zastępstwo trafia na najbliższą lekcję " +
                            "(plan, zastępstwa, strona główna, widżety) i wysyła powiadomienie. Znikają po " +
                            "„Usuń symulacje” albo przy synchronizacji tego dnia ze stroną szkoły."
                    ) {
                        ActionRow("Symuluj zastępstwo") { viewModel.devSimulateSubstitution(freed = false) }
                        RowDivider()
                        ActionRow("Symuluj odwołanie lekcji") { viewModel.devSimulateSubstitution(freed = true) }
                        RowDivider()
                        ActionRow("Symuluj ogłoszenie") { viewModel.devSimulateAnnouncement() }
                        RowDivider()
                        ActionRow("Usuń symulacje") { viewModel.devClearSimulations() }
                        RowDivider()
                        ActionRow("Raport błędu (bez awarii)") { viewModel.openFeedback() }
                        RowDivider()
                        ActionRow("Symuluj awarię aplikacji") { confirmCrash = true }
                        RowDivider()
                        ActionRow("Wyłącz tryb dewelopera") { viewModel.setDevMode(false) }
                    }
                }
            }

            // Licencja: plakietka GPLv3 (dotknięcie otwiera tekst licencji).
            item {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.gpl_v3),
                        contentDescription = "Licencja GNU GPL w wersji 3 lub nowszej - otwórz tekst licencji",
                        modifier = Modifier.width(112.dp).pressable { SafeUrls.open(ctx, LICENSE_URL) }
                    )
                }
            }
        }
    }
}

private fun aboutText(linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        append("Nieoficjalny odpowiednik aplikacji ")
        link("Atom", "https://github.com/kacpergorka/atom", linkColor)
        append(" autorstwa ")
        link("Kacpra Górki", "https://github.com/kacpergorka", linkColor)
        append(", przeznaczony na Androida. Projekt niezależny, niepowiązany z ZSE w Bydgoszczy ani z firmą VULCAN. ")
        append("Kod aplikacji napisało bezduszne AI, głównie Claude (Anthropic).")
    }

private fun AnnotatedString.Builder.link(text: String, url: String, color: Color) {
    val start = length
    append(text)
    addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), start, length)
    addLink(LinkAnnotation.Url(url), start, length)
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent,
                // outline, nie outlineVariant: wyłączony przełącznik był prawie niewidoczny na białej karcie.
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                uncheckedThumbColor = Color.White
            ),
            thumbContent = null
        )
    }
}

@Composable
internal fun ActionRow(
    label: String,
    destructive: Boolean = false,
    trailingIcon: Boolean = false,
    trailingChevron: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (trailingIcon) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (trailingChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun UpdateNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
}
