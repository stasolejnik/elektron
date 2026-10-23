package pl.zse.bydgoszcz.elektron.presentation.settings

import androidx.compose.foundation.selection.toggleable

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
import androidx.compose.foundation.layout.heightIn
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
import pl.zse.bydgoszcz.elektron.domain.model.UpdateChannel
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.togetherWith

private const val REPO_URL = "https://github.com/stasolejnik/elektron"
private const val PRIVACY_URL = "https://github.com/stasolejnik/elektron/blob/main/PRYWATNOSC.md"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** Kategorie Ustawień - każda ma własną stronę. Kolejność: co dostajesz, co widzisz, sprawy techniczne. */
internal enum class SettingsPage(val title: String) {
    NOTIFICATIONS("Powiadomienia"), HOME("Strona główna"), LOOK("Wygląd"), PLAN("Plan lekcji"),
    TRANSIT("Przejazdy"), BAR("Pasek nawigacji"), WIDGETS("Widżety"),
    BACKGROUND("Praca w tle"), UPDATES("Aktualizacje"), DATA("Dane"), ABOUT("O aplikacji"), DEV("Tryb dewelopera")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenGroups: () -> Unit = {},
    onOpenSubjects: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    transitViewModel: pl.zse.bydgoszcz.elektron.presentation.transit.TransitViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val transitSettings by transitViewModel.settings.collectAsStateWithLifecycle()
    var transitPicker by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    if (transitPicker != 0) pl.zse.bydgoszcz.elektron.presentation.transit.StopPicker(transitViewModel, origin = transitPicker == 2) { transitPicker = 0 }
    // Tryb dewelopera.
    val devMode by viewModel.devMode.collectAsStateWithLifecycle()
    val notesViewModel: pl.zse.bydgoszcz.elektron.presentation.timetable.LessonNotesViewModel = hiltViewModel()
    val notesState by notesViewModel.state.collectAsStateWithLifecycle()
    var showNotes by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (showNotes) pl.zse.bydgoszcz.elektron.presentation.timetable.LessonNotesLibrary(
        notesState.notes.filter { it.classId == state.selectedClassId }, notesViewModel) { showNotes = false }
    val noteError by notesViewModel.settingsError.collectAsStateWithLifecycle()
    val settingError by viewModel.settingError.collectAsStateWithLifecycle()
    val devMessage by viewModel.devMessage.collectAsStateWithLifecycle()
    val toastContext = LocalContext.current
    var toast by remember { mutableStateOf<Toast?>(null) }
    val showToast: (String) -> Unit = { text ->
        toast?.cancel()
        toast = Toast.makeText(toastContext, text, Toast.LENGTH_SHORT).also { it.show() }
    }
    LaunchedEffect(settingError) { settingError?.let { showToast(it); viewModel.settingError.value = null } }
    LaunchedEffect(noteError) { noteError?.let { showToast(it); notesViewModel.settingsError.value = null } }
    LaunchedEffect(devMessage) {
        devMessage?.let { showToast(it); viewModel.consumeDevMessage() }
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

    // Otwarta kategoria (null - lista kategorii). Wstecz wraca do listy - tylko na widocznej zakładce,
    // inaczej przechwytywałoby cofanie na sąsiedniej (pager trzyma ją w pamięci).
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    if (page == SettingsPage.DEV && !devMode) page = null
    val visible = pl.zse.bydgoszcz.elektron.presentation.common.LocalScreenVisible.current
    androidx.activity.compose.BackHandler(enabled = page != null && visible) { page = null }

    // Lista kategorii pamięta przewinięcie i zwinięty tytuł - po powrocie ze strony kategorii jest tam, gdzie była.
    val homeList = androidx.compose.foundation.lazy.rememberLazyListState()
    val homeBar = androidx.compose.material3.rememberTopAppBarState()
    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { if (forward) it / 3 else -it / 3 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200))) togetherWith
                (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(260)) { if (forward) -it / 3 else it / 3 } +
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
        },
        label = "settings-page"
    ) { current ->
        if (current == null) SettingsHome(state, transitSettings.preferences, devMode, viewModel::setClass, onOpenGroups, homeList, homeBar) { page = it }
        else SettingsSubpage(current.title, onBack = { page = null }) {
            when (current) {
                SettingsPage.NOTIFICATIONS -> {
                    item(key = "notifications") {
                        NotificationsSection(state.notifSubs, state.notifAnn, state.quiet,
                            onSubs = viewModel::setNotifSubs, onAnnouncements = viewModel::setNotifAnn, onQuiet = viewModel::setQuietHours,
                            live = state.liveNotifications, onLive = { viewModel.setLiveNotifications(it) },
                            onLiveResume = { viewModel.refreshLiveNotifications() })
                    }
                    item(key = "reminders") {
                        val reminderStatus by viewModel.reminderStatus.collectAsStateWithLifecycle()
                        RemindersSection(state.reminder, reminderStatus, viewModel::refreshReminderStatus) { viewModel.setReminder(it) }
                    }
                    item(key = "notes") {
                        NoteRemindersSection(notesState.enabled, notesState.ready && !notesState.error, notesState.timing, notesViewModel::setReminderTiming, notesViewModel::refreshReminders, notesViewModel::setReminders) { showNotes = true }
                    }
                }
                SettingsPage.HOME -> item(key = "home") {
                    HomeSection(state.startScreen, transitTab = transitSettings.preferences.visible, onChange = viewModel::setStartScreen) {
                        SwitchRow("Następna lekcja", state.showNextLesson) { viewModel.setShowNextLesson(it) }
                        RowDivider()
                        SwitchRow("Czas do końca lekcji", state.showDayEnd,
                            supporting = "Ile godzin i minut zostało do końca lekcji dzisiaj - na stronie głównej i w widżecie Plan dnia") { viewModel.setShowDayEnd(it) }
                        RowDivider()
                        SwitchRow("Zastępstwa", state.showSubstitutions) { viewModel.setShowSubstitutions(it) }
                        RowDivider()
                        SwitchRow("Najnowsze ogłoszenia", state.showAnnouncements) { viewModel.setShowAnnouncements(it) }
                        RowDivider()
                        SwitchRow("Przejazdy", transitSettings.preferences.showOnDashboard,
                            enabled = transitSettings.ready && !transitSettings.failed,
                            supporting = "Dojazd rano i powrót po lekcjach (Ustawienia -> Przejazdy)") { transitViewModel.setShowOnDashboard(it) }
                    }
                }
                SettingsPage.LOOK -> {
                    item(key = "look") {
                        GroupedSection("Wygląd", icon = Icons.Outlined.Palette,
                            footer = "Kolor akcentu i kolor tła dotyczą też widżetów. Kolor tła zmienia tło i karty aplikacji.") {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                            // Kolory akcentu i wyglądy zawsze widoczne. Przy kolorach z tapety żaden nie jest
                            // zaznaczony, a wybór wyłącza kolory z tapety.
                            RowDivider()
                            val wallpaper = state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            AccentPicker(state.accent, wallpaperActive = wallpaper) {
                                viewModel.setAccent(it)
                                if (wallpaper) viewModel.setDynamicColor(false)
                            }
                            RowDivider()
                            LookPicker(state.baseLook, state.accent, wallpaperActive = wallpaper) {
                                viewModel.setLook(it)
                                if (wallpaper) viewModel.setDynamicColor(false)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                RowDivider()
                                SwitchRow("Kolory z tapety", state.dynamicColor, supporting = "Material You zamiast koloru akcentu i koloru tła") { viewModel.setDynamicColor(it) }
                            }
                        }
                    }
                    item(key = "icon") { GroupedSection("Ikona aplikacji") { AppIconRow() } }
                }
                SettingsPage.PLAN -> item(key = "plan") {
                    val look = state.look
                    GroupedSection("Plan lekcji", icon = Icons.Outlined.CalendarMonth) {
                        ActionRow("Nazwy i kolory przedmiotów", trailingChevron = true, onClick = onOpenSubjects)
                        RowDivider()
                        SwitchRow("Pokazuj salę", look.showRoom) { viewModel.setTimetableLook(look.copy(showRoom = it)) }
                        RowDivider()
                        SwitchRow("Pokazuj nauczyciela", look.showTeacher) { viewModel.setTimetableLook(look.copy(showTeacher = it)) }
                    }
                }
                SettingsPage.TRANSIT -> item(key = "transit") {
                    val transitSaving by transitViewModel.saving.collectAsStateWithLifecycle()
                    val transitError by transitViewModel.settingsError.collectAsStateWithLifecycle()
                    TransitSection(transitSettings, transitSaving, transitError,
                        onTab = { transitViewModel.setTabVisible("transit", it) },
                        onCard = transitViewModel::setShowOnDashboard,
                        onTransfers = transitViewModel::setAllowTransfers,
                        onPick = { origin -> transitViewModel.query.value = ""; transitPicker = if (origin) 2 else 1 })
                }
                SettingsPage.BAR -> item(key = "bar") { pl.zse.bydgoszcz.elektron.presentation.transit.TransitVisibilitySection(transitViewModel) }
                SettingsPage.WIDGETS -> item(key = "widgets") { WidgetsSection(state.widgetLook) { viewModel.setWidgetLook(it) } }
                SettingsPage.BACKGROUND -> item(key = "background") { BackgroundSection() }
                SettingsPage.UPDATES -> item(key = "updates") { UpdatesSection(state, viewModel) }
                SettingsPage.DATA -> item(key = "data") { DataSection(viewModel) }
                SettingsPage.ABOUT -> item(key = "about") { AboutSection(viewModel, devMode, showToast) }
                SettingsPage.DEV -> item(key = "dev") { DevSection(viewModel) }
            }
        }
    }
}

