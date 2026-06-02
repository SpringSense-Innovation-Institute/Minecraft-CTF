package moe.tachyon.ctf.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.mainWorld
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import taboolib.common.platform.function.submit
import taboolib.common.util.random
import taboolib.common.util.randomDouble
import taboolib.platform.BukkitPlugin
import taboolib.platform.util.title
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextULong

enum class LocationStatus
{
    IN_LEFT_FIELD,
    IN_RIGHT_FIELD,
    IN_LEFT_BASE,
    IN_RIGHT_BASE,
    IN_LEFT_PRISON,
    IN_RIGHT_PRISON,
    IN_CENTER,
    IN_FIELD,
    OUT_OF_FIELD,
    ;

    fun location(world: GameWorld): Location = location(world.bukkitWorld() ?: mainWorld)
    fun location(world: World): Location = when (this)
    {
        IN_LEFT_FIELD, IN_LEFT_BASE -> Location(world, -21.5, 1.0, 0.5)
        IN_RIGHT_FIELD, IN_RIGHT_BASE -> Location(world, 22.5, 1.0, 0.5)
        IN_LEFT_PRISON -> Location(world, -15.5, 1.0, 28.5)
        IN_RIGHT_PRISON -> Location(world, 16.5, 1.0, 28.5)
        IN_CENTER -> Location(world, 0.5, 1.0, 0.5)
        IN_FIELD -> Location(world, 0.5, 1.0, 0.5)
        OUT_OF_FIELD ->
        {
            val atX = randomDouble() < 0.5
            val tmp = randomDouble() < 0.5
            val x = if (atX) random(-24.0, 24.0) else random(-36.0, 36.0)

            val location = Location(
                world,
                if (atX) x else if (tmp) -24.0 else 24.0,
                30.0,
                if (atX) if (tmp) -36.0 else 36.0 else x,
            )

            val vec = Location(world, 0.5, 1.0, 0.5).toVector().subtract(location.toVector())

            location.direction = vec

            location
        }
    }

    fun contains(location: Location): Boolean
    {
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        return when (this)
        {
            OUT_OF_FIELD -> x !in -24..24 || z !in -36..36 || y !in -64..12
            IN_LEFT_FIELD -> x in -24..-1 && z in -36..36 && y in -64..12
            IN_RIGHT_FIELD -> x in 1..24 && z in -36..36 && y in -64..12
            IN_LEFT_BASE -> x in -23..-21 && z in -1..1 && y in -64..12
            IN_RIGHT_BASE -> x in 21..23 && z in -1..1 && y in -64..12
            IN_LEFT_PRISON -> x in -18..-14 && z in 26..30 && y in -64..12
            IN_RIGHT_PRISON -> x in 14..18 && z in 26..30 && y in -64..12
            IN_CENTER -> x == 0 && z in -36..36 && y in -64..12
            IN_FIELD -> x in -24..24 && z in -36..36 && y in -64..12
        }
    }
}

fun Entity.teleport(world: GameWorld, locationStatus: LocationStatus)
{
    if (locationStatus == LocationStatus.OUT_OF_FIELD && this is Player && this.allowFlight)
        isFlying = true
    else if (this is Player)
        isFlying = false

    teleport(locationStatus.location(world))
}

enum class PlayerTeam
{
    LEFT, RIGHT, NONE
}

/**
 * 判断一个坐标所处的位置状态
 * @param location 要判断的坐标
 * @return 一个LocationStatus枚举值，表示该坐标所处的位置状态
 */
fun locationStatus(location: Location): LocationStatus
{
    return listOf(
        LocationStatus.IN_LEFT_BASE,
        LocationStatus.IN_RIGHT_BASE,
        LocationStatus.IN_LEFT_PRISON,
        LocationStatus.IN_RIGHT_PRISON,
        LocationStatus.IN_CENTER,
        LocationStatus.IN_LEFT_FIELD,
        LocationStatus.IN_RIGHT_FIELD,
        LocationStatus.OUT_OF_FIELD,
    ).first()
    {
        it.contains(location)
    }
}

data class GameSettingData(
    var leftGroup: Group = Group.PlayerGroup(1),
    var rightGroup: Group = Group.PlayerGroup(1),
    var playerCountPerGroup: Int = 1,
    var randomMap: Boolean = true,
)

const val MIN_PLAYER_COUNT_PER_GROUP = 1
const val MAX_PLAYER_COUNT_PER_GROUP = 50

