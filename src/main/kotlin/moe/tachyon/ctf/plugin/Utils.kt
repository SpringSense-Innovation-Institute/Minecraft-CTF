package moe.tachyon.ctf.plugin

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.HumanEntity
import taboolib.common.platform.function.submit
import taboolib.platform.util.title
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
        OUT_OF_FIELD -> Location(world.bukkitWorld(), -35.5, 1.0, 0.5)
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

fun createGame(data: GameSettingData): GameWorld
{
    var world: GameWorld? = null
    val date = Date()
    val format = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
    val record = GameRecord.file({ world!! }, "${format.format(date)}_${data.leftGroupNumber}vs${data.rightGroupNumber}_${randomId()}")
    world = generateGameWorld(
        "game-${data.leftGroupNumber}vs${data.rightGroupNumber}-${randomId()}",
        data,
        record,
    )
    world.prepareGame()
    return world
}

fun HumanEntity.teleportToWorld(world: World = Bukkit.getWorld("world")!!)
{
    teleport(Location(world, 0.5, 1.0, 0.5))
}