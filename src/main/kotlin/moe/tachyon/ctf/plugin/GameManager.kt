package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.plugin.bot.createBots
import org.bukkit.Bukkit
import taboolib.common.platform.function.warning
import taboolib.common.util.randomDouble

const val PLAYER_PREPARE_MESSAGE = "I'm ready!"
const val GAME_PREPARED_MESSAGE = "Are you ready?"
const val GAME_START_MESSAGE = "Game start: "
const val GAME_END_MESSAGE = "Game over!"
const val GAME_RESULT_MESSAGE = "Game result: "

sealed interface Enemy
{
    data object Any: Enemy
    data class Specific(val group: Group): Enemy
}

enum class MapType
{
    FIXED,
    RANDOM,
    ANY,
}

data class MatchSetting(
    val group: Int,
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

private fun notifyMatchCreationFailure(error: Throwable, vararg playerNames: String)
{
    val message = error.message ?: error::class.simpleName ?: "未知错误"
    warning("创建比赛失败：$message")
    playerNames.forEach()
    { playerName ->
        Bukkit.getPlayer(playerName)?.sendMessage("§c创建比赛失败：$message")
    }
}

fun processWaitList() = synchronized(waitList)
{
    val list = waitList.toList().sortedBy()
    {
        when (it.second.enemy)
        {
            is Enemy.Specific  ->
            {
                when ((it.second.enemy as Enemy.Specific).group)
                {
                    is Group.None        -> 0
                    is Group.Bot         -> 1
                    is Group.PlayerGroup -> 2
                }
            }
            is Enemy.Any   ->
            {
                when (it.second.mapType)
                {
                    MapType.FIXED, MapType.RANDOM -> 3
                    MapType.ANY                   -> 4
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

        val world = tryGetGameWorld(setting)

        if (world != null)
        {
            joinGame(playerName, world, Group.PlayerGroup(setting.group))
            waitList -= playerName
            list[i] = list[i].copy(third = false)
            continue
        }

        if (performanceMode)
        {
            continue
        }

        if (setting.enemy is Enemy.Specific && (setting.enemy.group.isNone || setting.enemy.group.isBot))
        {
            try
            {
                makeSelfGame(
                    playerName,
                    setting.group,
                    setting.enemy.group,
                    setting.playersPerGroup,
                    setting.mapType
                )
            }
            catch (e: Throwable)
            {
                notifyMatchCreationFailure(e, playerName)
            }

            waitList -= playerName
            list[i] = list[i].copy(third = false)
            continue
        }

        for (j in 0 until i)
        {
            val (playerName2, setting2, valid) = list[j]
            if (!valid) continue

            if (isGameSettingMatched(setting, setting2))
            {
                try
                {
                    makeGame(playerName, setting, playerName2, setting2)
                }
                catch (e: Throwable)
                {
                    notifyMatchCreationFailure(e, playerName, playerName2)
                }
                waitList -= playerName
                waitList -= playerName2
                list[i] = list[i].copy(third = false)
                list[j] = list[j].copy(third = false)
                break
            }
        }
    }
}

private fun tryGetGameWorld(setting: MatchSetting): GameWorld?
{
    return getGameWorlds().firstOrNull()
    {
        if (it.playerCountPerGroup != setting.playersPerGroup) return@firstOrNull false
        if (it.leftGroup.id != setting.group && it.rightGroup.id != setting.group) return@firstOrNull false
        if (setting.mapType != MapType.ANY && it.randomMap != (setting.mapType == MapType.RANDOM)) return@firstOrNull false
        val enemyMatched =
            if (setting.enemy is Enemy.Any) true
            else when ((setting.enemy as Enemy.Specific).group)
            {
                is Group.None  -> it.leftGroup.isNone || it.rightGroup.isNone
                is Group.Bot   -> it.leftGroup.isBot || it.rightGroup.isBot
                is Group.PlayerGroup -> it.leftGroup.id == setting.enemy.group.id || it.rightGroup.id == setting.enemy.group.id
            }
        if (!enemyMatched) return@firstOrNull false
        if (it.gameStatus != GameStatus.PREPARING) return@firstOrNull false

        true
    }
}

private fun isGameSettingMatched(setting1: MatchSetting, setting2: MatchSetting): Boolean
{
    if (setting1.group == setting2.group) return false
    if (setting1.playersPerGroup != setting2.playersPerGroup) return false
    if (setting1.mapType != MapType.ANY && setting2.mapType != MapType.ANY && setting1.mapType != setting2.mapType) return false

    val enemyMatched1 =
        if (setting1.enemy is Enemy.Any) true
        else when ((setting1.enemy as Enemy.Specific).group)
        {
            is Group.None  -> false
            is Group.Bot   -> false
            is Group.PlayerGroup -> setting2.group == setting1.enemy.group.id
        }

    val enemyMatched2 =
        if (setting2.enemy is Enemy.Any) true
        else when ((setting2.enemy as Enemy.Specific).group)
        {
            is Group.None  -> false
            is Group.Bot   -> false
            is Group.PlayerGroup -> setting1.group == setting2.enemy.group.id
        }

    return enemyMatched1 && enemyMatched2
}

private fun makeSelfGame(player: String, group: Int, enemy: Group, playerCount: Int, map: MapType)
{
    Bukkit.getPlayer(player) ?: return
    val atLeft = randomDouble() < 0.5
    val group = Group.PlayerGroup(group)
    val leftGroup = if (atLeft) group else enemy
    val rightGroup = if (atLeft) enemy else group
    val randomMap = when (map)
    {
        MapType.FIXED  -> false
        MapType.RANDOM -> true
        MapType.ANY    -> randomDouble() < 0.5
    }
    val setting = GameSettingData(
        leftGroup = leftGroup,
        rightGroup = rightGroup,
        playerCountPerGroup = playerCount,
        randomMap = randomMap,
    )
    val world = createGame(setting, !enemy.isNone)
    joinGame(player, world, group)

    if (enemy.isBot) createBots(world)
}

private fun makeGame(player1: String, setting1: MatchSetting, player2: String, setting2: MatchSetting)
{
    val atLeft = randomDouble() < 0.5
    val leftGroup = if (atLeft) setting1.group else setting2.group
    val rightGroup = if (atLeft) setting2.group else setting1.group
    val randomMap = when (setting1.mapType)
    {
        MapType.FIXED  -> false
        MapType.RANDOM -> true
        MapType.ANY    -> when (setting2.mapType)
        {
            MapType.FIXED  -> false
            MapType.RANDOM -> true
            MapType.ANY    -> randomDouble() < 0.5
        }
    }
    val setting = GameSettingData(
        leftGroup = Group.PlayerGroup(leftGroup),
        rightGroup = Group.PlayerGroup(rightGroup),
        playerCountPerGroup = setting1.playersPerGroup,
        randomMap = randomMap,
    )
    val world = createGame(setting, true)

    joinGame(player1, world, Group.PlayerGroup(setting1.group))
    joinGame(player2, world, Group.PlayerGroup(setting2.group))
}

private fun joinGame(player: String, world: GameWorld, asGroup: Group) =
    world.addPlayer(player, asGroup)
