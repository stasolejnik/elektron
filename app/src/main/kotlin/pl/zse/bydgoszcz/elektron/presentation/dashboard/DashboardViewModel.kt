package pl.zse.bydgoszcz.elektron.presentation.dashboard

import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import pl.zse.bydgoszcz.elektron.work.LessonReminderScheduler
import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome
import pl.zse.bydgoszcz.elektron.domain.model.SyncErrors
import java.time.LocalDateTime
import pl.zse.bydgoszcz.elektron.domain.model.LessonClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionRelevance
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.repository.UpdateRepository
import pl.zse.bydgoszcz.elektron.domain.repository.AppUpdate
import pl.zse.bydgoszcz.elektron.presentation.common.currentDateFlow
import pl.zse.bydgoszcz.elektron.presentation.common.minuteTicker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val substitutionsRepo: SubstitutionsRepository,
    private val announcementsRepo: AnnouncementsRepository,
    private val notificationsRepo: NotificationsRepository,
    private val timetableRepo: TimetableRepository,
    private val updateRepo: UpdateRepository,
    private val widgetUpdater: WidgetUpdater,
    private val reminders: LessonReminderScheduler
) : ViewModel() {

    /** Break = przerwa między dwiema lekcjami dziś (pokazujemy następną lekcję + czas przerwy). */
    enum class LessonStatus { Now, Break, Next, None }

    data class State(
        val selectedClassId: String? = null,
        val className: String? = null,
        val isSyncing: Boolean = false,
        val lastSyncError: String? = null,
        val loadingTimetable: Boolean = true,
        val loadingSubs: Boolean = true,
        val loadingAnns: Boolean = true,
        val nextLesson: Lesson? = null,
        val nextLessonDayLabel: String? = null,
        val nextLessonStatus: LessonStatus = LessonStatus.None,
        val countdownMinutes: Long? = null,
        /** Trwająca lekcja: postęp 0..1 i pozostałe minuty (jak w widżecie). */
        val lessonProgress: Float? = null,
        /** Nowa wersja aplikacji na GitHubie (baner), null gdy brak. */
        val availableUpdate: AppUpdate? = null,
        /** Czas ostatniej udanej synchronizacji, jeśli dane są starsze niż godzina (offline). */
        val staleSince: Instant? = null,
        val minutesLeft: Long? = null,
        val upcomingSubstitutions: List<Substitution> = emptyList(),
        val latestAnnouncements: List<Announcement> = emptyList(),
        val isRefreshing: Boolean = false,
        val lastSyncAt: Instant? = null,
        val showNextLesson: Boolean = true,
        val showSubstitutions: Boolean = true,
        val showAnnouncements: Boolean = true,
        /** Pierwsze dane już są (do tego czasu ekran jest niewidoczny, bez mignięć). */
        val ready: Boolean = false
    )

    private data class Core(
        val cid: String? = null,
        // Bug #2: classShort MUSI pochodzić z listy klas (dostępnej od razu po sync sidebaru
        // podczas Setup), a nie z pierwszej zsynchronizowanej lekcji — inaczej, zanim plan
        // lekcji się załaduje, className jest null i filtr zastępstw przepuszczał WSZYSTKIE
        // klasy zamiast żadnej.
        val classShort: String? = null,
        val lessons: List<Lesson> = emptyList(),
        val subs: List<Substitution> = emptyList(),
        val refresh: Boolean = false,
        val anns: List<Announcement> = emptyList(),
        val lastSync: Instant? = null,
        val syncing: Boolean = false,
        val error: String? = null,
        val loaded: Set<String> = emptySet(),
        // Bug #1: sygnał "trwa pierwszy sync" — jaśniejszy niż samo "loaded", bo obejmuje
        // całe okno między wyborem klasy a zakończeniem (sukcesem lub porażką) syncu.
        val initialSyncPending: Boolean = false,
        /** Wybrane grupy zajęciowe dla klasy (przedmiot -> grupa / NONE). */
        val groups: Map<String, String> = emptyMap(),
        val update: AppUpdate? = null,
        val showNextLesson: Boolean = true,
        val showSubstitutions: Boolean = true,
        val showAnnouncements: Boolean = true
    )

    private val refreshing = MutableStateFlow(false)
    // Zakres dat liczony od bieżącej daty (przebudowywany po północy), nie od chwili
    // utworzenia ViewModelu — ten żyje przez cały czas działania aplikacji.
    private val todayFlow = currentDateFlow()

    private val selected = settings.selectedClassId

    // Skrót klasy niezależny od tego, czy plan lekcji już się zsynchronizował — pochodzi
    // z listy klas (observeClasses), dostępnej od razu po sync sidebaru.
    private val classShortFlow: Flow<String?> = selected.flatMapLatest { cid: String? ->
        if (cid == null) flowOf(null)
        else timetableRepo.observeClasses().map { list -> list.firstOrNull { it.id == cid }?.shortName }
    }

    private val lessonsFlow: Flow<List<Lesson>> = combine(selected, todayFlow) { cid, day -> cid to day }
        .flatMapLatest { (cid, day) ->
            if (cid == null) flowOf(emptyList())
            else timetableRepo.observeLessons(cid, day, day.plusDays(21))
        }

    private val subsFlow: Flow<List<Substitution>> = todayFlow.flatMapLatest { day -> substitutionsRepo.observeFrom(day) }

    val state: StateFlow<State> = combine(
        selected, classShortFlow, lessonsFlow, subsFlow, refreshing
    ) { cid: String?, classShort: String?, lessons: List<Lesson>, subs: List<Substitution>, refresh: Boolean ->
        Core(cid = cid, classShort = classShort, lessons = lessons, subs = subs, refresh = refresh)
    }
        .combine(announcementsRepo.observeLatest(3)) { core, anns -> core.copy(anns = anns) }
        .combine(notificationsRepo.observeLastSyncAt()) { core, at -> core.copy(lastSync = at) }
        .combine(notificationsRepo.observeIsSyncing()) { core, syncing -> core.copy(syncing = syncing) }
        .combine(notificationsRepo.observeLastSyncError()) { core, err -> core.copy(error = err) }
        .combine(notificationsRepo.observeLoadedResources()) { core, loaded -> core.copy(loaded = loaded) }
        .combine(notificationsRepo.observeInitialSyncPending()) { core, pending -> core.copy(initialSyncPending = pending) }
        .combine(settings.showNextLesson) { core, v -> core.copy(showNextLesson = v) }
        .combine(settings.showSubstitutions) { core, v -> core.copy(showSubstitutions = v) }
        .combine(settings.showAnnouncements) { core, v -> core.copy(showAnnouncements = v) }
        .combine(settings.activeGroupSelections) { core, sel -> core.copy(groups = sel) }
        .combine(updateRepo.availableUpdate) { core, u -> core.copy(update = u) }
        .combine(minuteTicker()) { core, _ -> core }
        .map { core -> compute(core).copy(ready = true) }
        // Przeliczanie stanu (filtry grup, zastępstwa, odliczanie) poza wątkiem UI.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private fun compute(c: Core): State {
        val cid = c.cid
        if (cid == null) {
            return State(selectedClassId = null, loadingTimetable = false,
                loadingSubs = false, loadingAnns = false,
                latestAnnouncements = emptyList(), lastSyncAt = c.lastSync,
                isSyncing = c.syncing, lastSyncError = c.error,
                showNextLesson = c.showNextLesson, showSubstitutions = c.showSubstitutions,
                showAnnouncements = c.showAnnouncements)
        }

        // Bug #1 (drugorzędny): today/now liczone na nowo przy każdym przeliczeniu, nie raz
        // przy tworzeniu ViewModelu — appka otwarta przez noc nie pokaże wczorajszej lekcji
        // jako "trwającej teraz".
        val today = LocalDate.now()
        val now = LocalTime.now()

        // Lekcje po odfiltrowaniu grup, na które użytkownik nie chodzi. c.lessons (surowe)
        // zostają do oceny, czy zastępstwo z numerem grupy dotyczy użytkownika.
        val lessons = LessonGroups.filter(c.lessons, c.groups)
        val className = lessons.firstOrNull()?.className ?: c.lessons.firstOrNull()?.className

        // Trwa / przerwa / przed lekcją - wspólna reguła z widżetami i planem (LessonClock):
        // czas do lekcji tylko w przerwie albo w ostatnich 30 min przed nią (nie w okienku).
        val clock = LessonClock.status(lessons, LocalDateTime.of(today, now))
        val (lesson, status) = when (clock) {
            is LessonClock.During -> clock.lesson to LessonStatus.Now
            is LessonClock.Break -> clock.next to LessonStatus.Break
            is LessonClock.Before -> clock.next to LessonStatus.Next
            LessonClock.Nothing -> null to LessonStatus.None
        }

        val dayLabel = lesson?.date?.let { d: LocalDate ->
            when (d) {
                today -> "Dziś"
                today.plusDays(1) -> "Jutro"
                else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pl", "PL"))
                    .replaceFirstChar { it.titlecase(Locale("pl", "PL")) }
            }
        }

        val countdownMinutes: Long? = (clock as? LessonClock.Before)?.minutesUntil

        // Koniec lekcji wg dzwonków (z całego planu klasy, przed filtrem grup) - zastępstwo
        // znika ze strony głównej po swojej lekcji, także za lekcję innej grupy.
        val lessonEnds = SubstitutionRelevance.lessonEnds(c.lessons)
        val nowDt = LocalDateTime.of(today, now)

        // Bug #2, druga część: gdy klasa nie jest jeszcze znana, pokazujemy PUSTĄ listę,
        // nigdy wszystkie zastępstwa. To bezpieczny domyślny wariant — lepiej chwilę
        // "Ładowanie" niż zastępstwa cudzej klasy.
        val upcoming: List<Substitution> = if (c.classShort == null) emptyList() else c.subs
            .filter { sub: Substitution -> SubstitutionRelevance.matchesClass(sub, c.classShort) }
            .filter { sub: Substitution -> LessonGroups.substitutionRelevant(sub, c.lessons, c.groups) }
            .filter { sub: Substitution -> !SubstitutionRelevance.isOver(sub, nowDt, lessonEnds) }
            .sortedWith(compareBy({ it.date }, { it.lessonNumber }))
            .take(10)

        val timetableLoaded = "timetable" in c.loaded
        val subsLoaded = "subs" in c.loaded
        val annsLoaded = "anns" in c.loaded

        // Bug #1: dopóki trwa pierwszy sync ALBO zasób jeszcze nie jest oznaczony jako
        // załadowany, a nie mamy jeszcze danych do pokazania — pokazujemy "Ładowanie",
        // nie pusty/"brak" stan.
        val stillLoadingBase = c.initialSyncPending

        return State(
            selectedClassId = cid,
            className = className,
            isSyncing = c.syncing,
            lastSyncError = c.error,
            loadingTimetable = (stillLoadingBase || !timetableLoaded) && lesson == null,
            loadingSubs = (stillLoadingBase || !subsLoaded) && upcoming.isEmpty(),
            loadingAnns = (stillLoadingBase || !annsLoaded) && c.anns.isEmpty(),
            nextLesson = lesson,
            nextLessonDayLabel = dayLabel,
            nextLessonStatus = status,
            staleSince = c.lastSync?.takeIf { java.time.Duration.between(it, Instant.now()).toMinutes() >= 60 },
            availableUpdate = c.update,
            countdownMinutes = countdownMinutes,
            // Postęp: w trakcie lekcji — lekcji; w przerwie — przerwy (do początku następnej lekcji).
            lessonProgress = when (clock) {
                is LessonClock.During -> clock.progress
                is LessonClock.Break -> clock.progress
                else -> null
            },
            minutesLeft = when (clock) {
                is LessonClock.During -> clock.minutesLeft
                is LessonClock.Break -> clock.minutesUntil
                else -> null
            },
            upcomingSubstitutions = upcoming,
            latestAnnouncements = c.anns.take(3),
            isRefreshing = c.refresh,
            lastSyncAt = c.lastSync,
            showNextLesson = c.showNextLesson,
            showSubstitutions = c.showSubstitutions,
            showAnnouncements = c.showAnnouncements
        )
    }

    fun dismissUpdate(versionName: String) {
        viewModelScope.launch { updateRepo.dismiss(versionName) }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            val cid = selected.first()
            try {
                // Porażki źródeł (zbierane, nie ignorowane): dawniej odświeżenie bez internetu
                // zapisywało "zsynchronizowano teraz" i kasowało komunikat o błędzie.
                val failures = java.util.concurrent.ConcurrentHashMap<String, Throwable>()
                val succeeded = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        // Lista klas (sidebar) dawniej odświeżała się tylko w tle co 15 min.
                        launch { timetableRepo.syncSidebar() }
                        launch {
                            val r = timetableRepo.syncTimetable(cid ?: return@launch, LocalDate.now())
                            r.onSuccess { succeeded += SyncOutcome.TIMETABLE; notificationsRepo.markLoaded("timetable") }
                                .onFailure { failures[SyncOutcome.TIMETABLE] = it }
                        }
                        launch {
                            val r = substitutionsRepo.syncAll()
                            r.onSuccess { succeeded += SyncOutcome.SUBSTITUTIONS; notificationsRepo.markLoaded("subs") }
                                .onFailure { failures[SyncOutcome.SUBSTITUTIONS] = it }
                        }
                        launch {
                            val r = announcementsRepo.syncAll()
                            r.onSuccess { succeeded += SyncOutcome.ANNOUNCEMENTS; notificationsRepo.markLoaded("anns") }
                                .onFailure { failures[SyncOutcome.ANNOUNCEMENTS] = it }
                        }
                    }
                }
                // "Zsynchronizowano" tylko po odświeżeniu planu albo zastępstw (jak w SyncWorker).
                if (SyncOutcome.freshDataLoaded(succeeded)) notificationsRepo.setLastSyncAt(Instant.now())
                notificationsRepo.setLastSyncError(
                    SyncOutcome.errorMessage(failures) ?: if (succeeded.isEmpty() && failures.isNotEmpty())
                        SyncErrors.userMessage(failures.values.first()) else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notificationsRepo.setLastSyncError(SyncErrors.userMessage(e))
            } finally {
                // Widżety i przypomnienie od razu po nowych danych (np. zwolnienie z lekcji),
                // a nie dopiero po najbliższym syncu w tle.
                reminders.requestReschedule()
                widgetUpdater.requestUpdate()
                refreshing.value = false
            }
        }
    }
}
