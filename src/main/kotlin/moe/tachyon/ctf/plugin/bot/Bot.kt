package moe.tachyon.ctf.plugin.bot

import moe.tachyon.ctf.plugin.GameWorld
import moe.tachyon.ctf.plugin.LocationStatus
import moe.tachyon.ctf.plugin.randomId
import moe.tachyon.ctf.plugin.teleport
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning

abstract class Bot(
    val world: GameWorld
)
{
    companion object
    {
        fun randomBotName(): String =
            generateSequence { "CTF-Bot-" + randomId() }.first { Bukkit.getPlayer(it) == null }
    }

    val name: String by lazy(::randomBotName)

    private var init = true
    protected var player: Player? = null
        private set

    fun init()
    {
        submit(period = 1)
        {
            if (init)
            {
                init = false
                val bukkitWorld = world.bukkitWorld() ?: return@submit
                player = BotNMSHandler.instance.spawnFakePlayer(bukkitWorld, name, LocationStatus.IN_CENTER.location(world))
                player?.teleport(world, LocationStatus.IN_CENTER)
                world.addPlayer(name, if (world.leftGroup.isBot) world.leftGroup else world.rightGroup)
                return@submit
            }

            if (world.gameStatus.none())
            {
                submit(delay = 5) { player?.kickPlayer(null) }
                cancel()
                return@submit
            }

            if (world.gameStatus.preparing())
            {
                world.playerPrepared(name)
            }

            if (world.gameStatus.inGame())
            {
                runCatching()
                {
                    player?.let { BotNMSHandler.instance.tickBot(it, tick()) }
                }.onFailure(::warning)
            }
        }
    }

    abstract fun tick(): PlayerInput

    protected open fun getMap() = world.getMap()
}