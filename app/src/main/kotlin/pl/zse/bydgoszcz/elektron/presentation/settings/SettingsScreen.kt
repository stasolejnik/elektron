package pl.zse.bydgoszcz.elektron.presentation.settings


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import android.widget.Toast
import pl.zse.bydgoszcz.elektron.crash.ReportDialog
import pl.zse.bydgoszcz.elektron.crash.CrashReport
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.zse.bydgoszcz.elektron.BuildConfig
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import pl.zse.bydgoszcz.elektron.presentation.common.GroupedSection
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith

internal const val REPO_URL = "https://github.com/stasolejnik/elektron"
internal const val PRIVACY_URL = "https://github.com/stasolejnik/elektron/blob/main/PRYWATNOSC.md"
internal const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

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
    LaunchedEffect(devMode) { if (page == SettingsPage.DEV && !devMode) page = null }
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
                            onLiveResume = { viewModel.refreshLiveNotifications() },
                            notices = state.notifyNotices, onNotices = viewModel::setNotifNotices,
                            updates = state.notifyUpdates, onUpdates = viewModel::setNotifyUpdates)
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
