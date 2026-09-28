package pl.zse.bydgoszcz.elektron.work

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Wariant gms (GitHub): powiadomienia push przez Firebase Cloud Messaging. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    abstract fun bindPushTopics(impl: FcmTopicManager): PushTopics
}
