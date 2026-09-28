package pl.zse.bydgoszcz.elektron.work

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Wariant foss (F-Droid): bez powiadomień push. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    abstract fun bindPushTopics(impl: NoPushTopics): PushTopics
}
