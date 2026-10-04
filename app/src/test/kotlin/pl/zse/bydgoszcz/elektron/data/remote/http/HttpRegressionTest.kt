package pl.zse.bydgoszcz.elektron.data.remote.http

import kotlinx.coroutines.*
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class HttpRegressionTest {
    @get:Rule val temp = TemporaryFolder()

    private class Server(private val handler: (Socket, String) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}/"
        private val worker = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                socket.use {
                    it.soTimeout = 5000
                    val reader = it.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        lines += line
                    }
                    handler(it, lines.joinToString("\n"))
                }
            }
        }
        override fun close() { server.close(); worker.join(2000) }
    }

    @Test fun unchangedResponseUsesEtagAndCachedBody() = runBlocking {
        val validators = CopyOnWriteArrayList<Boolean>()
        val server = Server { socket, request ->
            val conditional = request.contains("If-None-Match: \"v1\"", ignoreCase = true)
            validators += conditional
            val status = if (conditional) "304 Not Modified" else "200 OK"
            val body = if (conditional) "" else "hello"
            socket.getOutputStream().write(("HTTP/1.1 $status\r\nETag: \"v1\"\r\n" +
                "Cache-Control: max-age=3600\r\nConnection: close\r\nContent-Length: ${body.length}\r\n\r\n$body").toByteArray())
        }
        val client = OkHttpClientProvider.create(false, "test", temp.newFolder())
        try {
            val request = Request.Builder().url(server.url).build()
            assertEquals("hello", client.newCall(request).readCancellable { it.body!!.string() })
            assertEquals("hello", client.newCall(request).readCancellable {
                assertEquals(304, it.networkResponse!!.code)
                assertNotNull(it.cacheResponse)
                it.body!!.string()
            })
            assertEquals(listOf(false, true), validators)
        } finally {
            client.cache!!.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            server.close()
        }
    }

    @Test fun cancellationCancelsCallWhileReadingBody() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = Server { socket, _ ->
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\nConnection: close\r\n\r\nA".toByteArray())
            socket.getOutputStream().flush()
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val client = OkHttpClientProvider.create(false, "test", temp.newFolder())
        val call = client.newCall(Request.Builder().url(server.url).build())
        try {
            val job = async(Dispatchers.IO) { call.readCancellable { it.body!!.string() } }
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            withTimeout(1000) { job.cancelAndJoin() }
            assertTrue(call.isCanceled())
        } finally {
            release.countDown()
            client.cache!!.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            server.close()
        }
    }
}
