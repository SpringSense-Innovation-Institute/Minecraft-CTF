package moe.tachyon.ctf.plugin

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.web.SessionManager
import moe.tachyon.ctf.plugin.web.setupServer
import org.bukkit.Bukkit
import org.bukkit.World
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
    var sessionCleanupTask: PlatformExecutor.PlatformTask? = null

    val config by lazy()
    {
        val file = BukkitPlugin.getInstance().dataFolder.resolve("config.json")
        if (!file.exists())
        {
            file.parentFile?.mkdirs()
            file.writeText(Json.encodeToString(PluginConfig()))
            return@lazy PluginConfig()
        }
        else
        {
            runCatching()
            {
                Json.decodeFromString<PluginConfig>(file.readText())
            }.getOrElse()
            {
                file.writeText(Json.encodeToString(PluginConfig()))
                return@lazy PluginConfig()
            }
        }
    }
    private val server: HttpServer by lazy { HttpServer.create(InetSocketAddress(config.webPort), 0) }
    val mainWorld: World by lazy { Bukkit.getServer().worlds.first { it.environment == World.Environment.NORMAL } }

    override fun onEnable()
    {
        matchTask = submit(period = 40, async = false)
        {
            processWaitList()
        }

        trekTask = submit(period = 4, async = false)
        {
            val players = mainWorld.players
            players.forEach()
            {
                if ("文明终点：黑曜石" in it.inventory.itemInMainHand.getName())
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

        sessionCleanupTask = submit(period = 6000, async = true)
        {
            SessionManager.cleanExpiredSessions()
        }

        server.setupServer()

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
        sessionCleanupTask?.cancel()
        SessionManager.clearAllSessions()
        server.stop(0)
    }
}

@Serializable
data class PluginConfig(
    val webPort: Int = 8001,
    val adminPassword: String = "password123",
)

data class PracticeMode(
    val name: String = "Practice_" + random(0, 10000),
    val enemy: Enemy? = null,
    val mapType: MapType? = null,
    val playersPerGroup: Int? = null,
)

@Volatile
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
    val displayName: String
        get() = when (this)
        {
            is PlayerGroup   -> "Group${id}"
            None             -> "none"
            is Bot           -> displayName
        }

    val isNone get() = this is None
    val isBot get() = this is Bot
    val isPlayer get() = this is PlayerGroup

    val id: Int? get() = null

    data class PlayerGroup(override val id: Int): Group
    data object None: Group

    enum class Bot(val selectId: String, override val displayName: String): Group
    {
        DEFAULT("bot", "Default"),
        DEFAULT_JUMP("jump-bot", "DefaultJump"),
        DEFAULT_SNEAK("sneak-bot", "DefaultSneak"),
        SIMPLE("simple", "Simple"),
        SIMPLE_JUMP("simple-jump", "SimpleJump"),
        TRIVIAL_BOT("trivial", "Trivial")
        ;
    }
}
