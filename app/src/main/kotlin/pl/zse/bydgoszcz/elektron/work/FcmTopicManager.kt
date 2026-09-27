package pl.zse.bydgoszcz.elektron.work

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Funkcja #7: subskrybuje stałe tematy FCM dla ZSE Bydgoszcz. Wywoływane raz przy każdym
 * starcie appki — subscribeToTopic jest idempotentne, więc powtarzanie tego przy każdym
 * uruchomieniu jest bezpieczne i nie wymaga śledzenia stanu.
 */
@Singleton
class FcmTopicManager @Inject constructor() {

    fun start() {
        val fm = FirebaseMessaging.getInstance()
        fm.subscribeToTopic(SUBS_TOPIC).addOnFailureListener { Log.w(TAG, "Nie udało się zasubskrybować $SUBS_TOPIC", it) }
        fm.subscribeToTopic(ANNS_TOPIC).addOnFailureListener { Log.w(TAG, "Nie udało się zasubskrybować $ANNS_TOPIC", it) }
    }

    companion object {
        private const val TAG = "FcmTopicManager"
        const val SUBS_TOPIC = "elektron-subs-zse-bydgoszcz"
        const val ANNS_TOPIC = "elektron-anns-zse-bydgoszcz"
    }
}
