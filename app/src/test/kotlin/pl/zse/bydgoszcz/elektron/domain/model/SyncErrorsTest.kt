package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncErrorsTest {

    @Test fun offline() {
        assertEquals(SyncErrors.OFFLINE, SyncErrors.userMessage(UnknownHostException("Unable to resolve host zastepstwa.zse.bydgoszcz.pl")))
        assertEquals(SyncErrors.OFFLINE, SyncErrors.userMessage(SocketTimeoutException("timeout")))
        assertEquals(SyncErrors.OFFLINE, SyncErrors.userMessage(IOException("unexpected end of stream")))
    }

    @Test fun serverError() {
        assertEquals(SyncErrors.SERVER, SyncErrors.userMessage(IOException("Zastępstwa: HTTP 503 dla https://zastepstwa.zse.bydgoszcz.pl/index.html")))
    }

    @Test fun pageChangedAndWrapped() {
        assertEquals(SchoolPageChangedException.USER_MESSAGE, SyncErrors.userMessage(SchoolPageChangedException("plan lekcji")))
        assertEquals(SyncErrors.OFFLINE, SyncErrors.userMessage(RuntimeException("x", UnknownHostException("h"))))
        assertEquals(SyncErrors.GENERIC, SyncErrors.userMessage(IllegalStateException("coś")))
    }
}
