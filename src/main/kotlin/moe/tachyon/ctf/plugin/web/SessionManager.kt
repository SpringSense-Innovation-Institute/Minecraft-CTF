package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpExchange
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SessionManager
{
    private val sessions = ConcurrentHashMap<String, String>() // token -> playerId

    fun createSession(playerId: String): String
    {
        val token = UUID.randomUUID().toString()
        sessions[token] = playerId
        return token
    }

    fun getPlayerId(token: String): String? = sessions[token]

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
}
