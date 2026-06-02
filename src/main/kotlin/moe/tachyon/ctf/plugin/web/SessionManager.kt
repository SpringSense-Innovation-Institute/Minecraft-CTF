package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpExchange
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SessionManager
{
    private const val SESSION_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    private data class SessionEntry(val playerId: String, val createdAt: Long)

    private val sessions = ConcurrentHashMap<String, SessionEntry>()

    fun createSession(playerId: String): String
    {
        val token = UUID.randomUUID().toString()
        sessions[token] = SessionEntry(playerId, System.currentTimeMillis())
        return token
    }

    fun getPlayerId(token: String): String?
    {
        val entry = sessions[token] ?: return null
        if (System.currentTimeMillis() - entry.createdAt > SESSION_TTL_MS)
        {
            sessions.remove(token)
            return null
        }
        return entry.playerId
    }

    fun removeSession(token: String)
    {
        sessions.remove(token)
    }

    fun getPlayerIdFromCookie(exchange: HttpExchange): String?
    {
        val cookieHeader = exchange.requestHeaders.getFirst("Cookie") ?: return null
        val sessionToken = cookieHeader.split(";")
            .map { it.trim() }
            .find { it.startsWith("session=") }
            ?.removePrefix("session=")
            ?: return null
        return getPlayerId(sessionToken)
    }

    fun cleanExpiredSessions()
    {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { now - it.value.createdAt > SESSION_TTL_MS }
    }

    fun clearAllSessions()
    {
        sessions.clear()
    }
}
