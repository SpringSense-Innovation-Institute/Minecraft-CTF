package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin
import moe.tachyon.ctf.plugin.group.getAllPlayers
import moe.tachyon.ctf.plugin.group.getPlayerById

fun HttpServer.login()
{
    // Serve login.html
    createContext("/login")
    { exchange ->
        val html = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/login.html")?.readAllBytes()
            ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(html)
        os.close()
    }

    // POST /login/auth - player login
    createContext("/login/auth")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        @Serializable
        data class LoginData(val id: String, val password: String)

        try
        {
            val data = Json.decodeFromString<LoginData>(body)
            val player = getAllPlayers().find { it.id == data.id }
                ?: return@createContext run {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.responseBody.close()
                }

            if (player.password.isNotEmpty() && player.password != data.password)
            {
                exchange.sendResponseHeaders(401, -1)
                exchange.responseBody.close()
                return@createContext
            }

            val token = SessionManager.createSession(player.id)
            val response = Json.encodeToString(
                mapOf("id" to player.id, "name" to player.name)
            ).toByteArray()

            exchange.responseHeaders.add("Set-Cookie", "session=$token; Path=/; HttpOnly")
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            val os = exchange.responseBody
            os.write(response)
            os.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, -1)
            exchange.responseBody.close()
        }
    }

    // GET /login/check - verify session, return user info
    createContext("/login/check")
    { exchange ->
        val playerId = SessionManager.getPlayerIdFromCookie(exchange)
        if (playerId == null)
        {
            exchange.sendResponseHeaders(401, -1)
            exchange.responseBody.close()
            return@createContext
        }
        val player = getPlayerById(playerId)
        if (player == null)
        {
            exchange.sendResponseHeaders(401, -1)
            exchange.responseBody.close()
            return@createContext
        }
        val r = Json.encodeToString(
            mapOf("id" to player.id, "name" to player.name)
        ).toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, r.size.toLong())
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }

    // POST /login/logout - player logout
    createContext("/login/logout")
    { exchange ->
        val cookieHeader = exchange.requestHeaders.getFirst("Cookie") ?: ""
        val token = cookieHeader.split(";")
            .map { it.trim() }
            .find { it.startsWith("session=") }
            ?.removePrefix("session=")

        if (token != null) SessionManager.removeSession(token)

        exchange.responseHeaders.add("Set-Cookie", "session=; Path=/; HttpOnly; Max-Age=0")
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.close()
    }
}