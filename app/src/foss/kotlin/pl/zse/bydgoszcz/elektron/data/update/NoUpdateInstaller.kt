package pl.zse.bydgoszcz.elektron.data.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.zse.bydgoszcz.elektron.domain.repository.AppUpdate
import pl.zse.bydgoszcz.elektron.domain.repository.InstallState
import pl.zse.bydgoszcz.elektron.domain.repository.UpdateInstaller
import javax.inject.Inject
import javax.inject.Singleton

/** Wariant foss (F-Droid): bez instalowania APK - aktualizacjami zajmuje się F-Droid. */
@Singleton
class NoUpdateInstaller @Inject constructor() : UpdateInstaller {
    override val supported: Boolean = false
    override val state: StateFlow<InstallState> = MutableStateFlow(InstallState.Idle)
    override fun start(update: AppUpdate) = Unit
    override fun install() = Unit
    override fun resumeIfPermitted() = Unit
}
