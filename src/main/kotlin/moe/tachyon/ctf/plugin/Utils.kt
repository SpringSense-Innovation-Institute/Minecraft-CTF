package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.server
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.util.random
import taboolib.common.util.randomDouble
import taboolib.platform.BukkitPlugin
import taboolib.platform.util.title
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    fun location(world: GameWorld): Location = when (this)
    {
        IN_LEFT_FIELD, IN_LEFT_BASE -> Location(world.bukkitWorld(), -21.5, 1.0, 0.5)
        IN_RIGHT_FIELD, IN_RIGHT_BASE -> Location(world.bukkitWorld(), 22.5, 1.0, 0.5)
        IN_LEFT_PRISON -> Location(world.bukkitWorld(), -15.5, 1.0, 28.5)
        IN_RIGHT_PRISON -> Location(world.bukkitWorld(), 16.5, 1.0, 28.5)
        IN_CENTER -> Location(world.bukkitWorld(), 0.5, 1.0, 0.5)
        IN_FIELD -> Location(world.bukkitWorld(), 0.5, 1.0, 0.5)
        OUT_OF_FIELD ->
        {
            val atX = randomDouble() < 0.5
            val tmp = randomDouble() < 0.5
            val x = if (atX) random(-24.0, 24.0) else random(-36.0, 36.0)

            val location = Location(
                world.bukkitWorld(),
                if (atX) x else if (tmp) -24.0 else 24.0,
                30.0,
                if (atX) if (tmp) -36.0 else 36.0 else x,
            )

            val vec = Location(world.bukkitWorld(), 0.5, 1.0, 0.5).toVector().subtract(location.toVector())

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
            OUT_OF_FIELD -> x !in -24..24 || z !in -36..36 || y !in -64..20
            IN_LEFT_FIELD -> x in -24..-1 && z in -36..36 && y in -64..20
            IN_RIGHT_FIELD -> x in 1..24 && z in -36..36 && y in -64..20
            IN_LEFT_BASE -> x in -23..-21 && z in -1..1 && y in -64..20
            IN_RIGHT_BASE -> x in 21..23 && z in -1..1 && y in -64..20
            IN_LEFT_PRISON -> x in -18..-14 && z in 26..30 && y in -64..20
            IN_RIGHT_PRISON -> x in 14..18 && z in 26..30 && y in -64..20
            IN_CENTER -> x == 0 && z in -36..36 && y in -64..20
            IN_FIELD -> x in -24..24 && z in -36..36 && y in -64..20
        }
    }
}

fun Entity.teleport(world: GameWorld, locationStatus: LocationStatus)
{
    if (locationStatus == LocationStatus.OUT_OF_FIELD && this is Player)
        isFlying = true
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
    var leftGroupNumber: Int = 1,
    var rightGroupNumber: Int = 1,
    var playerCountPerGroup: Int = 1,
    var randomMap: Boolean = true,
)

private fun randomId() = (System.currentTimeMillis()%0x1000000).toString(16)

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
        world.getPlayers().forEach()
        {
            val player = Bukkit.getPlayer(it) ?: return@forEach
            player.title("§e即将开始回放 $count", "", 0, 20, 0)
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
    var world: GameWorld? = null
    val date = Date()
    val format = SimpleDateFormat("yy-MM-dd_HH-mm-ss")
    val prefix = practiceMode?.name?.plus("_") ?: ""
    val record = GameRecord.file({ world!! }, "${prefix}${format.format(date)}_${data.leftGroupNumber}vs${data.rightGroupNumber}_${randomId()}")
    world = generateGameWorld(
        "game-${data.leftGroupNumber}vs${data.rightGroupNumber}-${randomId()}",
        data,
        record,
    )
    world.prepareGame(timeout)
    return world
}

fun HumanEntity.teleportToWorld(world: World = Bukkit.getWorld("world")!!)
{
    teleport(Location(world, 0.5, 1.0, 0.5))
}

fun getPlayerGroup(player: String): Int? =
    player.split("-").getOrNull(1)?.toIntOrNull()

data class StatisticData(
    var withNoneTimes: MutableMap<Int, Long> = mutableMapOf(),
    var withOtherTimes: MutableMap<Int, Long> = mutableMapOf(),
    var catchTimes: MutableMap<Int, Long> = mutableMapOf(),
    var getFlagTimes: MutableMap<Int, Long> = mutableMapOf(),
    var totalTime: MutableMap<Int, Long> = mutableMapOf(),
    var winTimes: MutableMap<Int, Long> = mutableMapOf(),
)

