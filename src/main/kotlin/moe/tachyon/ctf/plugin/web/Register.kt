package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin
import moe.tachyon.ctf.plugin.group.createGroup
import moe.tachyon.ctf.plugin.group.getGroups

fun HttpServer.register()
{
    @Serializable
    data class RegisterData(
        val name: String,
        val players: List<String>,
    )

    createContext("/register")
    { exchange ->
        val html = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/register.html")?.readAllBytes() ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(html)
        os.close()
    }

    createContext("/registerGroup")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        try
        {
            val data = Json.decodeFromString<RegisterData>(body)
            val group = createGroup(data.name, data.players)
            val r = group.toString()
            exchange.sendResponseHeaders(200, r.toByteArray().size.toLong())
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=UTF-8")
            val os = exchange.responseBody
            os.write(r.toByteArray())
            os.close()
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
            return@createContext
        }
    }

    createContext("/groups")
    { exchange ->
        val groups = getGroups()
        val r = Json.encodeToString(groups).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }
}
