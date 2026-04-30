package moe.tachyon.ctf.plugin.bot

import moe.tachyon.ctf.plugin.GameWorld
import moe.tachyon.ctf.plugin.LocationStatus
import moe.tachyon.ctf.plugin.teleport
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import kotlin.random.Random
import kotlin.random.nextULong

abstract class Bot(
    val world: GameWorld
)
{
    companion object
    {
        fun randomBotName() = "CTF-Bot-" + Random.nextULong().toString(36).take(4).padStart(4, '0')
    }

    val name: String = randomBotName()

    private var init = true
    private var tickCount = 0
    protected var player: Player? = null
        private set

    fun init()
    {
        submit(period = 1)
        {
            tickCount++
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
                player?.kickPlayer(null)
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

    enum class MapElement
    {
        LEFT_FLAG,
        LEFT_TARGET,
        LEFT_TARGET_WITH_FLAG,
        RIGHT_FLAG,
        RIGHT_TARGET,
        RIGHT_TARGET_WITH_FLAG,

        BLANK,
        OBSTACLE,
        ;
        fun passable(): Boolean
        {
            return this == BLANK || this == LEFT_TARGET || this == RIGHT_TARGET
        }
    }

    private val updateInterval = 5
    private var buf = listOf<List<MapElement>>()
    private var bufTick = - updateInterval - 1

    protected open fun getMap(): List<List<MapElement>>
    {
        if (tickCount - bufTick <= updateInterval)
            return buf

        val map = (-24..24).map()
        { x ->
            (-36..36).map()
            { z ->
                getElement(x, z)
            }
        }
        buf = map
        bufTick = tickCount
        return map
    }

    private fun getElement(x: Int, z: Int): MapElement
    {
        val location = Location(world.bukkitWorld(), x.toDouble(), 0.0, z.toDouble())

        val isTarget = location.block.type == Material.GOLD_BLOCK
        val isLeftFlag = location.clone().add(0.0, 2.0, 0.0).block.type == Material.RED_BANNER
        val isRightFlag = location.clone().add(0.0, 2.0, 0.0).block.type == Material.BLUE_BANNER

        return when
        {
            isLeftFlag && isTarget  -> MapElement.LEFT_TARGET_WITH_FLAG
            isLeftFlag              -> MapElement.LEFT_FLAG
            isRightFlag && isTarget -> MapElement.RIGHT_TARGET_WITH_FLAG
            isRightFlag             -> MapElement.RIGHT_FLAG
            isTarget                -> if (x < 0) MapElement.LEFT_TARGET else MapElement.RIGHT_TARGET
            else                    ->
            {
                if (!location.clone().add(0.0, 1.0, 0.0).block.isPassable) MapElement.OBSTACLE
                else if (!location.clone().add(0.0, 2.0, 0.0).block.isPassable) MapElement.OBSTACLE
                else MapElement.BLANK
            }
        }
    }
}