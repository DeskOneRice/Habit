package com.habit.app.data.ai

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class UrlConnectionAiHttpTransport internal constructor(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onConnectionPublished: (() -> Unit)? = null,
    private val onFirstIoReady: (() -> Unit)? = null,
) : AiHttpTransport {
    override suspend fun execute(request: AiHttpRequest): AiHttpResponse = withContext(dispatcher) {
        val coordinator = ConnectionCoordinator()
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { coordinator.cancel() }
            try {
                val response = executeBlocking(request, coordinator)
                if (continuation.isActive) continuation.resume(response)
            } catch (cancellation: CancellationException) {
                if (continuation.isActive) continuation.resumeWithException(cancellation)
            } catch (failure: AiServiceFailure) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            } catch (_: SocketTimeoutException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        AiServiceFailure(AiFailureKind.TIMEOUT, TIMEOUT_MESSAGE, "Socket timeout"),
                    )
                }
            } catch (_: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        AiServiceFailure(AiFailureKind.OFFLINE, OFFLINE_MESSAGE, "Network I/O failure"),
                    )
                }
            } catch (_: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(invalidTransportResponse("Invalid transport response"))
                }
            } finally {
                coordinator.cancel()
            }
        }
    }

    private fun executeBlocking(
        request: AiHttpRequest,
        coordinator: ConnectionCoordinator,
    ): AiHttpResponse {
        var currentUrl = URL(request.url)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = connectionFactory(currentUrl)
            try {
                coordinator.publish(connection)
                onConnectionPublished?.invoke()
                coordinator.startFirstIo(connection) {
                    configure(connection, request)
                    onFirstIoReady?.invoke()
                }.use { it.write(request.body) }
                val statusCode = connection.responseCode
                if (statusCode in REDIRECT_CODES) {
                    if (redirectCount == MAX_REDIRECTS) {
                        throw invalidTransportResponse("Too many redirects")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw invalidTransportResponse("Redirect has no location")
                    val redirected = try {
                        URL(currentUrl, location)
                    } catch (_: Exception) {
                        throw invalidTransportResponse("Invalid redirect location")
                    }
                    if (!sameSchemeAndHost(currentUrl, redirected)) {
                        throw invalidTransportResponse("Cross-origin redirect rejected")
                    }
                    currentUrl = redirected
                } else {
                    val stream = if (statusCode >= 400) {
                        connection.errorStream
                    } else {
                        connection.inputStream
                    }
                    val body = stream?.use { readBounded(it, responseContentLength(connection)) } ?: byteArrayOf()
                    return AiHttpResponse(statusCode, body)
                }
            } finally {
                coordinator.finish(connection)
                connection.disconnect()
            }
        }
        throw invalidTransportResponse("Too many redirects")
    }

    private fun configure(connection: HttpURLConnection, request: AiHttpRequest) {
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.useCaches = false
        connection.connectTimeout = request.connectTimeoutMillis
        connection.readTimeout = request.readTimeoutMillis
        connection.setFixedLengthStreamingMode(request.body.size)
        request.headers.forEach(connection::setRequestProperty)
    }

    private fun responseContentLength(connection: HttpURLConnection): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connection.contentLengthLong
        } else {
            connection.contentLength.toLong()
        }

    private fun readBounded(stream: InputStream, contentLength: Long): ByteArray {
        if (contentLength > MAX_AI_HTTP_RESPONSE_BYTES) {
            throw invalidTransportResponse("Response exceeds size limit")
        }
        val output = ByteArrayOutputStream(
            contentLength.takeIf { it in 1..MAX_AI_HTTP_RESPONSE_BYTES.toLong() }?.toInt() ?: 8192,
        )
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_AI_HTTP_RESPONSE_BYTES) {
                throw invalidTransportResponse("Response exceeds size limit")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sameSchemeAndHost(first: URL, second: URL): Boolean =
        first.protocol.equals(second.protocol, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true)

    private fun invalidTransportResponse(diagnostic: String) = AiServiceFailure(
        AiFailureKind.INVALID_RESPONSE,
        MALFORMED_RESPONSE_MESSAGE,
        diagnostic,
    )

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }

    private class ConnectionCoordinator {
        private val lock = ReentrantLock(true)
        private val activeConnection = AtomicReference<HttpURLConnection?>()

        @Volatile
        private var startIoHoldsLock = false
        private var state: ConnectionState = ConnectionState.Waiting

        fun publish(connection: HttpURLConnection) {
            lock.lock()
            try {
                if (state == ConnectionState.Cancelled) {
                    connection.disconnect()
                    throw CancellationException("AI HTTP request cancelled")
                }
                check(state == ConnectionState.Waiting) { "AI HTTP connection state is invalid" }
                activeConnection.set(connection)
                state = ConnectionState.Published
            } finally {
                lock.unlock()
            }
        }

        fun startFirstIo(connection: HttpURLConnection, configureAndWait: () -> Unit) = run {
            lock.lock()
            try {
                if (state != ConnectionState.Published || activeConnection.get() !== connection) {
                    connection.disconnect()
                    throw CancellationException("AI HTTP request cancelled")
                }
                state = ConnectionState.IoStarted
                startIoHoldsLock = true
                configureAndWait()
                connection.outputStream
            } finally {
                startIoHoldsLock = false
                lock.unlock()
            }
        }

        fun finish(connection: HttpURLConnection) {
            lock.lock()
            try {
                if (activeConnection.compareAndSet(connection, null) && state != ConnectionState.Cancelled) {
                    state = ConnectionState.Waiting
                }
            } finally {
                lock.unlock()
            }
        }

        fun cancel() {
            if (lock.tryLock()) {
                try {
                    cancelWhileLocked()
                } finally {
                    lock.unlock()
                }
                return
            }

            if (startIoHoldsLock) activeConnection.get()?.disconnect()
            lock.lock()
            try {
                cancelWhileLocked()
            } finally {
                lock.unlock()
            }
        }

        private fun cancelWhileLocked() {
            state = ConnectionState.Cancelled
            activeConnection.getAndSet(null)?.disconnect()
        }
    }

    private enum class ConnectionState {
        Waiting,
        Published,
        IoStarted,
        Cancelled,
    }
}
