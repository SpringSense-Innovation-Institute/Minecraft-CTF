package moe.tachyon.ctf.plugin

import com.sun.net.httpserver.HttpServer
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.common.util.random
import taboolib.platform.BukkitPlugin
import java.net.InetSocketAddress

object MinecraftCtfPlugin: Plugin()
{
    val recordsFolder by lazy { BukkitPlugin.getInstance().dataFolder.resolve("records") }

    var matchTask: PlatformExecutor.PlatformTask? = null

    val server by lazy { HttpServer.create(InetSocketAddress(8964), 0) }

    override fun onEnable()
    {
        matchTask = submit(period = 40, async = false)
        {
            processWaitList()
        }

        setupServer()

        info("Successfully running Minecraft-CTF-Plugin!")
    }

    override fun onDisable()
    {
        onlinePlayers().forEach()
        {
            it.kick("服务器关闭")
        }
        getGameWorlds().forEach()
        {
            removeGameWorld(it.name)
        }
        matchTask?.cancel()
        server.stop(0)
    }
}

data class PracticeMode(
    val name: String = "Practice_" + random(0, 10000),
    val enemy: Enemy? = null,
    val mapType: MapType? = null,
    val playersPerGroup: Int? = null,
)

var practiceMode: PracticeMode? = null

enum class GameStatus
{
    NONE,
    REPLAYING,
    PREPARING,
    IN_GAME,
    ;

    fun none() = this == NONE
    fun inGame() = this == IN_GAME
    fun preparing() = this == PREPARING
    fun replaying() = this == REPLAYING
}