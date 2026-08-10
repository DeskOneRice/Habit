package com.habit.app.data.ai

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
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
) : AiHttpTransport {
    override suspend fun execute(request: AiHttpRequest): AiHttpResponse = withContext(dispatcher) {
        val state = AtomicReference<ConnectionState>(ConnectionState.Waiting)
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel(state) }
            try {
                val response = executeBlocking(request, state)
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
                cancel(state)
            }
        }
    }

    private fun executeBlocking(
        request: AiHttpRequest,
        state: AtomicReference<ConnectionState>,
    ): AiHttpResponse {
        var currentUrl = URL(request.url)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = connectionFactory(currentUrl)
            try {
                val published = publish(state, connection)
                onConnectionPublished?.invoke()
                startIo(state, published)
                configure(connection, request)
                connection.outputStream.use { it.write(request.body) }
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
                    val body = stream?.use { readBounded(it, connection.contentLengthLong) } ?: byteArrayOf()
                    return AiHttpResponse(statusCode, body)
                }
            } finally {
                finishConnection(state, connection)
                connection.disconnect()
            }
        }
        throw invalidTransportResponse("Too many redirects")
    }

    private fun publish(
        state: AtomicReference<ConnectionState>,
        connection: HttpURLConnection,
    ): ConnectionState.Published {
        val published = ConnectionState.Published(connection)
        if (!state.compareAndSet(ConnectionState.Waiting, published)) {
            connection.disconnect()
            throw CancellationException("AI HTTP request cancelled")
        }
        return published
    }

    private fun startIo(state: AtomicReference<ConnectionState>, published: ConnectionState.Published) {
        if (!state.compareAndSet(published, ConnectionState.IoStarted(published.connection))) {
            published.connection.disconnect()
            throw CancellationException("AI HTTP request cancelled")
        }
    }

    private fun finishConnection(state: AtomicReference<ConnectionState>, connection: HttpURLConnection) {
        while (true) {
            when (val current = state.get()) {
                is ConnectionState.Published -> if (current.connection !== connection) return else {
                    if (state.compareAndSet(current, ConnectionState.Waiting)) return
                }
                is ConnectionState.IoStarted -> if (current.connection !== connection) return else {
                    if (state.compareAndSet(current, ConnectionState.Waiting)) return
                }
                ConnectionState.Cancelled, ConnectionState.Waiting -> return
            }
        }
    }

    private fun cancel(state: AtomicReference<ConnectionState>) {
        while (true) {
            when (val current = state.get()) {
                ConnectionState.Cancelled -> return
                ConnectionState.Waiting -> if (state.compareAndSet(current, ConnectionState.Cancelled)) return
                is ConnectionState.Published -> if (state.compareAndSet(current, ConnectionState.Cancelled)) {
                    current.connection.disconnect()
                    return
                }
                is ConnectionState.IoStarted -> if (state.compareAndSet(current, ConnectionState.Cancelled)) {
                    current.connection.disconnect()
                    return
                }
            }
        }
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

    private sealed interface ConnectionState {
        data object Waiting : ConnectionState
        data class Published(val connection: HttpURLConnection) : ConnectionState
        data class IoStarted(val connection: HttpURLConnection) : ConnectionState
        data object Cancelled : ConnectionState
    }
}
