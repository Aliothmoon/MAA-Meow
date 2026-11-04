package com.aliothmoon.maameow.web.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiAuthTest {

    private class MemoryStore(var saved: List<WebSession> = emptyList()) : WebUiAuth.SessionStore {
        override suspend fun load() = saved
        override suspend fun save(sessions: List<WebSession>) {
            saved = sessions
        }
    }

    private var now = 1_000_000L
    private val store = MemoryStore()
    private val auth = WebUiAuth(store, clock = { now })

    private suspend fun pairOk(name: String = "Chrome"): String {
        val code = auth.newPairingCode().code
        val result = auth.pair(code, name)
        return (result as WebUiAuth.PairResult.Success).token
    }

    @Test
    fun `pairing issues a token that verifies and stores only its hash`() = runTest {
        val token = pairOk()

        assertTrue(auth.verify(token))
        assertEquals(listOf(WebUiAuth.hash(token)), store.saved.map { it.hash })
        assertFalse(store.saved.single().hash.contains(token))
        assertNull("码用过即作废", auth.pairingCode.value)
    }

    @Test
    fun `code cannot be reused`() = runTest {
        val code = auth.newPairingCode().code
        auth.pair(code, "a")

        assertEquals(WebUiAuth.PairResult.NoCode, auth.pair(code, "b"))
    }

    @Test
    fun `expired code is rejected`() = runTest {
        val code = auth.newPairingCode().code
        now += WebUiAuth.CODE_TTL_MS

        assertEquals(WebUiAuth.PairResult.NoCode, auth.pair(code, "a"))
    }

    @Test
    fun `code is burned after too many wrong attempts`() = runTest {
        val code = auth.newPairingCode().code
        val wrong = if (code == "000000") "111111" else "000000"
        repeat(WebUiAuth.MAX_ATTEMPTS - 1) {
            assertEquals(WebUiAuth.PairResult.Invalid, auth.pair(wrong, "x"))
        }
        assertEquals(WebUiAuth.PairResult.NoCode, auth.pair(wrong, "x"))

        assertEquals(WebUiAuth.PairResult.NoCode, auth.pair(code, "x"))
    }

    @Test
    fun `revoked or unknown tokens fail`() = runTest {
        val kept = pairOk("kept")
        val revoked = pairOk("revoked")
        auth.revokeToken(revoked)

        assertTrue(auth.verify(kept))
        assertFalse(auth.verify(revoked))
        assertFalse(auth.verify("not-a-token"))
        assertFalse(auth.verify(null))
        assertFalse(auth.verify(""))
    }

    @Test
    fun `sessions survive reload from store`() = runTest {
        val token = pairOk()
        val reloaded = WebUiAuth(MemoryStore(store.saved), clock = { now })
        reloaded.load()

        assertTrue(reloaded.verify(token))
    }

    @Test
    fun `oldest session is evicted at the cap`() = runTest {
        val first = pairOk("first")
        repeat(WebUiAuth.MAX_SESSIONS - 1) {
            now += 1
            pairOk("n$it")
        }
        now += 1
        val last = pairOk("last")

        assertEquals(WebUiAuth.MAX_SESSIONS, auth.sessions.value.size)
        assertFalse(auth.verify(first))
        assertTrue(auth.verify(last))
    }

    @Test
    fun `last seen is persisted at most hourly`() = runTest {
        val token = pairOk()
        val pairedAt = store.saved.single().lastSeenAt

        now += WebUiAuth.LAST_SEEN_PERSIST_INTERVAL_MS - 1
        auth.verify(token)
        assertEquals(pairedAt, store.saved.single().lastSeenAt)

        now += 1
        auth.verify(token)
        assertEquals(now, store.saved.single().lastSeenAt)
    }

    @Test
    fun `device name is trimmed and capped`() = runTest {
        pairOk("  " + "x".repeat(200) + "  ")

        assertEquals(WebUiAuth.NAME_MAX_LENGTH, store.saved.single().name.length)
    }
}
