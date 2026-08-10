package com.habit.app.data.ai

import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiTestStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {
    @Test
    fun authorizationOnlyLivesInTransportRequest() = runTest {
        val transport = RecordingTransport(response(200, validChatBody("{\"ok\":true}")))
        val client = OpenAiCompatibleClient(transport)

        client.completeText(config, "sk-private", "system", "user")

        val request = transport.request
        assertEquals("Bearer sk-private", request.headers["Authorization"])
        assertFalse(request.body.decodeToString().contains("sk-private"))
        assertEquals(15_000, request.connectTimeoutMillis)
        assertEquals(60_000, request.readTimeoutMillis)
    }

    @Test
    fun textRequestUsesConfiguredModelAndPrompts() = runTest {
        val transport = RecordingTransport(response(200, validChatBody("answer")))

        val answer = OpenAiCompatibleClient(transport)
            .completeText(config, "secret", "system prompt", "user prompt")

        assertEquals("answer", answer)
        assertEquals("https://api.example.com/v1/chat/completions", transport.request.url)
        val json = Json.parseToJsonElement(transport.request.body.decodeToString()).jsonObject
        assertEquals("configured-model-id", json.getValue("model").jsonPrimitive.content)
        assertEquals("0.2", json.getValue("temperature").jsonPrimitive.content)
        val messages = json.getValue("messages").jsonArray
        assertEquals("system", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("system prompt", messages[0].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("user prompt", messages[1].jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun visionRequestKeepsTextThenOneToThreeImagesInOrder() = runTest {
        val transport = RecordingTransport(response(200, validChatBody("answer")))
        val images = listOf(
            AiPreparedImage("image/jpeg", byteArrayOf(1, 2)),
            AiPreparedImage("image/jpeg", byteArrayOf(3, 4)),
            AiPreparedImage("image/jpeg", byteArrayOf(5, 6)),
        )

        OpenAiCompatibleClient(transport)
            .completeVision(config, "secret", "system", "describe", images)

        val messages = Json.parseToJsonElement(transport.request.body.decodeToString())
            .jsonObject.getValue("messages").jsonArray
        val parts = messages[1].jsonObject.getValue("content").jsonArray
        assertEquals("text", parts[0].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("describe", parts[0].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(
            listOf(
                "data:image/jpeg;base64,AQI=",
                "data:image/jpeg;base64,AwQ=",
                "data:image/jpeg;base64,BQY=",
            ),
            parts.drop(1).map {
                it.jsonObject.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content
            },
        )
    }

    @Test
    fun visionRequestRejectsImageCountsOutsideOneToThree() = runTest {
        val client = OpenAiCompatibleClient(RecordingTransport(response(200, validChatBody("answer"))))

        val noImages = assertThrows(AiServiceFailure::class.java) {
            runBlocking { client.completeVision(config, "secret", "system", "user", emptyList()) }
        }
        val tooMany = assertThrows(AiServiceFailure::class.java) {
            runBlocking {
                client.completeVision(
                    config,
                    "secret",
                    "system",
                    "user",
                    List(4) { AiPreparedImage("image/jpeg", byteArrayOf(1)) },
                )
            }
        }

        assertEquals(AiFailureKind.INVALID_RESPONSE, noImages.kind)
        assertEquals(AiFailureKind.INVALID_RESPONSE, tooMany.kind)
    }

    @Test
    fun parsesOnlyFirstChoiceMessageContent() = runTest {
        val body = """{"choices":[{"message":{"content":"first"}},{"message":{"content":"second"}}]}"""

        val result = OpenAiCompatibleClient(RecordingTransport(response(200, body)))
            .completeText(config, "secret", "system", "user")

        assertEquals("first", result)
    }

    @Test
    fun malformedEmptyAndOversizedResponsesAreInvalid() = runTest {
        val bodies = listOf(
            "not-json".encodeToByteArray(),
            """{"choices":[]}""".encodeToByteArray(),
            validChatBody("   ").encodeToByteArray(),
            validChatBody("x".repeat(MAX_AI_HTTP_RESPONSE_BYTES)).encodeToByteArray(),
        )

        bodies.forEach { body ->
            val failure = assertThrows(AiServiceFailure::class.java) {
                runBlocking {
                    OpenAiCompatibleClient(RecordingTransport(AiHttpResponse(200, body)))
                        .completeText(config, "secret", "system", "user")
                }
            }
            assertEquals(AiFailureKind.INVALID_RESPONSE, failure.kind)
            assertEquals(MALFORMED_RESPONSE_MESSAGE, failure.userMessage)
        }
    }

    @Test
    fun cancellationFromTransportPropagatesUnchanged() = runTest {
        val cancellation = CancellationException("cancelled by caller")
        val client = OpenAiCompatibleClient(object : AiHttpTransport {
            override suspend fun execute(request: AiHttpRequest): AiHttpResponse = throw cancellation
        })

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { client.completeText(config, "secret", "system", "user") }
        }

        assertTrue(thrown === cancellation)
    }

    @Test
    fun transportFailuresCannotLeakTheApiKey() = runTest {
        val client = OpenAiCompatibleClient(object : AiHttpTransport {
            override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
                throw AiServiceFailure(
                    AiFailureKind.SERVER,
                    SERVER_MESSAGE,
                    "Authorization: Bearer sk-private provider detail",
                )
            }
        })

        val failure = assertThrows(AiServiceFailure::class.java) {
            runBlocking { client.completeText(config, "sk-private", "system", "user") }
        }

        assertEquals(AiFailureKind.SERVER, failure.kind)
        assertFalse(failure.safeDiagnostic.contains("sk-private"))
        assertFalse(failure.safeDiagnostic.contains("Authorization", ignoreCase = true))
        assertFalse(failure.toString().contains("sk-private"))
    }

    @Test
    fun transportFollowsOnlySameSchemeAndHostRedirects() = runTest {
        val connections = mutableListOf<FakeHttpURLConnection>()
        val transport = UrlConnectionAiHttpTransport(
            connectionFactory = { url ->
                FakeHttpURLConnection(
                    url = url,
                    statusCode = if (url.path.endsWith("/start")) 307 else 200,
                    responseBody = if (url.path.endsWith("/start")) byteArrayOf() else "ok".encodeToByteArray(),
                    responseHeaders = if (url.path.endsWith("/start")) mapOf("Location" to "/next") else emptyMap(),
                ).also(connections::add)
            },
            dispatcher = Dispatchers.IO,
        )

        val result = transport.execute(request("https://api.example.com/start"))

        assertEquals(200, result.statusCode)
        assertEquals("ok", result.body.decodeToString())
        assertEquals(listOf("/start", "/next"), connections.map { it.url.path })
        assertTrue(connections.all { it.disconnected.get() })
    }

    @Test
    fun transportRejectsRedirectToDifferentSchemeOrHost() = runTest {
        listOf(
            "http://api.example.com/next",
            "https://other.example.com/next",
        ).forEach { location ->
            val transport = UrlConnectionAiHttpTransport(
                connectionFactory = { url ->
                    FakeHttpURLConnection(
                        url = url,
                        statusCode = 307,
                        responseHeaders = mapOf("Location" to location),
                    )
                },
                dispatcher = Dispatchers.IO,
            )

            val failure = assertThrows(AiServiceFailure::class.java) {
                runBlocking { transport.execute(request("https://api.example.com/start")) }
            }

            assertEquals(AiFailureKind.INVALID_RESPONSE, failure.kind)
            assertFalse(failure.safeDiagnostic.contains(location))
        }
    }

    @Test
    fun transportRejectsResponseBodiesOverTwoMiB() = runTest {
        val transport = UrlConnectionAiHttpTransport(
            connectionFactory = { url ->
                FakeHttpURLConnection(
                    url = url,
                    statusCode = 200,
                    responseBody = ByteArray(MAX_AI_HTTP_RESPONSE_BYTES + 1),
                )
            },
            dispatcher = Dispatchers.IO,
        )

        val failure = assertThrows(AiServiceFailure::class.java) {
            runBlocking { transport.execute(request("https://api.example.com/chat/completions")) }
        }

        assertEquals(AiFailureKind.INVALID_RESPONSE, failure.kind)
        assertEquals(MALFORMED_RESPONSE_MESSAGE, failure.userMessage)
    }

    @Test
    fun transportReturnsErrorStatusEvenWhenThereIsNoErrorBody() = runTest {
        val transport = UrlConnectionAiHttpTransport(
            connectionFactory = { url ->
                FakeHttpURLConnection(
                    url = url,
                    statusCode = 404,
                    hasErrorStream = false,
                )
            },
            dispatcher = Dispatchers.IO,
        )

        val response = transport.execute(request("https://api.example.com/chat/completions"))

        assertEquals(404, response.statusCode)
        assertTrue(response.body.isEmpty())
    }

    @Test
    fun cancellingTransportDisconnectsActiveConnection() = runBlocking {
        val connection = BlockingHttpURLConnection(URL("https://api.example.com/chat/completions"))
        val transport = UrlConnectionAiHttpTransport(
            connectionFactory = { connection },
            dispatcher = Dispatchers.IO,
        )

        val call = launch(Dispatchers.Default) { transport.execute(request(connection.url.toString())) }
        assertTrue(connection.responseStarted.await(2, TimeUnit.SECONDS))
        call.cancelAndJoin()

        assertTrue(connection.disconnectCalled.await(2, TimeUnit.SECONDS))
        assertTrue(call.isCancelled)
    }

    @Test
    fun cancellationBeforeConnectionPublicationNeverStartsIo() = runBlocking {
        val factoryEntered = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val connection = FakeHttpURLConnection(
            URL("https://api.example.com/chat/completions"),
            200,
            "ok".encodeToByteArray(),
        )
        val transport = UrlConnectionAiHttpTransport(
            connectionFactory = {
                factoryEntered.countDown()
                releaseFactory.await(2, TimeUnit.SECONDS)
                connection
            },
            dispatcher = Dispatchers.IO,
        )

        val call = launch(Dispatchers.Default) { transport.execute(request(connection.url.toString())) }
        assertTrue(factoryEntered.await(2, TimeUnit.SECONDS))
        call.cancel()
        releaseFactory.countDown()
        call.join()

        assertTrue(call.isCancelled)
        assertEquals(0, connection.responseCodeCalls.get())
        assertTrue(connection.disconnected.get())
    }

    private fun request(url: String) = AiHttpRequest(
        url = url,
        headers = mapOf("Authorization" to "Bearer secret", "Content-Type" to "application/json"),
        body = "{}".encodeToByteArray(),
    )

    private class RecordingTransport(private val response: AiHttpResponse) : AiHttpTransport {
        lateinit var request: AiHttpRequest

        override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
            this.request = request
            return response
        }
    }

    private open class FakeHttpURLConnection(
        url: URL,
        private val statusCode: Int,
        private val responseBody: ByteArray = byteArrayOf(),
        private val responseHeaders: Map<String, String> = emptyMap(),
        private val hasErrorStream: Boolean = true,
    ) : HttpURLConnection(url) {
        val disconnected = AtomicBoolean(false)
        val responseCodeCalls = AtomicInteger(0)
        val writtenBody = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun usingProxy(): Boolean = false
        override fun disconnect() {
            disconnected.set(true)
        }

        override fun getOutputStream() = writtenBody
        override fun getResponseCode(): Int {
            responseCodeCalls.incrementAndGet()
            return statusCode
        }
        override fun getInputStream() = if (statusCode >= 400 && !hasErrorStream) {
            throw FileNotFoundException("HTTP error has no body")
        } else {
            ByteArrayInputStream(responseBody)
        }
        override fun getErrorStream() = if (hasErrorStream) ByteArrayInputStream(responseBody) else null
        override fun getHeaderField(name: String?): String? = responseHeaders[name]
        override fun getContentLengthLong(): Long = responseBody.size.toLong()
    }

    private class BlockingHttpURLConnection(url: URL) : FakeHttpURLConnection(url, 200) {
        val responseStarted = CountDownLatch(1)
        val disconnectCalled = CountDownLatch(1)

        override fun getResponseCode(): Int {
            responseStarted.countDown()
            disconnectCalled.await(5, TimeUnit.SECONDS)
            return 200
        }

        override fun disconnect() {
            super.disconnect()
            disconnectCalled.countDown()
        }
    }

    private companion object {
        val config = AiModelConfig(
            externalId = "model-external-id",
            name = "Configured model",
            baseUrl = "https://api.example.com/v1",
            modelId = "configured-model-id",
            supportsText = true,
            supportsVision = true,
            allowInsecureHttp = false,
            enabled = true,
            lastTestedAt = null,
            lastTestStatus = AiTestStatus.UNTESTED,
            lastTestMessage = "",
            createdAt = 0,
            updatedAt = 0,
        )

        fun response(statusCode: Int, body: String) = AiHttpResponse(statusCode, body.encodeToByteArray())
        fun validChatBody(content: String): String =
            """{"choices":[{"message":{"content":${Json.encodeToString(content)}}}]}"""
    }
}
