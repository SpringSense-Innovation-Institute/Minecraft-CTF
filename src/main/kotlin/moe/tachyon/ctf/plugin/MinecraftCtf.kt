package moe.tachyon.ctf.plugin

import com.sun.net.httpserver.HttpServer
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.common.util.random
import taboolib.module.nms.getName
import taboolib.platform.BukkitPlugin
import taboolib.platform.util.title
import java.net.InetSocketAddress

object MinecraftCtfPlugin: Plugin()
{
    val recordsFolder by lazy { BukkitPlugin.getInstance().dataFolder.resolve("records") }

    var matchTask: PlatformExecutor.PlatformTask? = null
    var trekTask: PlatformExecutor.PlatformTask? = null

    val server by lazy { HttpServer.create(InetSocketAddress(8964), 0) }
    val mainWorld = Bukkit.getServer().worlds.first { it.environment == World.Environment.NORMAL }

    override fun onEnable()
    {
        matchTask = submit(period = 40, async = false)
        {
            processWaitList()
        }

        trekTask = submit(period = 4, async = false)
        {
            val players = onlinePlayers().filter { it.world == mainWorld.name }.map { it.cast<Player>() }
            players.forEach()
            {
                if (it.inventory.itemInMainHand.getName() == "文明终点：黑曜石")
                {
                    it.inventory.clear()
                    players.forEach()
                    { p ->
                        p.sendMessage("§a恭喜玩家 ${it.name} 夺得黑曜石！§r")
                        p.title("§6${it.name} §a夺得黑曜石！", null, 10, 70, 20)
                    }
                }
            }
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
        trekTask?.cancel()
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

sealed interface Group
{
    val name: String
        get() = when (this)
        {
            is PlayerGroup  -> "Group$id"
            None            -> "none"
            Bot.DEFAULT     -> "DefaultBot"
            Bot.JUMP_SPRINT -> "JumpSprintBot"
        }

    val isNone get() = this is None
    val isBot get() = this is Bot
    val isPlayer get() = this is PlayerGroup

    val id: Int? get() = null

    data class PlayerGroup(override val id: Int): Group
    data object None: Group
    enum class Bot: Group
    {
        DEFAULT,
        JUMP_SPRINT,
    }
}