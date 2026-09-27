package pl.zse.bydgoszcz.elektron.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.zse.bydgoszcz.elektron.data.remote.sources.AnnouncementsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.SubstitutionsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.TimetableSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.zse.ZseRssAnnouncementsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.zse.ZseSubstitutionsSource
import pl.zse.bydgoszcz.elektron.data.remote.sources.zse.ZseTimetableSource
import javax.inject.Singleton

/**
 * Bindingi interfejsów źródeł do implementacji ZSE.
 * Podmieniając tu jedną linię można w przyszłości podmienić całe źródło.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SourcesModule {

    @Binds @Singleton
    abstract fun bindTimetableSource(impl: ZseTimetableSource): TimetableSource

    @Binds @Singleton
    abstract fun bindSubstitutionsSource(impl: ZseSubstitutionsSource): SubstitutionsSource

    @Binds @Singleton
    abstract fun bindAnnouncementsSource(impl: ZseRssAnnouncementsSource): AnnouncementsSource
}
