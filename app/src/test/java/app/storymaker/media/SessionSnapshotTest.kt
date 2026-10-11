package app.storymaker.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSnapshotTest {

    private val json = SessionSnapshot.json
    private val snapshot = SessionSnapshot(
        sessionId = "abc",
        createdAt = 1_700_000_000_000,
        photoWidth = 2160,
        photoHeight = 3840,
        caption = "Hello #world",
        showOnPhoto = false,
        copyToClipboard = true,
        revision = 7,
        exportedRevision = 5,
        sharedAt = 1_700_000_100_000,
    )

    @Test
    fun roundTrip_preservesAllFields() {
        val encoded = json.encodeToString(SessionSnapshot.serializer(), snapshot)
        assertEquals(snapshot, json.decodeFromString(SessionSnapshot.serializer(), encoded))
    }

    @Test
    fun encoding_writesVersion() {
        val encoded = json.encodeToString(SessionSnapshot.serializer(), snapshot)
        assertTrue(encoded.contains("\"version\":${SessionSnapshot.CURRENT_VERSION}"))
    }

    @Test
    fun unknownKeys_areIgnored() {
        val encoded = """{"version":1,"sessionId":"abc","createdAt":1,"photoWidth":10,"photoHeight":20,"futureField":[1,2]}"""
        assertEquals("abc", json.decodeFromString(SessionSnapshot.serializer(), encoded).sessionId)
    }

    @Test
    fun missingOptionalFields_takeDefaults() {
        val encoded = """{"sessionId":"abc","createdAt":1,"photoWidth":10,"photoHeight":20}"""
        val decoded = json.decodeFromString(SessionSnapshot.serializer(), encoded)
        assertEquals(SessionSnapshot(sessionId = "abc", createdAt = 1, photoWidth = 10, photoHeight = 20), decoded)
        assertEquals(true, decoded.showOnPhoto)
        assertEquals(false, decoded.copyToClipboard)
    }
}
