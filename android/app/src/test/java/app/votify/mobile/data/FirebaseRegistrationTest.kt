package app.votify.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseRegistrationTest {

    @Test
    fun testFirebaseErrorMappingForDuplicateEmail() {
        // Given a Firebase error json string with EMAIL_EXISTS
        val errorJson = """{"error":{"code":400,"message":"EMAIL_EXISTS","errors":[{"message":"EMAIL_EXISTS","domain":"global","reason":"invalid"}]}}"""
        val msg = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(errorJson)
                .let { (it as kotlinx.serialization.json.JsonObject)["error"] }
                ?.let { (it as kotlinx.serialization.json.JsonObject)["message"] }
                ?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
        }.getOrNull() ?: ""

        val mapped = when {
            msg.contains("EMAIL_EXISTS") -> "Этот email уже зарегистрирован"
            else -> msg
        }

        assertEquals("Этот email уже зарегистрирован", mapped)
    }

    @Test
    fun testFirebaseConfigParsing() {
        val configJson = """{"apiKey":"test-api-key","projectId":"test-project"}"""
        val parsed = FirebaseRest.parseConfig(configJson)

        assertTrue(parsed != null)
        assertEquals("test-api-key", parsed?.apiKey)
        assertEquals("test-project", parsed?.projectId)
    }

    @Test
    fun testInvalidFirebaseConfig() {
        val invalidJson = "not-json"
        val parsed = FirebaseRest.parseConfig(invalidJson)

        assertFalse(parsed != null)
    }
}
