package moe.tachyon.ctf.plugin

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.platform.function.onlinePlayers
import taboolib.common.platform.function.severe
import taboolib.platform.util.toBukkitLocation

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

    val location: Location get() = when (this)
    {
        IN_LEFT_FIELD, IN_LEFT_BASE -> Location(Bukkit.getWorld("world"), -21.5, 1.0, 0.5)
        IN_RIGHT_FIELD, IN_RIGHT_BASE -> Location(Bukkit.getWorld("world"), 22.5, 1.0, 0.5)
        IN_LEFT_PRISON -> Location(Bukkit.getWorld("world"), -15.5, 1.0, 28.5)
        IN_RIGHT_PRISON -> Location(Bukkit.getWorld("world"), 16.5, 1.0, 28.5)
        IN_CENTER -> Location(Bukkit.getWorld("world"), 0.5, 1.0, 0.5)
        IN_FIELD -> Location(Bukkit.getWorld("world"), 0.5, 1.0, 0.5)
        OUT_OF_FIELD -> Location(Bukkit.getWorld("world"), -35.5, 1.0, 0.5)
    }

    fun contains(location: Location): Boolean
    {
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        return when (this)
        {
            OUT_OF_FIELD -> x !in -24..24 || z !in -36..36 || y !in -64..20 || location.world?.name != "world"
            IN_LEFT_FIELD -> x in -24..-1 && z in -36..36 && y in -64..20 && location.world?.name == "world"
            IN_RIGHT_FIELD -> x in 1..24 && z in -36..36 && y in -64..20 && location.world?.name == "world"
            IN_LEFT_BASE -> x in -23..-21 && z in -1..1 && y in -64..20 && location.world?.name == "world"
            IN_RIGHT_BASE -> x in 21..23 && z in -1..1 && y in -64..20 && location.world?.name == "world"
            IN_LEFT_PRISON -> x in -18..-14 && z in 26..30 && y in -64..20 && location.world?.name == "world"
            IN_RIGHT_PRISON -> x in 14..18 && z in 26..30 && y in -64..20 && location.world?.name == "world"
            IN_CENTER -> x == 0 && z in -36..36 && y in -64..20 && location.world?.name == "world"
            IN_FIELD -> x in -24..24 && z in -36..36 && y in -64..20 && location.world?.name == "world"
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

/**
 * 更新玩家所属队伍
 */
fun updateTeam()
{
    if (MinecraftCtfPlugin.gameStatus == GameStatus.IN_GAME) return
    val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard
    if (scoreboard == null)
    {
        severe("无法获取主计分板!")
        return
    }
    val players = onlinePlayers()
    players.forEach()
    {
        val team = scoreboard.getEntryTeam(it.name)
        if (it.name.startsWith("CTF-${MinecraftCtfPlugin.leftGroup}-"))
        {
            if (team?.name != "L")
            {
                team?.removeEntry(it.name)
                val leftTeam = scoreboard.getTeam("L") ?: scoreboard.registerNewTeam("L")
                leftTeam.addEntry(it.name)
            }
        }
        else if (it.name.startsWith("CTF-${MinecraftCtfPlugin.rightGroup}-"))
        {
            if (team?.name != "R")
            {
                team?.removeEntry(it.name)
                val rightTeam = scoreboard.getTeam("R") ?: scoreboard.registerNewTeam("R")
                rightTeam.addEntry(it.name)
            }
        }
    }
    scoreboard.teams.forEach()
    {
        if (it.name !in listOf("L", "R")) it.unregister()
    }
    (scoreboard.getTeam("L") ?: scoreboard.registerNewTeam("L")).entries.forEach()
    {
        if (!it.startsWith("CTF-${MinecraftCtfPlugin.leftGroup}-") || players.none { player -> player.name == it })
        {
            scoreboard.getTeam("L")?.removeEntry(it)
        }
    }
    (scoreboard.getTeam("R") ?: scoreboard.registerNewTeam("R")).entries.forEach()
    {
        if (!it.startsWith("CTF-${MinecraftCtfPlugin.rightGroup}-") || players.none { player -> player.name == it })
        {
            scoreboard.getTeam("R")?.removeEntry(it)
        }
    }

    players.forEach()
    {
        Bukkit.getServer().pluginManager.callEvent(PlayerMoveEvent(it.cast(), it.location.toBukkitLocation(), it.location.toBukkitLocation()))
    }
}

/**
 * 判断一个玩家属于哪个队伍
 * @param playerName 玩家名称
 * @return 如果玩家属于左边队伍，则返回-1，如果玩家属于右边队伍，则返回1，否则返回0
 */
fun getPlayerTeam(playerName: String): PlayerTeam
{
    val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return PlayerTeam.NONE
    val team = scoreboard.getEntryTeam(playerName) ?: return PlayerTeam.NONE
    return when (team.name)
    {
        "L"  -> PlayerTeam.LEFT
        "R"  -> PlayerTeam.RIGHT
        else -> PlayerTeam.NONE
    }
}