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

class UrlConnectionAiHttpTransport(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiHttpTransport {
    override suspend fun execute(request: AiHttpRequest): AiHttpResponse = withContext(dispatcher) {
        val activeConnection = AtomicReference<HttpURLConnection?>()
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                activeConnection.getAndSet(null)?.disconnect()
            }
            try {
                val response = executeBlocking(request, activeConnection) { continuation.isActive }
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
                activeConnection.getAndSet(null)?.disconnect()
            }
        }
    }

    private fun executeBlocking(
        request: AiHttpRequest,
        activeConnection: AtomicReference<HttpURLConnection?>,
        isActive: () -> Boolean,
    ): AiHttpResponse {
        var currentUrl = URL(request.url)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = connectionFactory(currentUrl)
            activeConnection.set(connection)
            if (!isActive()) {
                if (activeConnection.compareAndSet(connection, null)) connection.disconnect()
                throw CancellationException("AI HTTP request cancelled")
            }
            try {
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
                if (activeConnection.compareAndSet(connection, null)) connection.disconnect()
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
}
