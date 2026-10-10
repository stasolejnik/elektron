package pl.zse.bydgoszcz.elektron.testutil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll

/**
 * Czyści ViewModele i czeka, aż ich korutyny (także przepływy Room na Dispatchers.Default)
 * naprawdę się zakończą. Bez tego @After zamykał bazę, gdy anulowany kolektor jeszcze
 * wyrejestrowywał obserwatora - "attempt to re-open an already-closed object" wylatywał
 * w kolejnym teście jako UncaughtExceptionsBeforeTest (losowo czerwone testy).
 */
suspend fun ViewModelStore.clearAndAwait(vararg viewModels: ViewModel) {
    val jobs = viewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] }
    clear()
    jobs.joinAll()
}
