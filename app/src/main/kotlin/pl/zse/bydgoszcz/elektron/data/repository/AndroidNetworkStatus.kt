package pl.zse.bydgoszcz.elektron.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import pl.zse.bydgoszcz.elektron.domain.repository.NetworkStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stan sieci z ConnectivityManager. Wystarcza zdolność INTERNET (bez VALIDATED): GrapheneOS
 * pozwala wyłączyć sprawdzanie łączności, a wtedy sieć nigdy nie jest oznaczana jako zweryfikowana.
 */
@Singleton
class AndroidNetworkStatus @Inject constructor(@ApplicationContext private val context: Context) : NetworkStatus {
    override val online: Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) { trySend(true); awaitClose { }; return@callbackFlow }
        fun current() = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            // Domyślna sieć: zmiana na inną przychodzi jako onAvailable nowej, utrata bez zastępstwa - onLost.
            override fun onAvailable(network: Network) {
                trySend(cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: true)
            }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(current())
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onFailure { trySend(true) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
