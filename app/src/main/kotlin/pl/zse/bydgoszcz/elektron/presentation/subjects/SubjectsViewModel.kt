package pl.zse.bydgoszcz.elektron.presentation.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.domain.model.SubjectStyle
import pl.zse.bydgoszcz.elektron.domain.model.SubjectStyles
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.presentation.common.currentDateFlow
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import javax.inject.Inject

/** Ustawienia -> Przedmioty: lista przedmiotów z planu wybranej klasy + własne nazwy i kolory. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubjectsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    timetableRepo: TimetableRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    data class Item(val key: String, val style: SubjectStyle)

    data class State(val loading: Boolean = true, val items: List<Item> = emptyList())

    // Przedmioty z bieżącego zakresu planu (jak ekran grup), bez sufiksów grup.
    private val subjectKeys = combine(settings.selectedClassId, currentDateFlow()) { cid, day -> cid to day }
        .flatMapLatest { (cid, day) ->
            if (cid == null) flowOf(emptyList())
            else timetableRepo.observeLessons(cid, day.minusDays(7), day.plusDays(28))
        }

    val state: StateFlow<State> = combine(subjectKeys, settings.subjectStyles) { lessons, styles ->
        val keys = lessons.flatMap { l -> l.groups.mapNotNull { SubjectStyles.key(it.subject) } }
            .distinct()
            .sortedBy { it.lowercase() }
        State(loading = false, items = keys.map { Item(it, styles[it] ?: SubjectStyle()) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun save(key: String, name: String?, color: Long?) {
        viewModelScope.launch {
            settings.setSubjectStyle(key, SubjectStyle(SubjectStyles.cleanName(name, key), color))
            widgetUpdater.requestUpdate()
        }
    }

    fun reset(key: String) = save(key, null, null)
}