fun isValidPlayerCountPerGroup(count: Int): Boolean =
    count in MIN_PLAYER_COUNT_PER_GROUP..MAX_PLAYER_COUNT_PER_GROUP

fun requireValidPlayerCountPerGroup(count: Int)
{
    require(isValidPlayerCountPerGroup(count))
    {
        "每组玩家人数必须在 $MIN_PLAYER_COUNT_PER_GROUP 到 $MAX_PLAYER_COUNT_PER_GROUP 之间"
    }
}

private fun randomId() = Random.nextULong().toString(36).take(4).padStart(4, '0')

fun HumanEntity.replayRecord(file: File)
{
    var world: GameWorld? = null
    val record = GameRecord.file({ world!! }, file)
    world = generateGameWorld(
        name = "replay-${file.nameWithoutExtension}-${randomId()}",
        setting = GameSettingData(),
        record = record,
    )

    world.gameStatus = GameStatus.REPLAYING
    val location = Location(world.bukkitWorld(), 0.5, 1.0, 0.5)
    teleport(location)
    var count = 10
    submit(period = 20)
    {
        world.getBukkitPlayers().forEach()
        {
            it.title("§e即将开始回放 $count", "", 0, 20, 0)
        }
        count--
        if (count <= 0)
        {
            cancel()
            record.replay()
        }
    }
}

fun createGame(data: GameSettingData, timeout: Boolean): GameWorld
{
    requireValidPlayerCountPerGroup(data.playerCountPerGroup)

    var world: GameWorld? = null
    val date = Date()
    val format = SimpleDateFormat("yy-MM-dd_HH-mm-ss")
    val prefix = practiceMode?.name?.plus("_") ?: ""
    val record = GameRecord.file({ world!! }, "${prefix}${format.format(date)}_${data.leftGroup.displayName}-vs-${data.rightGroup.displayName}_${randomId()}")
    world = generateGameWorld(
        "game-${data.leftGroup.displayName}-vs-${data.rightGroup.displayName}-${randomId()}",
        data,
        record,
    )
    world.prepareGame(timeout)
    return world
}

fun HumanEntity.teleportToWorld(world: World = mainWorld)
{
    val sita = random(-Math.PI, Math.PI)
    val r = 5
    val x = r * cos(sita) + 0.5
    val z = r * sin(sita) + 0.5
    val loc = Location(world, x, 1.0, z)
    loc.direction = Vector(-x, 0.0, -z)
    teleport(loc)
}

fun getPlayerGroup(player: String): Int? =
    player.split("-").getOrNull(1)?.toIntOrNull()

fun Material?.isBanner(): Boolean =
    this == Material.BLUE_BANNER || this == Material.RED_BANNER

@Serializable
data class StatisticData(
    var withNoneTimes: MutableMap<Int, Long> = mutableMapOf(),
    var withBotTimes: MutableMap<Int, Long> = mutableMapOf(),
    var withOtherTimes: MutableMap<Int, Long> = mutableMapOf(),
    var catchTimes: MutableMap<Int, Long> = mutableMapOf(),
    var getFlagTimes: MutableMap<Int, Long> = mutableMapOf(),
    var totalTime: MutableMap<Int, Long> = mutableMapOf(),
    var winTimes: MutableMap<Int, Long> = mutableMapOf(),
)

private val statisticFileLock = Any()

private fun getStatisticFile(name: String?) =  BukkitPlugin.getInstance().dataFolder.resolve("statistics/${name ?: "main"}.dat")
fun getStatisticFiles() =
    BukkitPlugin.getInstance()
        .dataFolder
        .resolve("statistics")
        .listFiles()
        ?.filter { it.isFile && it.name.endsWith(".dat") }
        ?.map { it.nameWithoutExtension }
        ?: emptyList()

fun getStatisticData(name: String?): StatisticData = synchronized(statisticFileLock)
{
    val file = getStatisticFile(name)
    if (!file.exists()) return StatisticData()
    return@synchronized runCatching()
    {
        Json.decodeFromString<StatisticData>(file.readText())
    }.getOrElse()
    {
        StatisticData()
    }
}

private fun saveStatisticData(name: String?, data: StatisticData) = synchronized(statisticFileLock)
{
    val file = getStatisticFile(name)
    if (!file.parentFile.exists()) file.parentFile.mkdirs()
    file.writeText(Json.encodeToString(data))
}

fun updateStatisticData(
    name: String? = null,
    block: StatisticData.() -> Unit
) = synchronized(statisticFileLock)
{
    val data = getStatisticData(name)
    data.block()
    saveStatisticData(name, data)
}
