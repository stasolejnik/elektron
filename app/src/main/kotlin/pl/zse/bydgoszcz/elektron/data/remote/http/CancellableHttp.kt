package pl.zse.bydgoszcz.elektron.data.remote.http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/** Anulowanie obejmuje połączenie i odczyt body; odpowiedź zawsze jest zamykana. */
suspend fun <T> Call.readCancellable(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWith(Result.failure(e))
        }
        override fun onResponse(call: Call, response: Response) {
            val result = runCatching {
                response.use {
                    if (continuation.isActive) read(it) else throw IOException("Canceled")
                }
            }
            continuation.resumeWith(result)
        }
    })
}
