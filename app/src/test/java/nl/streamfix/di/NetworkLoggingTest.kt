package nl.streamfix.di

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class NetworkLoggingTest {
    @Test
    fun loggerOmitsPathQueryResponseMessageAndBody() {
        val messages = mutableListOf<String>()
        val payload = """{"username":"body-user","password":"body-secret","url":"/live/embedded-user/embedded-secret/123.ts"}"""
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkModule.passwordRedactingLogger { messages.add(it) })
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("provider-secret")
                    .body(payload.toResponseBody())
                    .build()
            }
            .build()
        val request = Request.Builder()
            .url("https://example.test/live/path-user/path-secret/123.ts?username=query-user&password=query-secret")
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(payload, response.body?.string())
        }
        assertEquals(2, messages.size)
        assertEquals("GET -> example.test", messages.first())
        assertTrue(messages.last().matches(Regex("<- HTTP 200; [0-9]+ ms")))
        for (secret in listOf("path-user", "path-secret", "query-user", "query-secret", "body-user", "body-secret", "embedded-secret", "provider-secret")) {
            assertFalse(messages.joinToString().contains(secret))
        }
    }
}
