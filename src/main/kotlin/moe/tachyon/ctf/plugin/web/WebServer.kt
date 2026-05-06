package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpExchange
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.*
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.server
import moe.tachyon.ctf.plugin.group.createGroup
import moe.tachyon.ctf.plugin.group.getGroups
import org.bukkit.Bukkit
import taboolib.common.platform.function.submit
import java.io.IOException

private fun HttpExchange.args() =
    requestURI.query
        ?.split("&")
        ?.map { it.split("=") }
        ?.filter { it.size == 2 }
        ?.associate { it[0] to it[1] } ?: emptyMap()

fun setupServer()
{
    server.createContext("/statistics")
    { exchange ->
        exchange.sendResponseHeaders(200, statisticsHtml.toByteArray().size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(statisticsHtml.toByteArray())
        os.close()
    }

    server.createContext("/statistics_data")
    { exchange ->
        val args = exchange.args()
        val statistic = getStatisticData(args["name"] ?: practiceMode?.name)
        val r = Json.encodeToString(statistic).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }

    server.createContext("/list")
    { exchange ->
        val list = getStatisticFiles()
        val r = Json.encodeToString(list).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }

    server.createContext("/")
    { exchange ->
        exchange.sendResponseHeaders(200, viewerHtml.toByteArray().size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(viewerHtml.toByteArray())
        os.close()
    }

    server.createContext("/sse")
    { exchange ->
        val args = exchange.args()
        val group = args["group"]?.toIntOrNull() ?: return@createContext

        exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=UTF-8")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.responseHeaders.add("Connection", "keep-alive")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, 0)

        submit(period = 5) loop@
        {
            val world = getGameWorlds().firstOrNull()
            {
                (it.leftGroup.id == group || it.rightGroup.id == group) && it.gameStatus.inGame()
            } ?: return@loop

            val map = world.getMap()

            val data = GameData(
                myTeam = if (world.leftGroup.id == group) "red" else "blue",
                map = map,
                players = world.getPlayers()
                    .mapNotNull(Bukkit::getPlayer)
                    .filter { world.getPlayerTeam(it.name) != PlayerTeam.NONE }
                    .associate { it.name to GameData.PlayerInfo(it.location.x + 24, it.location.z + 36, if (world.getPlayerTeam(it.name) == PlayerTeam.LEFT) "red" else "blue") },
                cows = world.getCows().map { GameData.Position(it.first + 24, it.second + 36) },
                leftScore = map.flatten().count { it == GameWorld.MapElement.RIGHT_TARGET_WITH_FLAG },
                rightScore = map.flatten().count { it == GameWorld.MapElement.LEFT_TARGET_WITH_FLAG },
            )

            val json = Json.encodeToString(data)
            submit(async = true)
            {
                try
                {
                    exchange.responseBody.write("data: $json\n\n".toByteArray())
                    exchange.responseBody.flush()
                }
                catch (_: IOException)
                {
                    this@loop.cancel()
                }
            }
        }
    }

    @Serializable
    data class RegisterData(
        val name: String,
        val players: List<String>,
    )

    server.createContext("/register")
    { exchange ->
        exchange.sendResponseHeaders(200, registerHtml.toByteArray().size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(registerHtml.toByteArray())
        os.close()
    }

    server.createContext("/registerGroup")
    { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        val data = Json.decodeFromString<RegisterData>(body)
        val group = try
        {
            createGroup(data.name, data.players)
        }
        catch (e: Throwable)
        {
            exchange.sendResponseHeaders(400, e.message?.toByteArray()?.size?.toLong() ?: 0)
            val os = exchange.responseBody
            os.write(e.message?.toByteArray() ?: ByteArray(0))
            os.close()
            return@createContext
        }
        val r = group.toString()
        exchange.sendResponseHeaders(200, r.toByteArray().size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=UTF-8")
        val os = exchange.responseBody
        os.write(r.toByteArray())
        os.close()
    }

    server.createContext("/groups")
    { exchange ->
        val groups = getGroups()
        val r = Json.encodeToString(groups).toByteArray()
        exchange.sendResponseHeaders(200, r.size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")
        val os = exchange.responseBody
        os.write(r)
        os.close()
    }

    server.executor = null
    server.start()
}

@Serializable
data class GameData(
    val myTeam: String,
    val map: List<List<GameWorld.MapElement>>,
    val players: Map<String, PlayerInfo>,
    val cows: List<Position>,
    val leftScore: Int,
    val rightScore: Int,
)
{
    @Serializable
    data class Position(
        val x: Double,
        val y: Double,
    )

    @Serializable
    data class PlayerInfo(
        val x: Double,
        val y: Double,
        val team: String,
    )
}