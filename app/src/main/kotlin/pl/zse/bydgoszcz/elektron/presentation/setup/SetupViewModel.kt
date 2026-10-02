package pl.zse.bydgoszcz.elektron.presentation.setup

import pl.zse.bydgoszcz.elektron.domain.model.SyncErrors
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.zse.bydgoszcz.elektron.domain.model.SchoolClass
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.usecase.ClassSelection
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val timetableRepo: TimetableRepository,
    private val classSelection: ClassSelection
) : ViewModel() {

    val classes: StateFlow<List<SchoolClass>> = timetableRepo.observeClasses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            timetableRepo.syncSidebar().onFailure {
                _error.value = SyncErrors.userMessage(it)
                Log.w(TAG, "syncSidebar padł", it)
            }
            _loading.value = false
        }
    }

    fun selectClass(id: String) = classSelection.select(id)

    companion object { private const val TAG = "SetupViewModel" }
}
