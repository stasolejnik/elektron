package pl.zse.bydgoszcz.elektron.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.zse.bydgoszcz.elektron.work.LocalNotificationSink
import pl.zse.bydgoszcz.elektron.work.NotificationSink
import javax.inject.Singleton

/**
 * Bindingi dla warstwy powiadomień. Gdyby kiedyś doszedł FCM:
 * dopisz FcmNotificationSink i podmień tu jedną linię.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds @Singleton
    abstract fun bindNotificationSink(impl: LocalNotificationSink): NotificationSink
}
