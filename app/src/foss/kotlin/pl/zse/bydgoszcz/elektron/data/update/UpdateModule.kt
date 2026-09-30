package pl.zse.bydgoszcz.elektron.data.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.zse.bydgoszcz.elektron.domain.repository.UpdateInstaller

/** Wariant foss (F-Droid): bez instalowania aktualizacji w aplikacji. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    abstract fun bindUpdateInstaller(impl: NoUpdateInstaller): UpdateInstaller
}
