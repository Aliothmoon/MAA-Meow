package com.aliothmoon.maameow.web.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Serializable
data class WebSession(
    /** token 的 SHA-256，原文不落盘 */
    val hash: String,
    val name: String,
    val createdAt: Long,
    val lastSeenAt: Long,
)

/** App 内出一次性配对码，网页拿码换长期 token */
class WebUiAuth(
    private val store: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    interface SessionStore {
        suspend fun load(): List<WebSession>
        suspend fun save(sessions: List<WebSession>)
    }

    data class PairingCode(val code: String, val expiresAt: Long)

    sealed interface PairResult {
        data class Success(val token: String) : PairResult

        /** 码错了，还能再试 */
        data object Invalid : PairResult

        /** 没有可用的码：未生成、过期或试错次数用完 */
        data object NoCode : PairResult
    }

    companion object {
        const val CODE_LENGTH = 6
        const val CODE_TTL_MS = 3 * 60_000L
        const val MAX_ATTEMPTS = 5
        const val MAX_SESSIONS = 20
        const val NAME_MAX_LENGTH = 64

        // lastSeen 只用于展示，写盘节流
        const val LAST_SEEN_PERSIST_INTERVAL_MS = 60 * 60_000L

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    private class Pending(val code: String, val expiresAt: Long, var attemptsLeft: Int)

    private val mutex = Mutex()
    private var pending: Pending? = null
    private var loaded = false

    private val _pairingCode = MutableStateFlow<PairingCode?>(null)
    val pairingCode: StateFlow<PairingCode?> = _pairingCode.asStateFlow()

    private val _sessions = MutableStateFlow<List<WebSession>>(emptyList())
    val sessions: StateFlow<List<WebSession>> = _sessions.asStateFlow()

    suspend fun load() = mutex.withLock { ensureLoaded() }

    suspend fun newPairingCode(): PairingCode = mutex.withLock {
        val code = (0 until CODE_LENGTH).joinToString("") { random.nextInt(10).toString() }
        val expiresAt = clock() + CODE_TTL_MS
        pending = Pending(code, expiresAt, MAX_ATTEMPTS)
        PairingCode(code, expiresAt).also { _pairingCode.value = it }
    }

    suspend fun cancelPairing() = mutex.withLock { clearPending() }

    suspend fun pair(code: String, name: String): PairResult = mutex.withLock {
        ensureLoaded()
        val current = pending
        if (current == null || clock() >= current.expiresAt) {
            clearPending()
            return@withLock PairResult.NoCode
        }
        if (!MessageDigest.isEqual(code.trim().toByteArray(), current.code.toByteArray())) {
            current.attemptsLeft--
            if (current.attemptsLeft <= 0) {
                clearPending()
                return@withLock PairResult.NoCode
            }
            return@withLock PairResult.Invalid
        }
        clearPending()

        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val now = clock()
        val session = WebSession(
            hash = hash(token),
            name = name.trim().take(NAME_MAX_LENGTH).ifEmpty { "Browser" },
            createdAt = now,
            lastSeenAt = now,
        )
        val kept = _sessions.value.sortedByDescending { it.lastSeenAt }.take(MAX_SESSIONS - 1)
        commit(kept + session)
        PairResult.Success(token)
    }

    suspend fun verify(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        val hash = hash(token)
        val session = _sessions.value.firstOrNull { it.hash == hash } ?: return false
        val now = clock()
        if (now - session.lastSeenAt >= LAST_SEEN_PERSIST_INTERVAL_MS) {
            mutex.withLock {
                commit(_sessions.value.map { if (it.hash == hash) it.copy(lastSeenAt = now) else it })
            }
        }
        return true
    }

    suspend fun revokeToken(token: String) = revoke(hash(token))

    suspend fun revoke(hash: String) = mutex.withLock {
        ensureLoaded()
        commit(_sessions.value.filterNot { it.hash == hash })
    }

    suspend fun revokeAll() = mutex.withLock {
        ensureLoaded()
        commit(emptyList())
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _sessions.value = store.load()
        loaded = true
    }

    private suspend fun commit(sessions: List<WebSession>) {
        _sessions.value = sessions
        store.save(sessions)
    }

    private fun clearPending() {
        pending = null
        _pairingCode.value = null
    }
}