private val statisticFileLock = Any()
private fun getStatisticData(filename: String): StatisticData = synchronized(statisticFileLock)
{
    val file = BukkitPlugin.getInstance().dataFolder.resolve("$filename.dat")
    if (!file.exists()) return StatisticData()
    val input = DataInputStream(file.inputStream())
    val withNoneTimes = mutableMapOf<Int, Long>()
    val withOtherTimes = mutableMapOf<Int, Long>()
    val catchTimes = mutableMapOf<Int, Long>()
    val getFlagTimes = mutableMapOf<Int, Long>()
    val totalTime = mutableMapOf<Int, Long>()
    val winTimes = mutableMapOf<Int, Long>()
    while (input.available() > 0)
    {
        val field = input.readUTF()
        val size = input.readInt()
        repeat(size)
        {
            val group = input.readInt()
            val time = input.readLong()
            when (field)
            {
                "withNoneTimes" -> withNoneTimes[group] = time
                "withOtherTimes" -> withOtherTimes[group] = time
                "catchTimes" -> catchTimes[group] = time
                "getFlagTimes" -> getFlagTimes[group] = time
                "totalTime" -> totalTime[group] = time
                "winTimes" -> winTimes[group] = time
            }
        }
    }
    return StatisticData(withNoneTimes, withOtherTimes, catchTimes, getFlagTimes, totalTime, winTimes)
}

private fun saveStatisticData(filename: String, data: StatisticData) = synchronized(statisticFileLock)
{
    val file = BukkitPlugin.getInstance().dataFolder.resolve("$filename.dat")
    DataOutputStream(file.outputStream()).use()
    { output ->
        listOf(
            "withNoneTimes" to data.withNoneTimes,
            "withOtherTimes" to data.withOtherTimes,
            "catchTimes" to data.catchTimes,
            "getFlagTimes" to data.getFlagTimes,
            "totalTime" to data.totalTime,
            "winTimes" to data.winTimes,
        ).forEach()
        { (field, map) ->
            output.writeUTF(field)
            output.writeInt(map.size)
            map.forEach()
            { (group, time) ->
                output.writeInt(group)
                output.writeLong(time)
            }
        }
    }
}

fun updateStatisticData(
    name: String? = null,
    block: StatisticData.() -> Unit
) = synchronized(statisticFileLock)
{
    val data = getStatisticData(name ?: "statistics")
    data.block()
    saveStatisticData(name ?: "statistics", data)
}

fun setupServer()
{
    server.createContext("/statistics")
    { exchange ->
        val statistic = getStatisticData(practiceMode?.name ?: "statistics")

        val sb = StringBuilder()

        sb.append("{")
        sb.append("\"withNoneTimes\": {")
        sb.append(statistic.withNoneTimes.entries.joinToString(",") { "\"${it.key}\": ${it.value}" })
        sb.append("},")
        sb.append("\"withOtherTimes\": {")
        sb.append(statistic.withOtherTimes.entries.joinToString(",") { "\"${it.key}\": ${it.value}" })
        sb.append("},")
        sb.append("\"catchTimes\": {")
        sb.append(statistic.catchTimes.entries.joinToString(",") { "\"${it.key}\": ${it.value}" })
        sb.append("},")
        sb.append("\"getFlagTimes\": {")
        sb.append(statistic.getFlagTimes.entries.joinToString(",") { "\"${it.key}\": ${it.value}" })
        sb.append("},")
        sb.append("\"totalTime\": {")
        sb.append(statistic.totalTime.entries.joinToString(",") { "\"${it.key}\": ${it.value}" })
        sb.append("},")
        sb.append("\"winTimes\": {")
        sb.append(statistic.winTimes.entries.joinToString(",") { "\"${it.key}\": ${it.value}" })
        sb.append("}")
        sb.append("}")

        val r = sb.toString()

        exchange.sendResponseHeaders(200, r.toByteArray().size.toLong())
        exchange.responseHeaders.add("Content-Type", "application/json")

        val os = exchange.responseBody
        os.write(r.toByteArray())
        os.close()
    }
    server.executor = null
    server.start()
}