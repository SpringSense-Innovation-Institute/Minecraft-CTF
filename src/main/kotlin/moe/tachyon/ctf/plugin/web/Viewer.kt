package moe.tachyon.ctf.plugin.web

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.GameWorld
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin
import moe.tachyon.ctf.plugin.PlayerTeam
import moe.tachyon.ctf.plugin.getGameWorlds
import org.bukkit.Bukkit
import org.intellij.lang.annotations.Language
import taboolib.common.platform.function.submit
import java.io.IOException

fun HttpServer.viewer2d()
{
    createContext("/viewer2d")
    { exchange ->
        val html = MinecraftCtfPlugin.javaClass.getResourceAsStream("/web/viewer2d.html")?.readAllBytes() ?: return@createContext exchange.sendResponseHeaders(404, -1)
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        val os = exchange.responseBody
        os.write(html)
        os.close()
    }

    createContext("/viewer2d/sse")
    { exchange ->
        val player = SessionManager.getPlayerIdFromCookie(exchange)
            ?: return@createContext exchange.sendResponseHeaders(401, -1)

        exchange.sseHeaders()

        submit(period = 5) loop@
        {
            val world = getGameWorlds().firstOrNull()
            {
                (player in it.leftGroupPlayers() || player in it.rightGroupPlayers()) && !it.gameStatus.none()
            } ?: return@loop

            val map = world.getMap()

            val data = GameData(
                myTeam = if (player in world.leftGroupPlayers()) "red" else "blue",
                map = map,
                players = world.getPlayers()
                    .mapNotNull(Bukkit::getPlayer)
                    .filter { world.getPlayerTeam(it.name) != PlayerTeam.NONE }
                    .associate { it.name to GameData.PlayerInfo(it.location.x + 24, it.location.z + 36, if (world.getPlayerTeam(it.name) == PlayerTeam.LEFT) "red" else "blue") },
                cows = world.getCows().map { GameData.Position(it.first + 24, it.second + 36) },
                silverfish = world.getSilverfish().map { GameData.Position(it.first + 24, it.second + 36) },
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
}

@Serializable
data class GameData(
    val myTeam: String,
    val map: List<List<GameWorld.MapElement>>,
    val players: Map<String, PlayerInfo>,
    val cows: List<Position>,
    val silverfish: List<Position>,
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