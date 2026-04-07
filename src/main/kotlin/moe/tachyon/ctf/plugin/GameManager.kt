package moe.tachyon.ctf.plugin

import org.bukkit.Bukkit
import taboolib.common.platform.function.warning
import taboolib.common.util.randomDouble

const val PLAYER_PREPARE_MESSAGE = "I'm ready!"
const val GAME_PREPARED_MESSAGE = "Are you ready?"
const val GAME_START_MESSAGE = "Game start: "
const val GAME_END_MESSAGE = "Game over!"

sealed interface Enemy
{
    data object None: Enemy
    data class Group(val groupId: Int): Enemy
    data object Default: Enemy
    data object Random: Enemy
}

enum class MapType
{
    FIXED,
    RANDOM,
    UNSPECIFIED,
}

data class MatchSetting(
    val enemy: Enemy,
    val mapType: MapType,
    val playersPerGroup: Int,
)

private val waitList = mutableMapOf<String, MatchSetting>()

fun addToWaitList(playerName: String, setting: MatchSetting) = synchronized(waitList)
{
    waitList[playerName] = setting
    Bukkit.getPlayer(playerName)?.sendMessage("Received your match request! Waiting for matchmaking...")
}

fun removeFromWaitList(playerName: String) = synchronized(waitList)
{
    waitList.remove(playerName)
}

fun processWaitList() = synchronized(waitList)
{
    val list = waitList.toList().sortedBy()
    {
        return@sortedBy when (it.second.enemy)
        {
            is Enemy.None    -> 0
            is Enemy.Default -> 1
            is Enemy.Group   -> 2
            is Enemy.Random  ->
            {
                when (it.second.mapType)
                {
                    MapType.FIXED, MapType.RANDOM -> 3
                    MapType.UNSPECIFIED           -> 4
                }
            }
        }
    }.map { Triple(it.first, it.second, true) }.toMutableList()

    for (i in list.indices)
    {
        val (playerName, setting) = list[i]

        val player = Bukkit.getPlayer(playerName) ?: run()
        {
            waitList -= playerName
            list[i] = list[i].copy(third = false)
            continue
        }

        val group = playerName.split("-").getOrNull(1)?.toIntOrNull() ?: run()
        {
            waitList -= playerName
            list[i] = list[i].copy(third = false)
            continue
        }

        val world = tryGetGameWorld(group, setting)

        if (world != null)
        {
            player.teleport(world, LocationStatus.IN_FIELD)
            waitList -= playerName
            list[i] = list[i].copy(third = false)
            continue
        }

        if (setting.enemy is Enemy.None || setting.enemy is Enemy.Default)
        {
            makeSelfGame(playerName, group, setting.enemy is Enemy.Default, setting.playersPerGroup, setting.mapType)
            waitList -= playerName
            list[i] = list[i].copy(third = false)
            continue
        }

        for (j in 0 until i)
        {
            val (playerName2, setting2, valid) = list[j]
            if (!valid) continue

            val group2 = playerName2.split("-").getOrNull(1)?.toIntOrNull() ?: continue

            if (isGameSettingMatched(group, setting, group2, setting2))
            {
                makeGame(playerName, group, setting, playerName2, group2, setting2)
                waitList -= playerName
                waitList -= playerName2
                list[i] = list[i].copy(third = false)
                list[j] = list[j].copy(third = false)
                break
            }
        }
    }
}

private fun tryGetGameWorld(group: Int, setting: MatchSetting): GameWorld?
{
    return getGameWorlds().firstOrNull()
    {
        if (it.playerCountPerGroup != setting.playersPerGroup) return@firstOrNull false
        if (it.leftGroupNumber != group && it.rightGroupNumber != group) return@firstOrNull false
        if (setting.mapType != MapType.UNSPECIFIED && it.randomMap != (setting.mapType == MapType.RANDOM)) return@firstOrNull false
        val enemyMatched = when (setting.enemy)
        {
            is Enemy.None    -> it.leftGroupNumber == 0 || it.rightGroupNumber == 0
            is Enemy.Default -> it.leftGroupNumber == -1 || it.rightGroupNumber == -1
            is Enemy.Group -> it.leftGroupNumber == setting.enemy.groupId || it.rightGroupNumber == setting.enemy.groupId
            is Enemy.Random -> true
        }
        if (!enemyMatched) return@firstOrNull false
        if (it.gameStatus != GameStatus.PREPARING) return@firstOrNull false

        true
    }
}

private fun isGameSettingMatched(g1: Int, setting1: MatchSetting, g2: Int, setting2: MatchSetting): Boolean
{
    warning("Matching $g1 with $setting1 and $g2 with $setting2")
    if (g1 == g2) return false
    if (setting1.playersPerGroup != setting2.playersPerGroup) return false
    if (setting1.mapType != MapType.UNSPECIFIED && setting2.mapType != MapType.UNSPECIFIED && setting1.mapType != setting2.mapType) return false
    val enemyMatched1 = when (setting1.enemy)
    {
        is Enemy.None    -> false
        is Enemy.Default -> false
        is Enemy.Group   -> g2 == setting1.enemy.groupId
        is Enemy.Random  -> true
    }
    val enemyMatched2 = when (setting2.enemy)
    {
        is Enemy.None    -> false
        is Enemy.Default -> false
        is Enemy.Group   -> g1 == setting2.enemy.groupId
        is Enemy.Random  -> true
    }
    return enemyMatched1 && enemyMatched2
}

private fun makeSelfGame(player: String, group: Int, default: Boolean, playerCount: Int, map: MapType)
{
    val p = Bukkit.getPlayer(player) ?: return
    val atLeft = randomDouble() < 0.5
    val enemy = if (default) -1 else 0
    val leftGroup = if (atLeft) group else enemy
    val rightGroup = if (atLeft) enemy else group
    val randomMap = when (map)
    {
        MapType.FIXED       -> false
        MapType.RANDOM      -> true
        MapType.UNSPECIFIED -> randomDouble() < 0.5
    }
    val setting = GameSettingData(
        leftGroupNumber = leftGroup,
        rightGroupNumber = rightGroup,
        playerCountPerGroup = playerCount,
        randomMap = randomMap,
    )
    val world = createGame(setting, false)
    p.teleport(LocationStatus.IN_FIELD.location(world))
}

private fun makeGame(player1: String, group1: Int, setting1: MatchSetting, player2: String, group2: Int, setting2: MatchSetting)
{
    val p1 = Bukkit.getPlayer(player1) ?: return
    val p2 = Bukkit.getPlayer(player2) ?: return
    val atLeft = randomDouble() < 0.5
    val leftGroup = if (atLeft) group1 else group2
    val rightGroup = if (atLeft) group2 else group1
    val randomMap = when (setting1.mapType)
    {
        MapType.FIXED       -> false
        MapType.RANDOM      -> true
        MapType.UNSPECIFIED -> when (setting2.mapType)
        {
            MapType.FIXED       -> false
            MapType.RANDOM      -> true
            MapType.UNSPECIFIED -> randomDouble() < 0.5
        }
    }
    val setting = GameSettingData(
        leftGroupNumber = leftGroup,
        rightGroupNumber = rightGroup,
        playerCountPerGroup = setting1.playersPerGroup,
        randomMap = randomMap,
    )
    val world = createGame(setting, true)
    p1.teleport(LocationStatus.IN_FIELD.location(world))
    p2.teleport(LocationStatus.IN_FIELD.location(world))
}