/** Strona kategorii: duży tytuł ze strzałką wstecz i lista sekcji. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubpage(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTitleBar(title = title, scrollBehavior = scrollBehavior, navigationIcon = {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć do ustawień")
                }
            })
        }
    ) { padding ->
        androidx.compose.runtime.CompositionLocalProvider(pl.zse.bydgoszcz.elektron.presentation.common.LocalSectionPageTitle provides title) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                content = content
            )
        }
    }
}

/** Lista kategorii: na górze klasa i grupy (od nich zależy wszystko inne), pod nimi kategorie ze stanem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsHome(
    state: SettingsViewModel.State,
    transit: pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences,
    devMode: Boolean,
    onSelectClass: (String) -> Unit,
    onOpenGroups: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    barState: androidx.compose.material3.TopAppBarState = androidx.compose.material3.rememberTopAppBarState(),
    onOpen: (SettingsPage) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
    val ctx = LocalContext.current
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LargeTitleBar(title = "Ustawienia", scrollBehavior = scrollBehavior) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).revealWhen(state.ready),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Klasa i grupy osobno, na samej górze - od nich zależy wszystko inne.
            item(key = "class") { ClassCard(state.classes, state.selectedClassId, onSelect = onSelectClass, onOpenGroups = onOpenGroups) }
            item(key = "main") {
                GroupedSection(null) {
                    val notifications = listOfNotNull(
                        "zastępstwa".takeIf { state.notifSubs }, "ogłoszenia".takeIf { state.notifAnn },
                        "na żywo".takeIf { state.liveNotifications },
                        "przypomnienia".takeIf { state.reminder.mode != pl.zse.bydgoszcz.elektron.domain.model.ReminderMode.OFF })
                    CategoryRow(Icons.Outlined.Notifications, "Powiadomienia",
                        notifications.joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Wyłączone" }) { onOpen(SettingsPage.NOTIFICATIONS) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.Home, "Strona główna",
                        "Start: " + startScreenLabel(state.startScreen, transit.visible).lowercase()) { onOpen(SettingsPage.HOME) }
                    CategoryDivider()
                    val wallpaper = state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val theme = when (state.themeMode) { ThemeMode.SYSTEM -> "Auto"; ThemeMode.LIGHT -> "Jasny"; ThemeMode.DARK -> "Ciemny" }
                    CategoryRow(Icons.Outlined.Palette, "Wygląd",
                        if (wallpaper) "$theme · kolory z tapety"
                        else "$theme · ${ACCENT_LABELS[state.accent.color].orEmpty().lowercase()} · ${LOOK_LABELS[state.baseLook.look].orEmpty().lowercase()}") { onOpen(SettingsPage.LOOK) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.CalendarMonth, "Plan lekcji", "Nazwy i kolory przedmiotów, sala, nauczyciel") { onOpen(SettingsPage.PLAN) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.DirectionsBus, "Przejazdy",
                        if (transit.visible || transit.showOnDashboard) transit.destination?.name ?: "Wybierz swój przystanek" else "Wyłączone") { onOpen(SettingsPage.TRANSIT) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.SpaceDashboard, "Pasek nawigacji", "Widoczne zakładki i kolejność") { onOpen(SettingsPage.BAR) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.Widgets, "Widżety",
                        "Przezroczystość ${100 - state.widgetLook.opacity}%") { onOpen(SettingsPage.WIDGETS) }
                }
            }
            item(key = "system") {
                GroupedSection(null) {
                    var unrestricted by remember { mutableStateOf(pl.zse.bydgoszcz.elektron.presentation.common.BackgroundWork.isUnrestricted(ctx)) }
                    OnResume { unrestricted = pl.zse.bydgoszcz.elektron.presentation.common.BackgroundWork.isUnrestricted(ctx) }
                    CategoryRow(Icons.Outlined.BatteryChargingFull, "Praca w tle",
                        if (unrestricted) "Bez ograniczeń" else "Telefon może usypiać aplikację", warning = !unrestricted) { onOpen(SettingsPage.BACKGROUND) }
                    if (BuildConfig.UPDATE_CHECK) {
                        CategoryDivider()
                        CategoryRow(Icons.Outlined.SystemUpdate, "Aktualizacje",
                            state.updateChannel.label + if (state.notifyUpdates) " · z powiadomieniami" else "") { onOpen(SettingsPage.UPDATES) }
                    }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.Storage, "Dane", "Wyczyść dane podręczne") { onOpen(SettingsPage.DATA) }
                    CategoryDivider()
                    CategoryRow(Icons.Outlined.Info, "O aplikacji", "Wersja ${BuildConfig.VERSION_NAME} · zgłoś problem") { onOpen(SettingsPage.ABOUT) }
                    if (devMode) {
                        CategoryDivider()
                        CategoryRow(Icons.Outlined.Code, "Tryb dewelopera", "Symulacje i czas") { onOpen(SettingsPage.DEV) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String,
    warning: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(onClickLabel = "Otwórz: $title", onClick = onClick)
            .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Jeden spokojny kolor (tło wyróżnienia w kolorze akcentu) zamiast osobnej barwy dla każdej kategorii.
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(colors.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                fontWeight = if (warning) FontWeight.SemiBold else null,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

/** Linia między kategoriami - od tekstu, nie od kafelka ikony (jak w iOS). */
@Composable
private fun CategoryDivider() {
    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun UpdatesSection(state: SettingsViewModel.State, viewModel: SettingsViewModel) {
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    var choosingChannel by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> denied = !granted; if (granted) viewModel.setNotifyUpdates(true) }
    GroupedSection("Aktualizacje", icon = Icons.Outlined.SystemUpdate,
        footer = if (state.updateChannel == UpdateChannel.BETA)
            "Wersje beta są testowe i mogą zawierać błędy. Gdy ukaże się wersja stabilna, też ją dostaniesz."
        else null) {
        ValueRow("Kanał", state.updateChannel.label) { choosingChannel = true }
        RowDivider()
        SwitchRow("Powiadamiaj o nowej wersji", state.notifyUpdates,
            supporting = "Sprawdzanie w tle najwyżej co 12 godzin, jedno powiadomienie na wersję") { enable ->
            val needsPermission = enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (needsPermission) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else viewModel.setNotifyUpdates(enable)
        }
        if (denied) Text("Bez zgody na powiadomienia o nowej wersji dowiesz się z baneru na stronie głównej.",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RowDivider()
        ActionRow("Sprawdź aktualizacje", enabled = updateStatus != SettingsViewModel.UpdateStatus.Checking) { viewModel.checkForUpdates() }
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
    if (choosingChannel) {
        ChoiceDialog("Kanał aktualizacji",
            listOf(UpdateChannel.STABLE to "Stabilne (zalecane)", UpdateChannel.BETA to "Beta - wersje testowe i stabilne"),
            state.updateChannel,
            onSelect = { viewModel.setUpdateChannel(it); choosingChannel = false },
            onDismiss = { choosingChannel = false })
    }
}

@Composable
private fun DataSection(viewModel: SettingsViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    GroupedSection("Dane", icon = Icons.Outlined.Storage,
        footer = "Pobiera plan, zastępstwa i ogłoszenia od nowa. Klasa, grupy, notatki i ustawienia zostają.") {
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

@Composable
private fun AboutSection(viewModel: SettingsViewModel, devMode: Boolean, showToast: (String) -> Unit) {
    val ctx = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
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
    GroupedSection("O aplikacji", icon = Icons.Outlined.Info) {
        Column(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
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
            Spacer(Modifier.height(10.dp))
            Text("eLektron", style = MaterialTheme.typography.titleLarge)
            Text("Wersja ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(aboutText(linkColor),
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        RowDivider()
        // Raport z logami: kopiowanie, e-mail do dewelopera albo zgłoszenie na GitHubie.
        ActionRow("Zgłoś problem", trailingChevron = true) { viewModel.openFeedback() }
        RowDivider()
        ActionRow("Napisz do autora", supporting = CrashReport.CONTACT_EMAIL, trailingIcon = true) {
            if (!openContactEmail(ctx)) SafeUrls.open(ctx, REPO_URL)
        }
        RowDivider()
        ActionRow("Kod źródłowy na GitHubie", trailingIcon = true) { SafeUrls.open(ctx, REPO_URL) }
        RowDivider()
        ActionRow("Polityka prywatności", trailingIcon = true) { SafeUrls.open(ctx, PRIVACY_URL) }
        RowDivider()
        // Licencja: plakietka GPLv3 (dotknięcie otwiera tekst licencji).
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.gpl_v3),
                contentDescription = "Licencja GNU GPL w wersji 3 lub nowszej - otwórz tekst licencji",
                modifier = Modifier.width(112.dp).pressable { SafeUrls.open(ctx, LICENSE_URL) }
            )
        }
    }
}

@Composable
private fun DevSection(viewModel: SettingsViewModel) {
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
    val devDialog by viewModel.devDialog.collectAsStateWithLifecycle()
    DevSimulationDialogs(devDialog,
        onSubstitution = viewModel::devSimulateSubstitution,
        onAnnouncement = viewModel::devSimulateAnnouncement,
        onCustomSubstitution = { viewModel.devDialog.value = SettingsViewModel.DevDialog.CustomSubstitution },
        onCustomAnnouncement = { viewModel.devDialog.value = SettingsViewModel.DevDialog.CustomAnnouncement },
        onDismiss = { viewModel.devDialog.value = null })
    val offset by viewModel.simulatedOffset.collectAsStateWithLifecycle()
    GroupedSection(
        "Tryb dewelopera",
        icon = Icons.Outlined.Code,
        footer = "Symulacje są widoczne tylko na tym telefonie. Zastępstwo (własne albo wzorowane na zapisanym, " +
            "także minionym) trafia na najbliższą lekcję - do planu, zastępstw, strony głównej i widżetów - " +
            "i wysyła powiadomienie; tak samo ogłoszenie. Znikają po „Usuń symulacje” albo przy synchronizacji. " +
            "Symulowany czas dalej płynie; synchronizacja, przypomnienia i powiadomienia zostają w prawdziwym czasie."
    ) {
        DevTimeRows(offset, onSimulate = viewModel::simulateTime, onReset = viewModel::resetSimulatedTime)
        RowDivider()
        ActionRow("Symuluj zastępstwo…", trailingChevron = true) { viewModel.devOpenSubstitution() }
        RowDivider()
        ActionRow("Symuluj ogłoszenie…", trailingChevron = true) { viewModel.devOpenAnnouncement() }
        RowDivider()
        ActionRow("Usuń symulacje") { viewModel.devClearSimulations() }
        RowDivider()
        ActionRow("Odśwież widżety") { viewModel.devRefreshWidgets() }
        RowDivider()
        ActionRow("Raport błędu (bez awarii)") { viewModel.openFeedback() }
        RowDivider()
        ActionRow("Symuluj awarię aplikacji", destructive = true) { confirmCrash = true }
        RowDivider()
        ActionRow("Wyłącz tryb dewelopera") { viewModel.setDevMode(false) }
    }
}

private fun aboutText(linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        append("Nieoficjalny odpowiednik aplikacji ")
        link("Atom", "https://github.com/kacpergorka/atom", linkColor)
        append(" autorstwa ")
        link("Kacpra Górki", "https://github.com/kacpergorka", linkColor)
        append(", przeznaczony na Androida. Projekt niezależny, niepowiązany z ZSE w Bydgoszczy ani z firmą VULCAN. ")
        append("Kod aplikacji napisało bezduszne AI, głównie Claude (Anthropic) i ChatGPT (OpenAI).")
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

/** Etykieta wiersza z opcjonalnym opisem pod spodem (zamiast długich stopek pod sekcją). */
@Composable
private fun RowLabel(label: String, supporting: String?, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
        supporting?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(Modifier.toggleable(value = checked, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange))
            // Obszar dotyku co najmniej 52 dp (dawniej ok. 44 dp: 32 dp przełącznika + marginesy).
            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowLabel(label, supporting,
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
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

/**
 * Wiersz-akcja. Przejście dalej (strzałka) ma zwykły kolor tekstu, czynność - kolor akcentu,
 * nieodwracalna - czerwony; link do strony - ikona otwarcia na końcu.
 */
@Composable
internal fun ActionRow(
    label: String,
    destructive: Boolean = false,
    trailingIcon: Boolean = false,
    trailingChevron: Boolean = false,
    supporting: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = when {
            !enabled -> colors.onSurfaceVariant
            destructive -> colors.error
            trailingChevron || trailingIcon -> colors.onSurface
            else -> colors.primary
        }
        RowLabel(label, supporting, color, Modifier.weight(1f).padding(end = 8.dp))
        if (trailingIcon) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (trailingChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun UpdateNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
}

@Composable
internal fun SystemNotificationStatus() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!allowed) {
        Text("Android blokuje powiadomienia eLektrona. Zmiana opcji poniżej nie odblokuje ich w systemie.",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        // Z zapasowym ekranem aplikacji - bez awarii na systemach bez ustawień powiadomień.
        ActionRow("Otwórz ustawienia powiadomień", trailingIcon = true) { openNotificationSettings(context) }
        RowDivider()
    }
}
